package br.com.remediocerto.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.remediocerto.core.database.entity.MovimentacaoPendenteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimentacaoPendenteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(movimentacao: MovimentacaoPendenteEntity)

    /** Próximas a enviar, na ordem em que foram feitas. */
    @Query("SELECT * FROM movimentacao_pendente WHERE usuarioId = :usuarioId AND rejeitada = 0 ORDER BY criadaEm ASC LIMIT :limite")
    suspend fun pendentes(usuarioId: Int, limite: Int): List<MovimentacaoPendenteEntity>

    @Query("SELECT COUNT(*) FROM movimentacao_pendente WHERE rejeitada = 0")
    fun contarPendentes(): Flow<Int>

    @Query("SELECT * FROM movimentacao_pendente WHERE rejeitada = 1 ORDER BY criadaEm ASC")
    fun rejeitadas(): Flow<List<MovimentacaoPendenteEntity>>

    @Query("DELETE FROM movimentacao_pendente WHERE idLocal = :idLocal")
    suspend fun remover(idLocal: String)

    @Query("UPDATE movimentacao_pendente SET rejeitada = 1, motivo = :motivo WHERE idLocal = :idLocal")
    suspend fun marcarRejeitada(idLocal: String, motivo: String)
}
