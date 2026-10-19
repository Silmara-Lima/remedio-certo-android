package br.com.remediocerto

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
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    AppNavigation(usuarioInicial = sessaoSalva?.second)
                }
            }
        }
    }
}