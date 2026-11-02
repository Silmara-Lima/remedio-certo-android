import csv
import io
from datetime import date, datetime, time

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.dependencies import obter_usuario_atual
from app.enums import PerfilUsuario, TipoMovimentacao
from app.models import EstoqueLote, Medicamento, Movimentacao, Usuario


router = APIRouter(prefix="/relatorios", tags=["Relatorios"])


def _consulta_movimentacoes(
    db: Session,
    usuario: Usuario,
    unidade_id: int | None,
    medicamento_id: int | None,
    tipo: TipoMovimentacao | None,
    data_inicio: date | None,
    data_fim: date | None,
):
    q = db.query(Movimentacao).options(
        joinedload(Movimentacao.medicamento),
        joinedload(Movimentacao.unidade),
        joinedload(Movimentacao.usuario),
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(Movimentacao.unidade_id == usuario.unidade_id)
    if unidade_id:
        q = q.filter(Movimentacao.unidade_id == unidade_id)
    if medicamento_id:
        q = q.filter(Movimentacao.medicamento_id == medicamento_id)
    if tipo:
        q = q.filter(Movimentacao.tipo == tipo)
    if data_inicio:
        q = q.filter(Movimentacao.criada_em >= datetime.combine(data_inicio, time.min))
    if data_fim:
        q = q.filter(Movimentacao.criada_em <= datetime.combine(data_fim, time.max))

    return q.order_by(Movimentacao.criada_em.desc())


@router.get("/movimentacoes")
def relatorio_movimentacoes(
    unidadeId: int | None = Query(default=None),
    unidade_id: int | None = Query(default=None),
    medicamento_id: int | None = None,
    tipo: str | None = None,
    dataInicio: date | None = Query(default=None),
    dataFim: date | None = Query(default=None),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    id_unidade = unidadeId if unidadeId is not None else unidade_id
    tipo_ok = _normalizar_tipo_movimentacao(tipo)
    lista = _consulta_movimentacoes(db, usuario, id_unidade, medicamento_id, tipo_ok, dataInicio, dataFim).all()
    entradas = sum(item.quantidade for item in lista if item.tipo == TipoMovimentacao.entrada)
    saidas = sum(item.quantidade for item in lista if item.tipo in (TipoMovimentacao.saida, TipoMovimentacao.descarte))

    return {
        "resumo": {
            "total_movimentacoes": len(lista),
            "total_entradas": entradas,
            "total_saidas": saidas,
        },
        "resultados": [_movimentacao_para_dict(item) for item in lista],
    }


@router.get("/movimentacoes.csv")
def relatorio_movimentacoes_csv(
    unidadeId: int | None = Query(default=None),
    unidade_id: int | None = Query(default=None),
    medicamento_id: int | None = None,
    tipo: str | None = None,
    dataInicio: date | None = Query(default=None),
    dataFim: date | None = Query(default=None),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    id_unidade = unidadeId if unidadeId is not None else unidade_id
    tipo_ok = _normalizar_tipo_movimentacao(tipo)
    lista = _consulta_movimentacoes(db, usuario, id_unidade, medicamento_id, tipo_ok, dataInicio, dataFim).all()
    csv_file = io.StringIO()
    campos = ["id", "tipo", "medicamento", "unidade", "lote", "validade", "quantidade", "usuario", "numero_documento", "observacao", "criada_em"]
    writer = csv.DictWriter(csv_file, fieldnames=campos)
    writer.writeheader()

    for item in lista:
        writer.writerow(_movimentacao_para_dict(item))

    csv_file.seek(0)
    return StreamingResponse(
        iter([csv_file.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=movimentacoes.csv"},
    )


@router.get("/estoque-critico")
def relatorio_estoque_critico(db: Session = Depends(get_db), usuario: Usuario = Depends(obter_usuario_atual)):
    hoje = date.today()
    q = (
        db.query(EstoqueLote)
        .options(joinedload(EstoqueLote.medicamento), joinedload(EstoqueLote.unidade))
        .join(Medicamento)
        .filter((EstoqueLote.quantidade <= Medicamento.quantidade_minima) | (EstoqueLote.validade < hoje))
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(EstoqueLote.unidade_id == usuario.unidade_id)

    lista = []
    for estoque in q.order_by(EstoqueLote.validade).all():
        lista.append(
            {
                "estoque_id": estoque.id,
                "medicamento": estoque.medicamento.nome,
                "unidade": estoque.unidade.nome,
                "lote": estoque.lote,
                "validade": estoque.validade.isoformat(),
                "quantidade": estoque.quantidade,
                "quantidade_minima": estoque.medicamento.quantidade_minima,
                "estoque_baixo": estoque.quantidade <= estoque.medicamento.quantidade_minima,
                "vencido": estoque.validade < hoje,
            }
        )
    return lista


def _movimentacao_para_dict(item: Movimentacao) -> dict:
    return {
        "id": item.id,
        "tipo": item.tipo.value,
        "medicamento": item.medicamento.nome if item.medicamento else "",
        "unidade": item.unidade.nome if item.unidade else "",
        "lote": item.lote,
        "validade": item.validade.isoformat(),
        "quantidade": item.quantidade,
        "usuario": item.usuario.nome if item.usuario else "",
        "numero_documento": item.numero_documento or "",
        "observacao": item.observacao or "",
        "criada_em": item.criada_em.isoformat() if item.criada_em else "",
    }


def _normalizar_tipo_movimentacao(tipo: str | None) -> TipoMovimentacao | None:
    if not tipo:
        return None
    try:
        return TipoMovimentacao(tipo.lower())
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Tipo de movimentacao invalido.") from e
