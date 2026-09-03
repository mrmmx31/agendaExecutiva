package com.pessoal.agenda.mobile.health.report

import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.HealthSummary
import com.pessoal.agenda.mobile.health.VersionedHealthRecord
import com.pessoal.agenda.mobile.health.IntakeInput
import com.pessoal.agenda.mobile.health.SubjectiveKind
import com.pessoal.agenda.mobile.health.SymptomInput
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class HealthReportEntryKind { SENSOR_AGGREGATE, RECORDED_FACT, USER_OBSERVATION }

@Serializable
data class HealthReportPermission(
    val category: String,
    val enabled: Boolean,
    @SerialName("foreground_only") val foregroundOnly: Boolean,
    @SerialName("retention_days") val retentionDays: Int,
)

@Serializable
data class HealthReportEntry(
    val id: String,
    val kind: HealthReportEntryKind,
    val category: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    val title: String,
    val details: Map<String, String> = emptyMap(),
    val sources: List<String> = emptyList(),
)

@Serializable
data class HealthReportSnapshot(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("time_zone") val timeZone: String,
    @SerialName("subject_label") val subjectLabel: String,
    @SerialName("selected_categories") val selectedCategories: List<String>,
    val permissions: List<HealthReportPermission>,
    val sources: List<String>,
    val limitations: List<String>,
    @SerialName("excluded_entry_count") val excludedEntryCount: Int = 0,
    val entries: List<HealthReportEntry>,
)

data class HealthReportReview(
    val snapshot: HealthReportSnapshot? = null,
    val subjectLabel: String = "",
    val excludedEntryIds: Set<String> = emptySet(),
) {
    fun reviewedSnapshot(): HealthReportSnapshot? = snapshot?.copy(
        subjectLabel = subjectLabel.trim(),
        excludedEntryCount = excludedEntryIds.size,
        entries = snapshot.entries.filterNot { it.id in excludedEntryIds },
    )
}

class HealthReportBuilder(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun build(
        days: Int,
        categories: Set<HealthCategory>,
        subjectLabel: String,
        consents: List<HealthConsentEntity>,
        summaries: List<HealthSummary>,
        intakes: List<VersionedHealthRecord<IntakeInput>>,
        symptoms: List<VersionedHealthRecord<SymptomInput>>,
    ): HealthReportSnapshot {
        require(days in 1..365)
        require(categories.isNotEmpty())
        require(subjectLabel.length <= 120)
        val end = Instant.now(clock)
        val start = end.minusSeconds(days * 86_400L)
        val entries = buildList {
            summaries.filter { it.category in categories && overlaps(it.periodStart, it.periodEnd, start, end) }
                .forEach { add(it.toEntry()) }
            intakes.filter { inputCategory(it.value) in categories && inPeriod(it.value.occurredAt, start, end) }
                .forEach { add(it.toIntakeEntry()) }
            symptoms.filter { symptomCategory(it.value) in categories && inPeriod(it.value.occurredAt, start, end) }
                .forEach { add(it.toSymptomEntry()) }
        }.sortedWith(compareBy<HealthReportEntry> { it.startAt }.thenBy { it.id })
        val selected = categories.sortedBy(HealthCategory::ordinal)
        val permissions = selected.map { category ->
            val consent = requireNotNull(consents.firstOrNull { it.category == category.name }) {
                "Categoria ausente do catálogo de consentimentos."
            }
            HealthReportPermission(category.name, consent.enabled, consent.foregroundOnly, consent.retentionDays)
        }
        return HealthReportSnapshot(
            snapshotId = newId().also(UUID::fromString), generatedAt = end.toString(),
            periodStart = start.toString(), periodEnd = end.toString(), timeZone = zoneId.id,
            subjectLabel = subjectLabel.trim(), selectedCategories = selected.map { it.name },
            permissions = permissions,
            sources = entries.flatMap { it.sources }.distinct().sorted(),
            limitations = LIMITATIONS,
            entries = entries,
        )
    }

    private fun HealthSummary.toEntry(): HealthReportEntry {
        val detail = linkedMapOf("sample_count" to sampleCount.toString())
        coverageStart?.let { detail["coverage_start"] = it }
        coverageEnd?.let { detail["coverage_end"] = it }
        missingReason?.let { detail["missing_reason"] = it.name }
        metrics.forEach { detail[it.name.name.lowercase()] = "${it.value} ${it.unit}" }
        return HealthReportEntry(id, HealthReportEntryKind.SENSOR_AGGREGATE, category.name, periodStart, periodEnd, category.label(), detail, sourcePackages)
    }

    private fun VersionedHealthRecord<IntakeInput>.toIntakeEntry(): HealthReportEntry {
        val detail = linkedMapOf<String, String>()
        value.amount?.let { detail["amount"] = it.toString() }
        value.unit?.let { detail["unit"] = it }
        value.plannedAt?.let { detail["planned_at"] = it }
        value.context?.let { detail["context"] = it }
        value.perceivedEffect?.let { detail["perceived_effect"] = it }
        value.note?.let { detail["note"] = it }
        return HealthReportEntry(id, HealthReportEntryKind.RECORDED_FACT, inputCategory(value).name, value.occurredAt, title = value.name, details = detail, sources = listOf("MANUAL"))
    }

    private fun VersionedHealthRecord<SymptomInput>.toSymptomEntry(): HealthReportEntry {
        val detail = linkedMapOf<String, String>()
        value.intensity?.let { detail["subjective_intensity"] = it.toString() }
        value.note?.let { detail["note"] = it }
        return HealthReportEntry(id, HealthReportEntryKind.USER_OBSERVATION, symptomCategory(value).name, value.occurredAt, title = value.label, details = detail, sources = listOf("MANUAL"))
    }

    private fun inPeriod(value: String, start: Instant, end: Instant) = Instant.parse(value) in start..end
    private fun overlaps(from: String, to: String, start: Instant, end: Instant) = Instant.parse(from) <= end && Instant.parse(to) >= start

    companion object {
        val LIMITATIONS = listOf(
            "Relatório informativo baseado somente nos registros selecionados.",
            "Lacunas de dados não representam valor zero.",
            "O conteúdo não estabelece causalidade, diagnóstico ou orientação terapêutica.",
            "Revise o conteúdo antes de compartilhar com um profissional.",
        )
    }
}

private fun inputCategory(value: IntakeInput) = if (value.kind.name == "MEDICATION") HealthCategory.MEDICATION else HealthCategory.SUBSTANCE
private fun symptomCategory(value: SymptomInput) = if (value.kind == SubjectiveKind.ROUTINE_NOTE) HealthCategory.ROUTINE_NOTE else HealthCategory.SYMPTOM
private fun HealthCategory.label() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
