package dev.jesusmolano.alisio.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Una apertura desde Alisio. [key] es la clave del índice de búsqueda (app, acceso directo o ajuste). */
@Entity(tableName = "launch_event", indices = [Index("at")])
data class LaunchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    val at: Long,
    // Se rellena desde la fase 3 (regla Bluetooth de Ahora).
    @ColumnInfo(name = "bluetooth_device") val bluetoothDevice: String? = null
)

/**
 * Hueco del dock. Con [appKey] es una app; con [stackName] es una pila, cuyas apps están en
 * [StackItem]. Un hueco vacío no tiene fila.
 */
@Entity(tableName = "dock_slot")
data class DockSlot(
    @PrimaryKey val position: Int,
    @ColumnInfo(name = "app_key") val appKey: String? = null,
    @ColumnInfo(name = "stack_name") val stackName: String? = null
)

/** App de la pila que ocupa el hueco [slot]. */
@Entity(tableName = "stack_item", primaryKeys = ["slot", "app_key"])
data class StackItem(val slot: Int, @ColumnInfo(name = "app_key") val appKey: String, val position: Int)

class KeyAt(val key: String, val at: Long)

@Dao
interface LaunchEventDao {
    @Insert
    suspend fun insert(event: LaunchEvent)

    @Query("SELECT `key`, at FROM launch_event WHERE at >= :since ORDER BY at")
    fun since(since: Long): Flow<List<KeyAt>>

    @Query("DELETE FROM launch_event WHERE at < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface HomeConfigDao {
    @Query("SELECT * FROM dock_slot ORDER BY position")
    fun dock(): Flow<List<DockSlot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSlot(slot: DockSlot)

    @Query("DELETE FROM dock_slot WHERE position = :position")
    suspend fun clearSlot(position: Int)

    @Query("SELECT * FROM stack_item ORDER BY slot, position")
    fun stackItems(): Flow<List<StackItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToStack(item: StackItem)

    @Query("DELETE FROM stack_item WHERE slot = :slot AND app_key = :appKey")
    suspend fun removeFromStack(slot: Int, appKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM stack_item WHERE slot = :slot AND app_key = :appKey)")
    suspend fun isInStack(slot: Int, appKey: String): Boolean

    @Query("DELETE FROM stack_item WHERE slot = :slot")
    suspend fun clearStack(slot: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM stack_item WHERE slot = :slot")
    suspend fun nextStackPosition(slot: Int): Int
}

@Database(entities = [LaunchEvent::class, DockSlot::class, StackItem::class], version = 2, exportSchema = false)
abstract class AlisioDatabase : RoomDatabase() {
    abstract fun launchEvents(): LaunchEventDao

    abstract fun homeConfig(): HomeConfigDao
}
