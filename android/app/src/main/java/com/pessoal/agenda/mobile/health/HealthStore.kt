package com.pessoal.agenda.mobile.health

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.HealthChangeAuditEntity
import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
import com.pessoal.agenda.mobile.data.local.HealthIntakeLogEntity
import com.pessoal.agenda.mobile.data.local.HealthSymptomLogEntity
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

    companion object {
        const val PURPOSE = "USER_REVIEWABLE_REPORT"
        const val DEFAULT_RETENTION_DAYS = 365
        const val MAX_RETENTION_DAYS = 3650
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
