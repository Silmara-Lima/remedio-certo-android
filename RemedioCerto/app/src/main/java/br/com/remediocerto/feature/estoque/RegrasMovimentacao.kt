package br.com.remediocerto.feature.movimentacao

import br.com.remediocerto.core.network.EstoqueDto

/**
 * Lotes que podem ser usados na operação, já em ordem FEFO (o que vence primeiro vem primeiro).
 * RN03: lote vencido não pode ter saída comum, só descarte.
 */
fun lotesDisponiveisPara(tipo: TipoMov, lotes: List<EstoqueDto>, unidadeId: Int?): List<EstoqueDto> =
    lotes
        .filter { it.unidadeId == unidadeId }
        .filter {
            when (tipo) {
                TipoMov.SAIDA -> it.quantidade > 0 && it.status != "vencido"
                TipoMov.DESCARTE -> it.quantidade > 0
                else -> true
            }
        }
        .sortedBy { it.validade }

/**
 * Regras de uma movimentação feita sobre um lote existente (saída, descarte ou ajuste).
 * Retorna a mensagem de erro, ou null se estiver tudo certo.
 */
fun validarMovimentacaoEmLote(tipo: TipoMov, quantidade: Int, lote: EstoqueDto, observacao: String): String? = when {
    // RN01: o estoque nunca fica negativo
    tipo != TipoMov.AJUSTE && quantidade > lote.quantidade ->
        "Saldo insuficiente: o lote ${lote.lote} tem ${lote.quantidade} un."
    // RN06: ajuste exige justificativa
    tipo == TipoMov.AJUSTE && observacao.isBlank() ->
        "Informe a justificativa do ajuste no campo Observação."
    else -> null
}