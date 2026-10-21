package br.com.remediocerto.feature.cadastros.unidades

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.UnidadeDto
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class UnidadesViewModel : ViewModel() {
    var itens by mutableStateOf<List<UnidadeDto>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var busca by mutableStateOf("")
    var mostrarInativas by mutableStateOf(false); private set

    fun alternarInativas() { mostrarInativas = !mostrarInativas; carregar() }

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            itens = ApiClient.api.listarUnidades(somenteAtivas = !mostrarInativas)
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }

    fun filtradas(): List<UnidadeDto> {
        val termo = busca.trim()
        if (termo.isBlank()) return itens
        return itens.filter {
            it.nome.contains(termo, ignoreCase = true) ||
                    it.cnpj?.contains(termo) == true ||
                    it.endereco?.contains(termo, ignoreCase = true) == true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnidadesScreen(
    onVoltar: () -> Unit,
    onNova: () -> Unit,
    onEditar: (Int) -> Unit,
    vm: UnidadesViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unidades de saúde") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNova) { Icon(Icons.Filled.Add, "Nova unidade") }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = vm.busca,
                onValueChange = { vm.busca = it },
                placeholder = { Text("Buscar por nome, CNPJ ou endereço") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = vm.mostrarInativas, onClick = vm::alternarInativas, label = { Text("Mostrar inativas") })
            }
            when {
                vm.carregando && vm.itens.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.erro != null ->
                    Text(vm.erro!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val lista = vm.filtradas()
                    if (lista.isEmpty()) item { Text("Nenhuma unidade encontrada.") }
                    items(lista, key = { it.id }) { u ->
                        Card(Modifier.fillMaxWidth().clickable { onEditar(u.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.nome, style = MaterialTheme.typography.titleMedium)
                                    u.endereco?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    val contato = listOfNotNull(u.cnpj?.let { "CNPJ $it" }, u.telefone).joinToString(" · ")
                                    if (contato.isNotBlank()) {
                                        Text(contato, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (!u.ativa) AssistChip(onClick = {}, label = { Text("Inativa") })
                            }
                        }
                    }
                }
            }
        }
    }
}