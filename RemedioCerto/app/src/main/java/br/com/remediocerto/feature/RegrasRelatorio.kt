package br.com.remediocerto.feature

import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.ui.dataBr
import br.com.remediocerto.core.ui.dataHoraBr
import br.com.remediocerto.feature.estoque.agruparEstoque
import br.com.remediocerto.feature.historico.rotuloTipo

// Montagem dos relatórios (RF17). Funções puras, testadas sem a API.

/** Os três relatórios do plano: movimentações, validade e estoque. */
enum class TipoRelatorio(val rotulo: String, val titulo: String, val arquivo: String) {
    MOVIMENTACOES("Movimentações", "Relatório de movimentações", "relatorio_movimentacoes"),
    VALIDADE("Validade", "Relatório de validade", "relatorio_validade"),
    ESTOQUE("Estoque", "Relatório de estoque", "relatorio_estoque"),
}

/** Tabela pronta para a tela, o CSV e o PDF. */
data class Relatorio(val cabecalho: List<String>, val linhas: List<List<String>>)

fun relatorioMovimentacoes(itens: List<HistoricoDto>): Relatorio = Relatorio(
    cabecalho = listOf("Data", "Tipo", "Medicamento", "Quantidade", "Lote", "Unidade", "Responsável"),
    linhas = itens.sortedByDescending { it.criadaEm }.map { h ->
        listOf(
            dataHoraBr(h.criadaEm),
            rotuloTipo(h.tipo),
            h.medicamento?.nome ?: "—",
            h.quantidade.toString(),
            h.lote,
            h.unidade?.nome ?: "—",
            h.usuario?.nome ?: "—",
        )
    },
)

/** Lotes com saldo vencidos ou que vencem em até [dias] dias, do que vence primeiro ao último. */
fun relatorioValidade(lotes: List<EstoqueDto>, dias: Int): Relatorio = Relatorio(
    cabecalho = listOf("Medicamento", "Lote", "Validade", "Situação", "Quantidade", "Unidade"),
    linhas = lotes
        .filter { it.quantidade > 0 && it.diasParaVencer <= dias }
        .sortedBy { it.validade }
        .map { l ->
            listOf(
                l.medicamento?.nome ?: "Medicamento ${l.medicamentoId}",
                l.lote,
                dataBr(l.validade),
                prazoTexto(l.diasParaVencer),
                l.quantidade.toString(),
                l.unidade?.nome ?: "—",
            )
        },
)

/** Saldo por medicamento e unidade, com a mesma regra de situação da tela Estoque. */
fun relatorioEstoque(lotes: List<EstoqueDto>): Relatorio = Relatorio(
    cabecalho = listOf("Medicamento", "Unidade", "Saldo", "Mínimo", "Lotes", "Situação"),
    linhas = agruparEstoque(lotes).map { item ->
        listOf(
            item.nome,
            item.unidade ?: "—",
            item.total.toString(),
            item.minimo.toString(),
            item.lotes.size.toString(),
            item.status.rotulo,
        )
    },
)

fun prazoTexto(dias: Int): String = when {
    dias < 0 -> "Vencido há ${-dias} dia(s)"
    dias == 0 -> "Vence hoje"
    else -> "Vence em $dias dia(s)"
}

/** Uma linha do CSV: o ";" separa colunas, então ele não pode aparecer dentro de um valor. */
fun linhaCsv(valores: List<String>): String = valores.joinToString(";") { it.replace(";", ",").replace("\n", " ") }
