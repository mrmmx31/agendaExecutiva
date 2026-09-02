package com.pessoal.agenda.mobile.health

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthStoreTest {
    private lateinit var database: MobileDatabase
    private lateinit var store: HealthStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        val ids = ArrayDeque((1..40).map { "a2000000-0000-4000-8000-${it.toString().padStart(12, '0')}" })
        store = HealthStore(
            database = database,
            cipher = BoundTestCipher(),
            clock = Clock.fixed(Instant.parse("2026-09-02T15:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneId.of("America/Manaus"),
            newId = { ids.removeFirst() },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun catalogStartsWithEveryCategoryDisabled() = runBlocking {
        store.initializeConsentCatalog()

        val consents = store.consents()
        assertEquals(HealthCategory.entries.size, consents.size)
        assertTrue(consents.none { it.enabled })
        assertTrue(consents.all { it.foregroundOnly && it.grantedAt == null && it.revokedAt == null })
    }

    @Test(expected = IllegalArgumentException::class)
    fun manualEntryIsRejectedWithoutCategoryConsent() = runBlocking {
        store.initializeConsentCatalog()
        store.createIntake(fictitiousIntake())
        Unit
    }

    @Test
    fun intakeIsEncryptedCorrectedAndDeletedWithoutKeepingPayload() = runBlocking {
        store.initializeConsentCatalog()
        store.setConsent(HealthCategory.MEDICATION, true)

        val id = store.createIntake(fictitiousIntake())
        val stored = requireNotNull(database.offline().healthIntake(id))
        assertFalse(stored.ciphertext.contains("Item ficticio"))
        assertEquals("Item ficticio", store.intake(id)?.value?.name)

        store.updateIntake(id, fictitiousIntake().copy(name = "Item ficticio corrigido"))
        assertEquals(2L, store.intake(id)?.revision)
        assertEquals("Item ficticio corrigido", store.intake(id)?.value?.name)

        store.deleteIntake(id)
        val deleted = requireNotNull(database.offline().healthIntake(id))
        assertTrue(deleted.tombstone)
        assertEquals("", deleted.ciphertext)
        assertEquals("", deleted.iv)
        assertNull(store.intake(id))
        assertEquals(listOf("CREATED", "CORRECTED", "DELETED"), database.offline().healthAudit(id).map { it.action })
    }

    @Test
    fun symptomUsesIndependentConsentAndNullableIntensity() = runBlocking {
        store.initializeConsentCatalog()
        store.setConsent(HealthCategory.SYMPTOM, true)

        val id = store.createSymptom(
            SymptomInput("Evento ficticio", "2026-09-02T14:00:00Z"),
        )

        assertEquals("Evento ficticio", store.symptom(id)?.value?.label)
        assertNull(store.symptom(id)?.value?.intensity)
    }

    private fun fictitiousIntake() = IntakeInput(
        kind = IntakeKind.MEDICATION,
        name = "Item ficticio",
        occurredAt = "2026-09-02T14:00:00Z",
    )
}

private class BoundTestCipher : HealthDataCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = EncryptedHealthValue(
        ciphertext = Base64.getEncoder().encodeToString(plaintext.reversedArray()),
        iv = Base64.getEncoder().encodeToString(associatedData),
    )

    override fun decrypt(value: EncryptedHealthValue, associatedData: ByteArray): ByteArray {
        require(value.iv == Base64.getEncoder().encodeToString(associatedData))
        return Base64.getDecoder().decode(value.ciphertext).reversedArray()
    }
}
