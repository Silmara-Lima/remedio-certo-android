"""Persistent receipts for offline operations, committed together with stock changes."""

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, JSON, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SyncOperation(Base):
    __tablename__ = "sync_operations"
    __table_args__ = (UniqueConstraint("usuario_id", "id_local", name="uq_sync_usuario_local"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    usuario_id: Mapped[int] = mapped_column(ForeignKey("usuarios.id", ondelete="RESTRICT"), nullable=False)
    id_local: Mapped[str] = mapped_column(String(36), nullable=False)
    payload_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    resultado: Mapped[dict] = mapped_column(JSON, nullable=False)
    criada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
