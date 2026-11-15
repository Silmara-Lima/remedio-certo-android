package br.com.remediocerto.feature.dashboard

import br.com.remediocerto.core.network.HistoricoDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RegrasDashboardTest {

    private val hoje = LocalDate.of(2026, 11, 15)

    private fun mov(id: Int, tipo: String, criadaEm: String) = HistoricoDto(
        id = id, tipo = tipo, lote = "L1", validade = "2027-01-01", quantidade = 1, criadaEm = criadaEm,
    )

    @Test
    fun `sempre devolve 7 dias, do mais antigo para hoje`() {
        val dias = resumoUltimosDias(emptyList(), hoje)
        assertEquals(7, dias.size)
        assertEquals(LocalDate.of(2026, 11, 9), dias.first().data)
        assertEquals(hoje, dias.last().data)
        assertEquals(0, dias.sumOf { it.entradas + it.saidas })
    }

    @Test
    fun `separa entradas de saidas e descartes`() {
        val dias = resumoUltimosDias(
            listOf(
                mov(1, "entrada", "2026-11-15T09:00:00"),
                mov(2, "saida", "2026-11-15T10:00:00"),
                mov(3, "descarte", "2026-11-15T11:00:00"),
                mov(4, "ENTRADA", "2026-11-14T08:00:00.123456"),
            ),
            hoje,
        )
        assertEquals(1, dias.last().entradas)
        assertEquals(2, dias.last().saidas)
        assertEquals(1, dias[5].entradas)
    }

    @Test
    fun `ajustes e datas fora da janela nao entram`() {
        val dias = resumoUltimosDias(
            listOf(
                mov(1, "ajuste", "2026-11-15T09:00:00"),
                mov(2, "entrada", "2026-11-08T09:00:00"),   // 8 dias atrás
                mov(3, "saida", "data-invalida"),
            ),
            hoje,
        )
        assertEquals(0, dias.sumOf { it.entradas + it.saidas })
    }

    @Test
    fun `descricao para leitor de tela`() {
        val dias = resumoUltimosDias(listOf(mov(1, "entrada", "2026-11-15T09:00:00")), hoje)
        assertEquals(
            "Movimentações dos últimos 7 dias: 1 entrada(s) e 0 saída(s) ou descarte(s).",
            descricaoGrafico(dias),
        )
    }
}
