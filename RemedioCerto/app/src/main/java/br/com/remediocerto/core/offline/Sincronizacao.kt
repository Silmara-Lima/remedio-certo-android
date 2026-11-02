package br.com.remediocerto.core.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.remediocerto.RemedioCertoApp
import br.com.remediocerto.core.database.DatabaseProvider
import br.com.remediocerto.core.database.dao.MovimentacaoPendenteDao
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.LoteMovimentacoesRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ResultadoEnvio { CONCLUIDO, SEM_CONEXAO, SESSAO_ENCERRADA }

object Sincronizacao {
    private const val NOME_TRABALHO = "sincronizar-movimentacoes"
    private const val TAMANHO_LOTE = 100   // limite da API por envio

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Pede ao WorkManager para enviar as pendentes assim que houver internet,
     * mesmo com o app fechado. Se já houver um envio agendado, mantém o existente.
     */
    fun agendar(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<SincronizacaoWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOME_TRABALHO, ExistingWorkPolicy.KEEP, pedido)
    }

    /** Item do envio em lote: o JSON da movimentação mais o id_local. */
    fun itemDoLote(idLocal: String, payload: String): JsonObject {
        val dados = json.parseToJsonElement(payload).jsonObject
        return JsonObject(dados + ("id_local" to JsonPrimitive(idLocal)))
    }

    /** Envia as pendentes do usuário, de 100 em 100, até acabar a fila. */
    suspend fun enviarPendentes(dao: MovimentacaoPendenteDao, usuarioId: Int): ResultadoEnvio {
        while (true) {
            val fila = dao.pendentes(usuarioId, TAMANHO_LOTE)
            if (fila.isEmpty()) return ResultadoEnvio.CONCLUIDO

            val resposta = try {
                ApiClient.api.sincronizarMovimentacoes(
                    LoteMovimentacoesRequest(fila.map { itemDoLote(it.idLocal, it.payload) })
                )
            } catch (e: IOException) {
                return ResultadoEnvio.SEM_CONEXAO
            } catch (e: HttpException) {
                return if (e.code() == 401) ResultadoEnvio.SESSAO_ENCERRADA else ResultadoEnvio.SEM_CONEXAO
            }
            if (resposta.resultados.size < fila.size) return ResultadoEnvio.SEM_CONEXAO

            // A API responde na mesma ordem do envio.
            fila.zip(resposta.resultados).forEach { (pendente, resultado) ->
                if (resultado.status == "aceito") {
                    dao.remover(pendente.idLocal)
                } else {
                    dao.marcarRejeitada(
                        pendente.idLocal,
                        resultado.mensagem ?: resultado.codigo ?: "Recusada pela API.",
                    )
                }
            }
        }
    }
}

/** Trabalho em segundo plano que esvazia a fila de movimentações feitas sem internet. */
class SincronizacaoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RemedioCertoApp
        val sessao = app.sessao.carregar() ?: return Result.success()   // ninguém logado
        if (ApiClient.token == null) ApiClient.token = sessao.first        // app aberto só pelo WorkManager

        val dao = DatabaseProvider.getDatabase(applicationContext).movimentacaoPendenteDao()
        return when (Sincronizacao.enviarPendentes(dao, sessao.second.id)) {
            ResultadoEnvio.SEM_CONEXAO -> Result.retry()
            // Sessão encerrada: a fila fica guardada e sai no próximo login.
            ResultadoEnvio.CONCLUIDO, ResultadoEnvio.SESSAO_ENCERRADA -> Result.success()
        }
    }
}
