package br.com.remediocerto.core.notificacao

import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.feature.alertas.ehCritico

// Regras das notificações de alerta. Funções puras, testadas sem aparelho.

/** Título e texto de uma notificação. */
data class Aviso(val titulo: String, val texto: String)

/** Alertas ativos que ainda não viraram notificação. */
fun alertasParaNotificar(ativos: List<AlertaDto>, jaNotificados: Set<Int>): List<AlertaDto> =
    ativos.filter { it.id !in jaNotificados }

fun tituloDoTipo(tipo: String): String = when (tipo) {
    "estoque_baixo" -> "Estoque baixo"
    "vencimento_proximo" -> "Vencimento próximo"
    "vencido" -> "Lote vencido"
    else -> "Novo alerta"
}

/**
 * Um alerta novo: notificação com o tipo e a mensagem do alerta.
 * Vários de uma vez: um resumo só, para não encher a barra de notificações.
 */
fun montarAviso(novos: List<AlertaDto>): Aviso? = when {
    novos.isEmpty() -> null
    novos.size == 1 -> novos.first().let { Aviso(tituloDoTipo(it.tipo), it.mensagem) }
    else -> {
        val criticos = novos.count { ehCritico(it) }
        Aviso(
            "${novos.size} novos alertas de estoque",
            if (criticos > 0) "$criticos crítico(s). Toque para ver os alertas."
            else "Toque para ver os alertas.",
        )
    }
}
