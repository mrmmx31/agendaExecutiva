package com.pessoal.agenda.mobile.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mobile_metadata")
data class MobileMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: String,
)

@Entity(tableName = "task_replicas", indices = [Index("status")])
data class TaskReplicaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val revision: Long,
    val updatedAt: String,
    val tombstone: Boolean = false,
)

@Entity(tableName = "captures", indices = [Index("createdAt")])
data class CaptureEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: String,
    val organizationType: String? = null,
    val organizedEntityId: String? = null,
    val acknowledgedAt: String? = null,
)

@Entity(tableName = "protocol_templates")
data class ProtocolTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val revision: Long,
    val createdAt: String,
    val updatedAt: String,
    val tombstone: Boolean = false,
)

@Entity(
    tableName = "protocol_steps",
    foreignKeys = [ForeignKey(
        entity = ProtocolTemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["protocolId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("protocolId"), Index(value = ["protocolId", "position"], unique = true)],
)
data class ProtocolStepEntity(
    @PrimaryKey val id: String,
    val protocolId: String,
    val position: Int,
    val label: String,
)

@Entity(
    tableName = "protocol_runs",
    foreignKeys = [ForeignKey(
        entity = ProtocolTemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["protocolId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("protocolId"), Index("completedAt")],
)
data class ProtocolRunEntity(
    @PrimaryKey val id: String,
    val protocolId: String,
    val protocolRevision: Long,
    val startedAt: String,
    val completedAt: String? = null,
)

@Entity(
    tableName = "protocol_run_steps",
    primaryKeys = ["runId", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = ProtocolRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProtocolStepEntity::class,
            parentColumns = ["id"],
            childColumns = ["stepId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("stepId")],
)
data class ProtocolRunStepEntity(
    val runId: String,
    val stepId: String,
    val completedAt: String? = null,
)

@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["deviceId", "sequence"], unique = true),
        Index("status"),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class PendingOperationEntity(
    @PrimaryKey val operationId: String,
    val deviceId: String,
    val sequence: Long,
    val contractVersion: Int,
    val entityType: String,
    val entityId: String,
    val commandType: String,
    val occurredAt: String,
    val timeZone: String,
    val payloadJson: String,
    val payloadHash: String,
    val baseRevision: Long? = null,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val serverRevision: Long? = null,
    val conflictId: String? = null,
    val attemptCount: Int = 0,
    val updatedAt: String = occurredAt,
)

@Entity(
    tableName = "sync_conflicts",
    indices = [Index("operationId", unique = true), Index("status")],
)
data class SyncConflictEntity(
    @PrimaryKey val conflictId: String,
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val baseRevision: Long?,
    val serverRevision: Long,
    val reason: String,
    val localValueJson: String,
    val serverValueJson: String,
    val status: String = "OPEN",
    val createdAt: String,
)
