package com.pessoal.agenda.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MobileMetadataEntity::class,
        TaskReplicaEntity::class,
        DailyPlanEntity::class,
        DailyPlanItemEntity::class,
        FocusSelectionEntity::class,
        CaptureEntity::class,
        ProtocolTemplateEntity::class,
        ProtocolStepEntity::class,
        ProtocolRunEntity::class,
        ProtocolRunStepEntity::class,
        PendingOperationEntity::class,
        SyncConflictEntity::class,
        AlertDefinitionEntity::class,
        AlertMaterializationEntity::class,
        AlertDeliveryEntity::class,
        AlertActionEntity::class,
        SensoryProfileEntity::class,
        HealthConsentEntity::class,
        HealthIntakeLogEntity::class,
        HealthSymptomLogEntity::class,
        HealthChangeAuditEntity::class,
        HealthSummaryEntity::class,
        RecommendationEventEntity::class,
        RecommendationDecisionEntity::class,
        RecommendationSettingsEntity::class,
        PersonalModelArtifactEntity::class,
        PersonalModelShadowMetricsEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun offline(): OfflineDao

    companion object {
        const val DATABASE_NAME = "agenda-mobile.db"

        @Volatile private var instance: MobileDatabase? = null

        fun get(context: Context): MobileDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MobileDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
            ).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_replicas (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, status TEXT NOT NULL,
                        revision INTEGER NOT NULL, updatedAt TEXT NOT NULL, tombstone INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_task_replicas_status ON task_replicas(status)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS captures (
                        id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, createdAt TEXT NOT NULL,
                        organizationType TEXT, organizedEntityId TEXT, acknowledgedAt TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_captures_createdAt ON captures(createdAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_templates (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, revision INTEGER NOT NULL,
                        createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, tombstone INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_steps (
                        id TEXT NOT NULL PRIMARY KEY, protocolId TEXT NOT NULL, position INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        FOREIGN KEY(protocolId) REFERENCES protocol_templates(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_steps_protocolId ON protocol_steps(protocolId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_protocol_steps_protocolId_position ON protocol_steps(protocolId, position)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_runs (
                        id TEXT NOT NULL PRIMARY KEY, protocolId TEXT NOT NULL,
                        protocolRevision INTEGER NOT NULL, startedAt TEXT NOT NULL, completedAt TEXT,
                        FOREIGN KEY(protocolId) REFERENCES protocol_templates(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_runs_protocolId ON protocol_runs(protocolId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_runs_completedAt ON protocol_runs(completedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS protocol_run_steps (
                        runId TEXT NOT NULL, stepId TEXT NOT NULL, completedAt TEXT,
                        PRIMARY KEY(runId, stepId),
                        FOREIGN KEY(runId) REFERENCES protocol_runs(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(stepId) REFERENCES protocol_steps(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_protocol_run_steps_stepId ON protocol_run_steps(stepId)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_operations (
                        operationId TEXT NOT NULL PRIMARY KEY, deviceId TEXT NOT NULL,
                        sequence INTEGER NOT NULL, contractVersion INTEGER NOT NULL,
                        entityType TEXT NOT NULL, entityId TEXT NOT NULL, commandType TEXT NOT NULL,
                        occurredAt TEXT NOT NULL, timeZone TEXT NOT NULL, payloadJson TEXT NOT NULL,
                        payloadHash TEXT NOT NULL, baseRevision INTEGER, status TEXT NOT NULL,
                        errorCode TEXT, errorMessage TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_operations_deviceId_sequence ON pending_operations(deviceId, sequence)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_status ON pending_operations(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_entityType_entityId ON pending_operations(entityType, entityId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN serverRevision INTEGER")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN conflictId TEXT")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE pending_operations ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE pending_operations SET updatedAt=occurredAt WHERE updatedAt=''")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        conflictId TEXT NOT NULL PRIMARY KEY,
                        operationId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        baseRevision INTEGER,
                        serverRevision INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        localValueJson TEXT NOT NULL,
                        serverValueJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_conflicts_operationId ON sync_conflicts(operationId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_conflicts_status ON sync_conflicts(status)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alert_definitions (
                        id TEXT NOT NULL PRIMARY KEY, contractVersion INTEGER NOT NULL,
                        origin TEXT NOT NULL, referenceId TEXT, text TEXT NOT NULL,
                        reason TEXT NOT NULL, sourceDeviceId TEXT NOT NULL,
                        scheduledAt TEXT NOT NULL, validUntil TEXT NOT NULL,
                        criticality TEXT NOT NULL, allowedChannelsJson TEXT NOT NULL,
                        maxDeliveries INTEGER NOT NULL, minimumIntervalMinutes INTEGER NOT NULL,
                        actionsJson TEXT NOT NULL, createdAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_definitions_scheduledAt ON alert_definitions(scheduledAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_definitions_validUntil ON alert_definitions(validUntil)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alert_materializations (
                        alertId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL,
                        nextEligibleAt TEXT NOT NULL, deliveryCount INTEGER NOT NULL,
                        snoozeCount INTEGER NOT NULL, lastDeliveryAt TEXT, completedAt TEXT,
                        updatedAt TEXT NOT NULL,
                        FOREIGN KEY(alertId) REFERENCES alert_definitions(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_materializations_state_nextEligibleAt ON alert_materializations(state, nextEligibleAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alert_deliveries (
                        id TEXT NOT NULL PRIMARY KEY, alertId TEXT NOT NULL,
                        deviceId TEXT NOT NULL, channelsJson TEXT NOT NULL,
                        state TEXT NOT NULL, technicalReason TEXT, attemptedAt TEXT NOT NULL,
                        FOREIGN KEY(alertId) REFERENCES alert_definitions(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_deliveries_alertId ON alert_deliveries(alertId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_deliveries_state ON alert_deliveries(state)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS alert_actions (
                        operationId TEXT NOT NULL PRIMARY KEY, alertId TEXT NOT NULL,
                        sourceDeviceId TEXT NOT NULL, action TEXT NOT NULL,
                        occurredAt TEXT NOT NULL, snoozeUntil TEXT, syncState TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        FOREIGN KEY(alertId) REFERENCES alert_definitions(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_actions_alertId ON alert_actions(alertId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_alert_actions_syncState ON alert_actions(syncState)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sensory_profiles (
                        id TEXT NOT NULL PRIMARY KEY, contractVersion INTEGER NOT NULL,
                        globalEnabled INTEGER NOT NULL, enabledChannelsJson TEXT NOT NULL,
                        quietStartsAt TEXT, quietEndsAt TEXT, pausedUntil TEXT,
                        cooldownMinutes INTEGER NOT NULL, audioRoute TEXT NOT NULL,
                        snoozePresetMinutesJson TEXT NOT NULL,
                        snoozeMinimumMinutes INTEGER NOT NULL,
                        snoozeMaximumMinutes INTEGER NOT NULL,
                        snoozeMaximumCount INTEGER NOT NULL, updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alert_materializations ADD COLUMN wearRevision INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE protocol_runs ADD COLUMN wearRevision INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE protocol_runs ADD COLUMN acknowledgedWearOperationId TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_consents (
                        id TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, purpose TEXT NOT NULL,
                        enabled INTEGER NOT NULL, foregroundOnly INTEGER NOT NULL,
                        retentionDays INTEGER NOT NULL, grantedAt TEXT, revokedAt TEXT,
                        updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_health_consents_category ON health_consents(category)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_intake_logs (
                        id TEXT NOT NULL PRIMARY KEY, ciphertext TEXT NOT NULL, iv TEXT NOT NULL,
                        revision INTEGER NOT NULL, tombstone INTEGER NOT NULL, updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_health_intake_logs_updatedAt ON health_intake_logs(updatedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_symptom_logs (
                        id TEXT NOT NULL PRIMARY KEY, ciphertext TEXT NOT NULL, iv TEXT NOT NULL,
                        revision INTEGER NOT NULL, tombstone INTEGER NOT NULL, updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_health_symptom_logs_updatedAt ON health_symptom_logs(updatedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_change_audit (
                        changeId TEXT NOT NULL PRIMARY KEY, entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL, revision INTEGER NOT NULL, action TEXT NOT NULL,
                        occurredAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_health_change_audit_entityType_entityId_revision ON health_change_audit(entityType, entityId, revision)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_summaries (
                        id TEXT NOT NULL PRIMARY KEY, consentId TEXT NOT NULL,
                        category TEXT NOT NULL, ciphertext TEXT NOT NULL, iv TEXT NOT NULL,
                        revision INTEGER NOT NULL, importedAt TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_health_summaries_category ON health_summaries(category)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_health_summaries_importedAt ON health_summaries(importedAt)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recommendation_events (
                        id TEXT NOT NULL PRIMARY KEY, contractVersion INTEGER NOT NULL,
                        eventType TEXT NOT NULL, occurredAt TEXT NOT NULL,
                        localHour INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL,
                        sourceDevice TEXT NOT NULL, activeContext TEXT NOT NULL,
                        capacityContext TEXT NOT NULL, alertKind TEXT, deadlineBucket TEXT,
                        channel TEXT, responseLatencySeconds INTEGER, snoozeMinutes INTEGER,
                        recommendationId TEXT, optionCode TEXT, correctedAt TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_events_occurredAt ON recommendation_events(occurredAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_events_eventType_activeContext ON recommendation_events(eventType, activeContext)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recommendation_decisions (
                        id TEXT NOT NULL PRIMARY KEY, contractVersion INTEGER NOT NULL,
                        generatedAt TEXT NOT NULL, engineId TEXT NOT NULL,
                        ruleVersion TEXT NOT NULL, purpose TEXT NOT NULL,
                        sampleCount INTEGER NOT NULL, minimumSamples INTEGER NOT NULL,
                        fallback INTEGER NOT NULL, optionsJson TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_decisions_generatedAt ON recommendation_decisions(generatedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recommendation_decisions_purpose ON recommendation_decisions(purpose)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS recommendation_settings (
                        id TEXT NOT NULL PRIMARY KEY, personalizationEnabled INTEGER NOT NULL,
                        retentionDays INTEGER NOT NULL, capacityContext TEXT NOT NULL,
                        preferredSnoozeMinutes INTEGER, preferredChannel TEXT,
                        updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS personal_model_artifacts (
                        modelId TEXT NOT NULL, modelVersion TEXT NOT NULL,
                        contractVersion INTEGER NOT NULL, purpose TEXT NOT NULL,
                        runtime TEXT NOT NULL, featureContractVersion INTEGER NOT NULL,
                        artifactFormat TEXT NOT NULL, artifactJson TEXT NOT NULL,
                        artifactSha256 TEXT NOT NULL, trainedAt TEXT NOT NULL,
                        trainingSampleCount INTEGER NOT NULL, status TEXT NOT NULL,
                        evaluationSampleCount INTEGER NOT NULL,
                        top1Accuracy REAL NOT NULL, baselineTop1Accuracy REAL NOT NULL,
                        rollbackModelId TEXT, activatedAt TEXT, updatedAt TEXT NOT NULL,
                        PRIMARY KEY(modelId, modelVersion)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_model_artifacts_status ON personal_model_artifacts(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_model_artifacts_updatedAt ON personal_model_artifacts(updatedAt)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS personal_model_shadow_metrics (
                        modelId TEXT NOT NULL PRIMARY KEY, evaluatedCount INTEGER NOT NULL,
                        agreementCount INTEGER NOT NULL, lastRuleOption TEXT,
                        lastModelOption TEXT, updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_plans (
                        planDate TEXT NOT NULL PRIMARY KEY,
                        capacity TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        closedAt TEXT,
                        closingNote TEXT
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_plan_items (
                        planDate TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(planDate, taskId),
                        FOREIGN KEY(planDate) REFERENCES daily_plans(planDate)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(taskId) REFERENCES task_replicas(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_daily_plan_items_taskId ON daily_plan_items(taskId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_plan_items_planDate_role_position ON daily_plan_items(planDate, role, position)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS focus_selections (
                        singletonId INTEGER NOT NULL PRIMARY KEY,
                        taskId TEXT NOT NULL,
                        selectedAt TEXT NOT NULL,
                        FOREIGN KEY(taskId) REFERENCES task_replicas(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_focus_selections_taskId ON focus_selections(taskId)")
            }
        }
    }
}
