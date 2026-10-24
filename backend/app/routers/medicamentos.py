from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import exigir_admin, obter_usuario_atual
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import Medicamento, Usuario
from app.schemas import MedicamentoCreate, MedicamentoOut, MedicamentoUpdate, MensagemOut


router = APIRouter(prefix="/medicamentos", tags=["Medicamentos"])


@router.get("", response_model=list[MedicamentoOut])
def listar_medicamentos(
    somente_ativos: bool = True,
    busca: str | None = Query(default=None),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    q = db.query(Medicamento)

    if somente_ativos:
        q = q.filter(Medicamento.ativo.is_(True))

    if busca:
        termo = f"%{busca}%"
        q = q.filter(
            or_(
                Medicamento.nome.ilike(termo),
                Medicamento.principio_ativo.ilike(termo),
                Medicamento.fabricante.ilike(termo),
                Medicamento.codigo_barras.ilike(termo),
                Medicamento.registro_anvisa.ilike(termo),
            )
        )

    return q.order_by(Medicamento.nome).all()


@router.post("", response_model=MedicamentoOut, dependencies=[Depends(exigir_admin)])
def criar_medicamento(data: MedicamentoCreate, db: Session = Depends(get_db)):
    medicamento = Medicamento(**data.model_dump())
    return salvar(db, medicamento)


@router.get("/{medicamento_id}", response_model=MedicamentoOut)
def buscar_medicamento(
    medicamento_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    return buscar_ou_404(db, Medicamento, medicamento_id, "Medicamento")


@router.put("/{medicamento_id}", response_model=MedicamentoOut, dependencies=[Depends(exigir_admin)])
def atualizar_medicamento(medicamento_id: int, data: MedicamentoUpdate, db: Session = Depends(get_db)):
    medicamento = buscar_ou_404(db, Medicamento, medicamento_id, "Medicamento")
    aplicar_alteracoes(medicamento, data.model_dump(exclude_unset=True))
    return salvar(db, medicamento)


@router.delete("/{medicamento_id}", response_model=MensagemOut, dependencies=[Depends(exigir_admin)])
def desativar_medicamento(medicamento_id: int, db: Session = Depends(get_db)):
    medicamento = buscar_ou_404(db, Medicamento, medicamento_id, "Medicamento")
    medicamento.ativo = False
    db.commit()
    return MensagemOut(mensagem="Medicamento desativado com sucesso.")
