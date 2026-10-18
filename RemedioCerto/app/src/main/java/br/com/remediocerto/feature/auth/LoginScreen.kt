package br.com.remediocerto.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.LoginRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel : ViewModel() {
    var carregando by mutableStateOf(false); private set
    var mensagem by mutableStateOf<String?>(null); private set

    fun entrar(email: String, senha: String) = viewModelScope.launch {
        carregando = true; mensagem = null
        mensagem = try {
            val r = ApiClient.api.login(LoginRequest(email.trim(), senha))
            ApiClient.token = r.accessToken
            "Olá, ${r.usuario.nome} (${r.usuario.tipo})"
        } catch (e: HttpException) {
            if (e.code() == 401) "E-mail ou senha inválidos." else "Erro ${e.code()} na API."
        } catch (e: IOException) {
            "Não foi possível conectar à API: ${e.message}"
        }
        carregando = false
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    var email by remember { mutableStateOf("admin@remediocerto.com") }
    var senha by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Remédio Certo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(senha, { senha = it }, label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.entrar(email, senha) }, enabled = !vm.carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.carregando) "Entrando..." else "Entrar")
        }
        vm.mensagem?.let { Spacer(Modifier.height(16.dp)); Text(it) }
    }
}