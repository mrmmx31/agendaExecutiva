package com.pessoal.agenda.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {
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

    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeCaptures(): Flow<List<CaptureEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCapture(value: CaptureEntity)

    @Query("SELECT COUNT(*) FROM protocol_templates")
    suspend fun protocolCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocol(value: ProtocolTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocolSteps(values: List<ProtocolStepEntity>)

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

    @Query("SELECT * FROM protocol_run_steps WHERE runId = :runId")
    fun observeRunSteps(runId: String): Flow<List<ProtocolRunStepEntity>>

    @Query("SELECT * FROM protocol_run_steps WHERE runId = :runId AND stepId = :stepId")
    suspend fun runStep(runId: String, stepId: String): ProtocolRunStepEntity?

    @Query("UPDATE protocol_run_steps SET completedAt = :completedAt WHERE runId = :runId AND stepId = :stepId AND completedAt IS NULL")
    suspend fun completeRunStep(runId: String, stepId: String, completedAt: String): Int

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
