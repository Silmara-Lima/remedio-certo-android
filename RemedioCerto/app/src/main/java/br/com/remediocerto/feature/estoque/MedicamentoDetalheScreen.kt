package br.com.remediocerto.feature.estoque

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.remediocerto.core.database.DatabaseProvider
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.offline.paraDto
import br.com.remediocerto.core.ui.MovimentacaoItem
import br.com.remediocerto.core.ui.StatusBadge
import br.com.remediocerto.core.ui.StatusEstoque
import br.com.remediocerto.core.ui.dataBr
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

// ---------- Regras (funções puras, testadas sem a API) ----------

/** Lotes do medicamento na unidade, do que vence primeiro para o último (FEFO). Lotes zerados ficam de fora. */
fun lotesDoDetalhe(lotes: List<EstoqueDto>, medicamentoId: Int, unidadeId: Int): List<EstoqueDto> =
    lotes.filter { it.medicamentoId == medicamentoId && it.unidadeId == unidadeId && it.quantidade > 0 }
        .sortedBy { it.validade }

/** Situação geral do medicamento na unidade, com a mesma regra da lista do Estoque. */
fun situacaoDoMedicamento(lotes: List<EstoqueDto>, minimo: Int): StatusEstoque =
    agruparEstoque(lotes).firstOrNull()?.status
        ?: if (minimo > 0) StatusEstoque.BAIXO else StatusEstoque.NORMAL

// ---------- Estado da tela ----------

class MedicamentoDetalheViewModel(
    app: Application,
    private val medicamentoId: Int,
    private val unidadeId: Int,
) : AndroidViewModel(app) {
    private val estoqueLocal = DatabaseProvider.getDatabase(app).estoqueLocalDao()

    var medicamento by mutableStateOf<MedicamentoDto?>(null); private set
    var lotes by mutableStateOf<List<EstoqueDto>>(emptyList()); private set
    var movimentacoes by mutableStateOf<List<HistoricoDto>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var semConexao by mutableStateOf(false); private set

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            medicamento = ApiClient.api.buscarMedicamento(medicamentoId)
            lotes = lotesDoDetalhe(ApiClient.api.listarEstoque(medicamentoId), medicamentoId, unidadeId)
            movimentacoes = runCatching {
                ApiClient.api.listarMovimentacoes(limite = 10, medicamentoId = medicamentoId, unidadeId = unidadeId)
            }.getOrDefault(emptyList())
            semConexao = false
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            // Sem internet: mostra os lotes salvos no aparelho (modo offline).
            val salvos = estoqueLocal.listarPorMedicamento(medicamentoId).map { it.paraDto(LocalDate.now()) }
            lotes = lotesDoDetalhe(salvos, medicamentoId, unidadeId)
            if (medicamento == null) medicamento = salvos.firstOrNull()?.medicamento
            semConexao = true
            if (salvos.isEmpty()) erro = "Sem conexão e sem dados salvos deste medicamento."
        }
        carregando = false
    }
}

// ---------- Tela (T07) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicamentoDetalheScreen(
    medicamentoId: Int,
    unidadeId: Int,
    isAdmin: Boolean,
    onVoltar: () -> Unit,
    onEditar: (Int) -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: MedicamentoDetalheViewModel = viewModel(factory = viewModelFactory {
        initializer { MedicamentoDetalheViewModel(app, medicamentoId, unidadeId) }
    })
    LaunchedEffect(Unit) { vm.carregar() }
    val m = vm.medicamento
    val minimo = m?.quantidadeMinima ?: 0
    val total = vm.lotes.sumOf { it.quantidade }
    val situacao = situacaoDoMedicamento(vm.lotes, minimo)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(m?.nome ?: "Medicamento") },
            navigationIcon = {
                IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            },
            actions = {
                if (isAdmin && m != null) {
                    IconButton(onClick = { onEditar(m.id) }) { Icon(Icons.Filled.Edit, "Editar cadastro") }
                }
            },
            windowInsets = WindowInsets(0),
        )

        PullToRefreshBox(isRefreshing = vm.carregando && m != null, onRefresh = { vm.carregar() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (vm.semConexao) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                            Text(
                                "Sem conexão: mostrando os lotes salvos no aparelho.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                vm.erro?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (m == null && vm.erro == null) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }

                if (m != null) {
                    // ---------- Resumo ----------
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(Modifier.width(6.dp).fillMaxHeight().background(situacao.cor))
                                Column(Modifier.weight(1f).padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$total un.",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusBadge(situacao)
                                    }
                                    Text(
                                        "Saldo nesta unidade · mínimo $minimo · ${vm.lotes.size} lote(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    vm.lotes.firstOrNull()?.unidade?.nome?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // ---------- Dados do cadastro ----------
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Cadastro", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Dado("Princípio ativo", m.principioAtivo)
                                Dado("Dosagem", m.dosagem)
                                Dado("Forma farmacêutica", m.formaFarmaceutica)
                                Dado("Fabricante", m.fabricante)
                                Dado("Código de barras", m.codigoBarras)
                                Dado("Registro Anvisa", m.registroAnvisa)
                            }
                        }
                    }

                    // ---------- Lotes por validade (FEFO) ----------
                    item {
                        Text("Lotes por validade", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "O primeiro da lista é o que deve sair primeiro (FEFO).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (vm.lotes.isEmpty()) item { Text("Nenhum lote com saldo nesta unidade.") }
                    items(vm.lotes, key = { "lote-${it.id}" }) { lote -> LoteCard(lote) }

                    // ---------- Últimas movimentações ----------
                    if (!vm.semConexao) {
                        item { Text("Últimas movimentações", style = MaterialTheme.typography.titleMedium) }
                        if (vm.movimentacoes.isEmpty()) item { Text("Nenhuma movimentação registrada.") }
                        items(vm.movimentacoes, key = { "mov-${it.id}" }) { h ->
                            MovimentacaoItem(
                                tipo = h.tipo,
                                titulo = "Lote ${h.lote}",
                                detalhe = listOfNotNull(h.usuario?.nome, h.observacao?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                quantidade = h.quantidade,
                                dataIso = h.criadaEm,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dado(rotulo: String, valor: String?) {
    Row {
        Text(rotulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor?.takeIf { it.isNotBlank() } ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoteCard(lote: EstoqueDto) {
    val situacao = StatusEstoque.de(lote.status)
    val prazo = when {
        lote.diasParaVencer < 0 -> "Vencido há ${-lote.diasParaVencer} dia(s)"
        lote.diasParaVencer == 0 -> "Vence hoje"
        else -> "Vence em ${lote.diasParaVencer} dia(s)"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lote ${lote.lote}", style = MaterialTheme.typography.titleSmall)
                Text("Validade ${dataBr(lote.validade)} · $prazo", style = MaterialTheme.typography.bodySmall, color = situacao.cor)
            }
            Text("${lote.quantidade} un.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
