package com.pessoal.agenda.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_consents",
    indices = [Index(value = ["category"], unique = true)],
)
data class HealthConsentEntity(
    @PrimaryKey val id: String,
    val category: String,
    val purpose: String,
    val enabled: Boolean,
    val foregroundOnly: Boolean,
    val retentionDays: Int,
    val grantedAt: String?,
    val revokedAt: String?,
    val updatedAt: String,
)

@Entity(tableName = "health_intake_logs", indices = [Index("updatedAt")])
data class HealthIntakeLogEntity(
    @PrimaryKey val id: String,
    val ciphertext: String,
    val iv: String,
    val revision: Long,
    val tombstone: Boolean,
    val updatedAt: String,
)

@Entity(tableName = "health_symptom_logs", indices = [Index("updatedAt")])
data class HealthSymptomLogEntity(
    @PrimaryKey val id: String,
    val ciphertext: String,
    val iv: String,
    val revision: Long,
    val tombstone: Boolean,
    val updatedAt: String,
)

@Entity(
    tableName = "health_change_audit",
    indices = [Index(value = ["entityType", "entityId", "revision"], unique = true)],
)
data class HealthChangeAuditEntity(
    @PrimaryKey val changeId: String,
    val entityType: String,
    val entityId: String,
    val revision: Long,
    val action: String,
    val occurredAt: String,
)
