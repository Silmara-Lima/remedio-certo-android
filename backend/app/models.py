from datetime import date, datetime

from sqlalchemy import Boolean, Date, DateTime, Enum as SAEnum, ForeignKey, Index, Integer, String, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base
from app.enums import PerfilUsuario, StatusAlerta, TipoAlerta, TipoMovimentacao


class UnidadeSaude(Base):

    __tablename__ = "unidades_saude"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    nome: Mapped[str] = mapped_column(String(150), unique=True, nullable=False)
    cnpj: Mapped[str | None] = mapped_column(String(20), unique=True, nullable=True)
    endereco: Mapped[str | None] = mapped_column(String(255), nullable=True)
    telefone: Mapped[str | None] = mapped_column(String(30), nullable=True)
    ativa: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    criada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    atualizada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

    usuarios: Mapped[list["Usuario"]] = relationship(back_populates="unidade")
    estoques: Mapped[list["EstoqueLote"]] = relationship(back_populates="unidade")
    movimentacoes: Mapped[list["Movimentacao"]] = relationship(back_populates="unidade")
    configuracao_alerta: Mapped["ConfiguracaoAlerta | None"] = relationship(back_populates="unidade", uselist=False)
    alertas: Mapped[list["Alerta"]] = relationship(back_populates="unidade")


class Usuario(Base):

    __tablename__ = "usuarios"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    nome: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str] = mapped_column(String(150), unique=True, index=True, nullable=False)
    senha_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    perfil: Mapped[PerfilUsuario] = mapped_column(
        SAEnum(PerfilUsuario, native_enum=False),
        nullable=False,
        default=PerfilUsuario.funcionario,
    )
    ativo: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    criado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    atualizado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())
    ultimo_acesso: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    unidade_id: Mapped[int | None] = mapped_column(
        ForeignKey("unidades_saude.id", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
    )

    unidade: Mapped[UnidadeSaude | None] = relationship(back_populates="usuarios")
    movimentacoes: Mapped[list["Movimentacao"]] = relationship(back_populates="usuario")


class Medicamento(Base):

    __tablename__ = "medicamentos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    nome: Mapped[str] = mapped_column(String(150), nullable=False, index=True)
    principio_ativo: Mapped[str | None] = mapped_column(String(150), nullable=True, index=True)
    fabricante: Mapped[str | None] = mapped_column(String(120), nullable=True)
    forma_farmaceutica: Mapped[str | None] = mapped_column(String(80), nullable=True)
    dosagem: Mapped[str | None] = mapped_column(String(50), nullable=True)
    codigo_barras: Mapped[str | None] = mapped_column(String(50), unique=True, nullable=True)
    registro_anvisa: Mapped[str | None] = mapped_column(String(50), unique=True, nullable=True)
    quantidade_minima: Mapped[int] = mapped_column(Integer, nullable=False, default=10)
    ativo: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    criado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    atualizado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

    estoques: Mapped[list["EstoqueLote"]] = relationship(back_populates="medicamento")
    movimentacoes: Mapped[list["Movimentacao"]] = relationship(back_populates="medicamento")
    alertas: Mapped[list["Alerta"]] = relationship(back_populates="medicamento")


class EstoqueLote(Base):

    __tablename__ = "estoques_lotes"
    __table_args__ = (
        UniqueConstraint("medicamento_id", "unidade_id", "lote", "validade", name="uq_estoque_lote"),
        Index("idx_estoques_validade", "validade"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    medicamento_id: Mapped[int] = mapped_column(ForeignKey("medicamentos.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False, index=True)
    unidade_id: Mapped[int] = mapped_column(ForeignKey("unidades_saude.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False, index=True)
    lote: Mapped[str] = mapped_column(String(80), nullable=False)
    data_fabricacao: Mapped[date | None] = mapped_column(Date, nullable=True)
    validade: Mapped[date] = mapped_column(Date, nullable=False)
    quantidade_inicial: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    quantidade: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    criado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    atualizado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

    medicamento: Mapped[Medicamento] = relationship(back_populates="estoques")
    unidade: Mapped[UnidadeSaude] = relationship(back_populates="estoques")
    movimentacoes: Mapped[list["Movimentacao"]] = relationship(back_populates="estoque_lote")
    alertas: Mapped[list["Alerta"]] = relationship(back_populates="estoque_lote")

    @property
    def dias_para_vencer(self) -> int:
        return (self.validade - date.today()).days

    @property
    def status_estoque(self) -> str:
        if self.validade < date.today():
            return "vencido"
        if self.medicamento and self.quantidade <= self.medicamento.quantidade_minima:
            return "estoque_baixo"
        if self.dias_para_vencer <= 30:
            return "vencimento_proximo"
        return "normal"


class Movimentacao(Base):

    __tablename__ = "movimentacoes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    tipo: Mapped[TipoMovimentacao] = mapped_column(SAEnum(TipoMovimentacao, native_enum=False), nullable=False, index=True)
    medicamento_id: Mapped[int] = mapped_column(ForeignKey("medicamentos.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False)
    unidade_id: Mapped[int] = mapped_column(ForeignKey("unidades_saude.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False)
    estoque_lote_id: Mapped[int | None] = mapped_column(ForeignKey("estoques_lotes.id", ondelete="SET NULL", onupdate="CASCADE"), nullable=True)
    usuario_id: Mapped[int] = mapped_column(ForeignKey("usuarios.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False)
    lote: Mapped[str] = mapped_column(String(80), nullable=False)
    validade: Mapped[date] = mapped_column(Date, nullable=False)
    quantidade: Mapped[int] = mapped_column(Integer, nullable=False)
    numero_documento: Mapped[str | None] = mapped_column(String(80), nullable=True)
    observacao: Mapped[str | None] = mapped_column(Text, nullable=True)
    criada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), index=True)

    medicamento: Mapped[Medicamento] = relationship(back_populates="movimentacoes")
    unidade: Mapped[UnidadeSaude] = relationship(back_populates="movimentacoes")
    estoque_lote: Mapped[EstoqueLote | None] = relationship(back_populates="movimentacoes")
    usuario: Mapped[Usuario] = relationship(back_populates="movimentacoes")


class ConfiguracaoAlerta(Base):

    __tablename__ = "configuracoes_alerta"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    unidade_id: Mapped[int] = mapped_column(ForeignKey("unidades_saude.id", ondelete="CASCADE", onupdate="CASCADE"), nullable=False, unique=True)
    estoque_minimo_padrao: Mapped[int] = mapped_column(Integer, nullable=False, default=10)
    dias_alerta_vencimento: Mapped[int] = mapped_column(Integer, nullable=False, default=30)
    dias_alerta_critico: Mapped[int] = mapped_column(Integer, nullable=False, default=7)
    notificacao_email: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    notificacao_push: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    criada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    atualizada_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

    unidade: Mapped[UnidadeSaude] = relationship(back_populates="configuracao_alerta")


class Alerta(Base):

    __tablename__ = "alertas"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    tipo: Mapped[TipoAlerta] = mapped_column(SAEnum(TipoAlerta, native_enum=False), nullable=False, index=True)
    mensagem: Mapped[str] = mapped_column(Text, nullable=False)
    status: Mapped[StatusAlerta] = mapped_column(SAEnum(StatusAlerta, native_enum=False), nullable=False, default=StatusAlerta.ativo, index=True)
    medicamento_id: Mapped[int] = mapped_column(ForeignKey("medicamentos.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False)
    unidade_id: Mapped[int] = mapped_column(ForeignKey("unidades_saude.id", ondelete="RESTRICT", onupdate="CASCADE"), nullable=False)
    estoque_lote_id: Mapped[int | None] = mapped_column(ForeignKey("estoques_lotes.id", ondelete="SET NULL", onupdate="CASCADE"), nullable=True)
    gerado_em: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
    resolvido_em: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    # RF14: quem resolveu ou ignorou. Fica vazio quando o sistema resolve sozinho.
    resolvido_por_id: Mapped[int | None] = mapped_column(
        ForeignKey("usuarios.id", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
    )

    medicamento: Mapped[Medicamento] = relationship(back_populates="alertas")
    unidade: Mapped[UnidadeSaude] = relationship(back_populates="alertas")
    estoque_lote: Mapped[EstoqueLote | None] = relationship(back_populates="alertas")
    resolvido_por: Mapped[Usuario | None] = relationship(foreign_keys=[resolvido_por_id])
