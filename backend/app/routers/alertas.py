from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.dependencies import exigir_admin, obter_usuario_atual
from app.enums import PerfilUsuario, StatusAlerta, TipoAlerta
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import Alerta, EstoqueLote, Medicamento, UnidadeSaude, Usuario
from app.schemas import AlertaCreate, AlertaOut, AlertaUpdate, MensagemOut
from app.services import sincronizar_alertas


router = APIRouter(prefix="/alertas", tags=["Alertas"])


@router.get("", response_model=list[AlertaOut])
def listar_alertas(
    status_alerta: StatusAlerta | None = None,
    tipo: TipoAlerta | None = None,
    unidade_id: int | None = None,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    q = db.query(Alerta).options(
        joinedload(Alerta.medicamento),
        joinedload(Alerta.unidade),
        joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.medicamento),
        joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.unidade),
        joinedload(Alerta.resolvido_por),
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(Alerta.unidade_id == usuario.unidade_id)
    if status_alerta:
        q = q.filter(Alerta.status == status_alerta)
    if tipo:
        q = q.filter(Alerta.tipo == tipo)
    if unidade_id:
        q = q.filter(Alerta.unidade_id == unidade_id)

    return q.order_by(Alerta.gerado_em.desc()).all()


@router.post("", response_model=AlertaOut)
def criar_alerta(
    data: AlertaCreate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    buscar_ou_404(db, Medicamento, data.medicamento_id, "Medicamento")
    buscar_ou_404(db, UnidadeSaude, data.unidade_id, "Unidade")
    if data.estoque_lote_id:
        buscar_ou_404(db, EstoqueLote, data.estoque_lote_id, "Estoque")

    alerta = Alerta(**data.model_dump())
    return salvar(db, alerta)


@router.post("/gerar", response_model=MensagemOut)
def gerar_alertas(
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    unidade_id = usuario.unidade_id if usuario.perfil == PerfilUsuario.funcionario else None
    total = sincronizar_alertas(db, unidade_id=unidade_id)
    db.commit()
    return MensagemOut(mensagem=f"{total} alerta(s) novo(s) gerado(s).")


@router.get("/estoque-baixo")
def listar_alertas_estoque_baixo(
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    return _listar_alertas_frontend(
        db=db,
        usuario=usuario,
        tipos=[TipoAlerta.estoque_baixo],
    )


@router.get("/validade")
def listar_alertas_validade(
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    return _listar_alertas_frontend(
        db=db,
        usuario=usuario,
        tipos=[TipoAlerta.vencimento_proximo, TipoAlerta.vencido],
    )


@router.get("/{alerta_id}", response_model=AlertaOut)
def buscar_alerta(
    alerta_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    alerta = (
        db.query(Alerta)
        .options(
            joinedload(Alerta.medicamento),
            joinedload(Alerta.unidade),
            joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.medicamento),
            joinedload(Alerta.estoque_lote).joinedload(EstoqueLote.unidade),
            joinedload(Alerta.resolvido_por),
        )
        .filter(Alerta.id == alerta_id)
        .first()
    )
    if not alerta:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alerta nao encontrado.")
    if usuario.perfil == PerfilUsuario.funcionario and alerta.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Acesso negado.")
    return alerta


@router.put("/{alerta_id}", response_model=AlertaOut)
def atualizar_alerta(
    alerta_id: int,
    data: AlertaUpdate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    alerta = buscar_ou_404(db, Alerta, alerta_id, "Alerta")
    aplicar_alteracoes(alerta, data.model_dump(exclude_unset=True))

    if data.status in (StatusAlerta.resolvido, StatusAlerta.ignorado) and alerta.resolvido_em is None:
        alerta.resolvido_em = datetime.now()
        alerta.resolvido_por_id = admin.id
    if data.status == StatusAlerta.ativo:
        alerta.resolvido_em = None
        alerta.resolvido_por_id = None

    return salvar(db, alerta)


@router.patch("/{alerta_id}/resolver", response_model=AlertaOut)
def resolver_alerta(
    alerta_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    alerta = buscar_ou_404(db, Alerta, alerta_id, "Alerta")
    if usuario.perfil == PerfilUsuario.funcionario and alerta.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Acesso negado.")

    alerta.status = StatusAlerta.resolvido
    alerta.resolvido_em = datetime.now()
    alerta.resolvido_por_id = usuario.id
    return salvar(db, alerta)


@router.patch("/{alerta_id}/ignorar", response_model=AlertaOut)
def ignorar_alerta(
    alerta_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    alerta = buscar_ou_404(db, Alerta, alerta_id, "Alerta")
    if usuario.perfil == PerfilUsuario.funcionario and alerta.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Acesso negado.")

    alerta.status = StatusAlerta.ignorado
    alerta.resolvido_em = datetime.now()
    alerta.resolvido_por_id = usuario.id
    return salvar(db, alerta)


def _listar_alertas_frontend(db: Session, usuario: Usuario, tipos: list[TipoAlerta]) -> list[dict]:
    sincronizar_alertas(
        db,
        unidade_id=usuario.unidade_id if usuario.perfil == PerfilUsuario.funcionario else None,
    )
    db.commit()

    q = (
        db.query(Alerta)
        .filter(Alerta.status == StatusAlerta.ativo)
        .filter(Alerta.tipo.in_(tipos))
        .order_by(Alerta.gerado_em.desc())
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(Alerta.unidade_id == usuario.unidade_id)

    return [_alerta_para_frontend(alerta) for alerta in q.all()]


def _alerta_para_frontend(alerta: Alerta) -> dict:
    return {
        "id": alerta.id,
        "tipo": _tipo_alerta_frontend(alerta.tipo),
        "mensagem": alerta.mensagem,
        "status": _status_alerta_frontend(alerta.status),
        "dataGeracao": alerta.gerado_em.isoformat() if alerta.gerado_em else None,
        "dataResolucao": alerta.resolvido_em.isoformat() if alerta.resolvido_em else None,
        "medicamentoId": alerta.medicamento_id,
        "unidadeSaudeId": alerta.unidade_id,
    }


def _tipo_alerta_frontend(tipo: TipoAlerta) -> str:
    if tipo == TipoAlerta.estoque_baixo:
        return "ESTOQUE_BAIXO"
    return "VALIDADE"


def _status_alerta_frontend(status: StatusAlerta) -> str:
    if status == StatusAlerta.ativo:
        return "PENDENTE"
    if status == StatusAlerta.resolvido:
        return "RESOLVIDO"
    return "IGNORADO"
