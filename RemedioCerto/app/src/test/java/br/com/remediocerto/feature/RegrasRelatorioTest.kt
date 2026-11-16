package br.com.remediocerto.feature

import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.HistoricoDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.UnidadeDto
import org.junit.Assert.assertEquals
import org.junit.Test

class RegrasRelatorioTest {

    private fun lote(id: Int, validade: String, dias: Int, qtd: Int, status: String = "normal") = EstoqueDto(
        id = id, medicamentoId = 1, unidadeId = 1, lote = "L$id", validade = validade, quantidade = qtd,
        diasParaVencer = dias, status = status,
        medicamento = MedicamentoDto(id = 1, nome = "Dipirona 1g", quantidadeMinima = 30),
        unidade = UnidadeDto(id = 1, nome = "UBS Centro"),
    )

    @Test
    fun `validade traz vencidos e a vencer no prazo, do mais urgente ao ultimo`() {
        val r = relatorioValidade(
            listOf(
                lote(1, "2027-06-01", dias = 200, qtd = 10),   // fora do prazo
                lote(2, "2026-12-01", dias = 16, qtd = 5),
                lote(3, "2026-11-10", dias = -5, qtd = 8, status = "vencido"),
                lote(4, "2026-11-20", dias = 5, qtd = 0),      // zerado
            ),
            dias = 30,
        )
        assertEquals(listOf("L3", "L2"), r.linhas.map { it[1] })
        assertEquals("Vencido há 5 dia(s)", r.linhas.first()[3])
        assertEquals(6, r.cabecalho.size)
    }

    @Test
    fun `estoque soma os lotes por medicamento e unidade`() {
        val r = relatorioEstoque(listOf(lote(1, "2027-06-01", 200, 10), lote(2, "2027-01-01", 50, 15)))
        assertEquals(1, r.linhas.size)
        assertEquals("25", r.linhas.first()[2])     // saldo
        assertEquals("30", r.linhas.first()[3])     // mínimo
        assertEquals("2", r.linhas.first()[4])      // lotes
    }

    @Test
    fun `movimentacoes da mais recente para a mais antiga`() {
        val itens = listOf(
            HistoricoDto(1, "entrada", "A", "2027-01-01", 10, criadaEm = "2026-11-10T09:00:00"),
            HistoricoDto(2, "saida", "A", "2027-01-01", 2, criadaEm = "2026-11-15T09:00:00"),
        )
        val r = relatorioMovimentacoes(itens)
        assertEquals(listOf("Saída", "Entrada"), r.linhas.map { it[1] })
    }

    @Test
    fun `ponto e virgula dentro de um valor nao quebra o CSV`() {
        assertEquals("Dipirona, 1g;10", linhaCsv(listOf("Dipirona; 1g", "10")))
    }
}
