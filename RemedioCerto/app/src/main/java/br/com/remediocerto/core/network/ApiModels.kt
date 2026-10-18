package br.com.remediocerto.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val senha: String)


@Serializable
data class UsuarioDto(
    val id: Int,
    val nome: String,
    val email: String,
    val tipo: String,            // "ADMIN" ou "FUNCIONARIO"
    val ativo: Boolean,
    val unidadeSaudeId: Int? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val usuario: UsuarioDto,
)