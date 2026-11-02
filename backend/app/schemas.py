from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.enums import PerfilUsuario, StatusAlerta, TipoAlerta, TipoMovimentacao


class MensagemOut(BaseModel):
    mensagem: str


class LoginIn(BaseModel):
    email: EmailStr
    senha: str = Field(min_length=1)


class TrocarSenhaIn(BaseModel):
    senha_atual: str = Field(min_length=1)
    nova_senha: str = Field(min_length=6, max_length=120)


class UnidadeBase(BaseModel):
    nome: str = Field(min_length=2, max_length=150)
    cnpj: str | None = Field(default=None, max_length=20)
    endereco: str | None = Field(default=None, max_length=255)
    telefone: str | None = Field(default=None, max_length=30)
    ativa: bool = True


class UnidadeCreate(UnidadeBase):
    pass


class UnidadeUpdate(BaseModel):
    nome: str | None = Field(default=None, min_length=2, max_length=150)
    cnpj: str | None = Field(default=None, max_length=20)
    endereco: str | None = Field(default=None, max_length=255)
    telefone: str | None = Field(default=None, max_length=30)
    ativa: bool | None = None


class UnidadeOut(UnidadeBase):
    id: int
    criada_em: datetime
    atualizada_em: datetime

    model_config = ConfigDict(from_attributes=True)


class UsuarioBase(BaseModel):
    nome: str = Field(min_length=2, max_length=120)
    email: EmailStr
    perfil: PerfilUsuario = PerfilUsuario.funcionario
    ativo: bool = True
    unidade_id: int | None = None


class UsuarioCreate(UsuarioBase):
    senha: str = Field(min_length=6, max_length=120)


class UsuarioUpdate(BaseModel):
    nome: str | None = Field(default=None, min_length=2, max_length=120)
    email: EmailStr | None = None
    senha: str | None = Field(default=None, min_length=6, max_length=120)
    perfil: PerfilUsuario | None = None
    ativo: bool | None = None
    unidade_id: int | None = None


class UsuarioOut(UsuarioBase):
    id: int
    criado_em: datetime
    atualizado_em: datetime
    ultimo_acesso: datetime | None = None

    model_config = ConfigDict(from_attributes=True)


class UsuarioResumo(BaseModel):
    id: int
    nome: str
    email: EmailStr
    perfil: PerfilUsuario

    model_config = ConfigDict(from_attributes=True)


class UsuarioAuthOut(BaseModel):
    id: int
    nome: str
    email: EmailStr
    tipo: str
    ativo: bool
    unidadeSaudeId: int | None = None


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    usuario: UsuarioAuthOut


class MedicamentoBase(BaseModel):
    nome: str = Field(min_length=2, max_length=150)
    principio_ativo: str | None = Field(default=None, max_length=150)
    fabricante: str | None = Field(default=None, max_length=120)
    forma_farmaceutica: str | None = Field(default=None, max_length=80)
    dosagem: str | None = Field(default=None, max_length=50)
    codigo_barras: str | None = Field(default=None, max_length=50)
    registro_anvisa: str | None = Field(default=None, max_length=50)
    quantidade_minima: int = Field(default=10, ge=0)
    ativo: bool = True


class MedicamentoCreate(MedicamentoBase):
    pass


class MedicamentoUpdate(BaseModel):
    nome: str | None = Field(default=None, min_length=2, max_length=150)
    principio_ativo: str | None = Field(default=None, max_length=150)
    fabricante: str | None = Field(default=None, max_length=120)
    forma_farmaceutica: str | None = Field(default=None, max_length=80)
    dosagem: str | None = Field(default=None, max_length=50)
    codigo_barras: str | None = Field(default=None, max_length=50)
    registro_anvisa: str | None = Field(default=None, max_length=50)
    quantidade_minima: int | None = Field(default=None, ge=0)
    ativo: bool | None = None


class MedicamentoOut(MedicamentoBase):
    id: int
    criado_em: datetime
    atualizado_em: datetime

    model_config = ConfigDict(from_attributes=True)


class EstoqueBase(BaseModel):
    medicamento_id: int
    unidade_id: int
    lote: str = Field(min_length=1, max_length=80)
    data_fabricacao: date | None = None
    validade: date
    quantidade_inicial: int = Field(default=0, ge=0)
    quantidade: int = Field(default=0, ge=0)


class EstoqueCreate(EstoqueBase):
    pass


class EstoqueUpdate(BaseModel):
    medicamento_id: int | None = None
    unidade_id: int | None = None
    lote: str | None = Field(default=None, min_length=1, max_length=80)
    data_fabricacao: date | None = None
    validade: date | None = None
    quantidade_inicial: int | None = Field(default=None, ge=0)
    quantidade: int | None = Field(default=None, ge=0)


class EstoqueOut(EstoqueBase):
    id: int
    criado_em: datetime
    atualizado_em: datetime
    dias_para_vencer: int
    status_estoque: str
    medicamento: MedicamentoOut | None = None
    unidade: UnidadeOut | None = None

    model_config = ConfigDict(from_attributes=True)


class MovimentacaoBase(BaseModel):
    tipo: TipoMovimentacao
    medicamento_id: int
    unidade_id: int
    lote: str = Field(min_length=1, max_length=80)
    validade: date
    quantidade: int = Field(ge=0)
    data_fabricacao: date | None = None
    numero_documento: str | None = Field(default=None, max_length=80)
    observacao: str | None = None


class MovimentacaoCreate(MovimentacaoBase):
    estoque_lote_id: int | None = None


class MovimentacaoOut(BaseModel):
    id: int
    tipo: TipoMovimentacao
    medicamento_id: int
    unidade_id: int
    estoque_lote_id: int | None = None
    usuario_id: int
    lote: str
    validade: date
    quantidade: int
    numero_documento: str | None = None
    observacao: str | None = None
    criada_em: datetime
    medicamento: MedicamentoOut | None = None
    unidade: UnidadeOut | None = None
    usuario: UsuarioOut | None = None

    model_config = ConfigDict(from_attributes=True)


class ConfiguracaoAlertaBase(BaseModel):
    unidade_id: int
    estoque_minimo_padrao: int = Field(default=10, ge=0)
    dias_alerta_vencimento: int = Field(default=30, ge=1)
    dias_alerta_critico: int = Field(default=7, ge=1)
    notificacao_email: bool = False
    notificacao_push: bool = True


class ConfiguracaoAlertaCreate(ConfiguracaoAlertaBase):
    pass


class ConfiguracaoAlertaUpdate(BaseModel):
    estoque_minimo_padrao: int | None = Field(default=None, ge=0)
    dias_alerta_vencimento: int | None = Field(default=None, ge=1)
    dias_alerta_critico: int | None = Field(default=None, ge=1)
    notificacao_email: bool | None = None
    notificacao_push: bool | None = None


class ConfiguracaoAlertaOut(ConfiguracaoAlertaBase):
    id: int
    criada_em: datetime
    atualizada_em: datetime

    model_config = ConfigDict(from_attributes=True)


class AlertaBase(BaseModel):
    tipo: TipoAlerta
    mensagem: str = Field(min_length=2)
    status: StatusAlerta = StatusAlerta.ativo
    medicamento_id: int
    unidade_id: int
    estoque_lote_id: int | None = None


class AlertaCreate(AlertaBase):
    pass


class AlertaUpdate(BaseModel):
    mensagem: str | None = Field(default=None, min_length=2)
    status: StatusAlerta | None = None


class AlertaOut(AlertaBase):
    id: int
    gerado_em: datetime
    resolvido_em: datetime | None = None
    medicamento: MedicamentoOut | None = None
    unidade: UnidadeOut | None = None
    estoque_lote: EstoqueOut | None = None

    model_config = ConfigDict(from_attributes=True)


class DashboardOut(BaseModel):
    total_unidades: int
    total_medicamentos: int
    total_lotes: int
    total_usuarios: int
    total_alertas_ativos: int
    lotes_estoque_baixo: int
    lotes_vencidos: int
    lotes_vencimento_proximo: int
    movimentacoes_recentes: list[MovimentacaoOut]
    alertas_recentes: list[AlertaOut]
