package br.com.remediocerto.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
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
}

object ApiClient {
    // Emulador com túnel (adb reverse tcp:8000 tcp:8000): http://127.0.0.1:8000/
    // Sem túnel, no emulador: http://10.0.2.2:8000/
    // Quando a API estiver hospedada, trocar pelo endereço https dela.
    private const val BASE_URL = " http://127.0.0.1:8000/"

    var token: String? = null

    // Vira true quando a API responde 401 numa chamada autenticada (token vencido ou inválido).
    private val _sessaoExpirada = MutableStateFlow(false)
    val sessaoExpirada: StateFlow<Boolean> = _sessaoExpirada
    fun reiniciarAvisoSessao() { _sessaoExpirada.value = false }

    private val json = Json { ignoreUnknownKeys = true }

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
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: RemedioCertoApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RemedioCertoApi::class.java)
}