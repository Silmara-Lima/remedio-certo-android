package br.com.remediocerto.core.offline

import br.com.remediocerto.core.database.entity.EstoqueLocalEntity
import br.com.remediocerto.core.network.EstoqueDto
import br.com.remediocerto.core.network.MedicamentoDto
import br.com.remediocerto.core.network.UnidadeDto
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ConversoesOfflineTest {

    private val hoje = LocalDate.of(2026, 11, 2)

    private fun local(
        estoqueId: Int = 1,
        medicamentoId: Int = 10,
        unidadeId: Int = 1,
        nome: String = "Dipirona",
        validade: String = "2026-12-31",
        quantidade: Int = 50,
        status: String = "normal",
        codigoBarras: String? = "789",
    ) = EstoqueLocalEntity(
        chave = estoqueId.toString(), estoqueId = estoqueId, medicamentoId = medicamentoId,
        unidadeId = unidadeId, nome = nome, unidade = "UBS Centro", codigoBarras = codigoBarras,
        lote = "L$estoqueId", validade = validade, quantidade = quantidade, quantidadeMinima = 10,
        diasParaVencer = 59, status = status, atualizadoEm = 0L,
    )

    @Test
    fun `ida e volta preserva os dados do lote`() {
        val dto = EstoqueDto(
            id = 7, medicamentoId = 10, unidadeId = 1, lote = "A1", validade = "2026-12-31",
            quantidade = 40, diasParaVencer = 59, status = "normal",
            medicamento = MedicamentoDto(id = 10, nome = "Dipirona", codigoBarras = "789", quantidadeMinima = 10),
            unidade = UnidadeDto(id = 1, nome = "UBS Centro"),
        )
        val volta = dto.paraLocal(agora = 123L).paraDto(hoje)
        assertEquals(dto.id, volta.id)
        assertEquals(dto.lote, volta.lote)
        assertEquals(dto.quantidade, volta.quantidade)
        assertEquals("Dipirona", volta.medicamento?.nome)
        assertEquals("789", volta.medicamento?.codigoBarras)
        assertEquals(10, volta.medicamento?.quantidadeMinima)
        assertEquals("UBS Centro", volta.unidade?.nome)
    }

    @Test
    fun `lote que venceu depois de salvo vira vencido (RN03 offline)`() {
        val dto = local(validade = "2026-11-01", status = "vencendo").paraDto(hoje)
        assertEquals("vencido", dto.status)
        assertEquals(-1, dto.diasParaVencer)
    }

    @Test
    fun `dias para vencer sao recalculados com a data de hoje`() {
        assertEquals(29, local(validade = "2026-12-01").paraDto(hoje).diasParaVencer)
    }

    @Test
    fun `saldo depois de cada tipo de movimentacao`() {
        assertEquals(30, saldoDepois("saida", 50, 20))
        assertEquals(45, saldoDepois("descarte", 50, 5))
        assertEquals(60, saldoDepois("entrada", 50, 10))
        assertEquals(12, saldoDepois("ajuste", 50, 12))
    }

    @Test
    fun `medicamentos e unidades do cache sem repeticao`() {
        val itens = listOf(
            local(estoqueId = 1, medicamentoId = 10, unidadeId = 1),
            local(estoqueId = 2, medicamentoId = 10, unidadeId = 2),
            local(estoqueId = 3, medicamentoId = 20, unidadeId = 1, nome = "Amoxicilina", codigoBarras = null),
        )
        assertEquals(listOf(10, 20), medicamentosDoCache(itens).map { it.id })
        assertNull(medicamentosDoCache(itens)[1].codigoBarras)
        assertEquals(listOf(1, 2), unidadesDoCache(itens).map { it.id }.sorted())
    }

    @Test
    fun `item do lote leva o id_local e mantem o payload`() {
        val item = Sincronizacao.itemDoLote("abc-123", """{"tipo":"saida","quantidade":5}""")
        assertEquals("abc-123", item["id_local"]?.jsonPrimitive?.content)
        assertEquals("saida", item["tipo"]?.jsonPrimitive?.content)
        assertEquals(5, item["quantidade"]?.jsonPrimitive?.int)
    }
}
