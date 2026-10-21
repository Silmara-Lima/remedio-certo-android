package br.com.remediocerto.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {

    @Test
    fun `converte data digitada para o formato da API`() {
        assertEquals("2027-12-31", dataDigitosParaIso("31122027"))
    }

    @Test
    fun `rejeita datas que nao existem`() {
        assertNull(dataDigitosParaIso("31022027"))   // 31 de fevereiro
        assertNull(dataDigitosParaIso("15132027"))   // mês 13
    }

    @Test
    fun `rejeita data incompleta`() {
        assertNull(dataDigitosParaIso("3112202"))
    }

    @Test
    fun `formata datas para exibicao`() {
        assertEquals("15/12/2026", dataBr("2026-12-15"))
        assertEquals("19/10/2026 19:30", dataHoraBr("2026-10-19T19:30:12.123456"))
    }
}