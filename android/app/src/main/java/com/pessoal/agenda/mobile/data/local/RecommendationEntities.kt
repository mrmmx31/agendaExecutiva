package com.pessoal.agenda.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recommendation_events",
    indices = [Index("occurredAt"), Index(value = ["eventType", "activeContext"])],
)
data class RecommendationEventEntity(
    @PrimaryKey val id: String,
    val contractVersion: Int,
    val eventType: String,
    val occurredAt: String,
    val localHour: Int,
    val dayOfWeek: Int,
    val sourceDevice: String,
    val activeContext: String,
    val capacityContext: String,
    val alertKind: String?,
    val deadlineBucket: String?,
    val channel: String?,
    val responseLatencySeconds: Int?,
    val snoozeMinutes: Int?,
    val recommendationId: String?,
    val optionCode: String?,
    val correctedAt: String?,
)

@Entity(
    tableName = "recommendation_decisions",
    indices = [Index("generatedAt"), Index("purpose")],
)
data class RecommendationDecisionEntity(
    @PrimaryKey val id: String,
    val contractVersion: Int,
    val generatedAt: String,
    val engineId: String,
    val ruleVersion: String,
    val purpose: String,
    val sampleCount: Int,
    val minimumSamples: Int,
    val fallback: Boolean,
    val optionsJson: String,
)

@Entity(tableName = "recommendation_settings")
data class RecommendationSettingsEntity(
    @PrimaryKey val id: String,
    val personalizationEnabled: Boolean,
    val retentionDays: Int,
    val capacityContext: String,
    val preferredSnoozeMinutes: Int?,
    val preferredChannel: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "personal_model_artifacts",
    primaryKeys = ["modelId", "modelVersion"],
    indices = [Index("status"), Index("updatedAt")],
)
data class PersonalModelArtifactEntity(
    val modelId: String,
    val modelVersion: String,
    val contractVersion: Int,
    val purpose: String,
    val runtime: String,
    val featureContractVersion: Int,
    val artifactFormat: String,
    val artifactJson: String,
    val artifactSha256: String,
    val trainedAt: String,
    val trainingSampleCount: Int,
    val status: String,
    val evaluationSampleCount: Int,
    val top1Accuracy: Double,
    val baselineTop1Accuracy: Double,
    val rollbackModelId: String?,
    val activatedAt: String?,
    val updatedAt: String,
)

@Entity(tableName = "personal_model_shadow_metrics")
data class PersonalModelShadowMetricsEntity(
    @PrimaryKey val modelId: String,
    val evaluatedCount: Int,
    val agreementCount: Int,
    val lastRuleOption: String?,
    val lastModelOption: String?,
    val updatedAt: String,
)
