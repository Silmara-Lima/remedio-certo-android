package br.com.remediocerto.core.offline

import br.com.remediocerto.core.database.entity.EstoqueLocalEntity
import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.UnidadeDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Conversões entre a API e o banco local. Funções puras, testadas sem aparelho.

fun EstoqueDto.paraLocal(agora: Long): EstoqueLocalEntity = EstoqueLocalEntity(
    chave = id.toString(),
    estoqueId = id,
    medicamentoId = medicamentoId,
    unidadeId = unidadeId,
    nome = medicamento?.nome ?: "Medicamento $medicamentoId",
    unidade = unidade?.nome ?: "Unidade $unidadeId",
    codigoBarras = medicamento?.codigoBarras,
    lote = lote,
    validade = validade,
    quantidade = quantidade,
    quantidadeMinima = medicamento?.quantidadeMinima ?: 0,
    diasParaVencer = diasParaVencer,
    status = status,
    atualizadoEm = agora,
)

/**
 * Volta para o formato da API. Os dias para vencer são recalculados com a data de hoje:
 * um lote salvo ontem pode ter vencido hoje, e lote vencido não pode ter saída (RN03).
 */
fun EstoqueLocalEntity.paraDto(hoje: LocalDate): EstoqueDto {
    val vencimento = runCatching { LocalDate.parse(validade) }.getOrNull()
    val dias = vencimento?.let { ChronoUnit.DAYS.between(hoje, it).toInt() } ?: diasParaVencer
    return EstoqueDto(
        id = estoqueId,
        medicamentoId = medicamentoId,
        unidadeId = unidadeId,
        lote = lote,
        validade = validade,
        quantidade = quantidade,
        diasParaVencer = dias,
        status = if (dias < 0) "vencido" else status,
        medicamento = MedicamentoDto(
            id = medicamentoId,
            nome = nome,
            codigoBarras = codigoBarras,
            quantidadeMinima = quantidadeMinima,
        ),
        unidade = UnidadeDto(id = unidadeId, nome = unidade),
    )
}

/** Medicamentos que aparecem no estoque salvo (para a busca e o scanner sem internet). */
fun medicamentosDoCache(itens: List<EstoqueLocalEntity>): List<MedicamentoDto> =
    itens.distinctBy { it.medicamentoId }.map {
        MedicamentoDto(
            id = it.medicamentoId,
            nome = it.nome,
            codigoBarras = it.codigoBarras,
            quantidadeMinima = it.quantidadeMinima,
        )
    }

/** Unidades que aparecem no estoque salvo (para o administrador escolher sem internet). */
fun unidadesDoCache(itens: List<EstoqueLocalEntity>): List<UnidadeDto> =
    itens.distinctBy { it.unidadeId }.map { UnidadeDto(id = it.unidadeId, nome = it.unidade) }.sortedBy { it.nome }

/**
 * Saldo do lote no aparelho depois de uma movimentação feita sem internet.
 * Mantém as próximas validações offline corretas (RN01: nunca negativo).
 */
fun saldoDepois(tipo: String, saldoAtual: Int, quantidade: Int): Int = when (tipo) {
    "entrada" -> saldoAtual + quantidade
    "ajuste" -> quantidade          // no ajuste, a quantidade é o saldo contado
    else -> saldoAtual - quantidade // saída e descarte
}
