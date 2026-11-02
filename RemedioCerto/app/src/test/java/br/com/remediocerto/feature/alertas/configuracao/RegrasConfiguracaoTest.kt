package br.com.remediocerto.feature.alertas.configuracao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasConfiguracaoTest {

    private fun erro(r: ResultadoValidacao) = (r as ResultadoValidacao.Erro).mensagem

    @Test
    fun `padroes do plano sao aceitos`() {
        val r = validarConfiguracao("10", "30", "7")
        assertEquals(ResultadoValidacao.Ok(ValoresConfiguracao(10, 30, 7)), r)
    }

    @Test
    fun `aceita espacos em volta dos numeros`() {
        assertTrue(validarConfiguracao(" 5 ", " 60", "15 ") is ResultadoValidacao.Ok)
    }

    @Test
    fun `estoque minimo zero e permitido`() {
        assertTrue(validarConfiguracao("0", "30", "7") is ResultadoValidacao.Ok)
    }

    @Test
    fun `campo vazio ou com letras e recusado`() {
        assertTrue(erro(validarConfiguracao("", "30", "7")).contains("estoque mínimo"))
        assertTrue(erro(validarConfiguracao("10", "abc", "7")).contains("vencimento"))
        assertTrue(erro(validarConfiguracao("10", "30", "")).contains("crítico"))
    }

    @Test
    fun `critico precisa ser menor que o aviso de vencimento`() {
        assertTrue(erro(validarConfiguracao("10", "7", "7")).contains("menos dias"))
        assertTrue(erro(validarConfiguracao("10", "7", "10")).contains("menos dias"))
    }

    @Test
    fun `dias precisam ser de pelo menos 1`() {
        assertTrue(erro(validarConfiguracao("10", "0", "0")).contains("pelo menos 1 dia"))
        assertTrue(erro(validarConfiguracao("10", "30", "0")).contains("pelo menos 1 dia"))
    }

    @Test
    fun `aviso de vencimento tem limite de um ano`() {
        assertTrue(validarConfiguracao("10", "365", "7") is ResultadoValidacao.Ok)
        assertTrue(erro(validarConfiguracao("10", "366", "7")).contains("no máximo"))
    }

    @Test
    fun `estoque minimo negativo e recusado`() {
        assertTrue(erro(validarConfiguracao("-1", "30", "7")).contains("negativo"))
    }
}
