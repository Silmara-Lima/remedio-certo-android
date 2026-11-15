package br.com.remediocerto.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Autenticação ----------
@Serializable
data class LoginRequest(val email: String, val senha: String)

@Serializable
data class TrocarSenhaRequest(
    @SerialName("senha_atual") val senhaAtual: String,
    @SerialName("nova_senha") val novaSenha: String,
)

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
    // Vem do login e do /auth/refresh. Serve para pedir um access token novo sem novo login.
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiraEmSegundos: Int? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UsuarioResumoDto(val id: Int, val nome: String)

@Serializable
data class UsuarioAdminDto(
    val id: Int,
    val nome: String,
    val email: String,
    val perfil: String,                                  // "admin" | "funcionario"
    val ativo: Boolean = true,
    @SerialName("unidade_id") val unidadeId: Int? = null,
    @SerialName("ultimo_acesso") val ultimoAcesso: String? = null,
)

@Serializable
data class UsuarioRequest(
    val nome: String,
    val email: String,
    val perfil: String,
    val ativo: Boolean,
    @SerialName("unidade_id") val unidadeId: Int?,
    val senha: String?,                                  // na edição, null = manter a senha atual
)

@Serializable
data class MensagemDto(val mensagem: String)

// ---------- Unidades ----------
@Serializable
data class UnidadeDto(
    val id: Int,
    val nome: String,
    val cnpj: String? = null,
    val endereco: String? = null,
    val telefone: String? = null,
    val ativa: Boolean = true,
)

@Serializable
data class UnidadeRequest(
    val nome: String,
    val cnpj: String?,
    val endereco: String?,
    val telefone: String?,
    val ativa: Boolean,
)

// ---------- Medicamentos ----------
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

// ---------- Estoque ----------
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

// ---------- Alertas ----------
// Lote resumido que vem dentro do alerta. Tudo com valor padrão para a tela
// não quebrar se a API mandar o lote incompleto.
@Serializable
data class AlertaLoteDto(
    val id: Int,
    val lote: String = "",
    val validade: String = "",
    val quantidade: Int = 0,
    @SerialName("dias_para_vencer") val diasParaVencer: Int? = null,
)

@Serializable
data class AlertaDto(
    val id: Int,
    val tipo: String,                                   // estoque_baixo | vencimento_proximo | vencido
    val mensagem: String,
    val status: String,                                 // ativo | resolvido | ignorado
    @SerialName("medicamento_id") val medicamentoId: Int,
    @SerialName("unidade_id") val unidadeId: Int,
    @SerialName("gerado_em") val geradoEm: String,
    @SerialName("resolvido_em") val resolvidoEm: String? = null,
    @SerialName("resolvido_por") val resolvidoPor: UsuarioResumoDto? = null,   // quem resolveu ou ignorou (RF14)
    val medicamento: MedicamentoDto? = null,
    val unidade: UnidadeDto? = null,
    @SerialName("estoque_lote") val estoqueLote: AlertaLoteDto? = null,
)

// ---------- Configuração de alertas (uma por unidade) ----------
@Serializable
data class ConfiguracaoAlertaDto(
    val id: Int,
    @SerialName("unidade_id") val unidadeId: Int,
    @SerialName("estoque_minimo_padrao") val estoqueMinimoPadrao: Int = 10,
    @SerialName("dias_alerta_vencimento") val diasAlertaVencimento: Int = 30,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int = 7,
    @SerialName("notificacao_email") val notificacaoEmail: Boolean = false,
    @SerialName("notificacao_push") val notificacaoPush: Boolean = true,
)

// Serve para criar (POST) e para atualizar (PUT); na atualização a API ignora unidade_id.
@Serializable
data class ConfiguracaoAlertaRequest(
    @SerialName("unidade_id") val unidadeId: Int,
    @SerialName("estoque_minimo_padrao") val estoqueMinimoPadrao: Int,
    @SerialName("dias_alerta_vencimento") val diasAlertaVencimento: Int,
    @SerialName("dias_alerta_critico") val diasAlertaCritico: Int,
    @SerialName("notificacao_email") val notificacaoEmail: Boolean,
    @SerialName("notificacao_push") val notificacaoPush: Boolean,
)

// ---------- Movimentações ----------
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
    @SerialName("criada_em") val criadaEm: String? = null,
    @SerialName("usuario_id") val usuarioId: Int? = null,
    val medicamento: MedicamentoDto? = null,
    val unidade: UnidadeDto? = null,
)

// ---------- Envio em lote (movimentações feitas sem internet) ----------
// Cada item é o JSON de uma MovimentacaoRequest mais o "id_local" (UUID).
@Serializable
data class LoteMovimentacoesRequest(val movimentacoes: List<JsonObject>)

@Serializable
data class ResultadoSyncDto(
    @SerialName("id_local") val idLocal: String? = null,
    val status: String,                                 // aceito | rejeitado
    val repetida: Boolean = false,                      // a API já tinha recebido este id_local
    val codigo: String? = null,
    val mensagem: String? = null,
)

@Serializable
data class LoteMovimentacoesResposta(val resultados: List<ResultadoSyncDto>)

@Serializable
data class HistoricoDto(
    val id: Int,
    val tipo: String,                   // "entrada", "saida"...
    val lote: String,
    val validade: String,
    val quantidade: Int,
    @SerialName("numero_documento") val numeroDocumento: String? = null,
    val observacao: String? = null,
    @SerialName("criada_em") val criadaEm: String,
    val medicamento: MedicamentoDto? = null,
    val unidade: UnidadeDto? = null,
    val usuario: UsuarioResumoDto? = null,
)

// ---------- Dashboard ----------
@Serializable
data class MovRecenteDto(
    val id: Int,
    val tipo: String,                   // "ENTRADA", "SAIDA"...
    val medicamento: String? = null,
    val unidade: String? = null,
    val lote: String? = null,
    val quantidade: Int = 0,
    val responsavel: String? = null,
    val data: String? = null,
)

@Serializable
data class DashboardDto(
    val alertasAtivos: Int = 0,
    val estoqueCritico: Int = 0,
    val lotesVencimentoProximo: Int = 0,
    val movimentacoesHoje: Int = 0,
    val totalMedicamentos: Int = 0,
    val totalLotes: Int = 0,
    val movimentacoesRecentes: List<MovRecenteDto> = emptyList(),
)