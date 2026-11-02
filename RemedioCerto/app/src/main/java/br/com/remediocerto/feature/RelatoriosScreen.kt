
package br.com.remediocerto.feature

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.remediocerto.util.CsvExporter
import br.com.remediocerto.util.PdfExporter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatoriosScreen(
    onVoltar: () -> Unit
) {
    val context = LocalContext.current

    var filtroTipo by remember {
        mutableStateOf("TODOS")
    }

    // Lista simulada de movimentações
    val movimentacoesExemplo = listOf(
        "Paracetamol 500mg;ENTRADA;100;LOTE2026A;01/11/2026",
        "Amoxicilina 500mg;SAÍDA;50;LOTE2024C;01/11/2026",
        "Dipirona 1g;SAÍDA;20;LOTE2024B;01/11/2026",
        "Omeprazol 20mg;DESCARTE;5;LOTE2024D;01/11/2026"
    )

    val dadosFiltrados = if (filtroTipo == "TODOS") {
        movimentacoesExemplo
    } else {
        movimentacoesExemplo.filter {
            it.contains(filtroTipo)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // ---------- Botão Voltar ----------

        OutlinedButton(
            onClick = onVoltar
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar"
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Voltar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- Título ----------

        Text(
            text = "Relatórios de Movimentação",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------- Filtros ----------

        Text(
            text = "Filtrar por Tipo:",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                "TODOS",
                "ENTRADA",
                "SAÍDA",
                "DESCARTE"
            ).forEach { tipo ->

                ElevatedButton(
                    onClick = {
                        filtroTipo = tipo
                    },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor =
                            if (filtroTipo == tipo)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(text = tipo)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- Pré-visualização ----------

        Text(
            text = "Pré-visualização dos dados (${dadosFiltrados.size}):",
            style = MaterialTheme.typography.titleSmall
        )

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                dadosFiltrados.forEach { item ->
                    Text(
                        text = item.replace(";", " | "),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // ---------- Botões de exportação ----------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            Button(
                onClick = {
                    val arquivo = CsvExporter.exportarParaCSV(
                        context,
                        dadosFiltrados
                    )

                    notificarECompartilhar(
                        context,
                        arquivo,
                        "text/csv"
                    )
                }
            ) {
                Text("Exportar CSV")
            }

            Button(
                onClick = {
                    val arquivo = PdfExporter.exportarParaPDF(
                        context,
                        "Relatório - Filtro: $filtroTipo",
                        dadosFiltrados
                    )

                    notificarECompartilhar(
                        context,
                        arquivo,
                        "application/pdf"
                    )
                }
            ) {
                Text("Exportar PDF")
            }
        }
    }
}

// ---------- Compartilhar arquivo ----------


fun notificarECompartilhar(
    context: Context,
    arquivo: File?,
    mimeType: String
) {
    if (arquivo == null || !arquivo.exists()) {
        Toast.makeText(
            context,
            "Erro ao gerar o arquivo.",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                "Compartilhar Relatório"
            )
        )

    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Erro ao compartilhar: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}
