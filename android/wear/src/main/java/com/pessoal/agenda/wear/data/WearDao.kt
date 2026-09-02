package com.pessoal.agenda.wear.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WearDao {
    @Query("SELECT * FROM wear_alerts WHERE alertId=:alertId")
    suspend fun alert(alertId: String): WearAlertEntity?

    @Query("SELECT * FROM wear_alerts ORDER BY scheduledAt, alertId")
    fun observeAlerts(): Flow<List<WearAlertEntity>>

    @Upsert
    suspend fun upsertAlert(alert: WearAlertEntity)

    @Query("DELETE FROM wear_alerts WHERE alertId=:alertId")
    suspend fun deleteAlert(alertId: String): Int

    @Query("DELETE FROM wear_alerts WHERE validUntil<=:now AND alertId NOT IN (SELECT alertId FROM wear_action_outbox)")
    suspend fun deleteExpired(now: String): Int

    @Query("SELECT * FROM wear_action_outbox WHERE operationId=:operationId")
    suspend fun action(operationId: String): WearActionOutboxEntity?

    @Query("SELECT * FROM wear_action_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun actionsForSync(limit: Int = 20): List<WearActionOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAction(action: WearActionOutboxEntity)

    @Query("UPDATE wear_action_outbox SET state='STORED', attemptCount=attemptCount+1, updatedAt=:now WHERE operationId=:operationId")
    suspend fun markActionStored(operationId: String, now: String): Int

    @Query("DELETE FROM wear_action_outbox WHERE operationId=:operationId")
    suspend fun deleteAction(operationId: String): Int

    @Query("SELECT COUNT(*) FROM wear_action_outbox")
    suspend fun actionCount(): Int

    @Query("SELECT COUNT(*) FROM wear_alerts")
    suspend fun alertCount(): Int

    @Query("SELECT * FROM wear_protocol_states WHERE runId=:runId")
    suspend fun protocolState(runId: String): WearProtocolStateEntity?

    @Query("SELECT * FROM wear_protocol_states ORDER BY updatedAt DESC, runId")
    fun observeProtocolStates(): Flow<List<WearProtocolStateEntity>>

    @Upsert
    suspend fun upsertProtocolState(state: WearProtocolStateEntity)

    @Query("DELETE FROM wear_protocol_states WHERE runId=:runId")
    suspend fun deleteProtocolState(runId: String): Int

    @Query("SELECT * FROM wear_protocol_action_outbox WHERE operationId=:operationId")
    suspend fun protocolAction(operationId: String): WearProtocolActionOutboxEntity?

    @Query("SELECT * FROM wear_protocol_action_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun protocolActionsForSync(limit: Int = 20): List<WearProtocolActionOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProtocolAction(action: WearProtocolActionOutboxEntity)

    @Query("UPDATE wear_protocol_action_outbox SET state='STORED', attemptCount=attemptCount+1, updatedAt=:now WHERE operationId=:operationId")
    suspend fun markProtocolActionStored(operationId: String, now: String): Int

    @Query("DELETE FROM wear_protocol_action_outbox WHERE operationId=:operationId")
    suspend fun deleteProtocolAction(operationId: String): Int

    @Query("SELECT COUNT(*) FROM wear_protocol_action_outbox WHERE runId=:runId")
    suspend fun protocolActionCount(runId: String): Int
}
