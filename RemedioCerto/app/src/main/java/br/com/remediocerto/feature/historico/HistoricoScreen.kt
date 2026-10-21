package br.com.remediocerto.feature.historico

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.MovimentacaoItem
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class HistoricoViewModel : ViewModel() {
    var itens by mutableStateOf<List<HistoricoDto>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var filtroTipo by mutableStateOf<String?>(null); private set

    fun selecionarTipo(tipo: String?) { filtroTipo = tipo; carregar() }

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            itens = ApiClient.api.listarMovimentacoes(tipo = filtroTipo)
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }
}

private val filtros = listOf(null to "Todos", "entrada" to "Entrada", "saida" to "Saída", "descarte" to "Descarte", "ajuste" to "Ajuste")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(onVoltar: () -> Unit, vm: HistoricoViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.carregar() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Histórico de movimentações") },
            navigationIcon = {
                IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            },
            windowInsets = WindowInsets(0),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filtros.forEach { (valor, rotulo) ->
                FilterChip(selected = vm.filtroTipo == valor, onClick = { vm.selecionarTipo(valor) }, label = { Text(rotulo) })
            }
        }
        PullToRefreshBox(isRefreshing = vm.carregando, onRefresh = { vm.carregar() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                vm.erro?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (!vm.carregando && vm.erro == null && vm.itens.isEmpty()) {
                    item { Text("Nenhuma movimentação encontrada.") }
                }
                items(vm.itens, key = { it.id }) { h ->
                    MovimentacaoItem(
                        tipo = h.tipo,
                        titulo = h.medicamento?.nome ?: "Medicamento",
                        detalhe = listOfNotNull("Lote ${h.lote}", h.unidade?.nome, h.usuario?.nome).joinToString(" · "),
                        quantidade = h.quantidade,
                        dataIso = h.criadaEm,
                    )
                }
            }
        }
    }
}