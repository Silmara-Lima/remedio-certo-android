from datetime import date, datetime, time, timedelta

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.dependencies import obter_usuario_atual
from app.enums import PerfilUsuario, StatusAlerta
from app.models import Alerta, EstoqueLote, Medicamento, Movimentacao, UnidadeSaude, Usuario


router = APIRouter(prefix="/dashboard", tags=["Dashboard"])


@router.get("")
def dashboard(db: Session = Depends(get_db), usuario: Usuario = Depends(obter_usuario_atual)):
    hoje = date.today()
    limite = hoje + timedelta(days=30)

    filtro = []
    if usuario.perfil == PerfilUsuario.funcionario:
        filtro.append(EstoqueLote.unidade_id == usuario.unidade_id)

    unidades = db.query(UnidadeSaude).filter(UnidadeSaude.ativa.is_(True)).count()
    meds = db.query(Medicamento).filter(Medicamento.ativo.is_(True)).count()
    lotes = db.query(EstoqueLote).filter(*filtro).count()
    users = db.query(Usuario).filter(Usuario.ativo.is_(True)).count()

    q_alertas = db.query(Alerta).filter(Alerta.status == StatusAlerta.ativo)
    if usuario.perfil == PerfilUsuario.funcionario:
        q_alertas = q_alertas.filter(Alerta.unidade_id == usuario.unidade_id)
    alertas_total = q_alertas.count()

    baixo = (
        db.query(EstoqueLote)
        .join(Medicamento)
        .filter(*filtro)
        .filter(EstoqueLote.quantidade <= Medicamento.quantidade_minima)
        .count()
    )
    vencidos = db.query(EstoqueLote).filter(*filtro).filter(EstoqueLote.validade < hoje).count()
    validade = (
        db.query(EstoqueLote)
        .filter(*filtro)
        .filter(EstoqueLote.validade >= hoje, EstoqueLote.validade <= limite)
        .count()
    )

    hoje_mov = db.query(Movimentacao)
    if usuario.perfil == PerfilUsuario.funcionario:
        hoje_mov = hoje_mov.filter(Movimentacao.unidade_id == usuario.unidade_id)
    hoje_mov = hoje_mov.filter(
        Movimentacao.criada_em >= datetime.combine(hoje, time.min),
        Movimentacao.criada_em <= datetime.combine(hoje, time.max),
    ).count()

    movs = db.query(Movimentacao).options(
        joinedload(Movimentacao.medicamento),
        joinedload(Movimentacao.unidade),
        joinedload(Movimentacao.usuario),
    )
    if usuario.perfil == PerfilUsuario.funcionario:
        movs = movs.filter(Movimentacao.unidade_id == usuario.unidade_id)

    alertas = db.query(Alerta).options(
        joinedload(Alerta.medicamento),
        joinedload(Alerta.unidade),
        joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.medicamento),
        joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.unidade),
    )
    if usuario.perfil == PerfilUsuario.funcionario:
        alertas = alertas.filter(Alerta.unidade_id == usuario.unidade_id)

    return {
        "totalMedicamentos": meds,
        "alertasAtivos": alertas_total,
        "movimentacoesHoje": hoje_mov,
        "estoqueCritico": baixo + vencidos,
        "totalUnidades": unidades,
        "totalLotes": lotes,
        "totalUsuarios": users,
        "lotesEstoqueBaixo": baixo,
        "lotesVencidos": vencidos,
        "lotesVencimentoProximo": validade,
        "movimentacoesRecentes": [
            _movimentacao_para_dashboard(item)
            for item in movs.order_by(Movimentacao.criada_em.desc()).limit(10).all()
        ],
        "alertasRecentes": [
            _alerta_para_dashboard(item)
            for item in alertas.order_by(Alerta.gerado_em.desc()).limit(10).all()
        ],
    }


def _movimentacao_para_dashboard(item: Movimentacao) -> dict:
    return {
        "id": item.id,
        "tipo": item.tipo.value.upper(),
        "medicamento": item.medicamento.nome if item.medicamento else None,
        "unidade": item.unidade.nome if item.unidade else None,
        "lote": item.lote,
        "validade": item.validade.isoformat() if item.validade else None,
        "quantidade": item.quantidade,
        "responsavel": item.usuario.nome if item.usuario else None,
        "data": item.criada_em.isoformat() if item.criada_em else None,
    }


def _alerta_para_dashboard(item: Alerta) -> dict:
    return {
        "id": item.id,
        "tipo": "ESTOQUE_BAIXO" if item.tipo.value == "estoque_baixo" else "VALIDADE",
        "mensagem": item.mensagem,
        "status": "PENDENTE" if item.status.value == "ativo" else item.status.value.upper(),
        "dataGeracao": item.gerado_em.isoformat() if item.gerado_em else None,
        "dataResolucao": item.resolvido_em.isoformat() if item.resolvido_em else None,
        "medicamentoId": item.medicamento_id,
        "unidadeSaudeId": item.unidade_id,
    }
