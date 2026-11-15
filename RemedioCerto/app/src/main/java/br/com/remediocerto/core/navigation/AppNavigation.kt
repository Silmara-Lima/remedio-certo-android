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
import br.com.remediocerto.core.seguranca.Biometria
import br.com.remediocerto.feature.auth.DesbloqueioScreen
import br.com.remediocerto.feature.auth.LoginScreen
import br.com.remediocerto.feature.auth.RecuperarSenhaScreen
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
    var avisoInfo by remember { mutableStateOf<String?>(null) }
    var emailRecuperacao by remember { mutableStateOf("") }

    // Sessão salva + biometria ligada: pede a biometria antes de abrir o app (RF02).
    var bloqueado by remember { mutableStateOf(usuarioInicial != null && Biometria.deveDesbloquear(app)) }
    var destinoInicial by remember { mutableStateOf(if (usuarioInicial != null) "principal" else "login") }
    var oferecerBiometria by remember { mutableStateOf(false) }

    // avisarServidor = true no botão Sair: encerra a sessão também na API.
    fun encerrarSessao(aviso: String?, avisarServidor: Boolean = false) {
        val tokenAntigo = ApiClient.token
        ApiClient.token = null
        Notificacoes.limpar(app)
        Biometria.limpar(app)
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
        if (sessaoExpirada && usuario != null && !bloqueado) {
            encerrarSessao("Sua sessão expirou. Entre novamente.")
        }
        ApiClient.reiniciarAvisoSessao()
    }

    if (bloqueado) {
        DesbloqueioScreen(
            nome = usuarioInicial?.nome.orEmpty(),
            onDesbloqueado = { bloqueado = false },
            onUsarSenha = {
                // Entrar com senha: encerra a sessão salva e abre o login (o NavHost ainda não existe aqui).
                ApiClient.token = null
                Notificacoes.limpar(app)
                Biometria.limpar(app)
                scope.launch { app.sessao.limpar() }
                usuario = null
                destinoInicial = "login"
                bloqueado = false
            },
        )
        return
    }

    NavHost(nav, startDestination = destinoInicial) {
        composable("login") {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                LoginScreen(
                    aviso = avisoLogin,
                    avisoInfo = avisoInfo,
                    onLoginOk = { u ->
                        usuario = u
                        avisoLogin = null
                        avisoInfo = null
                        oferecerBiometria = Biometria.podeOferecer(app)   // Fluxo C do plano
                        nav.navigate("principal") { popUpTo("login") { inclusive = true } }
                    },
                    onEsqueciSenha = { email ->
                        emailRecuperacao = email
                        nav.navigate("recuperar-senha")
                    },
                )
            }
        }
        composable("recuperar-senha") {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                RecuperarSenhaScreen(
                    emailInicial = emailRecuperacao,
                    onVoltar = { nav.popBackStack() },
                    onConcluido = {
                        avisoLogin = null
                        avisoInfo = "Senha redefinida. Entre com a nova senha."
                        nav.popBackStack()
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
                    oferecerBiometria = oferecerBiometria,
                    onOfertaBiometriaRespondida = { oferecerBiometria = false },
                )
            }
        }
    }
}