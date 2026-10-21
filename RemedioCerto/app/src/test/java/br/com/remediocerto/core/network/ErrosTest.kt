package br.com.remediocerto.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrosTest {

    private fun erroHttp(codigo: Int, corpo: String) =
        HttpException(Response.error<Any>(codigo, corpo.toResponseBody("application/json".toMediaType())))

    @Test
    fun `401 vira aviso de sessao expirada`() {
        assertTrue(mensagemDeErro(erroHttp(401, """{"detail":"Token invalido"}""")).startsWith("Sessão expirada"))
    }

    @Test
    fun `usa a mensagem enviada pela API`() {
        assertEquals("Estoque insuficiente.", mensagemDeErro(erroHttp(400, """{"detail":"Estoque insuficiente."}""")))
    }

    @Test
    fun `422 com lista tecnica vira mensagem amigavel`() {
        val corpo = """{"detail":[{"type":"value_error","loc":["body","email"],"msg":"invalid"}]}"""
        assertTrue(mensagemDeErro(erroHttp(422, corpo)).startsWith("Dados inválidos"))
    }

    @Test
    fun `erro sem corpo legivel mostra o codigo`() {
        assertEquals("Erro 500 na API.", mensagemDeErro(erroHttp(500, "Internal Server Error")))
    }
}