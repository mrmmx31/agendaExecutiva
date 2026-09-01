package com.pessoal.agenda.mobile.sync

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.MobileMetadataEntity
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolStepEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.SyncConflictEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SyncRepository(
    private val database: MobileDatabase,
    private val transport: SyncTransport,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.offline()
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    val conflicts = dao.observeOpenConflicts()

    suspend fun syncOnce(): SyncSummary {
        val now = Instant.now(clock).toString()
        dao.recoverInFlight(now)
        val pending = dao.operationsForSync(100)
        var appliedCount = 0
        if (pending.isNotEmpty()) {
            check(dao.markInFlight(pending.map { it.operationId }, now) == pending.size) {
                "A fila foi alterada durante a sincronização."
            }
        }
        try {
            val response = transport.push(batch(pending))
            applyPushResponse(pending, response, now)
            appliedCount = response.results.count { it.status == "APPLIED" }
        } catch (error: Exception) {
            if (pending.isNotEmpty()) {
                dao.markRetryable(pending.map { it.operationId }, Instant.now(clock).toString())
            }
            throw error
        }

        var token: String? = null
        var snapshotId: String? = null
        var pages = 0
        var cursor = serverCursor()
        do {
            val page = transport.snapshot(token)
            check(snapshotId == null || snapshotId == page.snapshotId) { "Snapshot trocado entre páginas." }
            check(page.page == pages + 1) { "Ordem de páginas inválida." }
            snapshotId = page.snapshotId
            applySnapshot(page)
            pages++
            cursor = page.serverCursor
            token = page.nextPageToken
            check(page.hasMore == (token != null)) { "Token de snapshot inconsistente." }
        } while (token != null)

        return SyncSummary(
            sent = pending.size,
            applied = appliedCount,
            conflicts = dao.openConflicts().size,
            pages = pages,
            serverCursor = cursor,
        )
    }

    private suspend fun applyPushResponse(
        pending: List<PendingOperationEntity>,
        response: SyncBatchResponse,
        now: String,
    ) = database.withTransaction {
        check(response.contractVersion == 1) { "Contrato de resposta incompatível." }
        val expected = pending.map { it.operationId }.toSet()
        check(response.results.map { it.operationId }.toSet() == expected
            && response.results.size == expected.size) { "Resultados incompletos ou duplicados." }
        val conflicts = response.conflicts.associateBy { it.conflictId }
        response.results.forEach { result ->
            check(result.status in RESULT_STATUSES) { "Estado de resultado inválido." }
            check(dao.applySyncResult(
                result.operationId,
                result.status,
                result.errorCode,
                result.serverRevision,
                result.conflictId,
                now,
            ) == 1) { "Operação não estava em trânsito." }
            val operation = pending.first { it.operationId == result.operationId }
            if (result.status == "APPLIED" && operation.commandType == "CAPTURE_CREATED") {
                dao.acknowledgeCapture(operation.entityId, now)
            }
            if (result.status == "CONFLICT") {
                val conflict = requireNotNull(conflicts[result.conflictId]) {
                    "Detalhes do conflito ausentes."
                }
                dao.upsertConflicts(listOf(conflict.entity()))
            }
        }
    }

    private suspend fun applySnapshot(page: SnapshotPage) = database.withTransaction {
        dao.upsertTasks(page.tasks.map {
            TaskReplicaEntity(it.id, it.title, it.status, it.revision, it.updatedAt, it.tombstone)
        })
        dao.upsertProtocols(page.protocols.map {
            ProtocolTemplateEntity(
                it.id, it.title, it.revision, it.createdAt, it.updatedAt, it.tombstone,
            )
        })
        dao.upsertProtocolSteps(page.protocols.filterNot { it.tombstone }.flatMap { protocol ->
            protocol.steps.map { ProtocolStepEntity(it.id, protocol.id, it.position, it.label) }
        })
        if (!page.hasMore) {
            dao.saveMetadata(MobileMetadataEntity(
                SERVER_CURSOR_KEY, page.serverCursor.toString(), instant(),
            ))
        }
    }

    private suspend fun batch(operations: List<PendingOperationEntity>): SyncBatch = SyncBatch(
        deviceId = operations.firstOrNull()?.deviceId
            ?: requireNotNull(dao.metadata(DEVICE_ID_KEY)?.value) { "Identidade do aparelho ausente." },
        lastServerCursor = serverCursor(),
        operations = operations.map { it.envelope(json) },
    )

    private suspend fun serverCursor(): Long =
        dao.metadata(SERVER_CURSOR_KEY)?.value?.toLongOrNull() ?: 0

    private fun instant(): String = Instant.now(clock).toString()

    companion object {
        private const val SERVER_CURSOR_KEY = "server_cursor"
        private const val DEVICE_ID_KEY = "device_id"
        private val RESULT_STATUSES = setOf("APPLIED", "CONFLICT", "REJECTED", "RETRYABLE")
    }
}

data class SyncSummary(
    val sent: Int,
    val applied: Int,
    val conflicts: Int,
    val pages: Int,
    val serverCursor: Long,
)

private fun PendingOperationEntity.envelope(json: Json) = OperationEnvelope(
    operationId = operationId,
    deviceId = deviceId,
    sequence = sequence,
    contractVersion = contractVersion,
    entityType = entityType,
    entityId = entityId,
    commandType = commandType,
    occurredAt = occurredAt,
    timeZone = timeZone,
    payload = json.parseToJsonElement(payloadJson).jsonObject,
    payloadHash = payloadHash,
    baseRevision = baseRevision,
)

private fun ConflictPayload.entity() = SyncConflictEntity(
    conflictId = conflictId,
    operationId = operationId,
    entityType = entityType,
    entityId = entityId,
    baseRevision = baseRevision,
    serverRevision = serverRevision,
    reason = reason,
    localValueJson = Json.encodeToString(localValue),
    serverValueJson = Json.encodeToString(serverValue),
    createdAt = createdAt,
)
