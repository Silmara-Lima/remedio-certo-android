package br.com.remediocerto.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class EstiloMov(val rotulo: String, val cor: Color, val sinal: String)

fun estiloMov(tipo: String): EstiloMov = when (tipo.lowercase()) {
    "entrada" -> EstiloMov("Entrada", Color(0xFF15803D), "+")
    "saida" -> EstiloMov("Saída", Color(0xFF1D4ED8), "−")
    "descarte" -> EstiloMov("Descarte", Color(0xFFB91C1C), "−")
    "ajuste" -> EstiloMov("Ajuste", Color(0xFFB45309), "=")
    else -> EstiloMov(tipo, Color.Gray, "")
}

@Composable
fun MovimentacaoItem(tipo: String, titulo: String, detalhe: String, quantidade: Int, dataIso: String?) {
    val estilo = estiloMov(tipo)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = estilo.cor.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(estilo.sinal, color = estilo.cor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text("${estilo.rotulo} · $detalhe", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                dataIso?.let {
                    Text(dataHoraBr(it), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("${estilo.sinal}$quantidade", color = estilo.cor, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}