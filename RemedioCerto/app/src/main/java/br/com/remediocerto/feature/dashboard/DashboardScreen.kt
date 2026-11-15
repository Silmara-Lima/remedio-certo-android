package br.com.remediocerto.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.DashboardDto
import br.com.remediocerto.core.network.UsuarioDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.MovimentacaoItem
import br.com.remediocerto.feature.alertas.gravidadeDo
import br.com.remediocerto.feature.alertas.ordenarAlertas
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val COR_ENTRADA = Color(0xFF15803D)   // verde da paleta do plano
private val COR_SAIDA = Color(0xFF1D4ED8)     // azul (informação neutra)

class DashboardViewModel : ViewModel() {
    var dados by mutableStateOf<DashboardDto?>(null); private set
    var grafico by mutableStateOf<List<DiaMovimento>>(emptyList()); private set
    var alertasCriticos by mutableStateOf<List<AlertaDto>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            dados = ApiClient.api.dashboard()
            val hoje = LocalDate.now()
            // Gráfico e alertas são complementos: se falharem, os indicadores continuam aparecendo.
            grafico = runCatching {
                resumoUltimosDias(ApiClient.api.listarMovimentacoes(limite = 500, dataInicio = hoje.minusDays(6).toString()), hoje)
            }.getOrDefault(resumoUltimosDias(emptyList(), hoje))
            alertasCriticos = runCatching {
                ordenarAlertas(ApiClient.api.listarAlertas(status = "ativo")).take(3)
            }.getOrDefault(emptyList())
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
    onVerAlertas: () -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.carregar() }
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
                // ---------- Indicadores (RF16) ----------
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Alertas ativos", d.alertasAtivos, Color(0xFFB91C1C), Modifier.weight(1f))
                        Kpi("Estoque crítico", d.estoqueCritico, Color(0xFFB91C1C), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Vencendo em 30 dias", d.lotesVencimentoProximo, Color(0xFFB45309), Modifier.weight(1f))
                        Kpi("Movimentações hoje", d.movimentacoesHoje, COR_SAIDA, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Medicamentos cadastrados", d.totalMedicamentos, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Kpi("Lotes em estoque", d.totalLotes, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    }
                }

                // ---------- Gráfico dos últimos 7 dias ----------
                if (vm.grafico.isNotEmpty()) {
                    item { GraficoMovimentacoes(vm.grafico) }
                }

                // ---------- Alertas mais críticos ----------
                if (vm.alertasCriticos.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Alertas mais críticos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = onVerAlertas) { Text("Ver alertas") }
                        }
                    }
                    items(vm.alertasCriticos, key = { "alerta-${it.id}" }) { a -> AlertaResumo(a, onVerAlertas) }
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

/** Barras lado a lado por dia: entradas (verde) e saídas/descartes (azul), com o número em cima. */
@Composable
private fun GraficoMovimentacoes(dias: List<DiaMovimento>) {
    val maximo = dias.maxOf { maxOf(it.entradas, it.saidas) }.coerceAtLeast(1)
    val alturaMaxima = 96.dp
    val formato = DateTimeFormatter.ofPattern("dd/MM")
    val descricao = descricaoGrafico(dias)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Movimentações dos últimos 7 dias", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = descricao },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                dias.forEach { dia ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Barra(dia.entradas, maximo, alturaMaxima, COR_ENTRADA)
                            Barra(dia.saidas, maximo, alturaMaxima, COR_SAIDA)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(dia.data.format(formato), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legenda("Entradas", COR_ENTRADA)
                Legenda("Saídas e descartes", COR_SAIDA)
            }
        }
    }
}

@Composable
private fun Barra(valor: Int, maximo: Int, alturaMaxima: androidx.compose.ui.unit.Dp, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor.toString(), style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier
                .width(12.dp)
                .height(alturaMaxima * (valor.toFloat() / maximo) + 2.dp)   // 2 dp: dia zerado ainda aparece
                .background(cor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
        )
    }
}

@Composable
private fun Legenda(texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(cor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AlertaResumo(alerta: AlertaDto, onClick: () -> Unit) {
    val gravidade = gravidadeDo(alerta)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(gravidade.cor))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(
                    alerta.medicamento?.nome ?: "Medicamento ${alerta.medicamentoId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(alerta.mensagem, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}
