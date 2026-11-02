import time
import threading
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta

import pytest
from sqlalchemy import event, select, text
from sqlalchemy.orm import sessionmaker

from app import push
from app.enums import StatusAlerta, TipoAlerta
from app.models import Alerta, ConfiguracaoAlerta, EstoqueLote, UnidadeSaude, Usuario
from app.push_models import AlertCondition, DispositivoPush, PushDelivery
from app.services import sincronizar_alertas

TOKEN = "fcm-test-" + "a" * 100


def register(client, headers, token=TOKEN):
    response = client.post("/dispositivos", headers=headers, json={"token": token})
    assert response.status_code == 200, response.text
    assert "token" not in response.json()


def fake_sender(monkeypatch):
    messages = []
    monkeypatch.setattr(push, "firebase_configured", lambda: True)
    monkeypatch.setattr(push, "_send_push", lambda token, data: messages.append((token, data)))
    return messages


def test_push_only_for_users_unit_and_minimal_payload(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 2
    assert len(messages) == 2
    for token, data in messages:
        assert token == TOKEN
        assert data["unidade_id"] == "1"
        assert set(data) == {"alerta_id", "unidade_id", "tipo", "deep_link"}
        assert data["deep_link"] == f"remediocerto://alertas/{data['alerta_id']}"
    assert push.process_push_deliveries(db_session) == 0


def test_outbox_persists_retry_and_does_not_call_again_before_due(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    monkeypatch.setattr(push, "firebase_configured", lambda: True)

    def fail(token, data):
        raise RuntimeError("must never be logged: " + token)

    monkeypatch.setattr(push, "_send_push", fail)
    assert push.process_push_deliveries(db_session) == 0
    rows = list(db_session.scalars(select(PushDelivery)))
    assert all(row.status == "pendente" and row.tentativas == 1 for row in rows)
    assert all(row.proxima_tentativa > int(time.time()) and row.erro_tipo == "RuntimeError" for row in rows)
    assert push.process_push_deliveries(db_session) == 0
    for row in rows:
        row.proxima_tentativa = 0
    db_session.commit()
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 2
    assert len(messages) == 2


def test_invalid_fcm_token_removes_device_and_deliveries(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    monkeypatch.setattr(push, "firebase_configured", lambda: True)

    def unregistered(token, data):
        raise push.UnregisteredDevice()

    monkeypatch.setattr(push, "_send_push", unregistered)
    assert push.process_push_deliveries(db_session) == 0
    assert db_session.query(DispositivoPush).count() == 0
    assert db_session.query(PushDelivery).count() == 0


def test_reassign_shared_phone_removes_previous_accounts_queue(
    client, db_session, funcionario_headers, funcionario_outra_unidade_headers, monkeypatch,
):
    register(client, funcionario_headers)
    register(client, funcionario_outra_unidade_headers)
    messages = fake_sender(monkeypatch)
    push.process_push_deliveries(db_session)
    assert messages
    assert all(data["unidade_id"] == "2" for _, data in messages)
    assert db_session.query(DispositivoPush).one().usuario_id == 3


def test_cannot_unregister_another_users_device(client, db_session, funcionario_headers, funcionario_outra_unidade_headers):
    register(client, funcionario_headers)
    response = client.request("DELETE", "/dispositivos", headers=funcionario_outra_unidade_headers, json={"token": TOKEN})
    assert response.status_code == 200
    assert db_session.query(DispositivoPush).count() == 1
    response = client.request("DELETE", "/dispositivos", headers=funcionario_headers, json={"token": TOKEN})
    assert response.status_code == 200
    assert db_session.query(DispositivoPush).count() == 0
    assert db_session.query(PushDelivery).count() == 0


def test_scope_is_revalidated_before_sending(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    user = db_session.get(Usuario, 2)
    user.unidade_id = 2
    db_session.commit()
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 0
    assert not messages
    assert all(row.status == "cancelado" for row in db_session.query(PushDelivery).all())


def test_disabling_push_cancels_pending_notifications(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    cfg = db_session.query(ConfiguracaoAlerta).filter_by(unidade_id=1).one()
    cfg.notificacao_push = False
    db_session.commit()
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 0
    assert not messages


def test_no_credentials_leaves_events_pending(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    monkeypatch.setattr(push, "firebase_configured", lambda: False)
    assert push.process_push_deliveries(db_session) == 0
    assert all(row.status == "pendente" and row.tentativas == 0 for row in db_session.query(PushDelivery).all())


def test_logout_cancels_push_even_if_android_did_not_unregister(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    assert client.post("/auth/logout", headers=funcionario_headers).status_code == 200
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 0
    assert not messages
    assert all(row.status == "cancelado" for row in db_session.query(PushDelivery).all())


def test_register_after_new_login_replaces_revoked_session(client, db_session, funcionario_headers, monkeypatch):
    register(client, funcionario_headers)
    old_session = db_session.query(DispositivoPush).one().session_id
    assert client.post("/auth/logout", headers=funcionario_headers).status_code == 200
    response = client.post("/auth/login", json={"email": "maria@remediocerto.com", "senha": "123456"})
    headers = {"Authorization": "Bearer " + response.json()["access_token"]}
    register(client, headers)
    db_session.expire_all()
    assert db_session.query(DispositivoPush).one().session_id != old_session
    messages = fake_sender(monkeypatch)
    assert push.process_push_deliveries(db_session) == 2
    assert len(messages) == 2


def test_acknowledged_alert_does_not_reappear_until_condition_clears(client, db_session, funcionario_headers):
    register(client, funcionario_headers)
    alerta = db_session.query(Alerta).filter_by(tipo=TipoAlerta.estoque_baixo, unidade_id=1).one()
    original_id = alerta.id
    assert client.patch(f"/alertas/{original_id}/resolver", headers=funcionario_headers).status_code == 200
    db_session.expire_all()
    before = db_session.query(Alerta).count()
    sincronizar_alertas(db_session)
    db_session.commit()
    assert db_session.query(Alerta).count() == before
    assert db_session.get(Alerta, original_id).status == StatusAlerta.resolvido
    stock = db_session.query(EstoqueLote).filter_by(lote="LOTE-BAIXO").one()
    stock.quantidade = 100
    sincronizar_alertas(db_session)
    db_session.commit()
    stock.quantidade = 20
    sincronizar_alertas(db_session)
    db_session.commit()
    assert db_session.query(Alerta).count() == before + 1
    novo = db_session.query(Alerta).filter_by(tipo=TipoAlerta.estoque_baixo, unidade_id=1, status=StatusAlerta.ativo).one()
    assert novo.id != original_id


def test_low_stock_uses_total_across_lots_and_expiry_requires_balance(db_session):
    low = db_session.query(EstoqueLote).filter_by(lote="LOTE-BAIXO").one()
    db_session.add(EstoqueLote(
        medicamento_id=low.medicamento_id, unidade_id=low.unidade_id,
        lote="LOTE-COM-SALDO", validade=date.today() + timedelta(days=100),
        quantidade_inicial=100, quantidade=100,
    ))
    expired = db_session.query(EstoqueLote).filter_by(lote="LOTE-VENCIDO").one()
    expired.quantidade = 0
    sincronizar_alertas(db_session)
    db_session.commit()
    assert db_session.query(Alerta).filter_by(
        tipo=TipoAlerta.estoque_baixo, unidade_id=1, status=StatusAlerta.ativo,
    ).count() == 0
    assert db_session.query(Alerta).filter_by(
        tipo=TipoAlerta.vencido, status=StatusAlerta.ativo,
    ).count() == 0


def test_configuration_scope(client, funcionario_headers):
    response = client.get("/configuracoes-alerta", headers=funcionario_headers)
    assert response.status_code == 200
    assert [cfg["unidade_id"] for cfg in response.json()] == [1]
    assert client.get("/configuracoes-alerta/2", headers=funcionario_headers).status_code == 403


def test_postgres_movement_and_push_share_lock_order(client, db_session, funcionario_headers, monkeypatch):
    """Exercise real row locks in the PostgreSQL CI job; SQLite cannot model them."""
    engine = db_session.get_bind()
    if engine.dialect.name != "postgresql":
        pytest.skip("Requires PostgreSQL row locks (covered by PostgreSQL CI job).")
    register(client, funcionario_headers)
    db_session.rollback()
    messages = fake_sender(monkeypatch)
    sessions = sessionmaker(bind=engine, autoflush=False)
    user_locked = threading.Event()
    sender_at_user_lock = threading.Event()

    def observe_user_lock(conn, cursor, statement, parameters, context, executemany):
        sql = statement.lower()
        if threading.current_thread().name == "push-worker" and "usuarios" in sql and "for update" in sql:
            sender_at_user_lock.set()

    def movement():
        with sessions() as db:
            db.execute(text("SET LOCAL lock_timeout = '5s'"))
            db.scalar(select(Usuario).where(Usuario.id == 2).with_for_update())
            db.scalar(select(UnidadeSaude).where(UnidadeSaude.id == 1).with_for_update())
            user_locked.set()
            assert sender_at_user_lock.wait(5)
            stock = db.scalar(select(EstoqueLote).where(EstoqueLote.lote == "LOTE-BAIXO").with_for_update())
            stock.quantidade = 19
            sincronizar_alertas(db, estoque_lote_id=stock.id)
            db.commit()

    def sender():
        threading.current_thread().name = "push-worker"
        assert user_locked.wait(5)
        with sessions() as db:
            return push.process_push_deliveries(db)

    event.listen(engine, "before_cursor_execute", observe_user_lock)
    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            moving = pool.submit(movement)
            sending = pool.submit(sender)
            moving.result(timeout=15)
            assert sending.result(timeout=15) == 2
    finally:
        event.remove(engine, "before_cursor_execute", observe_user_lock)
    assert len(messages) == 2
