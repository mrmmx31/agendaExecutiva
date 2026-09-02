package com.pessoal.agenda.wear.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WearAlertEntity::class, WearActionOutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WearDatabase : RoomDatabase() {
    abstract fun wear(): WearDao

    companion object {
        const val DATABASE_NAME = "agenda-wear.db"

        @Volatile private var instance: WearDatabase? = null

        fun get(context: Context): WearDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WearDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
