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
) {
    private val dao = database.offline()

    val tasks: Flow<List<TaskReplicaEntity>> = dao.observeTasks()
    val captures: Flow<List<CaptureEntity>> = dao.observeCaptures()
    val protocols: Flow<List<ProtocolTemplateEntity>> = dao.observeProtocols()
    val activeRunSteps: Flow<List<ActiveRunStepRow>> = dao.observeActiveRunSteps()
    val operations: Flow<List<PendingOperationEntity>> = dao.observeOperations()

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

    suspend fun startProtocol(protocolId: String): String = database.withTransaction {
        dao.activeRun()?.id?.let { return@withTransaction it }
        val protocol = requireNotNull(dao.protocol(protocolId)) { "Protocolo inexistente." }
        val steps = dao.protocolSteps(protocolId)
        require(steps.isNotEmpty()) { "O protocolo nao possui passos." }
        val runId = validId()
        val occurredAt = instant()
        dao.insertProtocolRun(
            ProtocolRunEntity(
                id = runId,
                protocolId = protocol.id,
                protocolRevision = protocol.revision,
                startedAt = occurredAt,
            ),
        )
        dao.insertProtocolRunSteps(steps.map { ProtocolRunStepEntity(runId, it.id) })
        enqueue(
            operationId = validId(),
            entityType = "protocol_run",
            entityId = runId,
            commandType = "PROTOCOL_RUN_STARTED",
            occurredAt = occurredAt,
            payloadJson = Json.encodeToString(
                ProtocolRunStartedPayload(runId, protocol.id, protocol.revision, occurredAt),
            ),
        )
        runId
    }

    suspend fun completeProtocolStep(runId: String, stepId: String): Boolean = database.withTransaction {
        val existing = requireNotNull(dao.runStep(runId, stepId)) { "Passo da execucao inexistente." }
        if (existing.completedAt != null) return@withTransaction false
        val occurredAt = instant()
        if (dao.completeRunStep(runId, stepId, occurredAt) != 1) return@withTransaction false
        enqueue(
            operationId = validId(),
            entityType = "protocol_run_step",
            entityId = stepId,
            commandType = "PROTOCOL_STEP_COMPLETED",
            occurredAt = occurredAt,
            payloadJson = Json.encodeToString(ProtocolStepCompletedPayload(runId, stepId, occurredAt)),
        )
        if (dao.incompleteRunStepCount(runId) == 0) dao.completeRun(runId, occurredAt)
        true
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
        dao.metadata(DEVICE_ID_KEY)?.let { return it.value.also(UUID::fromString) }
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
