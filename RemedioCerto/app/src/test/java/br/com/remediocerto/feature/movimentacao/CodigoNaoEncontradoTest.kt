package br.com.remediocerto.feature.movimentacao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigoNaoEncontradoTest {

    @Test
    fun `administrador pode cadastrar o codigo que nao existe`() {
        assertTrue(podeCadastrarPeloCodigo("7891234567890", isAdmin = true, semConexao = false))
        assertTrue(mensagemCodigoNaoEncontrado("789", isAdmin = true, semConexao = false).contains("cadastrá-lo agora"))
    }

    @Test
    fun `funcionario e orientado a pedir o cadastro`() {
        assertFalse(podeCadastrarPeloCodigo("789", isAdmin = false, semConexao = false))
        assertTrue(mensagemCodigoNaoEncontrado("789", isAdmin = false, semConexao = false).contains("Peça ao administrador"))
    }

    @Test
    fun `sem internet nao oferece cadastro`() {
        assertFalse(podeCadastrarPeloCodigo("789", isAdmin = true, semConexao = true))
        assertTrue(mensagemCodigoNaoEncontrado("789", isAdmin = true, semConexao = true).contains("conecte-se à internet"))
    }

    @Test
    fun `sem codigo pendente nao ha botao`() {
        assertFalse(podeCadastrarPeloCodigo(null, isAdmin = true, semConexao = false))
    }
}
