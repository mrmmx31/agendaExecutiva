package com.pessoal.agenda.mobile.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobileDatabaseTest {
    private var database: MobileDatabase? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun metadataIsStoredByStableKey() = runBlocking {
        val metadata = requireNotNull(database).metadata()
        assertNull(metadata.find("contract_version"))

        metadata.save(
            MobileMetadataEntity(
                key = "contract_version",
                value = "1",
                updatedAt = "2026-08-31T00:00:00Z",
            ),
        )

        assertEquals("1", metadata.find("contract_version")?.value)
    }
}
