package br.com.remediocerto.feature.estoque

import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.ui.StatusEstoque
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicamentoDetalheTest {

    private fun lote(id: Int, unidade: Int, validade: String, qtd: Int, status: String = "normal", medicamento: Int = 1) =
        EstoqueDto(
            id = id, medicamentoId = medicamento, unidadeId = unidade, lote = "L$id", validade = validade,
            quantidade = qtd, diasParaVencer = 30, status = status,
            medicamento = MedicamentoDto(id = medicamento, nome = "Dipirona", quantidadeMinima = 10),
        )

    @Test
    fun `lotes da unidade em ordem de validade, sem os zerados`() {
        val lotes = listOf(
            lote(1, unidade = 1, validade = "2027-05-01", qtd = 10),
            lote(2, unidade = 1, validade = "2026-12-01", qtd = 5),
            lote(3, unidade = 2, validade = "2026-11-30", qtd = 8),   // outra unidade
            lote(4, unidade = 1, validade = "2026-11-20", qtd = 0),   // zerado
            lote(5, unidade = 1, validade = "2026-12-01", qtd = 3, medicamento = 2),   // outro medicamento
        )
        assertEquals(listOf(2, 1), lotesDoDetalhe(lotes, medicamentoId = 1, unidadeId = 1).map { it.id })
    }

    @Test
    fun `situacao usa a mesma regra da lista do estoque`() {
        val vencido = listOf(lote(1, 1, "2026-01-01", 50, status = "vencido"))
        assertEquals(StatusEstoque.VENCIDO, situacaoDoMedicamento(vencido, minimo = 10))
        val baixo = listOf(lote(1, 1, "2027-01-01", 5))
        assertEquals(StatusEstoque.BAIXO, situacaoDoMedicamento(baixo, minimo = 10))
    }

    @Test
    fun `sem lotes e com minimo fica como estoque baixo`() {
        assertEquals(StatusEstoque.BAIXO, situacaoDoMedicamento(emptyList(), minimo = 10))
    }
}
