package br.com.remediocerto.feature.cadastros.unidades

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.UnidadeRequest
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class UnidadeFormViewModel(private val id: Int?) : ViewModel() {
    val edicao = id != null

    var nome by mutableStateOf("")
    var cnpj by mutableStateOf("")
    var endereco by mutableStateOf("")
    var telefone by mutableStateOf("")
    var ativa by mutableStateOf(true); private set

    var carregando by mutableStateOf(id != null); private set
    var salvando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null)
    var concluido by mutableStateOf(false); private set

    init {
        if (id != null) viewModelScope.launch {
            try {
                val u = ApiClient.api.buscarUnidade(id)
                nome = u.nome
                cnpj = u.cnpj.orEmpty().filter(Char::isDigit)
                endereco = u.endereco.orEmpty()
                telefone = u.telefone.orEmpty()
                ativa = u.ativa
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            carregando = false
        }
    }

    fun salvar(reativar: Boolean = false) {
        erro = null
        if (nome.trim().length < 2) { erro = "Informe o nome da unidade."; return }
        val digitosCnpj = cnpj.filter(Char::isDigit)
        if (digitosCnpj.isNotEmpty() && digitosCnpj.length != 14) { erro = "O CNPJ deve ter 14 dígitos."; return }

        val req = UnidadeRequest(
            nome = nome.trim(),
            cnpj = if (digitosCnpj.isEmpty()) null else formatarCnpj(digitosCnpj),
            endereco = endereco.trim().ifBlank { null },
            telefone = telefone.trim().ifBlank { null },
            ativa = if (reativar) true else ativa,
        )
        executar { if (id == null) ApiClient.api.criarUnidade(req) else ApiClient.api.atualizarUnidade(id, req) }
    }

    fun desativar() {
        val id = id ?: return
        executar { ApiClient.api.desativarUnidade(id) }
    }

    private fun executar(acao: suspend () -> Unit) = viewModelScope.launch {
        salvando = true
        try {
            acao(); concluido = true
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        salvando = false
    }

    // 12345678000190 -> 12.345.678/0001-90 (mesmo formato dos dados de exemplo)
    private fun formatarCnpj(d: String) =
        "${d.substring(0, 2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.substring(12)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnidadeFormScreen(id: Int?, onConcluido: () -> Unit) {
    val vm: UnidadeFormViewModel = viewModel(factory = viewModelFactory { initializer { UnidadeFormViewModel(id) } })
    var confirmarDesativar by remember { mutableStateOf(false) }

    LaunchedEffect(vm.concluido) { if (vm.concluido) onConcluido() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.edicao) "Editar unidade" else "Nova unidade") },
                navigationIcon = {
                    IconButton(onClick = onConcluido) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (vm.carregando) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.ativa) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Esta unidade está inativa.", Modifier.weight(1f))
                        TextButton(onClick = { vm.salvar(reativar = true) }) { Text("Reativar") }
                    }
                }
            }
            Campo("Nome *", vm.nome) { vm.nome = it }
            // Só números, no máximo 14; o contador mostra quantos faltam
            OutlinedTextField(
                value = vm.cnpj,
                onValueChange = { vm.cnpj = it.filter(Char::isDigit).take(14) },
                label = { Text("CNPJ (somente números)") },
                singleLine = true,
                isError = vm.cnpj.isNotEmpty() && vm.cnpj.length != 14,
                supportingText = { if (vm.cnpj.isNotEmpty()) Text("${vm.cnpj.length}/14 dígitos") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Campo("Endereço", vm.endereco) { vm.endereco = it }
            Campo("Telefone", vm.telefone, KeyboardType.Phone) { vm.telefone = it }

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { vm.salvar() }, enabled = !vm.salvando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.salvando) "Salvando..." else "Salvar")
            }
            if (vm.edicao && vm.ativa) {
                OutlinedButton(
                    onClick = { confirmarDesativar = true },
                    enabled = !vm.salvando,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Desativar unidade") }
            }
        }
    }

    if (confirmarDesativar) {
        AlertDialog(
            onDismissRequest = { confirmarDesativar = false },
            title = { Text("Desativar unidade?") },
            text = { Text("A unidade deixará de aparecer nas listas. Estoque, usuários e histórico vinculados são mantidos, e é possível reativá-la depois.") },
            confirmButton = { TextButton(onClick = { confirmarDesativar = false; vm.desativar() }) { Text("Desativar") } },
            dismissButton = { TextButton(onClick = { confirmarDesativar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, teclado: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onChange, label = { Text(rotulo) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado), modifier = Modifier.fillMaxWidth(),
    )
}