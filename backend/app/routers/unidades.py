from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import exigir_admin, obter_usuario_atual
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import UnidadeSaude, Usuario
from app.schemas import MensagemOut, UnidadeCreate, UnidadeOut, UnidadeUpdate


router = APIRouter(prefix="/unidades", tags=["Unidades de Saude"])


@router.get("", response_model=list[UnidadeOut])
def listar_unidades(
    somente_ativas: bool = True,
    busca: str | None = Query(default=None),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    q = db.query(UnidadeSaude)

    if somente_ativas:
        q = q.filter(UnidadeSaude.ativa.is_(True))

    if busca:
        termo = f"%{busca}%"
        q = q.filter(
            or_(
                UnidadeSaude.nome.ilike(termo),
                UnidadeSaude.cnpj.ilike(termo),
                UnidadeSaude.endereco.ilike(termo),
            )
        )

    return q.order_by(UnidadeSaude.nome).all()


@router.post("", response_model=UnidadeOut, dependencies=[Depends(exigir_admin)])
def criar_unidade(data: UnidadeCreate, db: Session = Depends(get_db)):
    unidade = UnidadeSaude(**data.model_dump())
    return salvar(db, unidade)


@router.get("/{unidade_id}", response_model=UnidadeOut)
def buscar_unidade(
    unidade_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    return buscar_ou_404(db, UnidadeSaude, unidade_id, "Unidade")


@router.put("/{unidade_id}", response_model=UnidadeOut, dependencies=[Depends(exigir_admin)])
def atualizar_unidade(unidade_id: int, data: UnidadeUpdate, db: Session = Depends(get_db)):
    unidade = buscar_ou_404(db, UnidadeSaude, unidade_id, "Unidade")
    aplicar_alteracoes(unidade, data.model_dump(exclude_unset=True))
    return salvar(db, unidade)


@router.delete("/{unidade_id}", response_model=MensagemOut, dependencies=[Depends(exigir_admin)])
def desativar_unidade(unidade_id: int, db: Session = Depends(get_db)):
    unidade = buscar_ou_404(db, UnidadeSaude, unidade_id, "Unidade")
    unidade.ativa = False
    db.commit()
    return MensagemOut(mensagem="Unidade desativada com sucesso.")
