package br.com.remediocerto.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Movimentação feita sem internet, esperando envio para a API (/movimentacoes/lote).
 *
 * O idLocal (UUID) e o payload nunca mudam depois de criados: a API usa os dois para
 * reconhecer reenvios e não aplicar a mesma movimentação duas vezes.
 */
@Entity(tableName = "movimentacao_pendente")
data class MovimentacaoPendenteEntity(

    @PrimaryKey
    val idLocal: String,

    val usuarioId: Int,             // só o próprio usuário envia (o id_local é por usuário na API)

    val payload: String,            // JSON da MovimentacaoRequest, reenviado exatamente igual

    val descricao: String,          // ex.: "Saída de 5 un. de Dipirona"

    val criadaEm: Long,

    val rejeitada: Boolean = false, // a API recusou (ex.: estoque mudou enquanto estava offline)
    val motivo: String? = null
)
