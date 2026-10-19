package br.com.remediocerto.feature.auth

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.RemedioCertoApp
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.LoginRequest
import br.com.remediocerto.core.network.UsuarioDto
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import androidx.compose.ui.text.font.FontWeight

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var usuarioLogado by mutableStateOf<UsuarioDto?>(null); private set

    fun entrar(email: String, senha: String) = viewModelScope.launch {
        carregando = true; erro = null
        try {
            val r = ApiClient.api.login(LoginRequest(email.trim(), senha))
            ApiClient.token = r.accessToken
            getApplication<RemedioCertoApp>().sessao.salvar(r.accessToken, r.usuario)
            usuarioLogado = r.usuario
        } catch (e: HttpException) {
            erro = if (e.code() == 401) "E-mail ou senha inválidos." else "Erro ${e.code()} na API."
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API: ${e.message}"
        }
        carregando = false
    }
}

@Composable
fun LoginScreen(onLoginOk: (UsuarioDto) -> Unit, vm: LoginViewModel = viewModel()) {
    var email by remember { mutableStateOf("admin@remediocerto.com") }
    var senha by remember { mutableStateOf("") }

    LaunchedEffect(vm.usuarioLogado) { vm.usuarioLogado?.let(onLoginOk) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
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
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(senha, { senha = it }, label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.entrar(email, senha) }, enabled = !vm.carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.carregando) "Entrando..." else "Entrar")
        }
        vm.erro?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}