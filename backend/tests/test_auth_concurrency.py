"""Password resets must revoke sessions whose login is already in progress."""

from concurrent.futures import ThreadPoolExecutor
from threading import Event

import pytest
from sqlalchemy import text
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

from app import email_service
from app.models import Usuario
from app.routers import auth


def assert_user_is_locked(engine, user_id):
    """Probe lock ownership without sleeps or assumptions about scheduler speed."""
    with Session(engine) as probe:
        if engine.dialect.name == "sqlite":
            probe.execute(text("PRAGMA busy_timeout=0"))
            try:
                with pytest.raises(OperationalError, match="locked"):
                    probe.execute(text("BEGIN IMMEDIATE"))
            finally:
                probe.rollback()
                probe.execute(text("PRAGMA busy_timeout=30000"))
        else:
            # The login must own the row lock at the point where its password was verified.
            with pytest.raises(OperationalError) as locked:
                probe.query(Usuario).filter(Usuario.id == user_id).with_for_update(nowait=True).one()
            if engine.dialect.name == "postgresql":
                assert getattr(locked.value.orig, "sqlstate", None) == "55P03"


def test_reset_revokes_session_created_by_login_already_in_progress(client, db_session, monkeypatch):
    mailbox = []
    monkeypatch.setattr(email_service, "email_configurado", lambda: True)
    monkeypatch.setattr(email_service, "enviar_recuperacao", lambda email, token: mailbox.append(token))
    recovery = client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"})
    assert recovery.status_code == 200
    assert len(mailbox) == 1

    verified = Event()
    release_login = Event()
    reset_attempting_lock = Event()
    original_verify = auth.verificar_senha
    original_limit = auth.limitar

    def pause_after_verification(password, password_hash):
        valid = original_verify(password, password_hash)
        if valid and password == "123456":
            verified.set()
            assert release_login.wait(timeout=20), "Test did not release the verified login"
        return valid

    def signal_reset_started(db, identity, maximum, seconds=900):
        # SQLite serializes writers even before the user query, at its rate-limit write.
        if identity.startswith("reset-confirm:ip:"):
            reset_attempting_lock.set()
        return original_limit(db, identity, maximum, seconds)

    monkeypatch.setattr(auth, "verificar_senha", pause_after_verification)
    monkeypatch.setattr(auth, "limitar", signal_reset_started)
    reset_future = None
    with ThreadPoolExecutor(max_workers=2) as executor:
        login_future = executor.submit(
            client.post, "/auth/login",
            json={"email": "admin@remediocerto.com", "senha": "123456"},
        )
        try:
            assert verified.wait(timeout=20), "Login did not reach successful password verification"
            assert_user_is_locked(db_session.get_bind(), user_id=1)
            reset_future = executor.submit(
                client.post, "/auth/redefinir-senha",
                json={"token": mailbox[0], "nova_senha": "recuperada-concorrente123"},
            )
            assert reset_attempting_lock.wait(timeout=20), "Reset did not start its database work"
        finally:
            release_login.set()
        login_response = login_future.result(timeout=30)
        reset_response = reset_future.result(timeout=30)

    assert login_response.status_code == 200, login_response.text
    assert reset_response.status_code == 200, reset_response.text
    session = login_response.json()
    assert client.get("/auth/me", headers={"Authorization": f"Bearer {session['access_token']}"}).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": session["refresh_token"]}).status_code == 401
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "123456"}).status_code == 401
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "recuperada-concorrente123"}).status_code == 200
