package com.pessoal.agenda.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MobileMetadataEntity::class,
        TaskReplicaEntity::class,
        CaptureEntity::class,
        ProtocolTemplateEntity::class,
        ProtocolStepEntity::class,
        ProtocolRunEntity::class,
        ProtocolRunStepEntity::class,
        PendingOperationEntity::class,
        SyncConflictEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun offline(): OfflineDao

    companion object {
        const val DATABASE_NAME = "agenda-mobile.db"

        @Volatile private var instance: MobileDatabase? = null

        fun get(context: Context): MobileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MobileDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_replicas (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, status TEXT NOT NULL,
                        revision INTEGER NOT NULL, updatedAt TEXT NOT NULL, tombstone INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_task_replicas_status ON task_replicas(status)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS captures (
                        id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, createdAt TEXT NOT NULL,
                        organizationType TEXT, organizedEntityId TEXT, acknowledgedAt TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_captures_createdAt ON captures(createdAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_templates (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, revision INTEGER NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, tombstone INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_steps (
                        id TEXT NOT NULL PRIMARY KEY, protocolId TEXT NOT NULL, position INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        FOREIGN KEY(protocolId) REFERENCES protocol_templates(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_steps_protocolId ON protocol_steps(protocolId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_protocol_steps_protocolId_position ON protocol_steps(protocolId, position)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_runs (
                        id TEXT NOT NULL PRIMARY KEY, protocolId TEXT NOT NULL,
                        protocolRevision INTEGER NOT NULL, startedAt TEXT NOT NULL, completedAt TEXT,
                        FOREIGN KEY(protocolId) REFERENCES protocol_templates(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_runs_protocolId ON protocol_runs(protocolId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_runs_completedAt ON protocol_runs(completedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_run_steps (
                        runId TEXT NOT NULL, stepId TEXT NOT NULL, completedAt TEXT,
                        PRIMARY KEY(runId, stepId),
                        FOREIGN KEY(runId) REFERENCES protocol_runs(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(stepId) REFERENCES protocol_steps(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_run_steps_stepId ON protocol_run_steps(stepId)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_operations (
                        operationId TEXT NOT NULL PRIMARY KEY, deviceId TEXT NOT NULL,
                        sequence INTEGER NOT NULL, contractVersion INTEGER NOT NULL,
                        entityType TEXT NOT NULL, entityId TEXT NOT NULL, commandType TEXT NOT NULL,
                        occurredAt TEXT NOT NULL, timeZone TEXT NOT NULL, payloadJson TEXT NOT NULL,
                        payloadHash TEXT NOT NULL, baseRevision INTEGER, status TEXT NOT NULL,
                        errorCode TEXT, errorMessage TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_operations_deviceId_sequence ON pending_operations(deviceId, sequence)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_status ON pending_operations(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_entityType_entityId ON pending_operations(entityType, entityId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN serverRevision INTEGER")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN conflictId TEXT")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE pending_operations SET updatedAt=occurredAt WHERE updatedAt=''")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        conflictId TEXT NOT NULL PRIMARY KEY,
                        operationId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        baseRevision INTEGER,
                        serverRevision INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        localValueJson TEXT NOT NULL,
                        serverValueJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_conflicts_operationId ON sync_conflicts(operationId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_status ON sync_conflicts(status)")
            }
        }
    }
}
