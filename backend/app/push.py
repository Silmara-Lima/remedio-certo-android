"""Persistent push outbox. FCM calls run after the inventory transaction commits.

Delivery is at least once: the Android consumer must deduplicate by alerta_id.
No medication names, credentials or FCM tokens are written to logs/messages.
"""
import hashlib
import json
import logging
import threading
import time
from datetime import datetime, timedelta

from sqlalchemy import delete, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import settings
from app.auth_models import AuthSession
from app.database import SessionLocal
from app.enums import PerfilUsuario, StatusAlerta
from app.models import Alerta, ConfiguracaoAlerta, UnidadeSaude, Usuario
from app.push_models import DispositivoPush, PushDelivery

logger = logging.getLogger(__name__)
_firebase_lock = threading.Lock()
_firebase_app = None


class UnregisteredDevice(Exception):
    pass


def token_digest(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def firebase_configured() -> bool:
    return bool(settings.FIREBASE_CREDENTIALS_JSON)


def _send_push(token: str, data: dict[str, str]) -> None:
    global _firebase_app
    import firebase_admin
    from firebase_admin import credentials, messaging

    if _firebase_app is None:
        with _firebase_lock:
            if _firebase_app is None:
                _firebase_app = firebase_admin.initialize_app(
                    credentials.Certificate(json.loads(settings.FIREBASE_CREDENTIALS_JSON)),
                    options={"httpTimeout": 20}, name="remedio-certo-push",
                )
    message = messaging.Message(
        token=token, data=data,
        android=messaging.AndroidConfig(
            priority="high", ttl=timedelta(minutes=5),
        ),
    )
    try:
        messaging.send(message, app=_firebase_app)
    except messaging.UnregisteredError:
        raise UnregisteredDevice() from None


def _can_receive(user: Usuario | None, alerta: Alerta, unit: UnidadeSaude | None) -> bool:
    return bool(user and user.ativo and unit and unit.ativa and (
        user.perfil == PerfilUsuario.admin or user.unidade_id == alerta.unidade_id
    ))


def _session_active(session: AuthSession | None, device: DispositivoPush) -> bool:
    return bool(session and session.usuario_id == device.usuario_id and not session.revoked
                and session.expires_at > int(time.time()))


def enqueue_alert(db: Session, alerta: Alerta, dispositivo_id: int | None = None) -> None:
    """Add deliveries in the same transaction as a newly created alert."""
    db.flush()
    cfg = db.query(ConfiguracaoAlerta).filter_by(unidade_id=alerta.unidade_id).first()
    if cfg and not cfg.notificacao_push:
        return
    unit = db.get(UnidadeSaude, alerta.unidade_id)
    devices = db.query(DispositivoPush, Usuario).join(
        Usuario, DispositivoPush.usuario_id == Usuario.id
    )
    if dispositivo_id is not None:
        devices = devices.filter(DispositivoPush.id == dispositivo_id)
    for device, user in devices.all():
        if not _can_receive(user, alerta, unit) or not _session_active(db.get(AuthSession, device.session_id), device):
            continue
        exists = db.query(PushDelivery.id).filter_by(alerta_id=alerta.id, dispositivo_id=device.id).first()
        if exists:
            continue
        try:
            with db.begin_nested():
                db.add(PushDelivery(alerta_id=alerta.id, dispositivo_id=device.id))
                db.flush()
        except IntegrityError:
            # Another scheduler/request already queued this exact event/device.
            pass


def enqueue_current_alerts(db: Session, user: Usuario, device: DispositivoPush,
                           locked_unit_ids: list[int] | None = None) -> None:
    """New devices receive currently active alerts within their current scope."""
    query = db.query(Alerta).filter(Alerta.status == StatusAlerta.ativo)
    if user.perfil != PerfilUsuario.admin:
        query = query.filter(Alerta.unidade_id == user.unidade_id)
    if locked_unit_ids is not None:
        query = query.filter(Alerta.unidade_id.in_(locked_unit_ids))
    for alerta in query.all():
        enqueue_alert(db, alerta, dispositivo_id=device.id)


def _delete_device(db: Session, device: DispositivoPush) -> None:
    db.execute(delete(PushDelivery).where(PushDelivery.dispositivo_id == device.id))
    db.delete(device)


def process_push_deliveries(db: Session) -> int:
    """Claim each due row atomically, retry transient failures with bounded backoff."""
    if not firebase_configured():
        return 0
    now = int(time.time())
    ids = list(db.scalars(select(PushDelivery.id).where(
        PushDelivery.status == "pendente", PushDelivery.proxima_tentativa <= now,
    ).order_by(PushDelivery.id).limit(100)))
    sent = 0
    for delivery_id in ids:
        # A persistent lease prevents simultaneous workers from dispatching the same row.
        claimed = db.execute(update(PushDelivery).where(
            PushDelivery.id == delivery_id, PushDelivery.status == "pendente",
            PushDelivery.proxima_tentativa <= now,
        ).values(proxima_tentativa=now + 120, tentativas=PushDelivery.tentativas + 1))
        db.commit()
        if claimed.rowcount != 1:
            continue
        pending = db.get(PushDelivery, delivery_id)
        if pending is None:
            db.rollback()
            continue
        # Stable lock order matches inventory (user -> unit -> lot/rule/alert).
        # Reading IDs before locking does not authorize delivery: revalidate below.
        owner_id = db.scalar(select(DispositivoPush.usuario_id).where(
            DispositivoPush.id == pending.dispositivo_id
        ))
        unit_id = db.scalar(select(Alerta.unidade_id).where(Alerta.id == pending.alerta_id))
        user = db.scalar(select(Usuario).where(Usuario.id == owner_id).with_for_update()
                         .execution_options(populate_existing=True)) if owner_id else None
        unit = db.scalar(select(UnidadeSaude).where(UnidadeSaude.id == unit_id).with_for_update()
                         .execution_options(populate_existing=True)) if unit_id else None
        device = db.scalar(select(DispositivoPush).where(
            DispositivoPush.id == pending.dispositivo_id
        ).with_for_update().execution_options(populate_existing=True))
        delivery = db.scalar(select(PushDelivery).where(
            PushDelivery.id == delivery_id
        ).with_for_update().execution_options(populate_existing=True))
        if delivery is None:
            db.rollback()
            continue
        alerta = db.scalar(select(Alerta).where(Alerta.id == delivery.alerta_id).with_for_update()
                          .execution_options(populate_existing=True))
        auth_session = db.scalar(select(AuthSession).where(AuthSession.id == device.session_id).with_for_update()
                                .execution_options(populate_existing=True)) if device else None
        cfg = db.query(ConfiguracaoAlerta).filter_by(unidade_id=alerta.unidade_id).first() if alerta else None
        if (not device or not alerta or alerta.status != StatusAlerta.ativo
                or device.usuario_id != owner_id or alerta.unidade_id != unit_id
                or not _session_active(auth_session, device)
                or not _can_receive(user, alerta, unit) or (cfg and not cfg.notificacao_push)):
            delivery.status = "cancelado"
            db.commit()
            continue
        data = {
            "alerta_id": str(alerta.id), "unidade_id": str(alerta.unidade_id),
            "tipo": alerta.tipo.value, "deep_link": f"remediocerto://alertas/{alerta.id}",
        }
        try:
            _send_push(device.token, data)
        except UnregisteredDevice:
            _delete_device(db, device)
            db.commit()
            logger.info("Push: dispositivo removido por registro FCM expirado.")
        except Exception as exc:
            # Class name only: exception messages can contain credential/token details.
            delivery.erro_tipo = type(exc).__name__[:80]
            delivery.proxima_tentativa = int(time.time()) + min(3600, 30 * 2 ** min(delivery.tentativas - 1, 7))
            db.commit()
            logger.warning("Push: falha no envio; tentativa posterior agendada (%s).", type(exc).__name__)
        else:
            delivery.status = "enviado"
            delivery.erro_tipo = None
            db.commit()
            sent += 1
    return sent


def run_alert_cycle() -> None:
    from app.services import sincronizar_alertas

    try:
        with SessionLocal() as db:
            locked_units = list(db.scalars(select(UnidadeSaude.id).order_by(UnidadeSaude.id).with_for_update()))
            sincronizar_alertas(db)
            # Also cover manual alerts created through the existing API, and events
            # already present when this outbox was installed. The unique pair
            # guarantees these repeated scans never enqueue the same event twice.
            for alerta in db.query(Alerta).filter(Alerta.status == StatusAlerta.ativo,
                                                Alerta.unidade_id.in_(locked_units)).all():
                enqueue_alert(db, alerta)
            db.commit()
            process_push_deliveries(db)
    except Exception as exc:
        logger.error("Ciclo de alertas falhou (%s).", type(exc).__name__)
