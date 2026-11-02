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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarEstoque(itens: List<EstoqueLocalEntity>)

    @Query("DELETE FROM estoque_local")
    suspend fun limparEstoque()

    @Query("SELECT * FROM estoque_local WHERE chave = :chave")
    suspend fun buscarPorChave(chave: String): EstoqueLocalEntity?
}
