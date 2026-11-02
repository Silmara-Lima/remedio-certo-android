package br.com.remediocerto.feature.alertas

import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.network.AlertaLoteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasAlertaTest {

    private fun alerta(
        id: Int,
        tipo: String,
        status: String = "ativo",
        dias: Int? = null,
        geradoEm: String = "2026-11-01T10:00:00",
    ) = AlertaDto(
        id = id,
        tipo = tipo,
        mensagem = "teste",
        status = status,
        medicamentoId = 1,
        unidadeId = 1,
        geradoEm = geradoEm,
        estoqueLote = dias?.let { AlertaLoteDto(id = 10, lote = "L1", validade = "2026-12-01", quantidade = 5, diasParaVencer = it) },
    )

    @Test
    fun `vencido sempre e critico`() {
        assertTrue(ehCritico(alerta(1, "vencido", dias = -3)))
    }

    @Test
    fun `vencimento a 7 dias e critico, a 8 dias nao`() {
        assertTrue(ehCritico(alerta(1, "vencimento_proximo", dias = 7)))
        assertFalse(ehCritico(alerta(1, "vencimento_proximo", dias = 8)))
    }

    @Test
    fun `estoque baixo nao e critico por prazo`() {
        assertFalse(ehCritico(alerta(1, "estoque_baixo", dias = 2)))
    }

    @Test
    fun `cada tipo tem sua acao recomendada`() {
        assertTrue(acaoRecomendada(alerta(1, "vencido")).contains("descarte"))
        assertTrue(acaoRecomendada(alerta(1, "vencimento_proximo")).contains("Priorizar"))
        assertTrue(acaoRecomendada(alerta(1, "estoque_baixo")).contains("reposição"))
    }

    @Test
    fun `texto do prazo`() {
        assertEquals("Vencido há 2 dia(s)", textoPrazo(alerta(1, "vencido", dias = -2)))
        assertEquals("Vence hoje", textoPrazo(alerta(1, "vencimento_proximo", dias = 0)))
        assertEquals("Vence em 5 dia(s)", textoPrazo(alerta(1, "vencimento_proximo", dias = 5)))
        assertNull(textoPrazo(alerta(1, "estoque_baixo", dias = 5)))
        assertNull(textoPrazo(alerta(1, "vencimento_proximo")))
    }

    @Test
    fun `criticos primeiro, depois por gravidade e prazo`() {
        val lista = listOf(
            alerta(1, "vencimento_proximo", dias = 20),
            alerta(2, "estoque_baixo"),
            alerta(3, "vencimento_proximo", dias = 3),
            alerta(4, "vencido", dias = -1),
        )
        assertEquals(listOf(4, 3, 2, 1), ordenarAlertas(lista).map { it.id })
    }

    @Test
    fun `cada aba mostra so o seu status`() {
        val lista = listOf(
            alerta(1, "estoque_baixo", status = "ativo"),
            alerta(2, "estoque_baixo", status = "resolvido"),
            alerta(3, "estoque_baixo", status = "ignorado"),
        )
        assertEquals(listOf(1), alertasDaAba(lista, AbaAlerta.ATIVOS).map { it.id })
        assertEquals(listOf(2), alertasDaAba(lista, AbaAlerta.RESOLVIDOS).map { it.id })
        assertEquals(listOf(3), alertasDaAba(lista, AbaAlerta.IGNORADOS).map { it.id })
    }
}
