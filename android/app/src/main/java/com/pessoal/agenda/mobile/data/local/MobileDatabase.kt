package com.pessoal.agenda.mobile.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "mobile_metadata")
data class MobileMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: String,
)

@Dao
interface MobileMetadataDao {
    @Query("SELECT * FROM mobile_metadata WHERE `key` = :key")
    suspend fun find(key: String): MobileMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(metadata: MobileMetadataEntity)
}

@Database(
    entities = [MobileMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun metadata(): MobileMetadataDao

    companion object {
        const val DATABASE_NAME = "agenda-mobile.db"

        @Volatile private var instance: MobileDatabase? = null

        fun get(context: Context): MobileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MobileDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
