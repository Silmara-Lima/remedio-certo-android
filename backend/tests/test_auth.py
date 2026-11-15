import time
from datetime import datetime, timedelta, timezone

import pytest
from jose import jwt

from app.auth_models import AuthSession, PasswordReset, RefreshToken
from app.auth_service import token_hash
from app.config import Settings, settings
from app.models import Usuario
from app.routers.auth import codigo_hash


def login(client):
    response = client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "123456"})
    assert response.status_code == 200
    return response.json()


def headers(token):
    return {"Authorization": f"Bearer {token}"}


def usuario_id(db_session, email):
    return db_session.query(Usuario).filter_by(email=email).one().id


def redefinir(client, email, codigo, nova_senha):
    return client.post("/auth/redefinir-senha", json={"email": email, "codigo": codigo, "nova_senha": nova_senha})


def test_refresh_rotation_replay_and_no_plaintext_in_database(client, db_session):
    original = login(client)
    claims = jwt.decode(original["access_token"], settings.SECRET_KEY, algorithms=["HS256"])
    assert claims["exp"] - claims["iat"] == 900
    assert original["expires_in"] == 900
    assert 604798 <= original["refresh_expires_in"] <= 604800
    stored = db_session.get(RefreshToken, token_hash(original["refresh_token"]))
    assert stored is not None
    assert stored.token_hash != original["refresh_token"]
    rotated = client.post("/auth/refresh", json={"refresh_token": original["refresh_token"]})
    assert rotated.status_code == 200
    new = rotated.json()
    assert new["refresh_token"] != original["refresh_token"]
    assert new["access_token"] != original["access_token"]
    assert client.get("/auth/me", headers=headers(new["access_token"])).status_code == 200
    assert client.post("/auth/refresh", json={"refresh_token": original["refresh_token"]}).status_code == 401
    assert client.get("/auth/me", headers=headers(new["access_token"])).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": new["refresh_token"]}).status_code == 401


def test_logout_revokes_only_current_session(client):
    first, second = login(client), login(client)
    assert client.post("/auth/logout", headers=headers(first["access_token"])).status_code == 200
    assert client.get("/auth/me", headers=headers(first["access_token"])).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": first["refresh_token"]}).status_code == 401
    assert client.get("/auth/me", headers=headers(second["access_token"])).status_code == 200


def test_expired_access_can_refresh_but_expired_family_cannot(client, db_session):
    data = login(client)
    claims = jwt.decode(data["access_token"], settings.SECRET_KEY, algorithms=["HS256"])
    claims["exp"] = datetime.now(timezone.utc) - timedelta(seconds=1)
    expired = jwt.encode(claims, settings.SECRET_KEY, algorithm="HS256")
    assert client.get("/auth/me", headers=headers(expired)).status_code == 401
    result = client.post("/auth/refresh", json={"refresh_token": data["refresh_token"]})
    assert result.status_code == 200
    session = db_session.get(AuthSession, claims["sid"])
    session.expires_at = int(time.time()) - 1
    db_session.commit()
    assert client.post("/auth/refresh", json={"refresh_token": result.json()["refresh_token"]}).status_code == 401


def test_changed_password_keeps_android_session_and_revokes_others(client):
    first, second = login(client), login(client)
    result = client.put("/auth/trocar-senha", headers=headers(first["access_token"]),
                        json={"senha_atual": "123456", "nova_senha": "senha-nova123"})
    assert result.status_code == 200
    assert client.get("/auth/me", headers=headers(first["access_token"])).status_code == 200
    assert client.get("/auth/me", headers=headers(second["access_token"])).status_code == 401


@pytest.fixture
def mailbox(monkeypatch):
    from app import email_service
    messages = []
    monkeypatch.setattr(email_service, "email_configurado", lambda: True)
    monkeypatch.setattr(email_service, "enviar_recuperacao", lambda email, token: messages.append((email, token)))
    return messages


def test_password_recovery_single_use_revokes_sessions_and_hides_account(client, db_session, mailbox):
    session = login(client)
    known = client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"})
    unknown = client.post("/auth/recuperar-senha", json={"email": "inexistente@example.com"})
    assert known.status_code == unknown.status_code == 200
    assert known.json() == unknown.json()
    assert len(mailbox) == 1
    code = mailbox[0][1]
    assert len(code) == 6 and code.isdigit()
    assert code not in known.text
    # O código fica no banco só como HMAC ligado ao usuário, nunca em texto.
    assert db_session.get(PasswordReset, codigo_hash(usuario_id(db_session, "admin@remediocerto.com"), code)) is not None
    assert db_session.get(PasswordReset, code) is None
    response = redefinir(client, "admin@remediocerto.com", code, "recuperada123")
    assert response.status_code == 200
    assert client.get("/auth/me", headers=headers(session["access_token"])).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": session["refresh_token"]}).status_code == 401
    assert redefinir(client, "admin@remediocerto.com", code, "outra123").status_code == 400
    assert client.post("/auth/login", json={"email": "admin@remediocerto.com", "senha": "recuperada123"}).status_code == 200


def test_reset_expiry_superseding_code_and_rate_limit(client, db_session, mailbox):
    for _ in range(2):
        assert client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"}).status_code == 200
    if mailbox[0][1] != mailbox[1][1]:  # 1 chance em 1 milhão de o sorteio repetir o código
        assert redefinir(client, "admin@remediocerto.com", mailbox[0][1], "nova123").status_code == 400
    reset = db_session.get(PasswordReset, codigo_hash(usuario_id(db_session, "admin@remediocerto.com"), mailbox[1][1]))
    reset.expires_at = int(time.time()) - 1
    db_session.commit()
    assert redefinir(client, "admin@remediocerto.com", mailbox[1][1], "nova123").status_code == 400
    assert client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"}).status_code == 200
    assert client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"}).status_code == 429


def test_email_unconfigured_explicit_and_uniform(client, monkeypatch):
    from app import email_service
    monkeypatch.setattr(email_service, "email_configurado", lambda: False)
    for email in ["admin@remediocerto.com", "nobody@example.com"]:
        assert client.post("/auth/recuperar-senha", json={"email": email}).status_code == 503


def test_mail_failure_never_logs_code_or_discloses_account(client, mailbox, monkeypatch, caplog):
    from app import email_service
    secret = "PRIVATE-PROVIDER-API-KEY"
    def fail(*args):
        raise RuntimeError(secret)
    monkeypatch.setattr(email_service, "enviar_recuperacao", fail)
    response = client.post("/auth/recuperar-senha", json={"email": "admin@remediocerto.com"})
    assert response.status_code == 200
    assert secret not in caplog.text + response.text


def test_long_passwords_and_legacy_bcrypt_preserved(client, db_session):
    from app.security import gerar_hash_senha, verificar_senha
    import bcrypt
    user = db_session.query(Usuario).filter_by(email="admin@remediocerto.com").one()
    user.senha_hash = bcrypt.hashpw(b"123456", bcrypt.gensalt(rounds=4)).decode()
    db_session.commit()
    assert login(client)["usuario"]["tipo"] == "ADMIN"
    long = "a" * 90
    hashed = gerar_hash_senha(long)
    assert verificar_senha(long, hashed)
    assert not verificar_senha("a" * 89 + "b", hashed)


def test_production_config_rejects_placeholder_secret_and_sqlite():
    with pytest.raises(ValueError):
        Settings(_env_file=None, ENVIRONMENT="production", DATABASE_URL="postgresql://host/db", SECRET_KEY="troque" * 10)
    with pytest.raises(ValueError):
        Settings(_env_file=None, ENVIRONMENT="production", DATABASE_URL="sqlite:///db", SECRET_KEY="a" * 64)


def test_forged_subject_and_inactive_user_rejected(client, db_session):
    session = login(client)
    claims = jwt.decode(session["access_token"], settings.SECRET_KEY, algorithms=["HS256"])
    claims["sub"] = "invalid-int"
    assert client.get("/auth/me", headers=headers(jwt.encode(claims, settings.SECRET_KEY, algorithm="HS256"))).status_code == 401
    user = db_session.query(Usuario).filter_by(email="admin@remediocerto.com").one()
    user.ativo = False
    db_session.commit()
    assert client.get("/auth/me", headers=headers(session["access_token"])).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": session["refresh_token"]}).status_code == 401


def test_admin_password_change_revokes_existing_session_and_reset(client, admin_headers, mailbox):
    response = client.post("/auth/login", json={"email": "maria@remediocerto.com", "senha": "123456"})
    employee = response.json()
    client.post("/auth/recuperar-senha", json={"email": "maria@remediocerto.com"})
    result = client.put(f"/usuarios/{employee['usuario']['id']}", headers=admin_headers, json={"senha": "nova-admin123"})
    assert result.status_code == 200
    assert client.get("/auth/me", headers=headers(employee["access_token"])).status_code == 401
    assert redefinir(client, "maria@remediocerto.com", mailbox[0][1], "outra123").status_code == 400
