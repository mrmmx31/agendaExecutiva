package com.pessoal.agenda.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHealthConsent(value: HealthConsentEntity): Long

    @Upsert
    suspend fun upsertHealthConsent(value: HealthConsentEntity)

    @Query("SELECT * FROM health_consents WHERE category=:category")
    suspend fun healthConsent(category: String): HealthConsentEntity?

    @Query("SELECT * FROM health_consents ORDER BY category")
    suspend fun healthConsents(): List<HealthConsentEntity>

    @Upsert
    suspend fun upsertHealthIntake(value: HealthIntakeLogEntity)

    @Query("SELECT * FROM health_intake_logs WHERE id=:id")
    suspend fun healthIntake(id: String): HealthIntakeLogEntity?

    @Query("UPDATE health_intake_logs SET ciphertext='', iv='', revision=:revision, tombstone=1, updatedAt=:now WHERE id=:id AND tombstone=0")
    suspend fun tombstoneHealthIntake(id: String, revision: Long, now: String): Int

    @Upsert
    suspend fun upsertHealthSymptom(value: HealthSymptomLogEntity)

    @Query("SELECT * FROM health_symptom_logs WHERE id=:id")
    suspend fun healthSymptom(id: String): HealthSymptomLogEntity?

    @Query("UPDATE health_symptom_logs SET ciphertext='', iv='', revision=:revision, tombstone=1, updatedAt=:now WHERE id=:id AND tombstone=0")
    suspend fun tombstoneHealthSymptom(id: String, revision: Long, now: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHealthAudit(value: HealthChangeAuditEntity)

    @Query("SELECT * FROM health_change_audit WHERE entityId=:entityId ORDER BY revision")
    suspend fun healthAudit(entityId: String): List<HealthChangeAuditEntity>

    @Query("SELECT * FROM alert_definitions WHERE id=:id")
    suspend fun alertDefinition(id: String): AlertDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlertDefinition(value: AlertDefinitionEntity)

    @Query("SELECT * FROM alert_materializations WHERE alertId=:alertId")
    suspend fun alertMaterialization(alertId: String): AlertMaterializationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlertMaterialization(value: AlertMaterializationEntity)

    @Query("SELECT * FROM alert_deliveries WHERE id=:id")
    suspend fun alertDelivery(id: String): AlertDeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlertDelivery(value: AlertDeliveryEntity)

    @Query("SELECT * FROM alert_actions WHERE operationId=:operationId")
    suspend fun alertAction(operationId: String): AlertActionEntity?

    @Query("SELECT * FROM alert_actions WHERE alertId=:alertId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestAlertAction(alertId: String): AlertActionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlertAction(value: AlertActionEntity)

    @Query("SELECT * FROM sensory_profiles WHERE id=:id")
    suspend fun sensoryProfile(id: String): SensoryProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSensoryProfile(value: SensoryProfileEntity): Long

    @Upsert
    suspend fun upsertSensoryProfile(value: SensoryProfileEntity)

    @Query("UPDATE alert_materializations SET state='COMPLETED', completedAt=:now, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state!='COMPLETED'")
    suspend fun completeAlert(alertId: String, now: String): Int

    @Query("UPDATE alert_materializations SET state='SNOOZED', nextEligibleAt=:until, snoozeCount=snoozeCount+1, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state!='COMPLETED' AND snoozeCount<:maximumCount")
    suspend fun snoozeAlert(alertId: String, until: String, now: String, maximumCount: Int): Int

    @Query("UPDATE alert_materializations SET state='DELIVERED', deliveryCount=deliveryCount+1, lastDeliveryAt=:now, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state!='COMPLETED' AND deliveryCount<:maximumDeliveries")
    suspend fun markAlertDelivered(alertId: String, now: String, maximumDeliveries: Int): Int

    @Query("UPDATE alert_materializations SET state=:state, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state!='COMPLETED'")
    suspend fun markAlertDeliveryOutcome(alertId: String, state: String, now: String): Int

    @Query("SELECT COUNT(*) FROM alert_definitions")
    suspend fun alertDefinitionCount(): Int

    @Query("SELECT COUNT(*) FROM alert_deliveries")
    suspend fun alertDeliveryCount(): Int

    @Query("SELECT COUNT(*) FROM alert_actions")
    suspend fun alertActionCount(): Int

    @Query("SELECT MAX(lastDeliveryAt) FROM alert_materializations WHERE lastDeliveryAt IS NOT NULL")
    suspend fun lastSensoryDeliveryAt(): String?

    @Query("""
        SELECT alert_materializations.alertId AS alertId,
               alert_materializations.state AS state,
               alert_materializations.nextEligibleAt AS nextEligibleAt,
               alert_definitions.validUntil AS validUntil
        FROM alert_materializations
        JOIN alert_definitions ON alert_definitions.id=alert_materializations.alertId
        WHERE alert_materializations.state IN ('READY','SCHEDULED','SNOOZED')
    """)
    suspend fun alertSchedules(): List<AlertScheduleRow>

    @Query("""
        SELECT alert_materializations.alertId AS alertId,
               alert_materializations.state AS state,
               alert_materializations.nextEligibleAt AS nextEligibleAt,
               alert_definitions.validUntil AS validUntil
        FROM alert_materializations
        JOIN alert_definitions ON alert_definitions.id=alert_materializations.alertId
        WHERE alert_materializations.state IN ('SUPPRESSED','DELIVERY_FAILED')
    """)
    suspend fun reactivatableAlertSchedules(): List<AlertScheduleRow>

    @Query("UPDATE alert_materializations SET state='SCHEDULED', nextEligibleAt=:nextAt, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state NOT IN ('COMPLETED','CANCELLED','EXPIRED','DELIVERY_LIMIT_REACHED')")
    suspend fun scheduleAlertEvaluation(alertId: String, nextAt: String, now: String): Int

    @Query("UPDATE alert_materializations SET state=:state, updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state NOT IN ('COMPLETED','CANCELLED')")
    suspend fun updateAlertEvaluationState(alertId: String, state: String, now: String): Int

    @Query("UPDATE alert_materializations SET state='CANCELLED', updatedAt=:now, wearRevision=wearRevision+1 WHERE alertId=:alertId AND state NOT IN ('COMPLETED','CANCELLED')")
    suspend fun cancelAlert(alertId: String, now: String): Int

    @Query("SELECT * FROM mobile_metadata WHERE `key` = :key")
    suspend fun metadata(key: String): MobileMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMetadata(value: MobileMetadataEntity)

    @Query("SELECT * FROM task_replicas WHERE tombstone = 0 ORDER BY status, updatedAt DESC")
    fun observeTasks(): Flow<List<TaskReplicaEntity>>

    @Query("SELECT COUNT(*) FROM task_replicas")
    suspend fun taskCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTasks(values: List<TaskReplicaEntity>)

    @Upsert
    suspend fun upsertTasks(values: List<TaskReplicaEntity>)

    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeCaptures(): Flow<List<CaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCapture(value: CaptureEntity)

    @Query("SELECT COUNT(*) FROM protocol_templates")
    suspend fun protocolCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocol(value: ProtocolTemplateEntity)

    @Upsert
    suspend fun upsertProtocols(values: List<ProtocolTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocolSteps(values: List<ProtocolStepEntity>)

    @Upsert
    suspend fun upsertProtocolSteps(values: List<ProtocolStepEntity>)

    @Query("SELECT * FROM protocol_templates WHERE tombstone = 0 ORDER BY title")
    fun observeProtocols(): Flow<List<ProtocolTemplateEntity>>

    @Query("SELECT * FROM protocol_templates WHERE id = :id")
    suspend fun protocol(id: String): ProtocolTemplateEntity?

    @Query("SELECT * FROM protocol_steps WHERE protocolId = :protocolId ORDER BY position")
    suspend fun protocolSteps(protocolId: String): List<ProtocolStepEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocolRun(value: ProtocolRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocolRunSteps(values: List<ProtocolRunStepEntity>)

    @Query("SELECT * FROM protocol_runs WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveRun(): Flow<ProtocolRunEntity?>

    @Query("SELECT * FROM protocol_runs WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeRun(): ProtocolRunEntity?

    @Query("SELECT * FROM protocol_runs WHERE id = :runId")
    suspend fun protocolRun(runId: String): ProtocolRunEntity?

    @Query("""
        SELECT protocol_runs.id AS runId,
               protocol_runs.protocolId AS protocolId,
               protocol_templates.title AS protocolTitle,
               protocol_steps.id AS stepId,
               protocol_steps.position AS position,
               protocol_steps.label AS label,
               protocol_run_steps.completedAt AS completedAt
        FROM protocol_runs
        JOIN protocol_templates ON protocol_templates.id = protocol_runs.protocolId
        JOIN protocol_run_steps ON protocol_run_steps.runId = protocol_runs.id
        JOIN protocol_steps ON protocol_steps.id = protocol_run_steps.stepId
        WHERE protocol_runs.completedAt IS NULL
        ORDER BY protocol_runs.startedAt DESC, protocol_steps.position
    """)
    fun observeActiveRunSteps(): Flow<List<ActiveRunStepRow>>

    @Query("""
        SELECT protocol_runs.id AS runId,
               protocol_runs.protocolId AS protocolId,
               protocol_templates.title AS protocolTitle,
               protocol_steps.id AS stepId,
               protocol_steps.position AS position,
               protocol_steps.label AS label,
               protocol_run_steps.completedAt AS completedAt
        FROM protocol_runs
        JOIN protocol_templates ON protocol_templates.id = protocol_runs.protocolId
        JOIN protocol_run_steps ON protocol_run_steps.runId = protocol_runs.id
        JOIN protocol_steps ON protocol_steps.id = protocol_run_steps.stepId
        WHERE protocol_runs.id = :runId
        ORDER BY protocol_steps.position
    """)
    suspend fun runSteps(runId: String): List<ActiveRunStepRow>

    @Query("SELECT * FROM protocol_run_steps WHERE runId = :runId")
    fun observeRunSteps(runId: String): Flow<List<ProtocolRunStepEntity>>

    @Query("SELECT * FROM protocol_run_steps WHERE runId = :runId AND stepId = :stepId")
    suspend fun runStep(runId: String, stepId: String): ProtocolRunStepEntity?

    @Query("UPDATE protocol_run_steps SET completedAt = :completedAt WHERE runId = :runId AND stepId = :stepId AND completedAt IS NULL")
    suspend fun completeRunStep(runId: String, stepId: String, completedAt: String): Int

    @Query("UPDATE protocol_runs SET wearRevision=wearRevision+1, acknowledgedWearOperationId=:operationId WHERE id=:runId")
    suspend fun acknowledgeWearProtocolAction(runId: String, operationId: String): Int

    @Query("SELECT COUNT(*) FROM protocol_run_steps WHERE runId = :runId AND completedAt IS NULL")
    suspend fun incompleteRunStepCount(runId: String): Int

    @Query("UPDATE protocol_runs SET completedAt = :completedAt WHERE id = :runId AND completedAt IS NULL")
    suspend fun completeRun(runId: String, completedAt: String): Int

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM pending_operations WHERE deviceId = :deviceId")
    suspend fun nextSequence(deviceId: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingOperation(value: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations ORDER BY sequence DESC")
    fun observeOperations(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations WHERE status IN ('PENDING','RETRYABLE') ORDER BY sequence LIMIT :limit")
    suspend fun operationsForSync(limit: Int = 100): List<PendingOperationEntity>

    @Query("UPDATE pending_operations SET status='IN_FLIGHT', attemptCount=attemptCount+1, updatedAt=:now WHERE operationId IN (:ids) AND status IN ('PENDING','RETRYABLE')")
    suspend fun markInFlight(ids: List<String>, now: String): Int

    @Query("UPDATE pending_operations SET status='RETRYABLE', errorCode='TEMPORARY_FAILURE', updatedAt=:now WHERE operationId IN (:ids) AND status='IN_FLIGHT'")
    suspend fun markRetryable(ids: List<String>, now: String): Int

    @Query("UPDATE pending_operations SET status=:status, errorCode=:errorCode, serverRevision=:serverRevision, conflictId=:conflictId, updatedAt=:now WHERE operationId=:operationId AND status='IN_FLIGHT'")
    suspend fun applySyncResult(
        operationId: String,
        status: String,
        errorCode: String?,
        serverRevision: Long?,
        conflictId: String?,
        now: String,
    ): Int

    @Query("UPDATE captures SET acknowledgedAt=:now WHERE id=:captureId AND acknowledgedAt IS NULL")
    suspend fun acknowledgeCapture(captureId: String, now: String): Int

    @Query("UPDATE pending_operations SET status='RETRYABLE', errorCode='TEMPORARY_FAILURE', updatedAt=:now WHERE status='IN_FLIGHT'")
    suspend fun recoverInFlight(now: String): Int

    @Query("UPDATE pending_operations SET deviceId=:newDeviceId WHERE deviceId=:oldDeviceId")
    suspend fun replaceOperationDeviceId(oldDeviceId: String, newDeviceId: String): Int

    @Upsert
    suspend fun upsertConflicts(values: List<SyncConflictEntity>)

    @Query("SELECT * FROM sync_conflicts WHERE status='OPEN' ORDER BY createdAt")
    fun observeOpenConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE status='OPEN' ORDER BY createdAt")
    suspend fun openConflicts(): List<SyncConflictEntity>
}

data class ActiveRunStepRow(
    val runId: String,
    val protocolId: String,
    val protocolTitle: String,
    val stepId: String,
    val position: Int,
    val label: String,
    val completedAt: String?,
)

data class AlertScheduleRow(
    val alertId: String,
    val state: String,
    val nextEligibleAt: String,
    val validUntil: String,
)
