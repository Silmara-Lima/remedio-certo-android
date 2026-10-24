"""Additive tables: existing inventory, users and alerts remain unchanged."""
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base
from app.auth_models import AuthSession  # register the FK target in standalone seed/init scripts


class DispositivoPush(Base):
    __tablename__ = "dispositivos_push"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    usuario_id: Mapped[int] = mapped_column(ForeignKey("usuarios.id", ondelete="CASCADE"), index=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("auth_sessions.id", ondelete="CASCADE"), index=True)
    # Index the digest instead of a long FCM token (also works with MySQL index limits).
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    token: Mapped[str] = mapped_column(Text)
    ultimo_uso: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class PushDelivery(Base):
    __tablename__ = "push_deliveries"
    __table_args__ = (UniqueConstraint("alerta_id", "dispositivo_id", name="uq_push_alerta_dispositivo"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    alerta_id: Mapped[int] = mapped_column(ForeignKey("alertas.id", ondelete="CASCADE"), index=True)
    dispositivo_id: Mapped[int] = mapped_column(ForeignKey("dispositivos_push.id", ondelete="CASCADE"), index=True)
    status: Mapped[str] = mapped_column(String(20), default="pendente", index=True)
    tentativas: Mapped[int] = mapped_column(Integer, default=0)
    proxima_tentativa: Mapped[int] = mapped_column(Integer, default=0)
    erro_tipo: Mapped[str | None] = mapped_column(String(80), nullable=True)


class AlertCondition(Base):
    """One row per rule/lot: acknowledge once until the condition clears and recurs."""
    __tablename__ = "alert_conditions"

    chave: Mapped[str] = mapped_column(String(100), primary_key=True)
    ativo: Mapped[bool] = mapped_column(Boolean, default=False)
    alerta_id: Mapped[int | None] = mapped_column(ForeignKey("alertas.id", ondelete="SET NULL"), nullable=True)
