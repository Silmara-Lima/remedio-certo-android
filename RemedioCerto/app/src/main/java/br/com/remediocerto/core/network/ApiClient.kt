package br.com.remediocerto.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RemedioCertoApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): TokenResponse
    @GET("auth/me") suspend fun me(): UsuarioDto
    @GET("estoque") suspend fun listarEstoque(): List<EstoqueDto>
    @GET("estoque") suspend fun listarEstoque(@Query("medicamento_id") medicamentoId: Int? = null): List<EstoqueDto>
    @GET("medicamentos") suspend fun buscarMedicamentos(@Query("busca") busca: String? = null): List<MedicamentoDto>
    @GET("unidades") suspend fun listarUnidades(): List<UnidadeDto>
    @POST("movimentacoes") suspend fun registrarMovimentacao(@Body body: MovimentacaoRequest): MovimentacaoDto
}

object ApiClient {
    private const val BASE_URL = "http://127.0.0.1:8000/"   // http://10.0.2.2:8000/ trocar depois
    // & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000 usar túnel no powershell
    var token: String? = null                              // depois vai para o DataStore

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
            token?.let { req.header("Authorization", "Bearer $it") }
            chain.proceed(req.build())
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