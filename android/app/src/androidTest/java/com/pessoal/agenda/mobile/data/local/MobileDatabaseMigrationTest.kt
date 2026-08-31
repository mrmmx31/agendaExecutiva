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

        helper.runMigrationsAndValidate(TEST_DATABASE, 2, true, MobileDatabase.MIGRATION_1_2).use { database ->
            database.query("SELECT value FROM mobile_metadata WHERE `key`='contract_version'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("1", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM pending_operations").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "agenda-mobile-migration-test.db"
    }
}
