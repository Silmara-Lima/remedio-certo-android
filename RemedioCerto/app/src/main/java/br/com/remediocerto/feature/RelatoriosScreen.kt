package br.com.remediocerto.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.remediocerto.core.network.ApiClient
import br.com.remediocerto.core.network.mensagemDeErro
import br.com.remediocerto.feature.historico.Periodo
import br.com.remediocerto.feature.historico.dataInicioDo
import br.com.remediocerto.util.CsvExporter
import br.com.remediocerto.util.PdfExporter
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RelatoriosViewModel : ViewModel() {
    var tipo by mutableStateOf(TipoRelatorio.MOVIMENTACOES); private set
    var tipoMovimentacao by mutableStateOf<String?>(null); private set
    var periodo by mutableStateOf(Periodo.TRINTA); private set
    var diasValidade by mutableStateOf(30); private set

    var relatorio by mutableStateOf<Relatorio?>(null); private set
    var carregando by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set

    fun selecionarTipo(t: TipoRelatorio) { tipo = t; carregar() }
    fun selecionarTipoMovimentacao(t: String?) { tipoMovimentacao = t; carregar() }
    fun selecionarPeriodo(p: Periodo) { periodo = p; carregar() }
    fun selecionarDiasValidade(d: Int) { diasValidade = d; carregar() }

    /** Título que vai no PDF, com o filtro e a data de geração. */
    fun tituloDoArquivo(): String {
        val filtro = when (tipo) {
            TipoRelatorio.MOVIMENTACOES -> "${tipoMovimentacao?.let { br.com.remediocerto.feature.historico.rotuloTipo(it) } ?: "Todos os tipos"} · ${periodo.rotulo}"
            TipoRelatorio.VALIDADE -> "Vencidos e a vencer em $diasValidade dias"
            TipoRelatorio.ESTOQUE -> "Saldo por medicamento e unidade"
        }
        val agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        return "${tipo.titulo} · $filtro · $agora"
    }

    fun carregar() = viewModelScope.launch {
        carregando = true; erro = null
        try {
            relatorio = when (tipo) {
                TipoRelatorio.MOVIMENTACOES -> relatorioMovimentacoes(
                    ApiClient.api.listarMovimentacoes(
                        tipo = tipoMovimentacao,
                        limite = 500,
                        dataInicio = dataInicioDo(periodo, LocalDate.now()),
                    )
                )
                TipoRelatorio.VALIDADE -> relatorioValidade(ApiClient.api.listarEstoque(), diasValidade)
                TipoRelatorio.ESTOQUE -> relatorioEstoque(ApiClient.api.listarEstoque())
            }
        } catch (e: HttpException) {
            erro = mensagemDeErro(e)
        } catch (e: IOException) {
            erro = "Não foi possível conectar à API. Os relatórios precisam de internet."
        }
        carregando = false
    }
}

private val tiposMovimentacao = listOf(null to "Todos", "entrada" to "Entrada", "saida" to "Saída", "descarte" to "Descarte", "ajuste" to "Ajuste")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatoriosScreen(onVoltar: () -> Unit, vm: RelatoriosViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (vm.relatorio == null) vm.carregar() }
    val r = vm.relatorio

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Relatórios") },
            navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            windowInsets = WindowInsets(0),
        )

        // ---------- Escolha do relatório e filtros (linhas que rolam para o lado) ----------
        Linha {
            TipoRelatorio.entries.forEach { t ->
                FilterChip(selected = vm.tipo == t, onClick = { vm.selecionarTipo(t) }, label = { Text(t.rotulo) })
            }
        }
        when (vm.tipo) {
            TipoRelatorio.MOVIMENTACOES -> {
                Linha {
                    tiposMovimentacao.forEach { (valor, rotulo) ->
                        FilterChip(selected = vm.tipoMovimentacao == valor, onClick = { vm.selecionarTipoMovimentacao(valor) }, label = { Text(rotulo) })
                    }
                }
                Linha {
                    Periodo.entries.forEach { p ->
                        FilterChip(selected = vm.periodo == p, onClick = { vm.selecionarPeriodo(p) }, label = { Text(p.rotulo) })
                    }
                }
            }
            TipoRelatorio.VALIDADE -> Linha {
                listOf(30, 60, 90).forEach { d ->
                    FilterChip(selected = vm.diasValidade == d, onClick = { vm.selecionarDiasValidade(d) }, label = { Text("Até $d dias") })
                }
            }
            TipoRelatorio.ESTOQUE -> Unit
        }

        // ---------- Pré-visualização ----------
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                vm.carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vm.erro != null -> Text(vm.erro!!, color = MaterialTheme.colorScheme.error)
                r != null -> {
                    Text("Pré-visualização: ${r.linhas.size} registro(s)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxSize()) {
                        if (r.linhas.isEmpty()) {
                            Text("Nenhum registro com esses filtros.", Modifier.padding(16.dp))
                        } else {
                            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(r.linhas) { _, linha ->
                                    Column {
                                        Text(linha.first(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(linha.drop(1).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- Exportação ----------
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val rel = r ?: return@Button
                    val arquivo = CsvExporter.exportarParaCSV(
                        context,
                        rel.linhas.map(::linhaCsv),
                        cabecalho = linhaCsv(rel.cabecalho),
                        nomeArquivo = vm.tipo.arquivo,
                    )
                    notificarECompartilhar(context, arquivo, "text/csv")
                },
                enabled = r != null && !vm.carregando,
                modifier = Modifier.weight(1f),
            ) { Text("Exportar CSV") }
            Button(
                onClick = {
                    val rel = r ?: return@Button
                    val arquivo = PdfExporter.exportarParaPDF(
                        context,
                        vm.tituloDoArquivo(),
                        listOf(linhaCsv(rel.cabecalho)) + rel.linhas.map(::linhaCsv).ifEmpty { listOf("Nenhum registro encontrado.") },
                        nomeArquivo = vm.tipo.arquivo,
                    )
                    notificarECompartilhar(context, arquivo, "application/pdf")
                },
                enabled = r != null && !vm.carregando,
                modifier = Modifier.weight(1f),
            ) { Text("Exportar PDF") }
        }
    }
}

@Composable
private fun Linha(conteudo: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = conteudo,
    )
}

// ---------- Compartilhar arquivo ----------

fun notificarECompartilhar(
    context: Context,
    arquivo: File?,
    mimeType: String
) {
    if (arquivo == null || !arquivo.exists()) {
        Toast.makeText(context, "Erro ao gerar o arquivo.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
