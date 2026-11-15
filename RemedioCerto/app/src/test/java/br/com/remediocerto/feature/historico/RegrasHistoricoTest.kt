package br.com.remediocerto.feature.historico

import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.UnidadeDto
import br.com.remediocerto.core.network.UsuarioResumoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RegrasHistoricoTest {

    private fun mov(id: Int, remedio: String, unidade: Int, usuario: Int) = HistoricoDto(
        id = id, tipo = "saida", lote = "L$id", validade = "2027-01-01", quantidade = 1,
        criadaEm = "2026-11-15T10:00:00",
        medicamento = MedicamentoDto(id = id, nome = remedio),
        unidade = UnidadeDto(id = unidade, nome = "Unidade $unidade"),
        usuario = UsuarioResumoDto(id = usuario, nome = "Usuário $usuario"),
    )

    private val lista = listOf(
        mov(1, "Dipirona 1g", unidade = 1, usuario = 10),
        mov(2, "Paracetamol 500mg", unidade = 1, usuario = 11),
        mov(3, "Dipirona 500mg", unidade = 2, usuario = 11),
    )

    @Test
    fun `data inicial de cada periodo`() {
        val hoje = LocalDate.of(2026, 11, 15)
        assertEquals("2026-11-09", dataInicioDo(Periodo.SETE, hoje))
        assertEquals("2026-10-17", dataInicioDo(Periodo.TRINTA, hoje))
        assertNull(dataInicioDo(Periodo.TUDO, hoje))
    }

    @Test
    fun `filtra por nome do medicamento sem diferenciar maiusculas`() {
        assertEquals(listOf(1, 3), filtrarHistorico(lista, "dipirona", null, null).map { it.id })
    }

    @Test
    fun `filtra por unidade e usuario juntos`() {
        assertEquals(listOf(2), filtrarHistorico(lista, "", unidadeId = 1, usuarioId = 11).map { it.id })
    }

    @Test
    fun `sem filtros mostra tudo`() {
        assertEquals(3, filtrarHistorico(lista, "  ", null, null).size)
    }

    @Test
    fun `opcoes de unidade e usuario sem repeticao`() {
        assertEquals(listOf(1, 2), unidadesDe(lista).map { it.id })
        assertEquals(listOf(10, 11), usuariosDe(lista).map { it.id })
    }
}
