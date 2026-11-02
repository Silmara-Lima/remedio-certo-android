package br.com.remediocerto.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.remediocerto.core.database.entity.EstoqueLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EstoqueLocalDao {

    @Query("SELECT * FROM estoque_local ORDER BY nome ASC")
    fun listarEstoque(): Flow<List<EstoqueLocalEntity>>

    @Query("SELECT * FROM estoque_local ORDER BY nome ASC")
    suspend fun listarTodos(): List<EstoqueLocalEntity>

    @Query("SELECT * FROM estoque_local WHERE medicamentoId = :medicamentoId")
    suspend fun listarPorMedicamento(medicamentoId: Int): List<EstoqueLocalEntity>

    /** Busca por parte do nome ou pelo código de barras exato. */
    @Query("SELECT * FROM estoque_local WHERE nome LIKE '%' || :texto || '%' OR codigoBarras = :texto ORDER BY nome ASC")
    suspend fun buscar(texto: String): List<EstoqueLocalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarEstoque(itens: List<EstoqueLocalEntity>)

    @Query("DELETE FROM estoque_local")
    suspend fun limparEstoque()

    @Query("DELETE FROM estoque_local WHERE medicamentoId = :medicamentoId")
    suspend fun limparMedicamento(medicamentoId: Int)

    @Query("SELECT * FROM estoque_local WHERE chave = :chave")
    suspend fun buscarPorChave(chave: String): EstoqueLocalEntity?
}
