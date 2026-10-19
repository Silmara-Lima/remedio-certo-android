package br.com.remediocerto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.remediocerto.core.navigation.AppNavigation
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.ui.theme.RemedioCertoTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RemedioCertoApp
        val sessaoSalva = runBlocking { app.sessao.carregar() }
        ApiClient.token = sessaoSalva?.first
        setContent {
            RemedioCertoTheme {
                AppNavigation(usuarioInicial = sessaoSalva?.second)
            }
        }
    }
}