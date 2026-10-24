from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.dependencies import exigir_admin, obter_usuario_atual
from app.enums import PerfilUsuario
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import EstoqueLote, Medicamento, UnidadeSaude, Usuario
from app.schemas import EstoqueCreate, EstoqueOut, EstoqueUpdate, MensagemOut
from app.services import sincronizar_alertas


router = APIRouter(prefix="/estoque", tags=["Estoque"])


@router.get("", response_model=list[EstoqueOut])
def listar_estoques(
    unidade: int | None = Query(default=None),
    unidade_id: int | None = Query(default=None),
    medicamento_id: int | None = None,
    estoque_baixo: bool = False,
    vencidos: bool = False,
    vencimento_proximo: bool = False,
    dias: int = Query(default=30, ge=1),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    id_unidade = unidade if unidade is not None else unidade_id
    q = db.query(EstoqueLote).options(
        joinedload(EstoqueLote.medicamento),
        joinedload(EstoqueLote.unidade),
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(EstoqueLote.unidade_id == usuario.unidade_id)

    if id_unidade:
        q = q.filter(EstoqueLote.unidade_id == id_unidade)
    if medicamento_id:
        q = q.filter(EstoqueLote.medicamento_id == medicamento_id)
    if estoque_baixo:
        q = q.join(Medicamento).filter(EstoqueLote.quantidade <= Medicamento.quantidade_minima)
    if vencidos:
        q = q.filter(EstoqueLote.validade < date.today())
    if vencimento_proximo:
        hoje = date.today()
        q = q.filter(EstoqueLote.validade >= hoje, EstoqueLote.validade <= hoje + timedelta(days=dias))

    return q.order_by(EstoqueLote.validade, EstoqueLote.lote).all()


@router.post("", response_model=EstoqueOut)
def criar_estoque(
    data: EstoqueCreate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    buscar_ou_404(db, Medicamento, data.medicamento_id, "Medicamento")
    buscar_ou_404(db, UnidadeSaude, data.unidade_id, "Unidade")

    estoque = EstoqueLote(**data.model_dump())
    estoque = salvar(db, estoque)

    sincronizar_alertas(db, estoque_lote_id=estoque.id)
    db.commit()
    db.refresh(estoque)
    return estoque


@router.get("/{estoque_id}", response_model=EstoqueOut)
def buscar_estoque(
    estoque_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    estoque = (
        db.query(EstoqueLote)
        .options(joinedload(EstoqueLote.medicamento), joinedload(EstoqueLote.unidade))
        .filter(EstoqueLote.id == estoque_id)
        .first()
    )
    if not estoque:
        return buscar_ou_404(db, EstoqueLote, estoque_id, "Estoque")
    if usuario.perfil == PerfilUsuario.funcionario and estoque.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Acesso negado para esta unidade.")
    return estoque


@router.put("/{estoque_id}", response_model=EstoqueOut)
def atualizar_estoque(
    estoque_id: int,
    data: EstoqueUpdate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    estoque = buscar_ou_404(db, EstoqueLote, estoque_id, "Estoque")
    body = data.model_dump(exclude_unset=True)

    if body.get("medicamento_id"):
        buscar_ou_404(db, Medicamento, body["medicamento_id"], "Medicamento")
    if body.get("unidade_id"):
        buscar_ou_404(db, UnidadeSaude, body["unidade_id"], "Unidade")

    aplicar_alteracoes(estoque, body)
    estoque = salvar(db, estoque)

    sincronizar_alertas(db, estoque_lote_id=estoque.id)
    db.commit()
    db.refresh(estoque)
    return estoque


@router.delete("/{estoque_id}", response_model=MensagemOut)
def remover_estoque(
    estoque_id: int,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    estoque = buscar_ou_404(db, EstoqueLote, estoque_id, "Estoque")
    db.delete(estoque)
    db.commit()
    return MensagemOut(mensagem="Estoque removido com sucesso.")
