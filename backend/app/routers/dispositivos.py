from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import obter_usuario_atual
from app.enums import PerfilUsuario
from app.models import UnidadeSaude, Usuario
from app.push import enqueue_current_alerts, token_digest
from app.push_models import DispositivoPush, PushDelivery

router = APIRouter(prefix="/dispositivos", tags=["Dispositivos push"])


class DispositivoRequest(BaseModel):
    token: str = Field(min_length=20, max_length=4096)

    @field_validator("token")
    @classmethod
    def token_sem_espacos(cls, value: str) -> str:
        if value != value.strip() or any(char.isspace() for char in value):
            raise ValueError("Token FCM invalido.")
        return value


@router.post("")
def registrar_dispositivo(
    data: DispositivoRequest, request: Request, db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    usuario = db.scalar(select(Usuario).where(Usuario.id == usuario.id).with_for_update()
                        .execution_options(populate_existing=True))
    if not usuario.ativo:
        raise HTTPException(401, "Usuario inativo.")
    units = select(UnidadeSaude.id)
    if usuario.perfil != PerfilUsuario.admin:
        units = units.where(UnidadeSaude.id == usuario.unidade_id)
    # Queue insertion checks alert/device foreign keys. Lock units before devices,
    # matching movements and delivery workers so these operations cannot invert locks.
    unit_ids = list(db.scalars(units.order_by(UnidadeSaude.id).with_for_update()))
    digest = token_digest(data.token)
    device = db.scalar(select(DispositivoPush).where(DispositivoPush.token_hash == digest).with_for_update()
                       .execution_options(populate_existing=True))
    if device is None:
        device = DispositivoPush(usuario_id=usuario.id, session_id=request.state.auth_session_id,
                                 token_hash=digest, token=data.token)
        db.add(device)
        db.flush()
    elif device.usuario_id != usuario.id or device.session_id != request.state.auth_session_id:
        # Possession of the current FCM token permits re-association on a shared phone.
        # Old account's pending events must never follow the new login.
        db.execute(delete(PushDelivery).where(PushDelivery.dispositivo_id == device.id))
        device.usuario_id = usuario.id
        device.session_id = request.state.auth_session_id
    device.ultimo_uso = datetime.utcnow()
    enqueue_current_alerts(db, usuario, device, locked_unit_ids=unit_ids)
    db.commit()
    return {"mensagem": "Dispositivo registrado."}


@router.delete("")
def remover_dispositivo(
    data: DispositivoRequest, db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    db.scalar(select(Usuario).where(Usuario.id == usuario.id).with_for_update())
    device = db.scalar(select(DispositivoPush).where(
        DispositivoPush.token_hash == token_digest(data.token),
        DispositivoPush.usuario_id == usuario.id,
    ).with_for_update())
    if device:
        db.execute(delete(PushDelivery).where(PushDelivery.dispositivo_id == device.id))
        db.delete(device)
    db.commit()
    # Same response even for a token belonging to another user.
    return {"mensagem": "Dispositivo removido."}
