
package br.com.remediocerto.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.remediocerto.core.database.dao.EstoqueLocalDao
import br.com.remediocerto.core.database.entity.EstoqueLocalEntity

@Database(
    entities = [EstoqueLocalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun estoqueLocalDao(): EstoqueLocalDao
}
