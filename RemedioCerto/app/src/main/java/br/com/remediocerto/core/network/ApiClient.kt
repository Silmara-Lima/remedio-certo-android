package br.com.remediocerto.core.network

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface RemedioCertoApi {
    // ---------- Autenticação ----------
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): TokenResponse
    @GET("auth/me") suspend fun me(): UsuarioDto
    @PUT("auth/trocar-senha") suspend fun trocarSenha(@Body body: TrocarSenhaRequest): MensagemDto

    // ---------- Dashboard ----------
    @GET("dashboard") suspend fun dashboard(): DashboardDto

    // ---------- Estoque ----------
    @GET("estoque") suspend fun listarEstoque(@Query("medicamento_id") medicamentoId: Int? = null): List<EstoqueDto>

    // ---------- Usuários ----------
    @GET("usuarios") suspend fun listarUsuarios(@Query("somente_ativos") somenteAtivos: Boolean = true): List<UsuarioAdminDto>
    @GET("usuarios/{id}") suspend fun buscarUsuario(@Path("id") id: Int): UsuarioAdminDto
    @POST("usuarios") suspend fun criarUsuario(@Body body: UsuarioRequest): UsuarioAdminDto
    @PUT("usuarios/{id}") suspend fun atualizarUsuario(@Path("id") id: Int, @Body body: UsuarioRequest): UsuarioAdminDto
    @DELETE("usuarios/{id}") suspend fun desativarUsuario(@Path("id") id: Int): MensagemDto

    // ---------- Movimentações ----------
    @POST("movimentacoes") suspend fun registrarMovimentacao(@Body body: MovimentacaoRequest): MovimentacaoDto
    // Envio com id_local: a API reconhece reenvios e nunca aplica a mesma movimentação duas vezes.
    @POST("movimentacoes/lote") suspend fun sincronizarMovimentacoes(@Body body: LoteMovimentacoesRequest): LoteMovimentacoesResposta
    @GET("movimentacoes") suspend fun listarMovimentacoes(
        @Query("tipo") tipo: String? = null,
        @Query("limite") limite: Int = 100,
    ): List<HistoricoDto>

    // ---------- Medicamentos ----------
    @GET("medicamentos") suspend fun buscarMedicamentos(
        @Query("busca") busca: String? = null,
        @Query("somente_ativos") somenteAtivos: Boolean = true,
    ): List<MedicamentoDto>
    @GET("medicamentos/{id}") suspend fun buscarMedicamento(@Path("id") id: Int): MedicamentoDto
    @POST("medicamentos") suspend fun criarMedicamento(@Body body: MedicamentoRequest): MedicamentoDto
    @PUT("medicamentos/{id}") suspend fun atualizarMedicamento(@Path("id") id: Int, @Body body: MedicamentoRequest): MedicamentoDto
    @DELETE("medicamentos/{id}") suspend fun desativarMedicamento(@Path("id") id: Int): MensagemDto

    // ---------- Unidades ----------
    @GET("unidades") suspend fun listarUnidades(@Query("somente_ativas") somenteAtivas: Boolean = true): List<UnidadeDto>
    @GET("unidades/{id}") suspend fun buscarUnidade(@Path("id") id: Int): UnidadeDto
    @POST("unidades") suspend fun criarUnidade(@Body body: UnidadeRequest): UnidadeDto
    @PUT("unidades/{id}") suspend fun atualizarUnidade(@Path("id") id: Int, @Body body: UnidadeRequest): UnidadeDto
    @DELETE("unidades/{id}") suspend fun desativarUnidade(@Path("id") id: Int): MensagemDto

    // ---------- Alertas ----------
    // "gerar" pede para a API conferir o estoque e criar os alertas que faltam.
    @POST("alertas/gerar") suspend fun gerarAlertas(): MensagemDto
    @GET("alertas") suspend fun listarAlertas(@Query("status_alerta") status: String? = null): List<AlertaDto>
    @GET("alertas/{id}") suspend fun buscarAlerta(@Path("id") id: Int): AlertaDto
    @PATCH("alertas/{id}/resolver") suspend fun resolverAlerta(@Path("id") id: Int): AlertaDto
    @PATCH("alertas/{id}/ignorar") suspend fun ignorarAlerta(@Path("id") id: Int): AlertaDto

    // ---------- Configuração de alertas ----------
    @GET("configuracoes-alerta") suspend fun listarConfiguracoesAlerta(): List<ConfiguracaoAlertaDto>
    @POST("configuracoes-alerta") suspend fun criarConfiguracaoAlerta(@Body body: ConfiguracaoAlertaRequest): ConfiguracaoAlertaDto
    @PUT("configuracoes-alerta/{id}") suspend fun atualizarConfiguracaoAlerta(
        @Path("id") id: Int,
        @Body body: ConfiguracaoAlertaRequest,
    ): ConfiguracaoAlertaDto
}

/**
 * Rotas de sessão chamadas por um cliente separado, sem renovação automática,
 * para a renovação não chamar a si mesma.
 */
interface SessaoApi {
    @POST("auth/refresh") fun renovar(@Body body: RefreshRequest): Call<TokenResponse>
    @POST("auth/logout") suspend fun logout(@Header("Authorization") autorizacao: String): MensagemDto
}

/** Onde os tokens ficam guardados no aparelho (implementado em RemedioCertoApp com o SessionManager). */
interface ArmazenamentoTokens {
    fun lerRefreshToken(): String?
    fun salvarTokens(accessToken: String, refreshToken: String)
}

object ApiClient {
    // Emulador com túnel (adb reverse tcp:8000 tcp:8000): http://127.0.0.1:8000/
    // Sem túnel, no emulador: http://10.0.2.2:8000/
    // Quando a API estiver hospedada, trocar pelo endereço https dela.
    private const val BASE_URL = "http://127.0.0.1:8000/"

    var token: String? = null

    /** Definido no RemedioCertoApp. Sem ele, não há renovação e o 401 leva ao login. */
    var armazenamento: ArmazenamentoTokens? = null

    // Vira true quando a API responde 401 numa chamada autenticada (token vencido ou inválido).
    private val _sessaoExpirada = MutableStateFlow(false)
    val sessaoExpirada: StateFlow<Boolean> = _sessaoExpirada
    fun reiniciarAvisoSessao() { _sessaoExpirada.value = false }

    private val json = Json { ignoreUnknownKeys = true }
    private val conversor = json.asConverterFactory("application/json".toMediaType())
    private val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    private val sessaoApi: SessaoApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor(log).build())
        .addConverterFactory(conversor)
        .build()
        .create(SessaoApi::class.java)

    private val travaRenovacao = Any()

    private fun ehRotaDeSessao(caminho: String) =
        caminho.endsWith("/auth/login") || caminho.endsWith("/auth/refresh") || caminho.endsWith("/auth/logout")

    // Quando a API responde 401, tenta trocar o refresh token por um access token novo
    // e repete a chamada uma vez. Se não der, devolve o 401 e o app volta ao login.
    private val renovador = Authenticator { _, resposta ->
        val pedido = resposta.request
        if (ehRotaDeSessao(pedido.url.encodedPath)) return@Authenticator null
        if (resposta.priorResponse != null) return@Authenticator null   // já tentou uma vez
        val tokenUsado = pedido.header("Authorization")?.removePrefix("Bearer ")

        synchronized(travaRenovacao) {
            val atual = token ?: return@Authenticator null               // usuário saiu
            val novo = if (atual != tokenUsado) atual                    // outra chamada já renovou
                       else renovarTokens() ?: return@Authenticator null
            pedido.newBuilder().header("Authorization", "Bearer $novo").build()
        }
    }

    /** Chama /auth/refresh. Devolve o access token novo ou null se o refresh token não vale mais. */
    private fun renovarTokens(): String? {
        val refresh = armazenamento?.lerRefreshToken() ?: return null
        return try {
            val r = sessaoApi.renovar(RefreshRequest(refresh)).execute()
            val corpo = r.body()
            val novoRefresh = corpo?.refreshToken
            if (!r.isSuccessful || corpo == null || novoRefresh == null) return null
            if (token == null) return null   // o usuário saiu enquanto a renovação estava em andamento
            token = corpo.accessToken
            armazenamento?.salvarTokens(corpo.accessToken, novoRefresh)
            corpo.accessToken
        } catch (e: Exception) {
            null
        }
    }

    /** Encerra a sessão também no servidor (botão Sair). Falha em silêncio: o app sai de qualquer jeito. */
    suspend fun encerrarNoServidor(accessToken: String) {
        runCatching { sessaoApi.logout("Bearer $accessToken") }
    }

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val tokenAtual = token
            val req = chain.request().newBuilder()
            tokenAtual?.let { req.header("Authorization", "Bearer $it") }
            val resposta = chain.proceed(req.build())
            val ehLogin = chain.request().url.encodedPath.endsWith("/auth/login")
            if (resposta.code == 401 && tokenAtual != null && !ehLogin) {
                _sessaoExpirada.value = true
            }
            resposta
        }
        .authenticator(renovador)
        .addInterceptor(log)
        .build()

    val api: RemedioCertoApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(conversor)
        .build()
        .create(RemedioCertoApi::class.java)
}