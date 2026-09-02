package com.pessoal.agenda.wear.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wear_alerts",
    indices = [Index("status"), Index("validUntil")],
)
data class WearAlertEntity(
    @PrimaryKey val alertId: String,
    val revision: Long,
    val text: String,
    val reason: String,
    val sourceDeviceId: String,
    val scheduledAt: String,
    val validUntil: String,
    val updatedAt: String,
    val criticality: String,
    val actionsJson: String,
    val snoozeOptionsJson: String,
    val status: String,
    val acknowledgedOperationId: String?,
    val localFeedbackAction: String?,
    val localFeedbackMinutes: Int?,
)

@Entity(
    tableName = "wear_action_outbox",
    indices = [Index("alertId"), Index("state")],
)
data class WearActionOutboxEntity(
    @PrimaryKey val operationId: String,
    val alertId: String,
    val payload: ByteArray,
    val state: String,
    val attemptCount: Int,
    val createdAt: String,
    val updatedAt: String,
)
