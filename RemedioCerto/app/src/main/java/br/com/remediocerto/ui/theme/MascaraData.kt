package br.com.remediocerto.core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Mostra "31122027" como "31/12/2027".
 * O valor guardado continua sendo só os dígitos (máximo 8).
 */
class MascaraData : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digitos = text.text.take(8)
        val formatado = buildString {
            digitos.forEachIndexed { i, c ->
                append(c)
                if (i == 1 && digitos.length > 2) append('/')
                if (i == 3 && digitos.length > 4) append('/')
            }
        }

        // Mantém o cursor no lugar certo apesar das barras inseridas
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val t = when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    else -> offset + 2
                }
                return t.coerceAtMost(formatado.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val o = when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else -> offset - 2
                }
                return o.coerceIn(0, digitos.length)
            }
        }
        return TransformedText(AnnotatedString(formatado), mapa)
    }
}