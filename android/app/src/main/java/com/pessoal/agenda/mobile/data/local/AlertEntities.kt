package com.pessoal.agenda.mobile.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alert_definitions",
    indices = [Index("scheduledAt"), Index("validUntil")],
)
data class AlertDefinitionEntity(
    @PrimaryKey val id: String,
    val contractVersion: Int,
    val origin: String,
    val referenceId: String?,
    val text: String,
    val reason: String,
    val sourceDeviceId: String,
    val scheduledAt: String,
    val validUntil: String,
    val criticality: String,
    val allowedChannelsJson: String,
    val maxDeliveries: Int,
    val minimumIntervalMinutes: Int,
    val actionsJson: String,
    val createdAt: String,
)

@Entity(
    tableName = "alert_materializations",
    foreignKeys = [ForeignKey(
        entity = AlertDefinitionEntity::class,
        parentColumns = ["id"],
        childColumns = ["alertId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["state", "nextEligibleAt"])],
)
data class AlertMaterializationEntity(
    @PrimaryKey val alertId: String,
    val state: String,
    val nextEligibleAt: String,
    val deliveryCount: Int,
    val snoozeCount: Int,
    val lastDeliveryAt: String?,
    val completedAt: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "alert_deliveries",
    foreignKeys = [ForeignKey(
        entity = AlertDefinitionEntity::class,
        parentColumns = ["id"],
        childColumns = ["alertId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("alertId"), Index("state")],
)
data class AlertDeliveryEntity(
    @PrimaryKey val id: String,
    val alertId: String,
    val deviceId: String,
    val channelsJson: String,
    val state: String,
    val technicalReason: String?,
    val attemptedAt: String,
)

@Entity(
    tableName = "alert_actions",
    foreignKeys = [ForeignKey(
        entity = AlertDefinitionEntity::class,
        parentColumns = ["id"],
        childColumns = ["alertId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("alertId"), Index("syncState")],
)
data class AlertActionEntity(
    @PrimaryKey val operationId: String,
    val alertId: String,
    val sourceDeviceId: String,
    val action: String,
    val occurredAt: String,
    val snoozeUntil: String?,
    val syncState: String,
    val createdAt: String,
)

@Entity(tableName = "sensory_profiles")
data class SensoryProfileEntity(
    @PrimaryKey val id: String,
    val contractVersion: Int,
    val globalEnabled: Boolean,
    val enabledChannelsJson: String,
    val quietStartsAt: String?,
    val quietEndsAt: String?,
    val pausedUntil: String?,
    val cooldownMinutes: Int,
    val audioRoute: String,
    val snoozePresetMinutesJson: String,
    val snoozeMinimumMinutes: Int,
    val snoozeMaximumMinutes: Int,
    val snoozeMaximumCount: Int,
    val updatedAt: String,
)
