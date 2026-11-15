package br.com.remediocerto.core.notificacao

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.remediocerto.MainActivity
import br.com.remediocerto.R
import br.com.remediocerto.RemedioCertoApp
import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.network.ApiClient
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Notificações locais de alertas (RF13).
 *
 * O app consulta os alertas da API em segundo plano (WorkManager) e mostra uma
 * notificação do Android quando aparece um alerta novo. O toque abre a aba Alertas.
 */
object Notificacoes {
    const val EXTRA_ABRIR = "abrir"
    const val ABRIR_ALERTAS = "alertas"

    private const val CANAL = "alertas"
    private const val ID_NOTIFICACAO = 1001
    private const val PREFS = "notificacoes"
    private const val CHAVE_IDS = "ids_notificados"
    private const val CHAVE_PERGUNTOU = "perguntou_permissao"
    private const val TRABALHO_PERIODICO = "verificar-alertas"
    private const val TRABALHO_AGORA = "verificar-alertas-agora"

    private val comInternet = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun criarCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Alertas de estoque", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Estoque baixo, vencimento próximo e lotes vencidos"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /** Verificação a cada 15 minutos (o menor intervalo que o Android permite), mesmo com o app fechado. */
    fun agendar(context: Context) {
        val pedido = PeriodicWorkRequestBuilder<VerificarAlertasWorker>(15, TimeUnit.MINUTES)
            .setConstraints(comInternet)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(TRABALHO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    /** Verificação imediata, por exemplo logo depois de uma movimentação. */
    fun verificarAgora(context: Context) {
        val pedido = OneTimeWorkRequestBuilder<VerificarAlertasWorker>().setConstraints(comInternet).build()
        WorkManager.getInstance(context).enqueueUniqueWork(TRABALHO_AGORA, ExistingWorkPolicy.KEEP, pedido)
    }

    fun temPermissao(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Android 13 ou superior pede permissão; perguntamos uma vez só, com explicação antes (RNF15). */
    fun precisaPedirPermissao(context: Context): Boolean =
        !temPermissao(context) && !prefs(context).getBoolean(CHAVE_PERGUNTOU, false)

    fun marcarQuePerguntou(context: Context) {
        prefs(context).edit().putBoolean(CHAVE_PERGUNTOU, true).apply()
    }

    /** Mostra a notificação dos alertas novos e guarda quais já foram avisados. */
    @Synchronized
    fun avisar(context: Context, ativos: List<AlertaDto>) {
        val jaNotificados = prefs(context).getStringSet(CHAVE_IDS, emptySet())!!
            .mapNotNull { it.toIntOrNull() }.toSet()
        val aviso = montarAviso(alertasParaNotificar(ativos, jaNotificados))

        // Guarda só os ativos de agora: um alerta resolvido que volte com outro id é avisado de novo.
        prefs(context).edit().putStringSet(CHAVE_IDS, ativos.map { it.id.toString() }.toSet()).apply()

        if (aviso == null || !temPermissao(context)) return
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFF0F766E.toInt())
            .setContentTitle(aviso.titulo)
            .setContentText(aviso.texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(abrirAlertas(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_NOTIFICACAO, notificacao)
        } catch (e: SecurityException) {
            // Permissão retirada pelo usuário entre a checagem e o envio: não faz nada.
        }
    }

    /** Ao sair da conta: tira a notificação da barra e esquece os alertas já avisados. */
    fun limpar(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_NOTIFICACAO)
        prefs(context).edit().remove(CHAVE_IDS).apply()
    }

    private fun abrirAlertas(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ABRIR, ABRIR_ALERTAS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Consulta os alertas ativos na API e avisa os novos. */
class VerificarAlertasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RemedioCertoApp
        val sessao = app.sessao.carregar() ?: return Result.success()   // ninguém logado
        if (ApiClient.token == null) ApiClient.token = sessao.first

        val ativos = try {
            runCatching { ApiClient.api.gerarAlertas() }   // pede para a API conferir o estoque antes
            ApiClient.api.listarAlertas().filter { it.status == "ativo" }
        } catch (e: IOException) {
            return Result.retry()
        } catch (e: HttpException) {
            return Result.success()   // sessão encerrada ou erro da API: tenta de novo na próxima rodada
        }
        Notificacoes.avisar(applicationContext, ativos)
        return Result.success()
    }
}
