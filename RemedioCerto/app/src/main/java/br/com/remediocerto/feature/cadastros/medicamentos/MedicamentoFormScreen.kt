package br.com.remediocerto.feature.cadastros.medicamentos

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.MedicamentoRequest
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.feature.scanner.iniciarScanner
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class MedicamentoFormViewModel(private val id: Int?) : ViewModel() {
    val edicao = id != null

    var nome by mutableStateOf("")
    var principioAtivo by mutableStateOf("")
    var fabricante by mutableStateOf("")
    var formaFarmaceutica by mutableStateOf("")
    var dosagem by mutableStateOf("")
    var codigoBarras by mutableStateOf("")
    var registroAnvisa by mutableStateOf("")
    var quantidadeMinima by mutableStateOf("10")
    var ativo by mutableStateOf(true); private set

    var carregando by mutableStateOf(id != null); private set
    var salvando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null)
    var concluido by mutableStateOf(false); private set

    init {
        if (id != null) viewModelScope.launch {
            try {
                preencher(ApiClient.api.buscarMedicamento(id))
            } catch (e: HttpException) {
                erro = mensagemDeErro(e)
            } catch (e: IOException) {
                erro = "Não foi possível conectar à API."
            }
            carregando = false
        }
    }

    private fun preencher(m: MedicamentoDto) {
        nome = m.nome
        principioAtivo = m.principioAtivo.orEmpty()
        fabricante = m.fabricante.orEmpty()
        formaFarmaceutica = m.formaFarmaceutica.orEmpty()
        dosagem = m.dosagem.orEmpty()
        codigoBarras = m.codigoBarras.orEmpty()
        registroAnvisa = m.registroAnvisa.orEmpty()
        quantidadeMinima = m.quantidadeMinima.toString()
        ativo = m.ativo
    }

    fun salvar(reativar: Boolean = false) {
        erro = null
        val minimo = quantidadeMinima.trim().toIntOrNull()
        if (nome.trim().length < 2) { erro = "Informe o nome do medicamento."; return }
        if (minimo == null || minimo < 0) { erro = "Informe o estoque mínimo (número inteiro)."; return }

        val req = MedicamentoRequest(
            nome = nome.trim(),
            principioAtivo = principioAtivo.vazioParaNulo(),
            fabricante = fabricante.vazioParaNulo(),
            formaFarmaceutica = formaFarmaceutica.vazioParaNulo(),
            dosagem = dosagem.vazioParaNulo(),
            codigoBarras = codigoBarras.vazioParaNulo(),     // vazio vira nulo: o código é único no banco
            registroAnvisa = registroAnvisa.vazioParaNulo(),
            quantidadeMinima = minimo,
            ativo = if (reativar) true else ativo,
        )
        executar {
            if (id == null) ApiClient.api.criarMedicamento(req) else ApiClient.api.atualizarMedicamento(id, req)
        }
    }

    fun desativar() {
        val id = id ?: return
        executar { ApiClient.api.desativarMedicamento(id) }
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
}

private fun String.vazioParaNulo(): String? = trim().ifBlank { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicamentoFormScreen(id: Int?, onConcluido: () -> Unit) {
    val vm: MedicamentoFormViewModel = viewModel(factory = viewModelFactory {
        initializer { MedicamentoFormViewModel(id) }
    })
    val context = LocalContext.current
    var confirmarDesativar by remember { mutableStateOf(false) }

    LaunchedEffect(vm.concluido) { if (vm.concluido) onConcluido() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.edicao) "Editar medicamento" else "Novo medicamento") },
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
            if (!vm.ativo) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Este medicamento está inativo.", Modifier.weight(1f))
                        TextButton(onClick = { vm.salvar(reativar = true) }) { Text("Reativar") }
                    }
                }
            }
            Campo("Nome *", vm.nome) { vm.nome = it }
            Campo("Princípio ativo", vm.principioAtivo) { vm.principioAtivo = it }
            Campo("Dosagem (ex.: 500 mg)", vm.dosagem) { vm.dosagem = it }
            Campo("Forma farmacêutica (ex.: Comprimido)", vm.formaFarmaceutica) { vm.formaFarmaceutica = it }
            Campo("Fabricante", vm.fabricante) { vm.fabricante = it }
            Campo("Registro Anvisa", vm.registroAnvisa) { vm.registroAnvisa = it }
            Campo("Código de barras", vm.codigoBarras, KeyboardType.Number) { vm.codigoBarras = it }
            OutlinedButton(
                onClick = { iniciarScanner(context, onCodigo = { vm.codigoBarras = it }, onErro = { vm.erro = it }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ler código com a câmera") }
            Campo("Estoque mínimo *", vm.quantidadeMinima, KeyboardType.Number) { vm.quantidadeMinima = it.filter(Char::isDigit) }

            vm.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.salvar() },
                enabled = !vm.salvando,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.salvando) "Salvando..." else "Salvar") }

            if (vm.edicao && vm.ativo) {
                OutlinedButton(
                    onClick = { confirmarDesativar = true },
                    enabled = !vm.salvando,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Desativar medicamento") }
            }
        }
    }

    if (confirmarDesativar) {
        AlertDialog(
            onDismissRequest = { confirmarDesativar = false },
            title = { Text("Desativar medicamento?") },
            text = { Text("Ele deixará de aparecer nas buscas e movimentações. O histórico será mantido e é possível reativá-lo depois.") },
            confirmButton = {
                TextButton(onClick = { confirmarDesativar = false; vm.desativar() }) { Text("Desativar") }
            },
            dismissButton = { TextButton(onClick = { confirmarDesativar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, teclado: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth(),
    )
}