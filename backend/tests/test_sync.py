"""Offline retries must never apply a movement twice or allow a negative balance."""

from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from uuid import uuid4

from fastapi import HTTPException
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.models import EstoqueLote, Medicamento, Movimentacao, UnidadeSaude, Usuario
from app.routers import movimentacoes
from app.sync_models import SyncOperation
from conftest import seed_test_data


def movement(db, *, stock_id=1, quantidade=10, tipo="saida", **changes):
    stock = db.get(EstoqueLote, stock_id)
    result = {
        "tipo": tipo, "medicamento_id": stock.medicamento_id,
        "unidade_id": stock.unidade_id, "lote": stock.lote,
        "validade": stock.validade.isoformat(), "quantidade": quantidade,
        "estoque_lote_id": stock.id,
    }
    result.update(changes)
    return result


def offline(payload, id_local=None):
    return {"id_local": id_local or str(uuid4()), **payload}


def sync(client, headers, *items):
    response = client.post("/movimentacoes/lote", headers=headers, json={"movimentacoes": list(items)})
    assert response.status_code == 200, response.text
    return response.json()["resultados"]


def balance(db, stock_id=1):
    db.expire_all()
    return db.get(EstoqueLote, stock_id).quantidade


def test_retry_survives_new_request_and_returns_same_receipt(client, db_session, funcionario_headers):
    item = offline(movement(db_session))
    first = sync(client, funcionario_headers, item)[0]
    retry = sync(client, funcionario_headers, item)[0]
    assert first["status"] == retry["status"] == "aceito"
    assert first["repetida"] is False
    assert retry["repetida"] is True
    assert first["movimentacao"]["id"] == retry["movimentacao"]["id"]
    assert balance(db_session) == 90
    assert db_session.query(Movimentacao).count() == 1
    assert db_session.query(SyncOperation).count() == 1


def test_repeated_item_inside_same_batch_is_applied_once(client, db_session, funcionario_headers):
    item = offline(movement(db_session))
    receipts = sync(client, funcionario_headers, item, item)
    assert [r["repetida"] for r in receipts] == [False, True]
    assert balance(db_session) == 90


def test_same_uuid_with_changed_payload_is_rejected(client, db_session, funcionario_headers):
    item = offline(movement(db_session))
    sync(client, funcionario_headers, item)
    receipt = sync(client, funcionario_headers, {**item, "quantidade": 20})[0]
    assert receipt["codigo"] == "ID_LOCAL_REUTILIZADO"
    assert balance(db_session) == 90


def test_batch_reports_independent_failures_and_persists_rejection(client, db_session, funcionario_headers):
    insufficient = offline(movement(db_session, quantidade=101))
    valid = offline(movement(db_session, quantidade=20))
    receipts = sync(client, funcionario_headers, insufficient, valid)
    assert [r["status"] for r in receipts] == ["rejeitado", "aceito"]
    assert receipts[0]["codigo"] == "HTTP_400"
    # New stock after failure must not make an old receipt silently change its outcome.
    assert client.post("/movimentacoes", headers=funcionario_headers, json=movement(db_session, tipo="entrada", quantidade=100)).status_code == 200
    retry = sync(client, funcionario_headers, insufficient)[0]
    assert retry["status"] == "rejeitado"
    assert retry["repetida"] is True
    assert balance(db_session) == 180


def test_invalid_item_does_not_discard_valid_items(client, db_session, funcionario_headers):
    invalid = offline(movement(db_session, quantidade="invalid"))
    valid = offline(movement(db_session, quantidade=5))
    receipts = sync(client, funcionario_headers, invalid, valid)
    assert receipts[0]["codigo"] == "HTTP_422"
    assert receipts[1]["status"] == "aceito"
    assert sync(client, funcionario_headers, invalid)[0]["repetida"] is True
    assert balance(db_session) == 95


def test_id_local_is_scoped_to_authenticated_user(client, db_session, admin_headers, funcionario_headers):
    item = offline(movement(db_session, quantidade=5))
    a = sync(client, admin_headers, item)[0]
    b = sync(client, funcionario_headers, item)[0]
    assert a["status"] == b["status"] == "aceito"
    assert a["movimentacao"]["usuario_id"] != b["movimentacao"]["usuario_id"]
    assert balance(db_session) == 90


def test_foreign_unit_and_forged_stock_identity_do_not_change_stock(client, db_session, funcionario_headers):
    forged_id = offline(movement(db_session, estoque_lote_id=3))
    foreign_unit = offline(movement(db_session, stock_id=3, tipo="descarte"))
    receipts = sync(client, funcionario_headers, forged_id, foreign_unit)
    assert [r["codigo"] for r in receipts] == ["HTTP_409", "HTTP_403"]
    assert balance(db_session, 1) == 100
    assert balance(db_session, 3) == 10
    assert db_session.query(Movimentacao).count() == 0


def test_online_endpoint_uses_same_identity_protection(client, db_session, funcionario_headers):
    response = client.post("/movimentacoes", headers=funcionario_headers, json=movement(db_session, estoque_lote_id=3))
    assert response.status_code == 409
    assert balance(db_session, 3) == 10


def test_expired_output_is_rejected_but_disposal_is_allowed(client, db_session, admin_headers):
    expired = offline(movement(db_session, stock_id=3, quantidade=2))
    disposal = offline(movement(db_session, stock_id=3, tipo="descarte", quantidade=2))
    receipts = sync(client, admin_headers, expired, disposal)
    assert [r["status"] for r in receipts] == ["rejeitado", "aceito"]
    assert balance(db_session, 3) == 8


def test_adjustment_requires_reason_and_sets_absolute_balance(client, db_session, funcionario_headers):
    missing = offline(movement(db_session, tipo="ajuste", quantidade=0, observacao="  "))
    correct = offline(movement(db_session, tipo="ajuste", quantidade=0, observacao="Contagem fisica"))
    receipts = sync(client, funcionario_headers, missing, correct)
    assert [r["status"] for r in receipts] == ["rejeitado", "aceito"]
    assert balance(db_session) == 0


@pytest.mark.parametrize("entity", [Medicamento, UnidadeSaude])
def test_inactive_records_block_offline_movement(client, db_session, funcionario_headers, entity):
    record = db_session.get(entity, 1)
    if entity is Medicamento:
        record.ativo = False
    else:
        record.ativa = False
    db_session.commit()
    receipt = sync(client, funcionario_headers, offline(movement(db_session)))[0]
    assert receipt["status"] == "rejeitado"
    assert balance(db_session) == 100


def test_invalid_uuid_and_batch_limit(client, db_session, funcionario_headers):
    item = offline(movement(db_session), "not-a-uuid")
    assert sync(client, funcionario_headers, item)[0]["codigo"] == "ID_LOCAL_INVALIDO"
    response = client.post("/movimentacoes/lote", headers=funcionario_headers, json={"movimentacoes": [item] * 101})
    assert response.status_code == 422
    assert balance(db_session) == 100


def test_failure_after_stock_update_rolls_back_item_and_keeps_receipt(client, db_session, funcionario_headers, monkeypatch):
    original = movimentacoes.sincronizar_alertas

    def fail_after_stock_update(*args, **kwargs):
        raise HTTPException(status_code=409, detail="Conflito de teste")

    monkeypatch.setattr(movimentacoes, "sincronizar_alertas", fail_after_stock_update)
    item = offline(movement(db_session))
    assert sync(client, funcionario_headers, item)[0]["status"] == "rejeitado"
    monkeypatch.setattr(movimentacoes, "sincronizar_alertas", original)
    assert sync(client, funcionario_headers, item)[0]["repetida"] is True
    assert balance(db_session) == 100
    assert db_session.query(Movimentacao).count() == 0


def test_reassigned_employee_cannot_read_old_unit_receipt(client, db_session, funcionario_headers):
    item = offline(movement(db_session))
    assert sync(client, funcionario_headers, item)[0]["status"] == "aceito"
    user = db_session.query(Usuario).filter_by(email="maria@remediocerto.com").one()
    user.unidade_id = 2
    db_session.commit()
    retry = sync(client, funcionario_headers, item)[0]
    assert retry["codigo"] == "HTTP_403"
    assert retry["movimentacao"] is None


@pytest.mark.parametrize("same_receipt", [True, False])
def test_concurrent_retries_and_outputs_keep_stock_consistent(db_session, tmp_path, same_receipt):
    # In-memory StaticPool shares one connection and cannot represent independent writers.
    # PostgreSQL CI exercises its actual row locks; SQLite uses separate file connections.
    source_engine = db_session.get_bind()
    disposable = source_engine.dialect.name == "sqlite"
    engine = create_engine(
        f"sqlite:///{(tmp_path / 'concurrent.db').as_posix()}",
        connect_args={"check_same_thread": False, "timeout": 30},
    ) if disposable else source_engine
    Session = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    if disposable:
        Base.metadata.create_all(engine)
        with Session() as db:
            seed_test_data(db)
    with Session() as db:
        item = offline(movement(db, quantidade=60))
    barrier = Barrier(2)

    def worker(index):
        with Session() as db:
            user = db.query(Usuario).filter_by(email="maria@remediocerto.com" if index == 0 or same_receipt else "admin@remediocerto.com").one()
            operation = item if index == 0 or same_receipt else {**item, "id_local": str(uuid4())}
            barrier.wait(timeout=10)
            movimentacoes._serializar_escritas(db, user)
            receipt = movimentacoes._sincronizar_item(db, operation, user)
            db.commit()
            return receipt

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            receipts = list(executor.map(worker, range(2)))
        with Session() as db:
            assert db.get(EstoqueLote, 1).quantidade == 40
            assert db.query(Movimentacao).count() == 1
        if same_receipt:
            assert [r["status"] for r in receipts] == ["aceito", "aceito"]
            assert sorted(r["repetida"] for r in receipts) == [False, True]
        else:
            assert sorted(r["status"] for r in receipts) == ["aceito", "rejeitado"]
    finally:
        if disposable:
            engine.dispose()
