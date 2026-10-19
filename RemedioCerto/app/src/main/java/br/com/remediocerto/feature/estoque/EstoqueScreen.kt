package br.com.remediocerto.feature.estoque

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.ui.StatusBadge
import br.com.remediocerto.core.ui.StatusEstoque
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstoqueScreen(vm: EstoqueViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.carregar(puxouParaAtualizar = vm.estado is EstoqueUiState.Sucesso) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.busca,
            onValueChange = { vm.busca = it },
            placeholder = { Text("Buscar medicamento") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = vm.filtro == null, onClick = { vm.filtro = null }, label = { Text("Todos") })
            listOf(StatusEstoque.BAIXO, StatusEstoque.VENCENDO, StatusEstoque.VENCIDO).forEach { s ->
                FilterChip(
                    selected = vm.filtro == s,
                    onClick = { vm.filtro = if (vm.filtro == s) null else s },
                    label = { Text(s.rotulo) },
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = vm.atualizando,
            onRefresh = { vm.carregar(puxouParaAtualizar = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val estado = vm.estado) {
                EstoqueUiState.Carregando ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                is EstoqueUiState.Erro ->
                    MensagemCentral(estado.mensagem, botao = "Tentar novamente") { vm.carregar() }

                is EstoqueUiState.Sucesso -> {
                    val itens = vm.filtrar(estado.itens)
                    if (itens.isEmpty()) {
                        MensagemCentral("Nenhum medicamento encontrado.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(itens, key = { it.chave }) { ItemEstoqueCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemEstoqueCard(item: ItemEstoque) {
    var expandido by remember { mutableStateOf(false) }
    Card(onClick = { expandido = !expandido }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(item.status.cor))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.nome, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusBadge(item.status)
                }
                item.unidade?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saldo: ${item.total} un. · mínimo ${item.minimo} · ${item.lotes.size} lote(s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (expandido) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    item.lotes.forEach { lote ->
                        val situacao = StatusEstoque.de(lote.status)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Lote ${lote.lote}", Modifier.weight(1f))
                            Text("${lote.quantidade} un.", Modifier.padding(horizontal = 12.dp))
                            Text("Val. ${formatarData(lote.validade)}", color = situacao.cor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MensagemCentral(texto: String, botao: String? = null, onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(texto)
        if (botao != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) { Text(botao) }
        }
    }
}

private fun formatarData(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(iso)