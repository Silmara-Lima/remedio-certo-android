from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.schemas import MovimentacaoCreate, MovimentacaoOut


class MovimentacaoSync(MovimentacaoCreate):
    id_local: UUID
    model_config = ConfigDict(extra="forbid")


class LoteMovimentacoesIn(BaseModel):
    # Each item is validated independently so one invalid operation does not discard others.
    movimentacoes: list[dict[str, Any]] = Field(min_length=1, max_length=100)
    model_config = ConfigDict(extra="forbid")


class ResultadoSync(BaseModel):
    id_local: str | None = None
    status: Literal["aceito", "rejeitado"]
    repetida: bool = False
    movimentacao: MovimentacaoOut | None = None
    codigo: str | None = None
    mensagem: str | None = None


class LoteMovimentacoesOut(BaseModel):
    resultados: list[ResultadoSync]
