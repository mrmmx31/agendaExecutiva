package com.pessoal.agenda.mobile.health

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.HealthChangeAuditEntity
import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
import com.pessoal.agenda.mobile.data.local.HealthIntakeLogEntity
import com.pessoal.agenda.mobile.data.local.HealthSymptomLogEntity
import com.pessoal.agenda.mobile.data.local.HealthSummaryEntity
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class HealthCategory {
    HEART_RATE, RESTING_HEART_RATE, SLEEP, ACTIVITY,
    MEDICATION, SUBSTANCE, SYMPTOM, ROUTINE_NOTE,
}

enum class IntakeKind { MEDICATION, SUBSTANCE }
enum class SubjectiveKind { SYMPTOM, ROUTINE_NOTE }

data class IntakeInput(
    val kind: IntakeKind,
    val name: String,
    val amount: Double? = null,
    val unit: String? = null,
    val plannedAt: String? = null,
    val occurredAt: String,
    val context: String? = null,
    val perceivedEffect: String? = null,
    val note: String? = null,
)

data class SymptomInput(
    val label: String,
    val occurredAt: String,
    val intensity: Int? = null,
    val note: String? = null,
    val kind: SubjectiveKind = SubjectiveKind.SYMPTOM,
)

data class VersionedHealthRecord<T>(val id: String, val revision: Long, val value: T)

enum class HealthMetricName { AVERAGE_BPM, MINIMUM_BPM, MAXIMUM_BPM, SLEEP_MINUTES, STEPS }
enum class HealthMissingReason { NO_DATA, PERMISSION_REVOKED, UNAVAILABLE }

@Serializable
data class HealthMetric(
    val name: HealthMetricName,
    val value: Double,
    val unit: String,
)

data class HealthSummary(
    val id: String,
    val consentId: String,
    val category: HealthCategory,
    val periodStart: String,
    val periodEnd: String,
    val coverageStart: String?,
    val coverageEnd: String?,
    val sampleCount: Int,
    val metrics: List<HealthMetric>,
    val sourcePackages: List<String>,
    val missingReason: HealthMissingReason?,
    val importedAt: String,
)

class HealthStore(
    private val database: MobileDatabase,
    private val cipher: HealthDataCipher,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.offline()
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    suspend fun initializeConsentCatalog() = database.withTransaction {
        val now = now()
        HealthCategory.entries.forEach { category ->
            dao.insertHealthConsent(
                HealthConsentEntity(
                    id = validId(), category = category.name, purpose = PURPOSE,
                    enabled = false, foregroundOnly = true, retentionDays = DEFAULT_RETENTION_DAYS,
                    grantedAt = null, revokedAt = null, updatedAt = now,
                ),
            )
        }
    }

    suspend fun setConsent(
        category: HealthCategory,
        enabled: Boolean,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
        foregroundOnly: Boolean = true,
    ) = database.withTransaction {
        require(retentionDays in 1..MAX_RETENTION_DAYS)
        val now = now()
        val current = dao.healthConsent(category.name)
        dao.upsertHealthConsent(
            HealthConsentEntity(
                id = current?.id ?: validId(),
                category = category.name,
                purpose = PURPOSE,
                enabled = enabled,
                foregroundOnly = foregroundOnly,
                retentionDays = retentionDays,
                grantedAt = if (enabled) now else current?.grantedAt,
                revokedAt = if (!enabled && current?.enabled == true) now else null,
                updatedAt = now,
            ),
        )
    }

    suspend fun revokeImportableConsents(categories: Set<HealthCategory>) = database.withTransaction {
        val now = now()
        categories.forEach { category ->
            val current = dao.healthConsent(category.name) ?: return@forEach
            dao.upsertHealthConsent(
                current.copy(
                    enabled = false,
                    revokedAt = if (current.enabled) now else current.revokedAt,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun consents(): List<HealthConsentEntity> = dao.healthConsents()

    suspend fun createIntake(input: IntakeInput): String = database.withTransaction {
        validate(input)
        requireConsent(if (input.kind == IntakeKind.MEDICATION) HealthCategory.MEDICATION else HealthCategory.SUBSTANCE)
        val id = validId()
        writeIntake(id, 1, input, "CREATED")
        id
    }

    suspend fun updateIntake(id: String, input: IntakeInput) = database.withTransaction {
        validate(input)
        requireConsent(if (input.kind == IntakeKind.MEDICATION) HealthCategory.MEDICATION else HealthCategory.SUBSTANCE)
        val current = requireNotNull(dao.healthIntake(id)).also { require(!it.tombstone) }
        writeIntake(id, current.revision + 1, input, "CORRECTED")
    }

    suspend fun intake(id: String): VersionedHealthRecord<IntakeInput>? {
        val row = dao.healthIntake(id) ?: return null
        if (row.tombstone) return null
        val payload = decrypt<IntakePayload>(row.id, row.revision, row.ciphertext, row.iv)
        return VersionedHealthRecord(id, row.revision, payload.toInput())
    }

    suspend fun intakes(): List<VersionedHealthRecord<IntakeInput>> = dao.healthIntakes().map { row ->
        val payload = decrypt<IntakePayload>(row.id, row.revision, row.ciphertext, row.iv)
        VersionedHealthRecord(row.id, row.revision, payload.toInput())
    }

    suspend fun deleteIntake(id: String) = database.withTransaction {
        val current = requireNotNull(dao.healthIntake(id))
        if (current.tombstone) return@withTransaction
        val revision = current.revision + 1
        val now = now()
        check(dao.tombstoneHealthIntake(id, revision, now) == 1)
        audit("INTAKE", id, revision, "DELETED", now)
    }

    suspend fun createSymptom(input: SymptomInput): String = database.withTransaction {
        validate(input)
        requireConsent(if (input.kind == SubjectiveKind.SYMPTOM) HealthCategory.SYMPTOM else HealthCategory.ROUTINE_NOTE)
        val id = validId()
        writeSymptom(id, 1, input, "CREATED")
        id
    }

    suspend fun updateSymptom(id: String, input: SymptomInput) = database.withTransaction {
        validate(input)
        requireConsent(if (input.kind == SubjectiveKind.SYMPTOM) HealthCategory.SYMPTOM else HealthCategory.ROUTINE_NOTE)
        val current = requireNotNull(dao.healthSymptom(id)).also { require(!it.tombstone) }
        writeSymptom(id, current.revision + 1, input, "CORRECTED")
    }

    suspend fun symptom(id: String): VersionedHealthRecord<SymptomInput>? {
        val row = dao.healthSymptom(id) ?: return null
        if (row.tombstone) return null
        val payload = decrypt<SymptomPayload>(row.id, row.revision, row.ciphertext, row.iv)
        return VersionedHealthRecord(id, row.revision, payload.toInput())
    }

    suspend fun symptoms(): List<VersionedHealthRecord<SymptomInput>> = dao.healthSymptoms().map { row ->
        val payload = decrypt<SymptomPayload>(row.id, row.revision, row.ciphertext, row.iv)
        VersionedHealthRecord(row.id, row.revision, payload.toInput())
    }

    suspend fun saveHealthSummary(summary: HealthSummary): String = database.withTransaction {
        require(summary.category in IMPORTABLE_CATEGORIES)
        val consent = requireNotNull(dao.healthConsent(summary.category.name))
        require(consent.enabled && consent.id == summary.consentId)
        validate(summary)
        val id = summary.id.also(UUID::fromString)
        val revision = 1L
        val encrypted = encrypt(id, revision, HealthSummaryPayload.from(summary))
        dao.upsertHealthSummary(
            HealthSummaryEntity(
                id = id, consentId = consent.id, category = summary.category.name,
                ciphertext = encrypted.ciphertext, iv = encrypted.iv,
                revision = revision, importedAt = summary.importedAt,
            ),
        )
        id
    }

    suspend fun healthSummaries(): List<HealthSummary> = dao.healthSummaries().map { row ->
        decrypt<HealthSummaryPayload>(row.id, row.revision, row.ciphertext, row.iv).toSummary()
    }

    suspend fun enforceRetention() = database.withTransaction {
        val instant = Instant.now(clock)
        val now = instant.toString()
        val retention = dao.healthConsents().associate { HealthCategory.valueOf(it.category) to it.retentionDays }
        dao.healthIntakes().forEach { row ->
            val input = decrypt<IntakePayload>(row.id, row.revision, row.ciphertext, row.iv).toInput()
            val category = if (input.kind == IntakeKind.MEDICATION) HealthCategory.MEDICATION else HealthCategory.SUBSTANCE
            if (Instant.parse(input.occurredAt) < instant.minusSeconds(requireNotNull(retention[category]) * 86_400L)) {
                val revision = row.revision + 1
                check(dao.tombstoneHealthIntake(row.id, revision, now) == 1)
                audit("INTAKE", row.id, revision, "EXPIRED", now)
            }
        }
        dao.healthSymptoms().forEach { row ->
            val input = decrypt<SymptomPayload>(row.id, row.revision, row.ciphertext, row.iv).toInput()
            val category = if (input.kind == SubjectiveKind.ROUTINE_NOTE) HealthCategory.ROUTINE_NOTE else HealthCategory.SYMPTOM
            if (Instant.parse(input.occurredAt) < instant.minusSeconds(requireNotNull(retention[category]) * 86_400L)) {
                val revision = row.revision + 1
                check(dao.tombstoneHealthSymptom(row.id, revision, now) == 1)
                audit("SYMPTOM", row.id, revision, "EXPIRED", now)
            }
        }
        dao.healthSummaries().forEach { row ->
            val summary = decrypt<HealthSummaryPayload>(row.id, row.revision, row.ciphertext, row.iv).toSummary()
            val cutoff = instant.minusSeconds(requireNotNull(retention[summary.category]) * 86_400L)
            if (Instant.parse(summary.periodEnd) < cutoff) {
                check(dao.deleteHealthSummary(row.id) == 1)
                audit("SUMMARY", row.id, row.revision, "EXPIRED", now)
            }
        }
    }

    suspend fun deleteSymptom(id: String) = database.withTransaction {
        val current = requireNotNull(dao.healthSymptom(id))
        if (current.tombstone) return@withTransaction
        val revision = current.revision + 1
        val now = now()
        check(dao.tombstoneHealthSymptom(id, revision, now) == 1)
        audit("SYMPTOM", id, revision, "DELETED", now)
    }

    private suspend fun writeIntake(id: String, revision: Long, input: IntakeInput, action: String) {
        val now = now()
        val encrypted = encrypt(id, revision, IntakePayload.from(input, zoneId.id, now))
        dao.upsertHealthIntake(HealthIntakeLogEntity(id, encrypted.ciphertext, encrypted.iv, revision, false, now))
        audit("INTAKE", id, revision, action, now)
    }

    private suspend fun writeSymptom(id: String, revision: Long, input: SymptomInput, action: String) {
        val now = now()
        val encrypted = encrypt(id, revision, SymptomPayload.from(input, zoneId.id, now))
        dao.upsertHealthSymptom(HealthSymptomLogEntity(id, encrypted.ciphertext, encrypted.iv, revision, false, now))
        audit("SYMPTOM", id, revision, action, now)
    }

    private suspend fun requireConsent(category: HealthCategory) {
        require(dao.healthConsent(category.name)?.enabled == true) { "Consentimento da categoria nao esta ativo." }
    }

    private suspend fun audit(type: String, id: String, revision: Long, action: String, now: String) {
        dao.insertHealthAudit(HealthChangeAuditEntity(validId(), type, id, revision, action, now))
    }

    private inline fun <reified T> decrypt(id: String, revision: Long, ciphertext: String, iv: String): T =
        json.decodeFromString(cipher.decrypt(EncryptedHealthValue(ciphertext, iv), aad(id, revision)).decodeToString())

    private inline fun <reified T> encrypt(id: String, revision: Long, value: T): EncryptedHealthValue =
        cipher.encrypt(json.encodeToString(value).encodeToByteArray(), aad(id, revision))

    private fun aad(id: String, revision: Long) = "health-v1:$id:$revision".encodeToByteArray()
    private fun validId() = newId().also(UUID::fromString)
    private fun now() = Instant.now(clock).toString()

    private fun validate(value: IntakeInput) {
        require(value.name.trim().isNotEmpty() && value.name.length <= 120)
        require(value.amount == null || (value.amount.isFinite() && value.amount > 0))
        require(value.unit == null || value.unit.trim().isNotEmpty() && value.unit.length <= 40)
        require(value.context == null || value.context.length <= 500)
        require(value.perceivedEffect == null || value.perceivedEffect.length <= 500)
        require(value.note == null || value.note.length <= 2000)
        Instant.parse(value.occurredAt)
        value.plannedAt?.let(Instant::parse)
    }

    private fun validate(value: SymptomInput) {
        require(value.label.trim().isNotEmpty() && value.label.length <= 120)
        require(value.intensity == null || value.intensity in 0..10)
        require(value.note == null || value.note.length <= 2000)
        Instant.parse(value.occurredAt)
    }

    private fun validate(value: HealthSummary) {
        UUID.fromString(value.id)
        UUID.fromString(value.consentId)
        val start = Instant.parse(value.periodStart)
        val end = Instant.parse(value.periodEnd)
        require(start < end)
        require(value.sampleCount >= 0)
        require(value.metrics.size <= 8 && value.metrics.all { it.value.isFinite() })
        require(value.sourcePackages.size <= 20 && value.sourcePackages.distinct().size == value.sourcePackages.size)
        value.coverageStart?.let(Instant::parse)
        value.coverageEnd?.let(Instant::parse)
        Instant.parse(value.importedAt)
        if (value.sampleCount == 0) {
            require(value.coverageStart == null && value.coverageEnd == null && value.metrics.isEmpty())
            require(value.missingReason != null)
        } else {
            require(value.coverageStart != null && value.coverageEnd != null && value.missingReason == null)
        }
    }

    companion object {
        const val PURPOSE = "USER_REVIEWABLE_REPORT"
        const val DEFAULT_RETENTION_DAYS = 365
        const val MAX_RETENTION_DAYS = 3650
        val IMPORTABLE_CATEGORIES = setOf(
            HealthCategory.HEART_RATE,
            HealthCategory.RESTING_HEART_RATE,
            HealthCategory.SLEEP,
            HealthCategory.ACTIVITY,
        )
    }
}

@Serializable
private data class HealthSummaryPayload(
    @SerialName("contract_version") val contractVersion: Int = 1,
    @SerialName("summary_id") val summaryId: String,
    @SerialName("consent_id") val consentId: String,
    val category: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("coverage_start") val coverageStart: String?,
    @SerialName("coverage_end") val coverageEnd: String?,
    @SerialName("sample_count") val sampleCount: Int,
    val metrics: List<HealthMetric>,
    @SerialName("source_packages") val sourcePackages: List<String>,
    @SerialName("missing_reason") val missingReason: String?,
    @SerialName("imported_at") val importedAt: String,
) {
    fun toSummary() = HealthSummary(
        summaryId, consentId, HealthCategory.valueOf(category), periodStart, periodEnd,
        coverageStart, coverageEnd, sampleCount, metrics, sourcePackages,
        missingReason?.let(HealthMissingReason::valueOf), importedAt,
    )

    companion object {
        fun from(value: HealthSummary) = HealthSummaryPayload(
            summaryId = value.id, consentId = value.consentId, category = value.category.name,
            periodStart = value.periodStart, periodEnd = value.periodEnd,
            coverageStart = value.coverageStart, coverageEnd = value.coverageEnd,
            sampleCount = value.sampleCount, metrics = value.metrics,
            sourcePackages = value.sourcePackages, missingReason = value.missingReason?.name,
            importedAt = value.importedAt,
        )
    }
}

@Serializable
private data class IntakePayload(
    @SerialName("contract_version") val contractVersion: Int = 1,
    val kind: String,
    val name: String,
    val amount: Double?,
    val unit: String?,
    @SerialName("planned_at") val plannedAt: String?,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("time_zone") val timeZone: String,
    val context: String?,
    @SerialName("perceived_effect") val perceivedEffect: String?,
    val note: String?,
    val source: String = "MANUAL",
    @SerialName("recorded_at") val recordedAt: String,
) {
    fun toInput() = IntakeInput(IntakeKind.valueOf(kind), name, amount, unit, plannedAt, occurredAt, context, perceivedEffect, note)

    companion object {
        fun from(input: IntakeInput, zone: String, now: String) = IntakePayload(
            kind = input.kind.name, name = input.name.trim(), amount = input.amount,
            unit = input.unit?.trim(), plannedAt = input.plannedAt, occurredAt = input.occurredAt,
            timeZone = zone, context = input.context, perceivedEffect = input.perceivedEffect,
            note = input.note, recordedAt = now,
        )
    }
}

@Serializable
private data class SymptomPayload(
    @SerialName("contract_version") val contractVersion: Int = 1,
    val label: String,
    val kind: String = SubjectiveKind.SYMPTOM.name,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("time_zone") val timeZone: String,
    val intensity: Int?,
    val note: String?,
    val source: String = "MANUAL",
    @SerialName("recorded_at") val recordedAt: String,
) {
    fun toInput() = SymptomInput(label, occurredAt, intensity, note, SubjectiveKind.valueOf(kind))

    companion object {
        fun from(input: SymptomInput, zone: String, now: String) = SymptomPayload(
            label = input.label.trim(), kind = input.kind.name, occurredAt = input.occurredAt, timeZone = zone,
            intensity = input.intensity, note = input.note, recordedAt = now,
        )
    }
}
