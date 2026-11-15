package br.com.remediocerto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.remediocerto.core.navigation.AppNavigation
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.ui.theme.RemedioCertoTheme
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.remediocerto.core.notificacao.Notificacoes

class MainActivity : ComponentActivity() {
    // Tela pedida pelo toque na notificação ("alertas"), até ser aberta.
    private var destino by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) destino = intent?.getStringExtra(Notificacoes.EXTRA_ABRIR)
        enableEdgeToEdge()
        val app = application as RemedioCertoApp
        val sessaoSalva = runBlocking { app.sessao.carregar() }
        ApiClient.token = sessaoSalva?.first
        setContent {
            RemedioCertoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(
                        usuarioInicial = sessaoSalva?.second,
                        destino = destino,
                        onDestinoAberto = { destino = null },
                    )
                }
            }
        }
    }

    // App já aberto e o usuário toca na notificação.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Notificacoes.EXTRA_ABRIR)?.let { destino = it }
    }
}