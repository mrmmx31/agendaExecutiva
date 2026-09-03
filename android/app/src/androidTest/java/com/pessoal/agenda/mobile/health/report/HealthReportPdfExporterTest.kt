package com.pessoal.agenda.mobile.health.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthReportPdfExporterTest {
    @Test
    fun exportsReadablePdfOnAndroid() {
        val snapshot = HealthReportSnapshot(
            snapshotId = "b5000000-0000-4000-8000-000000000001",
            generatedAt = "2026-09-02T15:00:00Z",
            periodStart = "2026-08-26T15:00:00Z",
            periodEnd = "2026-09-02T15:00:00Z",
            timeZone = "America/Manaus",
            subjectLabel = "Pessoa fictícia",
            selectedCategories = listOf("SYMPTOM"),
            permissions = listOf(HealthReportPermission("SYMPTOM", true, true, 365)),
            sources = listOf("MANUAL"),
            limitations = HealthReportBuilder.LIMITATIONS,
            entries = listOf(
                HealthReportEntry(
                    "b5000000-0000-4000-8000-000000000002",
                    HealthReportEntryKind.USER_OBSERVATION,
                    "SYMPTOM", "2026-09-01T14:00:00Z", title = "Evento fictício",
                    details = mapOf("subjective_intensity" to "3"), sources = listOf("MANUAL"),
                ),
            ),
        )

        val pdf = HealthReportExporter.export(snapshot, HealthReportFormat.PDF)

        assertTrue(pdf.size > 500)
        assertTrue(pdf.copyOfRange(0, 4).decodeToString() == "%PDF")
    }
}
