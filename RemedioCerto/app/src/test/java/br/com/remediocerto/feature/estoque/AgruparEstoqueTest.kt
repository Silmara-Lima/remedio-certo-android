package br.com.remediocerto.feature.estoque

import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.UnidadeDto
import br.com.remediocerto.core.ui.StatusEstoque
import org.junit.Assert.assertEquals
import org.junit.Test

class AgruparEstoqueTest {

    private fun lote(
        id: Int, medicamentoId: Int = 1, unidadeId: Int = 1, quantidade: Int,
        validade: String = "2027-12-31", status: String = "normal",
        minimo: Int = 10, nome: String = "Dipirona 1g",
    ) = EstoqueDto(
        id = id, medicamentoId = medicamentoId, unidadeId = unidadeId, lote = "L$id",
        validade = validade, quantidade = quantidade, diasParaVencer = 100, status = status,
        medicamento = MedicamentoDto(id = medicamentoId, nome = nome, quantidadeMinima = minimo),
        unidade = UnidadeDto(id = unidadeId, nome = "Unidade $unidadeId"),
    )

    @Test
    fun `soma os lotes do mesmo medicamento na mesma unidade`() {
        val itens = agruparEstoque(listOf(lote(1, quantidade = 30), lote(2, quantidade = 20)))
        assertEquals(1, itens.size)
        assertEquals(50, itens[0].total)
        assertEquals(2, itens[0].lotes.size)
    }

    @Test
    fun `separa o mesmo medicamento em unidades diferentes`() {
        val itens = agruparEstoque(listOf(lote(1, unidadeId = 1, quantidade = 30), lote(2, unidadeId = 2, quantidade = 30)))
        assertEquals(2, itens.size)
    }

    @Test
    fun `lote vencido deixa o item como vencido`() {
        val itens = agruparEstoque(listOf(lote(1, quantidade = 50, status = "vencido"), lote(2, quantidade = 50)))
        assertEquals(StatusEstoque.VENCIDO, itens[0].status)
    }

    @Test
    fun `estoque baixo considera a soma de todos os lotes`() {
        val itens = agruparEstoque(listOf(lote(1, quantidade = 3), lote(2, quantidade = 4)))   // 7 <= mínimo 10
        assertEquals(StatusEstoque.BAIXO, itens[0].status)
    }

    @Test
    fun `itens criticos aparecem primeiro`() {
        val itens = agruparEstoque(listOf(
            lote(1, medicamentoId = 1, nome = "Amoxicilina", quantidade = 100),
            lote(2, medicamentoId = 2, nome = "Paracetamol", quantidade = 100, status = "vencido"),
        ))
        assertEquals("Paracetamol", itens[0].nome)
    }

    @Test
    fun `lotes ficam em ordem FEFO`() {
        val itens = agruparEstoque(listOf(
            lote(1, quantidade = 50, validade = "2027-05-01"),
            lote(2, quantidade = 50, validade = "2026-12-01"),
        ))
        assertEquals("2026-12-01", itens[0].lotes.first().validade)
    }
}