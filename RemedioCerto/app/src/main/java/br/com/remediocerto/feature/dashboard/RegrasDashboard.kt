package br.com.remediocerto.feature.dashboard

import br.com.remediocerto.core.network.HistoricoDto
import java.time.LocalDate

// Regras do gráfico do Início. Funções puras, testadas sem a API.

/** Movimentações de um dia: entradas de um lado, saídas e descartes do outro. */
data class DiaMovimento(val data: LocalDate, val entradas: Int, val saidas: Int)

/**
 * Agrupa as movimentações dos últimos [dias] dias (incluindo hoje), um item por dia,
 * inclusive os dias sem movimento. Ajustes não entram: corrigem saldo, não são fluxo.
 */
fun resumoUltimosDias(movimentacoes: List<HistoricoDto>, hoje: LocalDate, dias: Int = 7): List<DiaMovimento> {
    val inicio = hoje.minusDays((dias - 1).toLong())
    val porDia = movimentacoes
        .mapNotNull { m -> runCatching { LocalDate.parse(m.criadaEm.take(10)) }.getOrNull()?.let { it to m.tipo } }
        .filter { (data, _) -> !data.isBefore(inicio) && !data.isAfter(hoje) }
        .groupBy({ it.first }, { it.second })

    return (0 until dias).map { i ->
        val data = inicio.plusDays(i.toLong())
        val tipos = porDia[data].orEmpty()
        DiaMovimento(
            data = data,
            entradas = tipos.count { it.equals("entrada", ignoreCase = true) },
            saidas = tipos.count { it.equals("saida", ignoreCase = true) || it.equals("descarte", ignoreCase = true) },
        )
    }
}

/** Texto lido pelo TalkBack no lugar do gráfico (RNF04: a informação não depende só de cor). */
fun descricaoGrafico(dias: List<DiaMovimento>): String {
    val entradas = dias.sumOf { it.entradas }
    val saidas = dias.sumOf { it.saidas }
    return "Movimentações dos últimos ${dias.size} dias: $entradas entrada(s) e $saidas saída(s) ou descarte(s)."
}
