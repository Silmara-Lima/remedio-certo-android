package br.com.remediocerto.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.remediocerto.core.database.dao.EstoqueLocalDao
import br.com.remediocerto.core.database.dao.MovimentacaoPendenteDao
import br.com.remediocerto.core.database.entity.EstoqueLocalEntity
import br.com.remediocerto.core.database.entity.MovimentacaoPendenteEntity

@Database(
    entities = [EstoqueLocalEntity::class, MovimentacaoPendenteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun estoqueLocalDao(): EstoqueLocalDao

    abstract fun movimentacaoPendenteDao(): MovimentacaoPendenteDao
}
