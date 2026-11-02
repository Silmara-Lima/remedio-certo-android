from enum import Enum


class PerfilUsuario(str, Enum):
    admin = "admin"
    funcionario = "funcionario"


class TipoMovimentacao(str, Enum):
    entrada = "entrada"
    saida = "saida"
    descarte = "descarte"
    ajuste = "ajuste"


class TipoAlerta(str, Enum):
    estoque_baixo = "estoque_baixo"
    vencimento_proximo = "vencimento_proximo"
    vencido = "vencido"


class StatusAlerta(str, Enum):
    ativo = "ativo"
    resolvido = "resolvido"
    ignorado = "ignorado"
