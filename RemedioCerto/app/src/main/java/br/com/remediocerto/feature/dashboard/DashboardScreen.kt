package br.com.remediocerto.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.DashboardDto
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.MovimentacaoItem
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class DashboardViewModel : ViewModel() {
    var dados by mutableStateOf<DashboardDto?>(null); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            dados = ApiClient.api.dashboard()
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    usuario: UsuarioDto,
    onVerHistorico: () -> Unit,
    onNovaMovimentacao: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.carregar() }          // recarrega sempre que a aba é aberta
    val d = vm.dados

    PullToRefreshBox(isRefreshing = vm.carregando && d != null, onRefresh = { vm.carregar() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text("Olá, ${usuario.nome.substringBefore(" ")}!", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (usuario.tipo == "ADMIN") "Visão geral de todas as unidades" else "Resumo da sua unidade",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.erro?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (d == null && vm.erro == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            if (d != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Alertas ativos", d.alertasAtivos, Color(0xFFB91C1C), Modifier.weight(1f))
                        Kpi("Estoque crítico", d.estoqueCritico, Color(0xFFB91C1C), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Vencendo em 30 dias", d.lotesVencimentoProximo, Color(0xFFB45309), Modifier.weight(1f))
                        Kpi("Movimentações hoje", d.movimentacoesHoje, Color(0xFF1D4ED8), Modifier.weight(1f))
                    }
                }
                item {
                    Button(onClick = onNovaMovimentacao, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Registrar movimentação")
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Movimentações recentes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onVerHistorico) { Text("Ver histórico") }
                    }
                }
                if (d.movimentacoesRecentes.isEmpty()) {
                    item { Text("Nenhuma movimentação registrada ainda.") }
                }
                items(d.movimentacoesRecentes.take(5), key = { it.id }) { m ->
                    MovimentacaoItem(
                        tipo = m.tipo,
                        titulo = m.medicamento ?: "Medicamento",
                        detalhe = listOfNotNull(m.lote?.let { "Lote $it" }, m.responsavel).joinToString(" · "),
                        quantidade = m.quantidade,
                        dataIso = m.data,
                    )
                }
            }
        }
    }
}

@Composable
private fun Kpi(titulo: String, valor: Int, cor: Color, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(valor.toString(), style = MaterialTheme.typography.headlineMedium, color = cor, fontWeight = FontWeight.Bold)
            Text(titulo, style = MaterialTheme.typography.bodySmall)
        }
    }
}