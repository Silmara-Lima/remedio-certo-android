package br.com.remediocerto.feature.alertas

import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.ui.StatusEstoque

// Regras da aba Alertas. Ficam fora do ViewModel para poder ser testadas sem a API.

/** Abas da tela. O valor "api" é o status que a API usa. */
enum class AbaAlerta(val api: String, val titulo: String) {
    ATIVOS("ativo", "Ativos"),
    RESOLVIDOS("resolvido", "Resolvidos"),
    IGNORADOS("ignorado", "Ignorados"),
}

/** Padrão do plano (RN05): alerta crítico a 7 dias do vencimento. */
const val DIAS_CRITICO_PADRAO = 7

/** Reaproveita as cores e rótulos do estoque: vencido, estoque baixo, vencendo. */
fun gravidadeDo(alerta: AlertaDto): StatusEstoque = StatusEstoque.de(alerta.tipo)

/** Vencimento próximo a poucos dias também conta como crítico. */
fun ehCritico(alerta: AlertaDto, diasCritico: Int = DIAS_CRITICO_PADRAO): Boolean {
    val dias = alerta.estoqueLote?.diasParaVencer
    return alerta.tipo == "vencido" ||
            (alerta.tipo == "vencimento_proximo" && dias != null && dias <= diasCritico)
}

/** Ação recomendada que aparece no cartão (RF14). */
fun acaoRecomendada(alerta: AlertaDto): String = when (alerta.tipo) {
    "vencido" -> "Separar o lote para descarte. Lote vencido não pode ser usado em saída."
    "vencimento_proximo" -> "Priorizar a saída deste lote ou remanejar para outra unidade."
    "estoque_baixo" -> "Programar a reposição deste medicamento."
    else -> "Verificar o estoque deste medicamento."
}

/** Texto curto com o prazo: "Vence em 5 dias", "Vencido há 2 dias". */
fun textoPrazo(alerta: AlertaDto): String? {
    val dias = alerta.estoqueLote?.diasParaVencer ?: return null
    if (alerta.tipo == "estoque_baixo") return null
    return when {
        dias < 0 -> "Vencido há ${-dias} dia(s)"
        dias == 0 -> "Vence hoje"
        else -> "Vence em $dias dia(s)"
    }
}

/** Ordem da lista: mais grave primeiro; dentro da mesma gravidade, o que vence antes. */
fun ordenarAlertas(alertas: List<AlertaDto>): List<AlertaDto> =
    alertas.sortedWith(
        compareByDescending<AlertaDto> { ehCritico(it) }
            .thenByDescending { gravidadeDo(it).gravidade }
            .thenBy { it.estoqueLote?.diasParaVencer ?: Int.MAX_VALUE }
            .thenByDescending { it.geradoEm }
    )

fun alertasDaAba(alertas: List<AlertaDto>, aba: AbaAlerta): List<AlertaDto> =
    ordenarAlertas(alertas.filter { it.status == aba.api })
