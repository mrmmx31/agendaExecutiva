package com.pessoal.agenda.mobile.health

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.health.connect.HealthConnectGateway
import com.pessoal.agenda.mobile.health.connect.HealthConnectImportCoordinator
import com.pessoal.agenda.mobile.health.connect.HealthConnectStatus
import com.pessoal.agenda.mobile.health.connect.ImportedHealthSummary
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
        assertEquals(listOf(id), store.intakes().map { it.id })

        store.updateIntake(id, fictitiousIntake().copy(name = "Item ficticio corrigido"))
        assertEquals(2L, store.intake(id)?.revision)
        assertEquals("Item ficticio corrigido", store.intake(id)?.value?.name)

        store.deleteIntake(id)
        val deleted = requireNotNull(database.offline().healthIntake(id))
        assertTrue(deleted.tombstone)
        assertEquals("", deleted.ciphertext)
        assertEquals("", deleted.iv)
        assertNull(store.intake(id))
        assertTrue(store.intakes().isEmpty())
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

    @Test
    fun routineNoteRequiresItsOwnConsent() = runBlocking {
        store.initializeConsentCatalog()
        store.setConsent(HealthCategory.ROUTINE_NOTE, true)

        val id = store.createSymptom(
            SymptomInput("Observacao ficticia", "2026-09-02T14:00:00Z", kind = SubjectiveKind.ROUTINE_NOTE),
        )

        assertEquals(SubjectiveKind.ROUTINE_NOTE, store.symptom(id)?.value?.kind)
    }

    @Test
    fun healthSummaryPreservesCoverageAndSourcesInsideCiphertext() = runBlocking {
        store.initializeConsentCatalog()
        store.setConsent(HealthCategory.HEART_RATE, true)
        val consent = requireNotNull(store.consents().single { it.category == HealthCategory.HEART_RATE.name })
        val summary = HealthSummary(
            id = "a3000000-0000-4000-8000-000000000001",
            consentId = consent.id,
            category = HealthCategory.HEART_RATE,
            periodStart = "2026-08-26T12:00:00Z",
            periodEnd = "2026-09-02T12:00:00Z",
            coverageStart = "2026-09-01T10:00:00Z",
            coverageEnd = "2026-09-01T10:05:00Z",
            sampleCount = 2,
            metrics = listOf(HealthMetric(HealthMetricName.AVERAGE_BPM, 72.5, "bpm")),
            sourcePackages = listOf("com.example.fixture"),
            missingReason = null,
            importedAt = "2026-09-02T12:00:00Z",
        )

        store.saveHealthSummary(summary)

        val stored = database.offline().healthSummaries().single()
        assertFalse(stored.ciphertext.contains("72.5"))
        assertEquals(summary, store.healthSummaries().single())
    }

    @Test
    fun importerReadsOnlyEnabledImportableCategoriesAndPreservesNoData() = runBlocking {
        store.initializeConsentCatalog()
        store.setConsent(HealthCategory.HEART_RATE, true)
        store.setConsent(HealthCategory.MEDICATION, true)
        val gateway = FakeHealthConnectGateway()
        val importer = HealthConnectImportCoordinator(
            gateway, store,
            Clock.fixed(Instant.parse("2026-09-02T15:00:00Z"), ZoneOffset.UTC),
            newId = { "a4000000-0000-4000-8000-000000000001" },
        )

        assertEquals(1, importer.importEnabled())

        assertEquals(setOf(HealthCategory.HEART_RATE), gateway.requested)
        val summary = store.healthSummaries().single()
        assertEquals(0, summary.sampleCount)
        assertTrue(summary.metrics.isEmpty())
        assertEquals(HealthMissingReason.NO_DATA, summary.missingReason)
        assertEquals("2026-08-26T15:00:00Z", summary.periodStart)
    }

    private fun fictitiousIntake() = IntakeInput(
        kind = IntakeKind.MEDICATION,
        name = "Item ficticio",
        occurredAt = "2026-09-02T14:00:00Z",
    )
}

private class FakeHealthConnectGateway : HealthConnectGateway {
    var requested: Set<HealthCategory> = emptySet()
    override fun status() = HealthConnectStatus.AVAILABLE
    override fun permissionsFor(categories: Set<HealthCategory>) = categories.mapTo(linkedSetOf()) { "read:${it.name}" }
    override suspend fun grantedPermissions() = setOf("read:HEART_RATE")
    override suspend fun readSummaries(categories: Set<HealthCategory>, start: Instant, end: Instant): List<ImportedHealthSummary> {
        requested = categories
        return categories.map {
            ImportedHealthSummary(it, start, end, null, null, 0, emptyList(), emptyList(), HealthMissingReason.NO_DATA)
        }
    }
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
