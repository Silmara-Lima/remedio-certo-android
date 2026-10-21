package br.com.remediocerto.feature.movimentacao

import br.com.remediocerto.core.network.EstoqueDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RegrasMovimentacaoTest {

    private fun lote(id: Int, quantidade: Int, validade: String = "2027-12-31", status: String = "normal", unidadeId: Int = 1) =
        EstoqueDto(id, 1, unidadeId, "L$id", validade, quantidade, 100, status)

    // ---------- Lotes disponíveis (RN03 e FEFO) ----------
    @Test
    fun `saida nao oferece lote vencido nem zerado`() {
        val lotes = listOf(lote(1, 10, status = "vencido"), lote(2, 0), lote(3, 5))
        val disponiveis = lotesDisponiveisPara(TipoMov.SAIDA, lotes, unidadeId = 1)
        assertEquals(listOf(3), disponiveis.map { it.id })
    }

    @Test
    fun `descarte aceita lote vencido`() {
        val lotes = listOf(lote(1, 10, status = "vencido"), lote(2, 0))
        val disponiveis = lotesDisponiveisPara(TipoMov.DESCARTE, lotes, unidadeId = 1)
        assertEquals(listOf(1), disponiveis.map { it.id })
    }

    @Test
    fun `mostra apenas lotes da unidade escolhida`() {
        val lotes = listOf(lote(1, 10, unidadeId = 1), lote(2, 10, unidadeId = 2))
        assertEquals(listOf(2), lotesDisponiveisPara(TipoMov.SAIDA, lotes, unidadeId = 2).map { it.id })
    }

    @Test
    fun `sugere primeiro o lote que vence antes (FEFO)`() {
        val lotes = listOf(lote(1, 10, validade = "2027-08-01"), lote(2, 10, validade = "2027-01-01"))
        assertEquals(2, lotesDisponiveisPara(TipoMov.SAIDA, lotes, unidadeId = 1).first().id)
    }

    // ---------- Validações (RN01 e RN06) ----------
    @Test
    fun `bloqueia saida maior que o saldo`() {
        val erro = validarMovimentacaoEmLote(TipoMov.SAIDA, 11, lote(1, 10), "")
        assertNotNull(erro)
    }

    @Test
    fun `permite saida igual ao saldo`() {
        assertNull(validarMovimentacaoEmLote(TipoMov.SAIDA, 10, lote(1, 10), ""))
    }

    @Test
    fun `bloqueia ajuste sem justificativa`() {
        assertNotNull(validarMovimentacaoEmLote(TipoMov.AJUSTE, 5, lote(1, 10), "   "))
    }

    @Test
    fun `ajuste com justificativa pode ser maior que o saldo`() {
        assertNull(validarMovimentacaoEmLote(TipoMov.AJUSTE, 50, lote(1, 10), "Contagem física"))
    }
}