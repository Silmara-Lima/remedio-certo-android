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

@Serializable
data class UnidadeDto(val id: Int, val nome: String)

@Serializable
data class EstoqueDto(
    val id: Int,
    @SerialName("medicamento_id") val medicamentoId: Int,
    @SerialName("unidade_id") val unidadeId: Int,
    val lote: String,
    val validade: String,               // "2026-12-15"
    val quantidade: Int,
    @SerialName("dias_para_vencer") val diasParaVencer: Int,
    @SerialName("status_estoque") val status: String,
    val medicamento: MedicamentoDto? = null,
    val unidade: UnidadeDto? = null,
)

@Serializable
data class MovimentacaoRequest(
    val tipo: String,                                   // entrada | saida | descarte | ajuste
    @SerialName("medicamento_id") val medicamentoId: Int,
    @SerialName("unidade_id") val unidadeId: Int,
    val lote: String,
    val validade: String,                               // "2026-12-15"
    val quantidade: Int,
    @SerialName("estoque_lote_id") val estoqueLoteId: Int? = null,
    @SerialName("numero_documento") val numeroDocumento: String? = null,
    val observacao: String? = null,
)

@Serializable
data class MovimentacaoDto(
    val id: Int,
    val tipo: String,
    @SerialName("medicamento_id") val medicamentoId: Int,
    @SerialName("unidade_id") val unidadeId: Int,
    val lote: String,
    val validade: String,
    val quantidade: Int,
    @SerialName("numero_documento") val numeroDocumento: String? = null,
    val observacao: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("usuario_id") val usuarioId: Int? = null,
    val medicamento: MedicamentoDto? = null,
    val unidade: UnidadeDto? = null,
)

@Serializable
data class MedicamentoDto(
    val id: Int,
    val nome: String,
    @SerialName("principio_ativo") val principioAtivo: String? = null,
    val fabricante: String? = null,
    @SerialName("forma_farmaceutica") val formaFarmaceutica: String? = null,
    val dosagem: String? = null,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    @SerialName("registro_anvisa") val registroAnvisa: String? = null,
    @SerialName("quantidade_minima") val quantidadeMinima: Int = 0,
    val ativo: Boolean = true,
)

@Serializable
data class MedicamentoRequest(
    val nome: String,
    @SerialName("principio_ativo") val principioAtivo: String?,
    val fabricante: String?,
    @SerialName("forma_farmaceutica") val formaFarmaceutica: String?,
    val dosagem: String?,
    @SerialName("codigo_barras") val codigoBarras: String?,
    @SerialName("registro_anvisa") val registroAnvisa: String?,
    @SerialName("quantidade_minima") val quantidadeMinima: Int,
    val ativo: Boolean,
)

@Serializable
data class MensagemDto(val mensagem: String)