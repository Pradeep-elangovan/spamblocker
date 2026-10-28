package com.pradeep.spamblocker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocked")
data class BlockedEntity(
    @PrimaryKey val e164: String,
    /** BlockAction name. */
    val action: String,
    val label: String = "",
    val spam: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "allowed")
data class AllowedEntity(
    @PrimaryKey val e164: String,
    val label: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** PatternType name. */
    val type: String,
    val pattern: String,
    val label: String = "",
    val enabled: Boolean = true,
    val hits: Int = 0,
)

/** A row exists only while the region is blocked. */
@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey val region: String,
    val blockLandline: Boolean = true,
    val blockMobile: Boolean = true,
)

@Entity(tableName = "calls", indices = [Index(value = ["e164", "timestamp"], unique = true)])
data class CallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val e164: String?,
    val display: String,
    val timestamp: Long,
    val blocked: Boolean,
    /** Reason enum name, or "IMPORTED". */
    val reason: String,
    val detail: String = "",
    val region: String = "",
    /** Raw diagnostics: what the carrier / telecom stack supplied for this call. */
    val callerName: String = "",
    val verification: String = "",
    /** BlockAction name when blocked. */
    val action: String = "",
    /** "screened" or "import". */
    val source: String = "screened",
    /** Everything else Android handed the screening service for this call (diagnostics). */
    val extras: String = "",
    /** How the call ended: answered / missed / rejected / blocked, or empty if not known yet. */
    val outcome: String = "",
    /** Talk time in seconds (0 if not answered). */
    val durationSec: Int = 0,
)

@Dao
interface SpamDao {
    @Query("SELECT * FROM blocked ORDER BY addedAt DESC")
    fun observeBlocked(): Flow<List<BlockedEntity>>

    @Query("SELECT * FROM blocked")
    suspend fun blocked(): List<BlockedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlocked(e: BlockedEntity)

    @Query("DELETE FROM blocked WHERE e164 = :e164")
    suspend fun deleteBlocked(e164: String)

    @Query("UPDATE blocked SET action = :action WHERE e164 = :e164")
    suspend fun setBlockedAction(e164: String, action: String)

    @Query("SELECT * FROM allowed ORDER BY addedAt DESC")
    fun observeAllowed(): Flow<List<AllowedEntity>>

    @Query("SELECT * FROM allowed")
    suspend fun allowed(): List<AllowedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllowed(e: AllowedEntity)

    @Query("DELETE FROM allowed WHERE e164 = :e164")
    suspend fun deleteAllowed(e164: String)

    @Query("SELECT * FROM patterns ORDER BY id DESC")
    fun observePatterns(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM patterns")
    suspend fun patterns(): List<PatternEntity>

    @Insert
    suspend fun insertPattern(p: PatternEntity): Long

    @Query("UPDATE patterns SET enabled = :enabled WHERE id = :id")
    suspend fun setPatternEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM patterns WHERE id = :id")
    suspend fun deletePattern(id: Long)

    @Query("UPDATE patterns SET hits = hits + 1 WHERE id = :id")
    suspend fun bumpPattern(id: Long)

    @Query("SELECT * FROM regions")
    fun observeRegions(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM regions")
    suspend fun regions(): List<RegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegion(r: RegionEntity)

    @Query("DELETE FROM regions WHERE region = :region")
    suspend fun deleteRegion(region: String)

    @Query("SELECT * FROM calls ORDER BY timestamp DESC LIMIT 500")
    fun observeCalls(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCall(c: CallEntity): Long

    @Query("SELECT COUNT(*) FROM calls WHERE e164 = :e164 AND timestamp BETWEEN :from AND :to")
    suspend fun countNear(e164: String, from: Long, to: Long): Int

    @Query("SELECT * FROM calls ORDER BY timestamp ASC")
    suspend fun allCallsAscending(): List<CallEntity>

    @Query("SELECT * FROM calls WHERE e164 = :e164 AND timestamp BETWEEN :from AND :to LIMIT 1")
    suspend fun findNear(e164: String, from: Long, to: Long): CallEntity?

    @Query("UPDATE calls SET outcome = :outcome, durationSec = :durationSec WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: String, durationSec: Int)

    @Query("DELETE FROM calls")
    suspend fun clearCalls()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calls ADD COLUMN extras TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calls ADD COLUMN outcome TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calls ADD COLUMN durationSec INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [BlockedEntity::class, AllowedEntity::class, PatternEntity::class, RegionEntity::class, CallEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SpamDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "spamblocker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
