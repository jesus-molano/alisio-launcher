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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Una apertura desde Alisio. [key] es la clave del índice de búsqueda (app, acceso directo o ajuste). */
@Entity(tableName = "launch_event", indices = [Index("at")])
data class LaunchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val key: String,
    val at: Long,
    // Solo en las aperturas hechas al poco de conectar ese dispositivo de audio (regla Bluetooth de Ahora).
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
    @ColumnInfo(name = "stack_name") val stackName: String? = null,
    // Cómo se dibuja el icono de la pila (`StackIcon.id`) y su símbolo propio; `null` es el valor por defecto.
    @ColumnInfo(name = "stack_icon") val stackIcon: String? = null,
    @ColumnInfo(name = "stack_glyph") val stackGlyph: String? = null
)

/** App de la pila que ocupa el hueco [slot]. */
@Entity(tableName = "stack_item", primaryKeys = ["slot", "app_key"])
data class StackItem(val slot: Int, @ColumnInfo(name = "app_key") val appKey: String, val position: Int)

/** Cuántas veces se ha descartado una sugerencia de Ahora (regla y app) y cuándo fue la última. */
@Entity(tableName = "suggestion_feedback", primaryKeys = ["rule", "key"])
data class SuggestionFeedback(
    val rule: String,
    val key: String,
    val dismissals: Int,
    @ColumnInfo(name = "last_dismissed_at") val lastDismissedAt: Long
)

/**
 * Destino secundario del icono de [appKey] (deslizar hacia arriba sobre él): otra app
 * ([targetAppKey]) o un acceso directo, que se identifica por paquete, id y serie de usuario.
 */
@Entity(tableName = "icon_gesture")
data class IconGesture(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    @ColumnInfo(name = "target_app_key") val targetAppKey: String? = null,
    @ColumnInfo(name = "shortcut_package") val shortcutPackage: String? = null,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String? = null,
    @ColumnInfo(name = "shortcut_user") val shortcutUser: Long? = null
)

/** Isla de Zonas creada por el usuario. Las demás (Diario, las pilas, Dev, Sistema y Resto) se calculan. */
@Entity(tableName = "zone")
data class ZoneRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val position: Int)

/** Isla elegida a mano para la app [appKey]. [zone] es un `ZoneId` codificado (`dev`, `custom:3`…). */
@Entity(tableName = "zone_assignment")
data class ZoneAssignment(@PrimaryKey @ColumnInfo(name = "app_key") val appKey: String, val zone: String)

class KeyAt(val key: String, val at: Long)

class DeviceKeyAt(@ColumnInfo(name = "bluetooth_device") val device: String, val key: String, val at: Long)

/** De la 2 a la 3: el icono de las pilas. Con migración, para no borrar el dock ya configurado. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dock_slot ADD COLUMN stack_icon TEXT")
            db.execSQL("ALTER TABLE dock_slot ADD COLUMN stack_glyph TEXT")
        }
    }

/** De la 3 a la 4: los descartes de Ahora. */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `suggestion_feedback` (" +
                    "`rule` TEXT NOT NULL, `key` TEXT NOT NULL, `dismissals` INTEGER NOT NULL, " +
                    "`last_dismissed_at` INTEGER NOT NULL, PRIMARY KEY(`rule`, `key`))"
            )
        }
    }

/** De la 4 a la 5: el destino secundario de los iconos. */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `icon_gesture` (" +
                    "`app_key` TEXT NOT NULL, `target_app_key` TEXT, `shortcut_package` TEXT, " +
                    "`shortcut_id` TEXT, `shortcut_user` INTEGER, PRIMARY KEY(`app_key`))"
            )
        }
    }

/** De la 5 a la 6: las islas propias de Zonas y la isla elegida a mano para cada app. */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `zone` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `zone_assignment` (" +
                    "`app_key` TEXT NOT NULL, `zone` TEXT NOT NULL, PRIMARY KEY(`app_key`))"
            )
        }
    }

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zone ORDER BY position, id")
    fun zones(): Flow<List<ZoneRow>>

    /** La isla nueva va al final: su posición se decide en la misma sentencia que la crea. */
    @Query("INSERT INTO zone(name, position) VALUES(:name, (SELECT COALESCE(MAX(position), -1) + 1 FROM zone))")
    suspend fun create(name: String)

    @Query("UPDATE zone SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM zone WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM zone_assignment")
    fun assignments(): Flow<List<ZoneAssignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assign(assignment: ZoneAssignment)

    @Query("DELETE FROM zone_assignment WHERE app_key = :appKey")
    suspend fun clear(appKey: String)

    @Query("DELETE FROM zone_assignment WHERE zone = :zone")
    suspend fun clearZone(zone: String)
}

@Dao
interface IconGestureDao {
    @Query("SELECT * FROM icon_gesture")
    fun all(): Flow<List<IconGesture>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(gesture: IconGesture)

    @Query("DELETE FROM icon_gesture WHERE app_key = :appKey")
    suspend fun clear(appKey: String)
}

@Dao
interface SuggestionFeedbackDao {
    @Query("SELECT * FROM suggestion_feedback")
    fun all(): Flow<List<SuggestionFeedback>>

    /**
     * Suma un descarte en una sola sentencia: dos toques seguidos cuentan los dos, y no hace falta
     * haber leído antes el contador.
     */
    @Query(
        "INSERT INTO suggestion_feedback(rule, `key`, dismissals, last_dismissed_at) VALUES(:rule, :key, 1, :at) " +
            "ON CONFLICT(rule, `key`) DO UPDATE SET dismissals = dismissals + 1, last_dismissed_at = :at"
    )
    suspend fun addDismissal(rule: String, key: String, at: Long)
}

@Dao
interface LaunchEventDao {
    @Insert
    suspend fun insert(event: LaunchEvent)

    @Query("SELECT `key`, at FROM launch_event WHERE at >= :since ORDER BY at")
    fun since(since: Long): Flow<List<KeyAt>>

    /** Aperturas de [type] que llevan dispositivo Bluetooth: las hechas al poco de conectarlo. */
    @Query(
        "SELECT bluetooth_device, `key`, at FROM launch_event " +
            "WHERE bluetooth_device IS NOT NULL AND type = :type AND at >= :since ORDER BY at"
    )
    fun withBluetooth(type: String, since: Long): Flow<List<DeviceKeyAt>>

    @Query("DELETE FROM launch_event WHERE at < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface HomeConfigDao {
    @Query("SELECT * FROM dock_slot ORDER BY position")
    fun dock(): Flow<List<DockSlot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSlot(slot: DockSlot)

    @Query("UPDATE dock_slot SET stack_name = :name WHERE position = :position AND stack_name IS NOT NULL")
    suspend fun renameStack(position: Int, name: String)

    @Query(
        "UPDATE dock_slot SET stack_icon = :icon, stack_glyph = :glyph " +
            "WHERE position = :position AND stack_name IS NOT NULL"
    )
    suspend fun setStackIcon(position: Int, icon: String, glyph: String?)

    @Query("DELETE FROM dock_slot WHERE position = :position")
    suspend fun clearSlot(position: Int)

    @Query("SELECT * FROM stack_item ORDER BY slot, position")
    fun stackItems(): Flow<List<StackItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToStack(item: StackItem)

    @Query("UPDATE stack_item SET position = :position WHERE slot = :slot AND app_key = :appKey")
    suspend fun setStackPosition(slot: Int, appKey: String, position: Int)

    @Query("DELETE FROM stack_item WHERE slot = :slot AND app_key = :appKey")
    suspend fun removeFromStack(slot: Int, appKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM stack_item WHERE slot = :slot AND app_key = :appKey)")
    suspend fun isInStack(slot: Int, appKey: String): Boolean

    @Query("DELETE FROM stack_item WHERE slot = :slot")
    suspend fun clearStack(slot: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM stack_item WHERE slot = :slot")
    suspend fun nextStackPosition(slot: Int): Int
}

@Database(
    entities = [
        LaunchEvent::class,
        DockSlot::class,
        StackItem::class,
        SuggestionFeedback::class,
        IconGesture::class,
        ZoneRow::class,
        ZoneAssignment::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AlisioDatabase : RoomDatabase() {
    abstract fun launchEvents(): LaunchEventDao

    abstract fun homeConfig(): HomeConfigDao

    abstract fun suggestionFeedback(): SuggestionFeedbackDao

    abstract fun iconGestures(): IconGestureDao

    abstract fun zones(): ZoneDao
}
