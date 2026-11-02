package br.com.remediocerto.feature.alertas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.AlertaDto
import br.com.remediocerto.core.ui.StatusBadge
import br.com.remediocerto.core.ui.dataBr
import br.com.remediocerto.core.ui.dataHoraBr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    onRegistrarMovimentacao: () -> Unit,
    vm: AlertasViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.carregar(puxouParaAtualizar = vm.estado is AlertasUiState.Sucesso) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.aviso) {
        vm.aviso?.let { snackbar.showSnackbar(it); vm.aviso = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val todos = (vm.estado as? AlertasUiState.Sucesso)?.alertas.orEmpty()

            TabRow(selectedTabIndex = vm.aba.ordinal) {
                AbaAlerta.entries.forEach { aba ->
                    val qtd = todos.count { it.status == aba.api }
                    Tab(
                        selected = vm.aba == aba,
                        onClick = { vm.aba = aba },
                        text = { Text(if (qtd > 0) "${aba.titulo} ($qtd)" else aba.titulo) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = vm.atualizando,
                onRefresh = { vm.carregar(puxouParaAtualizar = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val estado = vm.estado) {
                    AlertasUiState.Carregando ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    is AlertasUiState.Erro ->
                        MensagemCentral(estado.mensagem, botao = "Tentar novamente") { vm.carregar() }

                    is AlertasUiState.Sucesso -> {
                        val lista = alertasDaAba(estado.alertas, vm.aba, estado.diasCritico)
                        if (lista.isEmpty()) {
                            MensagemCentral(
                                when (vm.aba) {
                                    AbaAlerta.ATIVOS -> "Nenhum alerta ativo. Tudo em dia!"
                                    AbaAlerta.RESOLVIDOS -> "Nenhum alerta resolvido ainda."
                                    AbaAlerta.IGNORADOS -> "Nenhum alerta ignorado."
                                }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(lista, key = { it.id }) { alerta ->
                                    AlertaCard(
                                        alerta = alerta,
                                        diasCritico = diasCriticoDo(alerta, estado.diasCritico),
                                        habilitado = !vm.enviando,
                                        onResolver = { vm.acaoPendente = AcaoPendente(alerta, resolver = true) },
                                        onIgnorar = { vm.acaoPendente = AcaoPendente(alerta, resolver = false) },
                                        onRegistrarMovimentacao = onRegistrarMovimentacao,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    vm.acaoPendente?.let { pendente ->
        AlertDialog(
            onDismissRequest = { vm.acaoPendente = null },
            title = { Text(if (pendente.resolver) "Marcar como resolvido?" else "Ignorar alerta?") },
            text = {
                Text(
                    if (pendente.resolver) "Confirme que a situação já foi tratada. O alerta vai para a aba Resolvidos " +
                            "e só volta se a situação mudar de novo, por exemplo com uma nova saída."
                    else "O alerta sai da lista de ativos e vai para a aba Ignorados."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmarAcao() }) {
                    Text(if (pendente.resolver) "Marcar como resolvido" else "Ignorar")
                }
            },
            dismissButton = { TextButton(onClick = { vm.acaoPendente = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AlertaCard(
    alerta: AlertaDto,
    diasCritico: Int,
    habilitado: Boolean,
    onResolver: () -> Unit,
    onIgnorar: () -> Unit,
    onRegistrarMovimentacao: () -> Unit,
) {
    val gravidade = gravidadeDo(alerta)
    val critico = ehCritico(alerta, diasCritico)
    val ativo = alerta.status == AbaAlerta.ATIVOS.api

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Faixa lateral com a cor da gravidade (o texto do selo repete a informação: nunca só cor).
            Box(Modifier.width(6.dp).fillMaxHeight().background(gravidade.cor))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alerta.medicamento?.nome ?: "Medicamento ${alerta.medicamentoId}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(gravidade)
                }
                if (critico && ativo) {
                    Text(
                        "CRÍTICO",
                        color = gravidade.cor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                alerta.unidade?.let {
                    Text(it.nome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(6.dp))
                Text(alerta.mensagem, style = MaterialTheme.typography.bodyMedium)

                alerta.estoqueLote?.let { lote ->
                    val prazo = textoPrazo(alerta)
                    Text(
                        buildString {
                            append("Lote ${lote.lote} · ${lote.quantidade} un.")
                            if (lote.validade.isNotBlank()) append(" · Val. ${dataBr(lote.validade)}")
                            if (prazo != null) append(" · $prazo")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (ativo) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(
                            "Ação recomendada: ${acaoRecomendada(alerta)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onResolver, enabled = habilitado) { Text("Marcar como resolvido") }
                        OutlinedButton(onClick = onIgnorar, enabled = habilitado) { Text("Ignorar") }
                    }
                    TextButton(onClick = onRegistrarMovimentacao, enabled = habilitado) {
                        Text("Registrar movimentação")
                    }
                } else {
                    alerta.resolvidoEm?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${if (alerta.status == AbaAlerta.RESOLVIDOS.api) "Resolvido" else "Ignorado"} em ${dataHoraBr(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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