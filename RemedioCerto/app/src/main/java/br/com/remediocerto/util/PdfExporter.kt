
package br.com.remediocerto.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportarParaPDF(
        context: Context,
        titulo: String,
        linhas: List<String>
    ): File? {

        val pdfDocument = PdfDocument()

        return try {

            // ---------- Configuração da página ----------

            val largura = 595
            val altura = 842

            val margem = 40f
            val larguraTexto = largura - (margem * 2)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.color = Color.BLACK
            paint.textSize = 12f

            // ---------- Preparação dos dados ----------

            val linhasRelatorio = if (linhas.isEmpty()) {
                listOf("Nenhuma movimentação encontrada.")
            } else {
                linhas
            }

            // Quebra textos muito grandes em várias linhas
            val linhasFormatadas = mutableListOf<String>()

            for (linha in linhasRelatorio) {

                var texto = linha.replace(";", " | ")

                while (texto.isNotEmpty()) {

                    val tamanho = paint.breakText(
                        texto,
                        true,
                        larguraTexto,
                        null
                    )

                    if (tamanho <= 0) break

                    linhasFormatadas.add(
                        texto.substring(0, tamanho)
                    )

                    texto = texto.substring(tamanho).trimStart()
                }
            }

            // ---------- Geração das páginas ----------

            val linhasPorPagina = 27

            val totalPaginas = (
                    (linhasFormatadas.size + linhasPorPagina - 1) /
                            linhasPorPagina
                    ).coerceAtLeast(1)

            for (numeroPagina in 0 until totalPaginas) {

                val pageInfo = PdfDocument.PageInfo.Builder(
                    largura,
                    altura,
                    numeroPagina + 1
                ).create()

                val page = pdfDocument.startPage(pageInfo)

                val canvas: Canvas = page.canvas

                // Cabeçalho
                paint.color = Color.rgb(30, 70, 120)
                paint.textSize = 20f
                paint.isFakeBoldText = true

                canvas.drawText(
                    "REMEDIO CERTO",
                    margem,
                    45f,
                    paint
                )

                // Título
                paint.color = Color.BLACK
                paint.textSize = 15f

                canvas.drawText(
                    titulo,
                    margem,
                    85f,
                    paint
                )

                // Linha divisória
                paint.color = Color.GRAY
                paint.strokeWidth = 1f

                canvas.drawLine(
                    margem,
                    100f,
                    largura - margem,
                    100f,
                    paint
                )

                // Conteúdo
                paint.color = Color.BLACK
                paint.textSize = 11f
                paint.isFakeBoldText = false

                var yPos = 130f

                val inicio = numeroPagina * linhasPorPagina
                val fim = minOf(
                    inicio + linhasPorPagina,
                    linhasFormatadas.size
                )

                for (i in inicio until fim) {

                    canvas.drawText(
                        linhasFormatadas[i],
                        margem,
                        yPos,
                        paint
                    )

                    yPos += 24f
                }

                // Rodapé
                paint.color = Color.GRAY
                paint.textSize = 9f

                canvas.drawText(
                    "Página ${numeroPagina + 1} de $totalPaginas",
                    margem,
                    810f,
                    paint
                )

                pdfDocument.finishPage(page)
            }

            // ---------- Salvar arquivo ----------

            val pasta = context.getExternalFilesDir(
                Environment.DIRECTORY_DOWNLOADS
            ) ?: throw IllegalStateException(
                "Pasta de armazenamento indisponível."
            )

            if (!pasta.exists()) {
                pasta.mkdirs()
            }

            val arquivo = File(
                pasta,
                "relatorio_movimentacoes.pdf"
            )

            FileOutputStream(arquivo).use { output ->
                pdfDocument.writeTo(output)
            }

            arquivo

        } catch (e: Exception) {

            e.printStackTrace()

            null

        } finally {

            pdfDocument.close()
        }
    }
}
