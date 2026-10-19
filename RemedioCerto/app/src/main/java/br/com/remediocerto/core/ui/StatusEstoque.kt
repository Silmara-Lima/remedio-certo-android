package br.com.remediocerto.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class StatusEstoque(val api: String, val rotulo: String, val cor: Color, val gravidade: Int) {
    VENCIDO("vencido", "Vencido", Color(0xFFB91C1C), 3),
    BAIXO("estoque_baixo", "Estoque baixo", Color(0xFFB91C1C), 2),
    VENCENDO("vencimento_proximo", "Vencendo", Color(0xFFB45309), 1),
    NORMAL("normal", "Em dia", Color(0xFF15803D), 0);

    companion object {
        fun de(valor: String) = entries.firstOrNull { it.api == valor } ?: NORMAL
    }
}

@Composable
fun StatusBadge(status: StatusEstoque) {
    Surface(color = status.cor.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            status.rotulo,
            color = status.cor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}