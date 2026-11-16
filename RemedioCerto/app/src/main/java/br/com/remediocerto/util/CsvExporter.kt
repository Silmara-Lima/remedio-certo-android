package br.com.remediocerto.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object CsvExporter {
    fun exportarParaCSV(
        context: Context,
        movimentacoes: List<String>,
        cabecalho: String = "Medicamento;Tipo;Quantidade;Lote;Data",   // cada relatório manda o seu
        nomeArquivo: String = "relatorio_movimentacoes",
    ): File? {
        // Salva na pasta de Downloads do app (compartilhada pelo FileProvider)
        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val arquivo = File(pasta, "$nomeArquivo.csv")

        try {
            // UTF-8 explícito (funciona do Android 8 em diante)
            val writer = OutputStreamWriter(FileOutputStream(arquivo), Charsets.UTF_8)
            // BOM: faz o Excel abrir os acentos certos
            writer.append('\uFEFF')
            // Cabeçalho do arquivo excel / csv
            writer.append(cabecalho).append("\n")

            for (linha in movimentacoes) {
                writer.append(linha).append("\n")
            }

            writer.flush()
            writer.close()
            return arquivo
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}