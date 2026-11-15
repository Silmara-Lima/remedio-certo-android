package br.com.remediocerto.feature.perfil

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.TrocarSenhaRequest
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.CampoSenha
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class TrocarSenhaViewModel : ViewModel() {
    var atual by mutableStateOf("")
    var nova by mutableStateOf("")
    var confirmacao by mutableStateOf("")
    var salvando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var sucesso by mutableStateOf(false); private set

    fun salvar() {
        erro = null
        when {
            atual.isEmpty() -> { erro = "Informe a senha atual."; return }
            nova.length < 6 -> { erro = "A nova senha deve ter pelo menos 6 caracteres."; return }
            nova == atual -> { erro = "A nova senha deve ser diferente da atual."; return }
            nova != confirmacao -> { erro = "A confirmação não confere com a nova senha."; return }
        }
        viewModelScope.launch {
            salvando = true
            try {
                ApiClient.api.trocarSenha(TrocarSenhaRequest(atual, nova))
                sucesso = true
                atual = ""; nova = ""; confirmacao = ""
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)          // ex.: "Senha atual incorreta."
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            salvando = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrocarSenhaScreen(onVoltar: () -> Unit, vm: TrocarSenhaViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trocar senha") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CampoSenha(valor = vm.atual, onValorChange = { vm.atual = it }, rotulo = "Senha atual")
            CampoSenha(valor = vm.nova, onValorChange = { vm.nova = it }, rotulo = "Nova senha (mínimo 6 caracteres)")
            CampoSenha(valor = vm.confirmacao, onValorChange = { vm.confirmacao = it }, rotulo = "Confirmar nova senha")

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (vm.sucesso) Text("Senha alterada com sucesso.", color = Color(0xFF15803D))

            Button(onClick = vm::salvar, enabled = !vm.salvando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.salvando) "Salvando..." else "Salvar nova senha")
            }
        }
    }
}