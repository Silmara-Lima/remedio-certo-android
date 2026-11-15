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
import br.com.remediocerto.core.notificacao.Notificacoes
import br.com.remediocerto.feature.auth.LoginScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    usuarioInicial: UsuarioDto?,
    destino: String? = null,            // "alertas" quando o app abre pela notificação
    onDestinoAberto: () -> Unit = {},
) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as RemedioCertoApp
    val scope = rememberCoroutineScope()
    var usuario by remember { mutableStateOf(usuarioInicial) }
    var avisoLogin by remember { mutableStateOf<String?>(null) }

    // avisarServidor = true no botão Sair: encerra a sessão também na API.
    fun encerrarSessao(aviso: String?, avisarServidor: Boolean = false) {
        val tokenAntigo = ApiClient.token
        ApiClient.token = null
        Notificacoes.limpar(app)
        scope.launch {
            app.sessao.limpar()
            if (avisarServidor && tokenAntigo != null) ApiClient.encerrarNoServidor(tokenAntigo)
        }
        usuario = null
        avisoLogin = aviso
        nav.navigate("login") { popUpTo(0) { inclusive = true } }   // limpa toda a pilha de telas
    }

    // Token vencido em qualquer tela -> volta ao login automaticamente
    val sessaoExpirada by ApiClient.sessaoExpirada.collectAsState()
    LaunchedEffect(sessaoExpirada) {
        if (sessaoExpirada && usuario != null) {
            encerrarSessao("Sua sessão expirou. Entre novamente.")
        }
        ApiClient.reiniciarAvisoSessao()
    }

    NavHost(nav, startDestination = if (usuarioInicial != null) "principal" else "login") {
        composable("login") {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                LoginScreen(
                    aviso = avisoLogin,
                    onLoginOk = { u ->
                        usuario = u
                        avisoLogin = null
                        nav.navigate("principal") { popUpTo("login") { inclusive = true } }
                    },
                )
            }
        }
        composable("principal") {
            usuario?.let { u ->
                MainScreen(
                    usuario = u,
                    onSair = { encerrarSessao(null, avisarServidor = true) },
                    abrirAlertas = destino == Notificacoes.ABRIR_ALERTAS,
                    onAlertasAbertos = onDestinoAberto,
                )
            }
        }
    }
}