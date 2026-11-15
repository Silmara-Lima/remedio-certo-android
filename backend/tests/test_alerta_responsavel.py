"""RF14: o alerta registra quem resolveu ou ignorou, e quando."""

from app.enums import TipoAlerta
from app.models import Alerta, EstoqueLote, Usuario


def _alerta(db):
    lote = db.get(EstoqueLote, 1)
    alerta = Alerta(
        tipo=TipoAlerta.estoque_baixo,
        mensagem="Alerta de teste",
        medicamento_id=lote.medicamento_id,
        unidade_id=lote.unidade_id,
        estoque_lote_id=lote.id,
    )
    db.add(alerta)
    db.commit()
    db.refresh(alerta)
    return alerta


def _maria(db):
    return db.query(Usuario).filter_by(email="maria@remediocerto.com").one()


def test_resolver_registra_quem_e_quando(client, db_session, funcionario_headers):
    alerta = _alerta(db_session)
    resposta = client.patch(f"/alertas/{alerta.id}/resolver", headers=funcionario_headers)
    assert resposta.status_code == 200, resposta.text
    corpo = resposta.json()
    maria = _maria(db_session)
    assert corpo["status"] == "resolvido"
    assert corpo["resolvido_em"] is not None
    assert corpo["resolvido_por"] == {"id": maria.id, "nome": maria.nome}


def test_ignorar_registra_quem_ignorou(client, db_session, funcionario_headers):
    alerta = _alerta(db_session)
    resposta = client.patch(f"/alertas/{alerta.id}/ignorar", headers=funcionario_headers)
    assert resposta.status_code == 200, resposta.text
    assert resposta.json()["resolvido_por"]["id"] == _maria(db_session).id


def test_lista_de_alertas_mostra_o_responsavel(client, db_session, funcionario_headers):
    alerta = _alerta(db_session)
    client.patch(f"/alertas/{alerta.id}/resolver", headers=funcionario_headers)
    lista = client.get("/alertas", headers=funcionario_headers).json()
    item = next(a for a in lista if a["id"] == alerta.id)
    assert item["resolvido_por"]["nome"] == _maria(db_session).nome


def test_alerta_ativo_nao_tem_responsavel(client, db_session, funcionario_headers):
    alerta = _alerta(db_session)
    resposta = client.get(f"/alertas/{alerta.id}", headers=funcionario_headers)
    assert resposta.status_code == 200, resposta.text
    assert resposta.json()["resolvido_por"] is None
