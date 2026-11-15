package br.com.remediocerto.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecuperarSenhaTest {

    @Test
    fun `email precisa ter formato valido`() {
        assertNull(validarEmailRecuperacao("maria@remediocerto.com"))
        assertNull(validarEmailRecuperacao("  maria@remediocerto.com  "))
        assertNotNull(validarEmailRecuperacao("maria"))
        assertNotNull(validarEmailRecuperacao(""))
    }

    @Test
    fun `codigo precisa ter 6 numeros`() {
        assertNull(validarRedefinicao("123456", "segredo", "segredo"))
        assertEquals("O código tem 6 números. Confira no e-mail.", validarRedefinicao("12345", "segredo", "segredo"))
        assertNotNull(validarRedefinicao("12a456", "segredo", "segredo"))
    }

    @Test
    fun `senha curta ou diferente da confirmacao e recusada`() {
        assertNotNull(validarRedefinicao("123456", "abc", "abc"))
        assertEquals("As senhas não conferem.", validarRedefinicao("123456", "segredo1", "segredo2"))
    }

    @Test
    fun `mensagens dos erros da API`() {
        assertEquals("A recuperação por e-mail ainda não está configurada no servidor.", mensagemErroRecuperacao(503, "x"))
        assertEquals("Código inválido ou expirado. Peça um novo código.", mensagemErroRecuperacao(400, "x"))
        assertEquals("padrão", mensagemErroRecuperacao(500, "padrão"))
    }
}
