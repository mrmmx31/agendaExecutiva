package com.pessoal.agenda.wear.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), WearDatabase::class.java,
    )

    @Test
    fun migrateAddsProtocolStateAndOutboxWithoutDroppingAlerts() {
        helper.createDatabase(DATABASE, 1).close()
        helper.runMigrationsAndValidate(DATABASE, 2, true, WearDatabase.MIGRATION_1_2).use { database ->
            database.query("SELECT COUNT(*) FROM wear_protocol_states").close()
            database.query("SELECT COUNT(*) FROM wear_protocol_action_outbox").close()
            database.query("SELECT COUNT(*) FROM wear_alerts").close()
        }
    }

    private companion object { const val DATABASE = "agenda-wear-migration-test.db" }
}
