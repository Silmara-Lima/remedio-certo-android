
package br.com.remediocerto.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "estoque_local")
data class EstoqueLocalEntity(

    @PrimaryKey
    val chave: String,

    val medicamentoId: Int,
    val unidadeId: Int,

    val nome: String,
    val unidade: String,

    val lote: String,
    val validade: String,

    val quantidade: Double,
    val quantidadeMinima: Double,

    val status: String,

    val atualizadoEm: Long
)
