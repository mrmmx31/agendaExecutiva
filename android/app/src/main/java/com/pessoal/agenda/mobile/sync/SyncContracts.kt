package com.pessoal.agenda.mobile.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OperationEnvelope(
    @SerialName("operation_id") val operationId: String,
    @SerialName("device_id") val deviceId: String,
    val sequence: Long,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("command_type") val commandType: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("time_zone") val timeZone: String,
    val payload: JsonObject,
    @SerialName("payload_hash") val payloadHash: String,
    @SerialName("base_revision") val baseRevision: Long? = null,
)

@Serializable
data class SyncBatch(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("device_id") val deviceId: String,
    @SerialName("last_server_cursor") val lastServerCursor: Long,
    val operations: List<OperationEnvelope>,
)

@Serializable
data class SyncResult(
    @SerialName("operation_id") val operationId: String,
    val status: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("server_revision") val serverRevision: Long? = null,
    @SerialName("conflict_id") val conflictId: String? = null,
)

@Serializable
data class ConflictPayload(
    @SerialName("conflict_id") val conflictId: String,
    @SerialName("operation_id") val operationId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("base_revision") val baseRevision: Long?,
    @SerialName("server_revision") val serverRevision: Long,
    val reason: String,
    @SerialName("local_value") val localValue: JsonObject,
    @SerialName("server_value") val serverValue: JsonObject,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class SyncBatchResponse(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("client_contiguous_sequence") val clientContiguousSequence: Long,
    @SerialName("server_cursor") val serverCursor: Long,
    val results: List<SyncResult>,
    val conflicts: List<ConflictPayload> = emptyList(),
)

@Serializable
data class SnapshotTask(
    val id: String,
    val title: String,
    val status: String,
    val revision: Long,
    @SerialName("updated_at") val updatedAt: String,
    val tombstone: Boolean,
    val notes: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    val priority: String = "NORMAL",
    val checklist: List<SnapshotChecklistItem> = emptyList(),
)

@Serializable
data class SnapshotChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean,
    val position: Int,
)

@Serializable
data class SnapshotProtocolStep(val id: String, val position: Int, val label: String)

@Serializable
data class SnapshotProtocol(
    val id: String,
    val title: String,
    val revision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val tombstone: Boolean,
    val steps: List<SnapshotProtocolStep> = emptyList(),
)

@Serializable
data class SnapshotPage(
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("server_cursor") val serverCursor: Long,
    val page: Int,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_page_token") val nextPageToken: String?,
    val tasks: List<SnapshotTask>,
    val protocols: List<SnapshotProtocol>,
)

interface SyncTransport {
    suspend fun push(batch: SyncBatch): SyncBatchResponse
    suspend fun snapshot(pageToken: String?): SnapshotPage
}
