package br.com.remediocerto.feature.cadastros.usuarios

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
import br.com.remediocerto.core.network.UsuarioAdminDto
import br.com.remediocerto.core.network.mensagemDeErro
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

fun rotuloPerfil(perfil: String) = if (perfil == "admin") "Administrador" else "Funcionário"

class UsuariosViewModel : ViewModel() {
    var itens by mutableStateOf<List<UsuarioAdminDto>>(emptyList()); private set
    var nomesUnidades by mutableStateOf<Map<Int, String>>(emptyMap()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var busca by mutableStateOf("")
    var mostrarInativos by mutableStateOf(false); private set

    fun alternarInativos() { mostrarInativos = !mostrarInativos; carregar() }

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            itens = ApiClient.api.listarUsuarios(somenteAtivos = !mostrarInativos)
            nomesUnidades = ApiClient.api.listarUnidades(somenteAtivas = false).associate { it.id to it.nome }
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }

    fun filtrados(): List<UsuarioAdminDto> {
        val termo = busca.trim()
        if (termo.isBlank()) return itens
        return itens.filter { it.nome.contains(termo, ignoreCase = true) || it.email.contains(termo, ignoreCase = true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuariosScreen(
    onVoltar: () -> Unit,
    onNovo: () -> Unit,
    onEditar: (Int) -> Unit,
    vm: UsuariosViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuários") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNovo) { Icon(Icons.Filled.Add, "Novo usuário") }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = vm.busca,
                onValueChange = { vm.busca = it },
                placeholder = { Text("Buscar por nome ou e-mail") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = vm.mostrarInativos, onClick = vm::alternarInativos, label = { Text("Mostrar inativos") })
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
                    val lista = vm.filtrados()
                    if (lista.isEmpty()) item { Text("Nenhum usuário encontrado.") }
                    items(lista, key = { it.id }) { u ->
                        Card(Modifier.fillMaxWidth().clickable { onEditar(u.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.nome, style = MaterialTheme.typography.titleMedium)
                                    Text(u.email, style = MaterialTheme.typography.bodySmall)
                                    val unidade = u.unidadeId?.let { vm.nomesUnidades[it] } ?: "Todas as unidades"
                                    Text(
                                        "${rotuloPerfil(u.perfil)} · $unidade",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!u.ativo) AssistChip(onClick = {}, label = { Text("Inativo") })
                            }
                        }
                    }
                }
            }
        }
    }
}