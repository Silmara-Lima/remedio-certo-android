package br.com.remediocerto.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cópia local de um lote de estoque, para o app funcionar sem internet.
 * Um registro por lote, igual ao que a API devolve em /estoque.
 */
@Entity(tableName = "estoque_local")
data class EstoqueLocalEntity(

    @PrimaryKey
    val chave: String,              // id do lote na API, como texto

    val estoqueId: Int,             // id do lote na API (estoque_lote_id)
    val medicamentoId: Int,
    val unidadeId: Int,

    val nome: String,               // nome do medicamento
    val unidade: String,            // nome da unidade
    val codigoBarras: String?,      // para o scanner funcionar offline

    val lote: String,
    val validade: String,           // "2026-12-15"

    val quantidade: Int,
    val quantidadeMinima: Int,
    val diasParaVencer: Int,

    val status: String,

    val atualizadoEm: Long          // quando veio da API (System.currentTimeMillis)
)
