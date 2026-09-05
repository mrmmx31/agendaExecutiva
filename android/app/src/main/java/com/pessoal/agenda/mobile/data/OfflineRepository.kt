package com.pessoal.agenda.mobile.data

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.CaptureEntity
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.MobileMetadataEntity
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolRunEntity
import com.pessoal.agenda.mobile.data.local.ProtocolRunStepEntity
import com.pessoal.agenda.mobile.data.local.ProtocolStepEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext
import com.pessoal.agenda.mobile.recommendation.RecommendationEventType
import com.pessoal.agenda.mobile.recommendation.RecommendationSourceDevice
import com.pessoal.agenda.mobile.recommendation.RecommendationStore
import com.pessoal.agenda.mobile.recommendation.RecommendationTelemetry
import com.pessoal.agenda.wear.contract.WearProtocolStatus
import com.pessoal.agenda.wear.contract.WearProtocolStepState
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineRepository(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val deviceIdProvider: (() -> String)? = null,
    private val recommendationTelemetry: RecommendationTelemetry = RecommendationTelemetry(
        RecommendationStore(database, clock, zoneId),
    ),
) {
    private val dao = database.offline()

    val tasks: Flow<List<TaskReplicaEntity>> = dao.observeTasks()
    val captures: Flow<List<CaptureEntity>> = dao.observeCaptures()
    val protocols: Flow<List<ProtocolTemplateEntity>> = dao.observeProtocols()
    val activeRunSteps: Flow<List<ActiveRunStepRow>> = dao.observeActiveRunSteps()
    val operations: Flow<List<PendingOperationEntity>> = dao.observeOperations()

    suspend fun alignDeviceIdentity() = database.withTransaction { deviceId(instant()) }

    suspend fun initializeFictitiousData() = database.withTransaction {
        val now = instant()
        if (dao.taskCount() == 0) {
            dao.insertTasks(
                listOf(
                    TaskReplicaEntity(
                        id = FIXTURE_TASK_ONE,
                        title = "Revisar o plano fictício do dia",
                        status = "PENDING",
                        revision = 1,
                        updatedAt = now,
                    ),
                    TaskReplicaEntity(
                        id = FIXTURE_TASK_TWO,
                        title = "Separar material de demonstração",
                        status = "PENDING",
                        revision = 1,
                        updatedAt = now,
                    ),
                ),
            )
        }
        if (dao.protocolCount() == 0) {
            dao.insertProtocol(
                ProtocolTemplateEntity(
                    id = FIXTURE_PROTOCOL,
                    title = "Saída rápida (demonstração)",
                    revision = 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.insertProtocolSteps(
                listOf("Chaves", "Carteira", "Celular", "Fone").mapIndexed { index, label ->
                    ProtocolStepEntity(
                        id = "00000000-0000-4000-8000-${(201 + index).toString().padStart(12, '0')}",
                        protocolId = FIXTURE_PROTOCOL,
                        position = index,
                        label = label,
                    )
                },
            )
        }
    }

    suspend fun createCapture(rawText: String): String {
        val text = rawText.trim()
        require(text.isNotEmpty()) { "A captura nao pode ficar vazia." }
        require(text.length <= MAX_CAPTURE_LENGTH) { "A captura deve ter no maximo $MAX_CAPTURE_LENGTH caracteres." }
        val captureId = validId()
        val operationId = validId()
        val occurredAt = instant()
        val payload = Json.encodeToString(CaptureCreatedPayload(captureId, text, occurredAt))

        database.withTransaction {
            dao.insertCapture(CaptureEntity(captureId, text, occurredAt))
            enqueue(
                operationId = operationId,
                entityType = "capture",
                entityId = captureId,
                commandType = "CAPTURE_CREATED",
                occurredAt = occurredAt,
                payloadJson = payload,
            )
        }
        return captureId
    }

    suspend fun startProtocol(protocolId: String): String {
        var createdAt: Instant? = null
        val runId = database.withTransaction {
            dao.activeRun()?.id?.let { return@withTransaction it }
            val protocol = requireNotNull(dao.protocol(protocolId)) { "Protocolo inexistente." }
            val steps = dao.protocolSteps(protocolId)
            require(steps.isNotEmpty()) { "O protocolo nao possui passos." }
            val id = validId()
            val occurredAt = Instant.now(clock).also { createdAt = it }.toString()
            dao.insertProtocolRun(
                ProtocolRunEntity(
                    id = id,
                    protocolId = protocol.id,
                    protocolRevision = protocol.revision,
                    startedAt = occurredAt,
                ),
            )
            dao.insertProtocolRunSteps(steps.map { ProtocolRunStepEntity(id, it.id) })
            enqueue(
                operationId = validId(),
                entityType = "protocol_run",
                entityId = id,
                commandType = "PROTOCOL_RUN_STARTED",
                occurredAt = occurredAt,
                payloadJson = Json.encodeToString(
                    ProtocolRunStartedPayload(id, protocol.id, protocol.revision, occurredAt),
                ),
            )
            id
        }
        createdAt?.let {
            recommendationTelemetry.record(
                RecommendationEventType.PROTOCOL_STARTED,
                it,
                RecommendationSourceDevice.PHONE,
                RecommendationActiveContext.PROTOCOL,
            )
        }
        return runId
    }

    suspend fun completeProtocolStep(
        runId: String,
        stepId: String,
        operationId: String = validId(),
        sourceDevice: RecommendationSourceDevice = RecommendationSourceDevice.PHONE,
    ): Boolean {
        var completedAt: Instant? = null
        val completed = database.withTransaction {
            UUID.fromString(operationId)
            val existing = requireNotNull(dao.runStep(runId, stepId)) { "Passo da execucao inexistente." }
            if (existing.completedAt != null) return@withTransaction false
            val occurredAt = Instant.now(clock).also { completedAt = it }.toString()
            if (dao.completeRunStep(runId, stepId, occurredAt) != 1) return@withTransaction false
            enqueue(
                operationId = operationId,
                entityType = "protocol_run_step",
                entityId = stepId,
                commandType = "PROTOCOL_STEP_COMPLETED",
                occurredAt = occurredAt,
                payloadJson = Json.encodeToString(ProtocolStepCompletedPayload(runId, stepId, occurredAt)),
            )
            check(dao.acknowledgeWearProtocolAction(runId, operationId) == 1)
            if (dao.incompleteRunStepCount(runId) == 0) dao.completeRun(runId, occurredAt)
            true
        }
        if (completed) {
            recommendationTelemetry.record(
                RecommendationEventType.PROTOCOL_STEP_COMPLETED,
                requireNotNull(completedAt),
                sourceDevice,
                RecommendationActiveContext.PROTOCOL,
            )
        }
        return completed
    }

    suspend fun cancelProtocol(runId: String): Boolean = database.withTransaction {
        val run = requireNotNull(dao.protocolRun(runId)) { "Execucao de protocolo inexistente." }
        if (run.completedAt != null) return@withTransaction false
        val occurredAt = Instant.now(clock).toString()
        if (dao.cancelRun(runId, occurredAt) != 1) return@withTransaction false
        enqueue(
            operationId = validId(),
            entityType = "protocol_run",
            entityId = runId,
            commandType = "PROTOCOL_RUN_CANCELLED",
            occurredAt = occurredAt,
            payloadJson = Json.encodeToString(ProtocolRunCancelledPayload(runId, occurredAt)),
        )
        true
    }

    suspend fun protocolWearState(runId: String): WearProtocolStepState? = database.withTransaction {
        val run = dao.protocolRun(runId) ?: return@withTransaction null
        val rows = dao.runSteps(runId)
        if (rows.size !in 1..100) return@withTransaction null
        val current = if (run.completedAt == null) rows.firstOrNull { it.completedAt == null } else null
        WearProtocolStepState(
            contractVersion = WearProtocolStepState.CONTRACT_VERSION,
            runId = run.id,
            protocolId = run.protocolId,
            revision = run.wearRevision,
            protocolTitle = rows.first().protocolTitle.trim().take(80),
            stepId = current?.stepId,
            stepLabel = current?.label?.trim()?.take(120),
            stepPosition = current?.position?.plus(1),
            stepCount = rows.size,
            updatedAt = run.completedAt
                ?: rows.mapNotNull(ActiveRunStepRow::completedAt).maxOrNull()
                ?: run.startedAt,
            status = if (current == null) WearProtocolStatus.COMPLETED else WearProtocolStatus.ACTIVE,
            acknowledgedOperationId = run.acknowledgedWearOperationId,
        ).also(WearProtocolStepState::validate)
    }

    suspend fun proposeProtocolStep(protocolId: String, rawLabel: String): String = database.withTransaction {
        val protocol = requireNotNull(dao.protocol(protocolId)) { "Protocolo inexistente." }
        val label = rawLabel.trim()
        require(label.isNotEmpty() && label.length <= 120) { "O item deve ter entre 1 e 120 caracteres." }
        val operationId = validId()
        val occurredAt = instant()
        enqueue(
            operationId = operationId,
            entityType = "protocol",
            entityId = protocolId,
            commandType = "PROTOCOL_STRUCTURE_PROPOSED",
            occurredAt = occurredAt,
            payloadJson = Json.encodeToString(
                ProtocolStructureProposedPayload(protocolId, protocol.revision, label, occurredAt),
            ),
            baseRevision = protocol.revision,
        )
        operationId
    }

    private suspend fun enqueue(
        operationId: String,
        entityType: String,
        entityId: String,
        commandType: String,
        occurredAt: String,
        payloadJson: String,
        baseRevision: Long? = null,
    ) {
        val deviceId = deviceId(occurredAt)
        dao.insertPendingOperation(
            PendingOperationEntity(
                operationId = operationId,
                deviceId = deviceId,
                sequence = dao.nextSequence(deviceId),
                contractVersion = CONTRACT_VERSION,
                entityType = entityType,
                entityId = entityId,
                commandType = commandType,
                occurredAt = occurredAt,
                timeZone = zoneId.id,
                payloadJson = payloadJson,
                payloadHash = sha256(payloadJson),
                baseRevision = baseRevision,
                status = "PENDING",
            ),
        )
    }

    private suspend fun deviceId(now: String): String {
        val authoritative = deviceIdProvider?.invoke()?.also(UUID::fromString)
        val existing = dao.metadata(DEVICE_ID_KEY)?.value?.also(UUID::fromString)
        if (authoritative != null) {
            if (existing != null && existing != authoritative) {
                dao.replaceOperationDeviceId(existing, authoritative)
            }
            if (existing != authoritative) {
                dao.saveMetadata(MobileMetadataEntity(DEVICE_ID_KEY, authoritative, now))
            }
            return authoritative
        }
        if (existing != null) return existing
        return validId().also { dao.saveMetadata(MobileMetadataEntity(DEVICE_ID_KEY, it, now)) }
    }

    private fun validId(): String = newId().also(UUID::fromString)
    private fun instant(): String = Instant.now(clock).toString()

    companion object {
        const val CONTRACT_VERSION = 1
        const val MAX_CAPTURE_LENGTH = 4000
        private const val DEVICE_ID_KEY = "device_id"
        private const val FIXTURE_TASK_ONE = "00000000-0000-4000-8000-000000000101"
        private const val FIXTURE_TASK_TWO = "00000000-0000-4000-8000-000000000102"
        const val FIXTURE_PROTOCOL = "00000000-0000-4000-8000-000000000200"

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

@Serializable
private data class CaptureCreatedPayload(
    @SerialName("capture_id") val captureId: String,
    val text: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class ProtocolRunStartedPayload(
    @SerialName("run_id") val runId: String,
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("protocol_revision") val protocolRevision: Long,
    @SerialName("started_at") val startedAt: String,
)

@Serializable
private data class ProtocolStepCompletedPayload(
    @SerialName("run_id") val runId: String,
    @SerialName("step_id") val stepId: String,
    @SerialName("completed_at") val completedAt: String,
)

@Serializable
private data class ProtocolRunCancelledPayload(
    @SerialName("run_id") val runId: String,
    @SerialName("cancelled_at") val cancelledAt: String,
)

@Serializable
private data class ProtocolStructureProposedPayload(
    @SerialName("protocol_id") val protocolId: String,
    @SerialName("base_revision") val baseRevision: Long,
    @SerialName("proposed_step_label") val proposedStepLabel: String,
    @SerialName("proposed_at") val proposedAt: String,
)
