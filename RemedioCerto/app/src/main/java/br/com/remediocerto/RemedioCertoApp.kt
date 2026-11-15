package br.com.remediocerto

import android.app.Application
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.ArmazenamentoTokens
import br.com.remediocerto.core.notificacao.Notificacoes
import br.com.remediocerto.core.offline.Sincronizacao
import br.com.remediocerto.core.session.SessionManager
import kotlinx.coroutines.runBlocking

class RemedioCertoApp : Application() {
    lateinit var sessao: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        sessao = SessionManager(this)

        // A renovação do token roda numa thread de rede do OkHttp (nunca na tela),
        // por isso pode esperar a leitura e a gravação do DataStore.
        ApiClient.armazenamento = object : ArmazenamentoTokens {
            override fun lerRefreshToken(): String? = runBlocking { sessao.refreshToken() }
            override fun salvarTokens(accessToken: String, refreshToken: String) {
                runBlocking { sessao.atualizarTokens(accessToken, refreshToken) }
            }
        }

        // Envia o que ficou na fila de movimentações offline assim que houver internet.
        Sincronizacao.agendar(this)

        // Notificações de alertas: canal do Android e verificação periódica em segundo plano.
        Notificacoes.criarCanal(this)
        Notificacoes.agendar(this)
    }
}
