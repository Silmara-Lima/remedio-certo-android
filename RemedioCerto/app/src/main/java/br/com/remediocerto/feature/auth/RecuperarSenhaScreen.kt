package br.com.remediocerto.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.RecuperarSenhaRequest
import br.com.remediocerto.core.network.RedefinirSenhaRequest
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.CampoSenha
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// ---------- Regras (funções puras, testadas sem a API) ----------

fun validarEmailRecuperacao(email: String): String? =
    if (!email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) "Informe um e-mail válido." else null

fun validarRedefinicao(codigo: String, senha: String, confirmacao: String): String? = when {
    !codigo.matches(Regex("^\\d{6}$")) -> "O código tem 6 números. Confira no e-mail."
    senha.length < 6 -> "A nova senha precisa ter pelo menos 6 caracteres."
    senha != confirmacao -> "As senhas não conferem."
    else -> null
}

/** Mensagens dos erros específicos da recuperação; os demais usam a mensagem padrão da API. */
fun mensagemErroRecuperacao(codigoHttp: Int, padrao: String): String = when (codigoHttp) {
    503 -> "A recuperação por e-mail ainda não está configurada no servidor."
    429 -> "Muitas tentativas. Aguarde alguns minutos e tente de novo."
    400 -> "Código inválido ou expirado. Peça um novo código."
    else -> padrao
}

// ---------- Estado da tela ----------

class RecuperarSenhaViewModel : ViewModel() {
    var email by mutableStateOf("")
    var codigo by mutableStateOf("")
    var novaSenha by mutableStateOf("")
    var confirmacao by mutableStateOf("")

    /** false: pedir o código; true: digitar o código e a nova senha. */
    var codigoEnviado by mutableStateOf(false); private set
    var enviando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var concluido by mutableStateOf(false); private set

    fun pedirCodigo() {
        erro = validarEmailRecuperacao(email)
        if (erro != null) return
        executar {
            ApiClient.api.recuperarSenha(RecuperarSenhaRequest(email.trim()))
            codigoEnviado = true
        }
    }

    fun redefinir() {
        erro = validarRedefinicao(codigo, novaSenha, confirmacao)
        if (erro != null) return
        executar {
            ApiClient.api.redefinirSenha(RedefinirSenhaRequest(email.trim(), codigo, novaSenha))
            concluido = true
        }
    }

    fun voltarParaEmail() {
        codigoEnviado = false; codigo = ""; novaSenha = ""; confirmacao = ""; erro = null
    }

    private fun executar(acao: suspend () -> Unit) = viewModelScope.launch {
        erro = null
        enviando = true
        try {
            acao()
        } catch (e: HttpException) {
            erro = mensagemErroRecuperacao(e.code(), mensagemDeErro(e))
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        enviando = false
    }
}

// ---------- Tela (T04 do plano) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecuperarSenhaScreen(
    emailInicial: String,
    onVoltar: () -> Unit,
    onConcluido: () -> Unit,
    vm: RecuperarSenhaViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { if (vm.email.isBlank()) vm.email = emailInicial }
    LaunchedEffect(vm.concluido) { if (vm.concluido) onConcluido() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar senha") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.codigoEnviado) {
                Text("Informe o e-mail da sua conta. Vamos enviar um código de 6 números para criar uma nova senha.")
                OutlinedTextField(
                    value = vm.email,
                    onValueChange = { vm.email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = vm::pedirCodigo,
                    enabled = !vm.enviando,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (vm.enviando) "Enviando..." else "Enviar código") }
            } else {
                // A API responde igual para e-mail cadastrado ou não, para não revelar quem tem conta.
                Text(
                    "Se ${vm.email.trim()} estiver cadastrado, o código chegou no e-mail. " +
                        "Ele vale por 15 minutos e só pode ser usado uma vez."
                )
                OutlinedTextField(
                    value = vm.codigo,
                    onValueChange = { vm.codigo = it.filter(Char::isDigit).take(6) },
                    label = { Text("Código de 6 números") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                CampoSenha(valor = vm.novaSenha, onValorChange = { vm.novaSenha = it }, rotulo = "Nova senha")
                CampoSenha(valor = vm.confirmacao, onValorChange = { vm.confirmacao = it }, rotulo = "Confirme a nova senha")
                vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = vm::redefinir,
                    enabled = !vm.enviando,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (vm.enviando) "Salvando..." else "Salvar nova senha") }
                TextButton(onClick = vm::pedirCodigo, enabled = !vm.enviando, modifier = Modifier.fillMaxWidth()) {
                    Text("Não recebi: enviar outro código")
                }
                TextButton(onClick = vm::voltarParaEmail, modifier = Modifier.fillMaxWidth()) {
                    Text("Usar outro e-mail")
                }
            }
        }
    }
}
