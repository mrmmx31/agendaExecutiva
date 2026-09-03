package com.pessoal.agenda.mobile.health.report

import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.HealthMetric
import com.pessoal.agenda.mobile.health.HealthMetricName
import com.pessoal.agenda.mobile.health.HealthSummary
import com.pessoal.agenda.mobile.health.IntakeInput
import com.pessoal.agenda.mobile.health.IntakeKind
import com.pessoal.agenda.mobile.health.SymptomInput
import com.pessoal.agenda.mobile.health.VersionedHealthRecord
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthReportTest {
    private val builder = HealthReportBuilder(
        Clock.fixed(Instant.parse("2026-09-02T15:00:00Z"), ZoneOffset.UTC),
        ZoneId.of("America/Manaus"),
        newId = { "b5000000-0000-4000-8000-000000000001" },
    )

    @Test
    fun snapshotFiltersPeriodAndSeparatesKinds() {
        val snapshot = builder.build(
            days = 7,
            categories = setOf(HealthCategory.HEART_RATE, HealthCategory.MEDICATION, HealthCategory.SYMPTOM),
            subjectLabel = "Pessoa fictícia",
            consents = listOf(consent(HealthCategory.HEART_RATE), consent(HealthCategory.MEDICATION), consent(HealthCategory.SYMPTOM)),
            summaries = listOf(summary()),
            intakes = listOf(
                VersionedHealthRecord("b5000000-0000-4000-8000-000000000003", 1, IntakeInput(IntakeKind.MEDICATION, "Item fictício", occurredAt = "2026-09-01T12:00:00Z")),
                VersionedHealthRecord("b5000000-0000-4000-8000-000000000004", 1, IntakeInput(IntakeKind.MEDICATION, "Antigo", occurredAt = "2026-01-01T12:00:00Z")),
            ),
            symptoms = listOf(VersionedHealthRecord("b5000000-0000-4000-8000-000000000005", 1, SymptomInput("Evento fictício", "2026-09-02T12:00:00Z", 3))),
        )

        assertEquals("2026-08-26T15:00:00Z", snapshot.periodStart)
        assertEquals(3, snapshot.entries.size)
        assertEquals(HealthReportEntryKind.SENSOR_AGGREGATE, snapshot.entries[0].kind)
        assertTrue(snapshot.entries.any { it.kind == HealthReportEntryKind.RECORDED_FACT })
        assertTrue(snapshot.entries.any { it.kind == HealthReportEntryKind.USER_OBSERVATION })
        assertFalse(snapshot.entries.any { it.title == "Antigo" })
        assertEquals(listOf("MANUAL", "com.example.fixture"), snapshot.sources)
    }

    @Test
    fun reviewAndExportsUseSameReducedSnapshot() {
        val original = builder.build(
            7, setOf(HealthCategory.SYMPTOM), "",
            listOf(consent(HealthCategory.SYMPTOM)), emptyList(), emptyList(),
            listOf(
                VersionedHealthRecord("b5000000-0000-4000-8000-000000000006", 1, SymptomInput("=Fórmula fictícia", "2026-09-01T12:00:00Z")),
                VersionedHealthRecord("b5000000-0000-4000-8000-000000000007", 1, SymptomInput("Excluir", "2026-09-02T12:00:00Z")),
            ),
        )
        val reviewed = HealthReportReview(original, "Identificação corrigida", setOf("b5000000-0000-4000-8000-000000000007")).reviewedSnapshot()!!

        assertEquals(1, reviewed.entries.size)
        assertEquals(1, reviewed.excludedEntryCount)
        val json = HealthReportExporter.export(reviewed, HealthReportFormat.JSON).decodeToString()
        val csv = HealthReportExporter.export(reviewed, HealthReportFormat.CSV).decodeToString()
        assertTrue(json.contains("Identificação corrigida"))
        assertTrue(csv.contains("'="))
        assertFalse(csv.contains("Excluir"))
    }

    private fun consent(category: HealthCategory) = HealthConsentEntity(
        "b5000000-0000-4000-8000-${category.ordinal.toString().padStart(12, '0')}", category.name,
        "USER_REVIEWABLE_REPORT", true, true, 365, "2026-09-01T00:00:00Z", null, "2026-09-01T00:00:00Z",
    )

    private fun summary() = HealthSummary(
        "b5000000-0000-4000-8000-000000000002", "b5000000-0000-4000-8000-000000000000",
        HealthCategory.HEART_RATE, "2026-08-30T12:00:00Z", "2026-09-01T12:00:00Z",
        "2026-08-30T13:00:00Z", "2026-09-01T11:00:00Z", 2,
        listOf(HealthMetric(HealthMetricName.AVERAGE_BPM, 72.0, "bpm")),
        listOf("com.example.fixture"), null, "2026-09-02T12:00:00Z",
    )
}
