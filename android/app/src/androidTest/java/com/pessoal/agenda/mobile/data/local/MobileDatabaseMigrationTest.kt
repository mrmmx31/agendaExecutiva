package com.pessoal.agenda.mobile.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MobileDatabase::class.java,
    )

    @Before
    fun ensureDatabaseDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.getDatabasePath(TEST_DATABASE).parentFile?.let { it.exists() || it.mkdirs() } == true)
    }

    @Test
    fun migrateMetadataDatabaseThroughDurableAlerts() {
        helper.createDatabase(TEST_DATABASE, 1).use { database ->
            database.execSQL(
                "INSERT INTO mobile_metadata (`key`, value, updatedAt) VALUES (?, ?, ?)",
                arrayOf("contract_version", "1", "2026-08-31T00:00:00Z"),
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            10,
            true,
            MobileDatabase.MIGRATION_1_2,
            MobileDatabase.MIGRATION_2_3,
            MobileDatabase.MIGRATION_3_4,
            MobileDatabase.MIGRATION_4_5,
            MobileDatabase.MIGRATION_5_6,
            MobileDatabase.MIGRATION_6_7,
            MobileDatabase.MIGRATION_7_8,
            MobileDatabase.MIGRATION_8_9,
            MobileDatabase.MIGRATION_9_10,
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
            QUEUE_DATABASE, 10, true, MobileDatabase.MIGRATION_2_3,
            MobileDatabase.MIGRATION_3_4, MobileDatabase.MIGRATION_4_5, MobileDatabase.MIGRATION_5_6,
            MobileDatabase.MIGRATION_6_7,
            MobileDatabase.MIGRATION_7_8,
            MobileDatabase.MIGRATION_8_9,
            MobileDatabase.MIGRATION_9_10,
        ).use { database ->
            database.query("SELECT status, attemptCount, updatedAt FROM pending_operations").use { cursor ->
                cursor.moveToFirst()
                assertEquals("PENDING", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals("2026-09-01T12:00:00Z", cursor.getString(2))
            }
        }
    }

    @Test
    fun migrateSyncDatabaseToDurableAlerts() {
        helper.createDatabase(ALERT_DATABASE, 3).close()

        helper.runMigrationsAndValidate(
            ALERT_DATABASE, 10, true, MobileDatabase.MIGRATION_3_4, MobileDatabase.MIGRATION_4_5,
            MobileDatabase.MIGRATION_5_6, MobileDatabase.MIGRATION_6_7, MobileDatabase.MIGRATION_7_8,
            MobileDatabase.MIGRATION_8_9,
            MobileDatabase.MIGRATION_9_10,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM alert_definitions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM sensory_profiles").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("PRAGMA table_info(alert_materializations)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) found = found || cursor.getString(nameIndex) == "wearRevision"
                assertEquals(true, found)
            }
            database.query("SELECT wearRevision, acknowledgedWearOperationId FROM protocol_runs LIMIT 0").close()
            database.query("SELECT COUNT(*) FROM health_consents").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT ciphertext, iv, revision, tombstone FROM health_intake_logs LIMIT 0").close()
            database.query("SELECT category, ciphertext, iv FROM health_summaries LIMIT 0").close()
            database.query("SELECT eventType, occurredAt, correctedAt FROM recommendation_events LIMIT 0").close()
            database.query("SELECT purpose, optionsJson FROM recommendation_decisions LIMIT 0").close()
            database.query("SELECT personalizationEnabled, retentionDays FROM recommendation_settings LIMIT 0").close()
        }
    }

    @Test
    fun migrateHealthDatabaseToLocalRecommendationStorage() {
        helper.createDatabase(RECOMMENDATION_DATABASE, 8).use { database ->
            database.execSQL(
                "INSERT INTO mobile_metadata (`key`, value, updatedAt) VALUES (?, ?, ?)",
                arrayOf("fixture", "preserved", "2026-09-02T12:00:00Z"),
            )
        }

        helper.runMigrationsAndValidate(
            RECOMMENDATION_DATABASE, 10, true, MobileDatabase.MIGRATION_8_9,
            MobileDatabase.MIGRATION_9_10,
        ).use { database ->
            database.query("SELECT value FROM mobile_metadata WHERE `key`='fixture'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("preserved", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM recommendation_events").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM recommendation_decisions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM recommendation_settings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM personal_model_artifacts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM personal_model_shadow_metrics").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrateRecommendationStorageToModelArtifacts() {
        helper.createDatabase(MODEL_DATABASE, 9).use { database ->
            database.execSQL(
                "INSERT INTO recommendation_settings(id, personalizationEnabled, retentionDays, capacityContext, preferredSnoozeMinutes, preferredChannel, updatedAt) VALUES(?,?,?,?,?,?,?)",
                arrayOf("installation", 1, 90, "STANDARD", null, null, "2026-09-03T12:00:00Z"),
            )
        }

        helper.runMigrationsAndValidate(
            MODEL_DATABASE, 10, true, MobileDatabase.MIGRATION_9_10,
        ).use { database ->
            database.query("SELECT personalizationEnabled FROM recommendation_settings WHERE id='installation'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT artifactJson, artifactSha256, status FROM personal_model_artifacts LIMIT 0").close()
            database.query("SELECT evaluatedCount, agreementCount FROM personal_model_shadow_metrics LIMIT 0").close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "agenda-mobile-migration-test.db"
        const val QUEUE_DATABASE = "agenda-mobile-queue-migration-test.db"
        const val ALERT_DATABASE = "agenda-mobile-alert-migration-test.db"
        const val RECOMMENDATION_DATABASE = "agenda-mobile-recommendation-migration-test.db"
        const val MODEL_DATABASE = "agenda-mobile-model-migration-test.db"
    }
}
