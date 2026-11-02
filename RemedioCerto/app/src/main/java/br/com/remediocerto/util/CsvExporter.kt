package br.com.remediocerto.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter

object CsvExporter {
    fun exportarParaCSV(context: Context, movimentacoes: List<String>): File? {
        // Salva na pasta de Downloads pública do celular
        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val arquivo = File(pasta, "relatorio_movimentacoes.csv")

        try {
            val writer = FileWriter(arquivo)
            // Cabeçalho do arquivo excel / csv
            writer.append("Medicamento;Tipo;Quantidade;Lote;Data\n")

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