package com.pessoal.agenda.wear.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WearAlertEntity::class,
        WearActionOutboxEntity::class,
        WearProtocolStateEntity::class,
        WearProtocolActionOutboxEntity::class,
    ],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wear_protocol_states (
                        runId TEXT NOT NULL PRIMARY KEY, protocolId TEXT NOT NULL,
                        revision INTEGER NOT NULL, protocolTitle TEXT NOT NULL,
                        stepId TEXT, stepLabel TEXT, stepPosition INTEGER,
                        stepCount INTEGER NOT NULL, updatedAt TEXT NOT NULL,
                        status TEXT NOT NULL, acknowledgedOperationId TEXT,
                        localFeedback INTEGER NOT NULL, localActionPending INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wear_protocol_states_status ON wear_protocol_states(status)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wear_protocol_action_outbox (
                        operationId TEXT NOT NULL PRIMARY KEY, runId TEXT NOT NULL,
                        payload BLOB NOT NULL, state TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL, createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wear_protocol_action_outbox_runId ON wear_protocol_action_outbox(runId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wear_protocol_action_outbox_state ON wear_protocol_action_outbox(state)")
            }
        }
    }
}
