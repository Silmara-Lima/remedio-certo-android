package br.com.remediocerto.feature.historico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.core.ui.MovimentacaoItem
import br.com.remediocerto.core.ui.dataBr
import br.com.remediocerto.core.ui.dataHoraBr
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

class HistoricoViewModel : ViewModel() {
    var itens by mutableStateOf<List<HistoricoDto>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set

    // Vão para a API (recarregam a lista)
    var filtroTipo by mutableStateOf<String?>(null); private set
    var periodo by mutableStateOf(Periodo.TRINTA); private set

    // Filtrados no aparelho
    var buscaMedicamento by mutableStateOf("")
    var unidadeId by mutableStateOf<Int?>(null)
    var usuarioId by mutableStateOf<Int?>(null)

    var detalhe by mutableStateOf<HistoricoDto?>(null)

    fun selecionarTipo(tipo: String?) { filtroTipo = tipo; carregar() }
    fun selecionarPeriodo(p: Periodo) { periodo = p; carregar() }

    fun limparFiltros() {
        buscaMedicamento = ""; unidadeId = null; usuarioId = null
        filtroTipo = null; periodo = Periodo.TRINTA
        carregar()
    }

    fun visiveis(): List<HistoricoDto> = filtrarHistorico(itens, buscaMedicamento, unidadeId, usuarioId)

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            itens = ApiClient.api.listarMovimentacoes(
                tipo = filtroTipo,
                limite = 500,
                dataInicio = dataInicioDo(periodo, LocalDate.now()),
            )
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API."
        }
        carregando = false
    }
}

private val filtrosTipo = listOf(null to "Todos", "entrada" to "Entrada", "saida" to "Saída", "descarte" to "Descarte", "ajuste" to "Ajuste")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(onVoltar: () -> Unit, vm: HistoricoViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.carregar() }
    val unidades = unidadesDe(vm.itens)
    val usuarios = usuariosDe(vm.itens)
    val lista = vm.visiveis()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Histórico de movimentações") },
            navigationIcon = {
                IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            },
            windowInsets = WindowInsets(0),
        )

        // ---------- Filtros (RF11) ----------
        OutlinedTextField(
            value = vm.buscaMedicamento,
            onValueChange = { vm.buscaMedicamento = it },
            placeholder = { Text("Buscar medicamento") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LinhaDeChips {
            Periodo.entries.forEach { p ->
                FilterChip(selected = vm.periodo == p, onClick = { vm.selecionarPeriodo(p) }, label = { Text(p.rotulo) })
            }
        }
        LinhaDeChips {
            filtrosTipo.forEach { (valor, rotulo) ->
                FilterChip(selected = vm.filtroTipo == valor, onClick = { vm.selecionarTipo(valor) }, label = { Text(rotulo) })
            }
        }
        // Unidade e usuário só aparecem quando há mais de uma opção na lista.
        if (unidades.size > 1) {
            LinhaDeChips {
                FilterChip(selected = vm.unidadeId == null, onClick = { vm.unidadeId = null }, label = { Text("Todas as unidades") })
                unidades.forEach { u ->
                    FilterChip(selected = vm.unidadeId == u.id, onClick = { vm.unidadeId = u.id }, label = { Text(u.nome) })
                }
            }
        }
        if (usuarios.size > 1) {
            LinhaDeChips {
                FilterChip(selected = vm.usuarioId == null, onClick = { vm.usuarioId = null }, label = { Text("Todos os usuários") })
                usuarios.forEach { u ->
                    FilterChip(selected = vm.usuarioId == u.id, onClick = { vm.usuarioId = u.id }, label = { Text(u.nome) })
                }
            }
        }

        PullToRefreshBox(isRefreshing = vm.carregando, onRefresh = { vm.carregar() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                vm.erro?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (!vm.carregando && vm.erro == null) {
                    item {
                        Text(
                            "${lista.size} movimentação(ões) encontrada(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!vm.carregando && vm.erro == null && lista.isEmpty()) {
                    item {
                        Column {
                            Text("Nenhuma movimentação encontrada com esses filtros.")
                            TextButton(onClick = vm::limparFiltros) { Text("Limpar filtros") }
                        }
                    }
                }
                items(lista, key = { it.id }) { h ->
                    Box(Modifier.clickable { vm.detalhe = h }) {
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

    vm.detalhe?.let { h -> DetalheMovimentacao(h, onFechar = { vm.detalhe = null }) }
}

@Composable
private fun LinhaDeChips(conteudo: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = conteudo,
    )
}

/** Detalhe da movimentação. Só leitura: movimentações não são editadas nem excluídas (RN06). */
@Composable
private fun DetalheMovimentacao(h: HistoricoDto, onFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("${rotuloTipo(h.tipo)} · ${h.quantidade} un.") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Campo("Medicamento", h.medicamento?.nome ?: "—")
                Campo("Lote", h.lote)
                Campo("Validade", dataBr(h.validade))
                Campo("Unidade", h.unidade?.nome ?: "—")
                Campo("Registrado por", h.usuario?.nome ?: "—")
                Campo("Data e hora", dataHoraBr(h.criadaEm))
                h.numeroDocumento?.takeIf { it.isNotBlank() }?.let { Campo("Documento", it) }
                h.observacao?.takeIf { it.isNotBlank() }?.let {
                    Campo(if (h.tipo.equals("ajuste", true)) "Justificativa" else "Observação", it)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Movimentações não podem ser editadas. Para corrigir um saldo, registre um ajuste com justificativa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
    )
}

@Composable
private fun Campo(rotulo: String, valor: String) {
    Column {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
