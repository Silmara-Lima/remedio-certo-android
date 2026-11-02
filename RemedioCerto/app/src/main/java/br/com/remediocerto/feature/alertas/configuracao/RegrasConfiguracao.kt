package br.com.remediocerto.feature.alertas.configuracao

// Validação da configuração de alertas (RF15). Fica fora do ViewModel para poder ser testada sem a API.

data class ValoresConfiguracao(
    val estoqueMinimo: Int,
    val diasVencimento: Int,
    val diasCritico: Int,
)

sealed interface ResultadoValidacao {
    data class Ok(val valores: ValoresConfiguracao) : ResultadoValidacao
    data class Erro(val mensagem: String) : ResultadoValidacao
}

/** Maior antecedência aceita para o aviso de vencimento: um ano. */
const val DIAS_VENCIMENTO_MAXIMO = 365

fun validarConfiguracao(estoqueMinimo: String, diasVencimento: String, diasCritico: String): ResultadoValidacao {
    val minimo = estoqueMinimo.trim().toIntOrNull()
        ?: return ResultadoValidacao.Erro("Informe o estoque mínimo em números.")
    val vencimento = diasVencimento.trim().toIntOrNull()
        ?: return ResultadoValidacao.Erro("Informe os dias de aviso de vencimento em números.")
    val critico = diasCritico.trim().toIntOrNull()
        ?: return ResultadoValidacao.Erro("Informe os dias do alerta crítico em números.")

    return when {
        minimo < 0 -> ResultadoValidacao.Erro("O estoque mínimo não pode ser negativo.")
        vencimento < 1 -> ResultadoValidacao.Erro("O aviso de vencimento precisa ser de pelo menos 1 dia.")
        vencimento > DIAS_VENCIMENTO_MAXIMO -> ResultadoValidacao.Erro("O aviso de vencimento pode ser de no máximo $DIAS_VENCIMENTO_MAXIMO dias.")
        critico < 1 -> ResultadoValidacao.Erro("O alerta crítico precisa ser de pelo menos 1 dia.")
        critico >= vencimento -> ResultadoValidacao.Erro("O alerta crítico precisa ter menos dias que o aviso de vencimento.")
        else -> ResultadoValidacao.Ok(ValoresConfiguracao(minimo, vencimento, critico))
    }
}
