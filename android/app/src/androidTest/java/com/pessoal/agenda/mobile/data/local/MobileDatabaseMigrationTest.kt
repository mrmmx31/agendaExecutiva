package com.pessoal.agenda.mobile.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MobileDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateFromMetadataOnlyDatabaseToOfflineCore() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                "INSERT INTO mobile_metadata (`key`, value, updatedAt) VALUES (?, ?, ?)",
                arrayOf("contract_version", "1", "2026-08-31T00:00:00Z"),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            MobileDatabase.MIGRATION_1_2,
            MobileDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT value FROM mobile_metadata WHERE `key`='contract_version'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("1", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM pending_operations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM sync_conflicts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migratePendingOperationToSyncStateMachine() {
        helper.createDatabase(QUEUE_DATABASE, 2).use { database ->
            database.execSQL("""
                INSERT INTO pending_operations(
                    operationId, deviceId, sequence, contractVersion, entityType, entityId,
                    commandType, occurredAt, timeZone, payloadJson, payloadHash,
                    baseRevision, status, errorCode, errorMessage
                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(), arrayOf(
                "10000000-0000-4000-8000-000000000001",
                "10000000-0000-4000-8000-000000000002",
                1, 1, "capture", "10000000-0000-4000-8000-000000000003",
                "CAPTURE_CREATED", "2026-09-01T12:00:00Z", "America/Manaus", "{}",
                "a".repeat(64), null, "PENDING", null, null,
            ))
        }

        helper.runMigrationsAndValidate(
            QUEUE_DATABASE, 3, true, MobileDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT status, attemptCount, updatedAt FROM pending_operations").use { cursor ->
                cursor.moveToFirst()
                assertEquals("PENDING", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("2026-09-01T12:00:00Z", cursor.getString(2))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "agenda-mobile-migration-test.db"
        const val QUEUE_DATABASE = "agenda-mobile-queue-migration-test.db"
    }
}
