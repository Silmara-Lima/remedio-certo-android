package br.com.remediocerto.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.remediocerto.RemedioCertoApp
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.feature.auth.LoginScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(usuarioInicial: UsuarioDto?) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as RemedioCertoApp
    val scope = rememberCoroutineScope()
    var usuario by remember { mutableStateOf(usuarioInicial) }

    NavHost(nav, startDestination = if (usuarioInicial != null) "principal" else "login") {
        composable("login") {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                LoginScreen(onLoginOk = { u ->
                    usuario = u
                    nav.navigate("principal") { popUpTo("login") { inclusive = true } }
                })
            }
        }
        composable("principal") {
            usuario?.let { u ->
                MainScreen(usuario = u, onSair = {
                    scope.launch { app.sessao.limpar() }
                    ApiClient.token = null
                    usuario = null
                    nav.navigate("login") { popUpTo("principal") { inclusive = true } }
                })
            }
        }
    }
}