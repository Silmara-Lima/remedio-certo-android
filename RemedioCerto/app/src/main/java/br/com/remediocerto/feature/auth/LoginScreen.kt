package br.com.remediocerto.feature.auth

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.RemedioCertoApp
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.LoginRequest
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.core.notificacao.Notificacoes
import br.com.remediocerto.core.offline.Sincronizacao
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var usuarioLogado by mutableStateOf<UsuarioDto?>(null); private set

    fun entrar(email: String, senha: String) = viewModelScope.launch {
        carregando = true; erro = null
        try {
            val r = ApiClient.api.login(LoginRequest(email.trim(), senha))
            ApiClient.token = r.accessToken
            getApplication<RemedioCertoApp>().sessao.salvar(r.accessToken, r.usuario, r.refreshToken)
            Sincronizacao.agendar(getApplication())   // envia movimentações offline que ficaram de antes
            Notificacoes.verificarAgora(getApplication())   // avisa os alertas ativos da unidade
            usuarioLogado = r.usuario
        } catch (e: HttpException) {
            erro = when (e.code()) {
                401 -> "E-mail ou senha inválidos."
                422 -> "Verifique o formato do e-mail e preencha a senha."
                429 -> "Muitas tentativas de login. Aguarde alguns minutos e tente de novo."
                else -> "Erro ${e.code()} na API."
            }
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }
}

@Composable
fun LoginScreen(
    onLoginOk: (UsuarioDto) -> Unit,
    aviso: String? = null,                       // problema (ex.: sessão expirada), em vermelho
    avisoInfo: String? = null,                   // informação (ex.: senha redefinida), em destaque neutro
    onEsqueciSenha: (email: String) -> Unit = {},
    vm: LoginViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("admin@remediocerto.com") }
    var senha by remember { mutableStateOf("") }

    LaunchedEffect(vm.usuarioLogado) { vm.usuarioLogado?.let(onLoginOk) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Remédio Certo",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Controle de estoque de medicamentos",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Aviso vindo de fora (ex.: sessão expirada)
        if (aviso != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(aviso, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (avisoInfo != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(avisoInfo, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.entrar(email, senha) },
            enabled = !vm.carregando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (vm.carregando) "Entrando..." else "Entrar")
        }
        vm.erro?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        // RF03 / T04: recuperação de senha por código enviado ao e-mail.
        TextButton(onClick = { onEsqueciSenha(email.trim()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Esqueci minha senha")
        }
    }
}