package br.com.remediocerto.feature.historico

import br.com.remediocerto.core.network.HistoricoDto
import java.time.LocalDate

// Filtros do histórico (RF11). Funções puras, testadas sem a API.

/** Período do filtro. dias = null significa "tudo". */
enum class Periodo(val rotulo: String, val dias: Long?) {
    SETE("7 dias", 7),
    TRINTA("30 dias", 30),
    NOVENTA("90 dias", 90),
    TUDO("Tudo", null),
}

/** Data inicial que vai para a API ("2026-11-09"), ou null para não filtrar. */
fun dataInicioDo(periodo: Periodo, hoje: LocalDate): String? =
    periodo.dias?.let { hoje.minusDays(it - 1).toString() }

/** Opção de filtro (unidade ou usuário) tirada da própria lista carregada. */
data class Opcao(val id: Int, val nome: String)

fun unidadesDe(itens: List<HistoricoDto>): List<Opcao> =
    itens.mapNotNull { it.unidade?.let { u -> Opcao(u.id, u.nome) } }.distinctBy { it.id }.sortedBy { it.nome }

fun usuariosDe(itens: List<HistoricoDto>): List<Opcao> =
    itens.mapNotNull { it.usuario?.let { u -> Opcao(u.id, u.nome) } }.distinctBy { it.id }.sortedBy { it.nome }

/** Filtros feitos no aparelho, sobre o que a API devolveu: medicamento (texto), unidade e usuário. */
fun filtrarHistorico(
    itens: List<HistoricoDto>,
    buscaMedicamento: String,
    unidadeId: Int?,
    usuarioId: Int?,
): List<HistoricoDto> {
    val termo = buscaMedicamento.trim()
    return itens.filter { h ->
        (termo.isEmpty() || h.medicamento?.nome?.contains(termo, ignoreCase = true) == true) &&
            (unidadeId == null || h.unidade?.id == unidadeId) &&
            (usuarioId == null || h.usuario?.id == usuarioId)
    }
}

fun rotuloTipo(tipo: String): String = when (tipo.lowercase()) {
    "entrada" -> "Entrada"
    "saida" -> "Saída"
    "descarte" -> "Descarte"
    "ajuste" -> "Ajuste"
    else -> tipo
}
