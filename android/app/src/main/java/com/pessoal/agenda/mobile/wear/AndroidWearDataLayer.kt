package com.pessoal.agenda.mobile.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.notification.AlertActionProcessor
import com.pessoal.agenda.mobile.alert.notification.AlertNotificationAction
import com.pessoal.agenda.mobile.alert.notification.AndroidAlertNotificationPublisher
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.contract.WearAlertAction
import com.pessoal.agenda.wear.contract.WearContractCodec
import com.pessoal.agenda.wear.contract.WearDataPaths
import com.pessoal.agenda.wear.contract.WearProtocolCodec
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

enum class WearStatePublishResult { STORED, NOT_ELIGIBLE, UNAVAILABLE }

interface AlertWearPublisher {
    suspend fun publish(alertId: String): WearStatePublishResult
}

object NoOpAlertWearPublisher : AlertWearPublisher {
    override suspend fun publish(alertId: String) = WearStatePublishResult.NOT_ELIGIBLE
}

class AndroidWearProtocolPublisher(
    private val repository: OfflineRepository,
    private val client: WearStateDataClient,
) {
    constructor(context: Context, repository: OfflineRepository) : this(
        repository,
        PlayServicesWearStateDataClient(context.applicationContext),
    )

    suspend fun publish(runId: String): WearStatePublishResult {
        val state = repository.protocolWearState(runId) ?: return WearStatePublishResult.NOT_ELIGIBLE
        return try {
            client.put(WearDataPaths.protocolState(runId), WearProtocolCodec.encodeState(state))
            WearStatePublishResult.STORED
        } catch (error: Exception) {
            Log.w("AgendaWearProtocol", "Etapa Wear não armazenada: ${error.javaClass.simpleName}")
            WearStatePublishResult.UNAVAILABLE
        }
    }
}

interface WearStateDataClient {
    suspend fun put(path: String, payload: ByteArray)
}

class AndroidWearStatePublisher(
    private val store: AlertStore,
    private val client: WearStateDataClient,
) : AlertWearPublisher {
    constructor(context: Context, store: AlertStore) : this(
        store,
        PlayServicesWearStateDataClient(context.applicationContext),
    )

    override suspend fun publish(alertId: String): WearStatePublishResult {
        val state = store.wearState(alertId) ?: return WearStatePublishResult.NOT_ELIGIBLE
        return try {
            client.put(WearDataPaths.alertState(alertId), WearContractCodec.encodeState(state))
            WearStatePublishResult.STORED
        } catch (error: Exception) {
            Log.w(TAG, "Estado Wear não armazenado: ${error.javaClass.simpleName}")
            WearStatePublishResult.UNAVAILABLE
        }
    }

    private companion object { const val TAG = "AgendaWearState" }
}

class PlayServicesWearStateDataClient(context: Context) : WearStateDataClient {
    private val client = Wearable.getDataClient(context)

    override suspend fun put(path: String, payload: ByteArray) {
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Payload Wear excede o limite interno." }
        val request = PutDataRequest.create(path).setData(payload).setUrgent()
        client.putDataItem(request).awaitResult()
    }

    private companion object { const val MAX_PAYLOAD_BYTES = 8 * 1024 }
}

class AndroidWearStateCleaner(context: Context) {
    private val client = Wearable.getDataClient(context.applicationContext)

    suspend fun clearAll(): WearStatePublishResult = try {
        val uri = Uri.parse("wear://*${WearDataPaths.ALERT_STATE_PREFIX}")
        client.deleteDataItems(uri, DataClient.FILTER_PREFIX).awaitResult()
        WearStatePublishResult.STORED
    } catch (error: Exception) {
        Log.w(TAG, "Estados Wear não removidos: ${error.javaClass.simpleName}")
        WearStatePublishResult.UNAVAILABLE
    }

    private companion object { const val TAG = "AgendaWearState" }
}

class PhoneWearActionEnqueuer(private val context: Context) {
    fun enqueue(uri: Uri, payload: ByteArray?) {
        val operationId = WearDataPaths.operationId(uri.path.orEmpty()) ?: return
        val action = payload?.takeIf { it.size <= MAX_ACTION_BYTES }
            ?.let { runCatching { WearContractCodec.decodeAction(it) }.getOrNull() }
        if (action == null || action.operationId != operationId) {
            Wearable.getDataClient(context).deleteDataItems(uri, DataClient.FILTER_LITERAL)
            return
        }
        val request = OneTimeWorkRequestBuilder<PhoneWearActionWorker>()
            .setInputData(workDataOf(
                PhoneWearActionWorker.URI_KEY to uri.toString(),
                PhoneWearActionWorker.PAYLOAD_KEY to payload,
            ))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PhoneWearActionWorker.uniqueName(operationId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object { const val MAX_ACTION_BYTES = 4 * 1024 }
}

class PhoneWearActionReconciler(private val context: Context) {
    suspend fun reconcile() {
        val client = Wearable.getDataClient(context.applicationContext)
        listOf(WearDataPaths.ACTION_PREFIX, WearDataPaths.PROTOCOL_ACTION_PREFIX).forEach { prefix ->
            val items = client.getDataItems(Uri.parse("wear://*$prefix"), DataClient.FILTER_PREFIX).awaitResult()
            try {
                val alertEnqueuer = PhoneWearActionEnqueuer(context.applicationContext)
                val protocolEnqueuer = PhoneWearProtocolActionEnqueuer(context.applicationContext)
                items.forEach {
                    alertEnqueuer.enqueue(it.uri, it.data)
                    protocolEnqueuer.enqueue(it.uri, it.data)
                }
            } finally {
                items.release()
            }
        }
    }
}

class PhoneWearListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val alertEnqueuer = PhoneWearActionEnqueuer(this)
        val protocolEnqueuer = PhoneWearProtocolActionEnqueuer(this)
        dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .forEach { event ->
                alertEnqueuer.enqueue(event.dataItem.uri, event.dataItem.data)
                protocolEnqueuer.enqueue(event.dataItem.uri, event.dataItem.data)
            }
    }
}

class PhoneWearProtocolActionEnqueuer(private val context: Context) {
    fun enqueue(uri: Uri, payload: ByteArray?) {
        val operationId = WearDataPaths.protocolOperationId(uri.path.orEmpty()) ?: return
        val action = payload?.takeIf { it.size <= MAX_ACTION_BYTES }
            ?.let { runCatching { WearProtocolCodec.decodeAction(it) }.getOrNull() }
        if (action == null || action.operationId != operationId) {
            Wearable.getDataClient(context).deleteDataItems(uri, DataClient.FILTER_LITERAL)
            return
        }
        val request = OneTimeWorkRequestBuilder<PhoneWearProtocolActionWorker>()
            .setInputData(workDataOf(
                PhoneWearProtocolActionWorker.URI_KEY to uri.toString(),
                PhoneWearProtocolActionWorker.PAYLOAD_KEY to payload,
            ))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PhoneWearProtocolActionWorker.uniqueName(operationId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object { const val MAX_ACTION_BYTES = 4 * 1024 }
}

class PhoneWearProtocolActionWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI_KEY)?.let(Uri::parse)
            ?: return Result.failure(errorData("INVALID_URI"))
        val payload = inputData.getByteArray(PAYLOAD_KEY)
            ?: return Result.failure(errorData("INVALID_PAYLOAD"))
        return try {
            val operationId = WearDataPaths.protocolOperationId(uri.path.orEmpty())
                ?: return Result.failure(errorData("INVALID_PATH"))
            val action = WearProtocolCodec.decodeAction(payload)
            require(action.operationId == operationId) { "Operação e caminho divergentes." }
            val credentials = DeviceCredentialStore(applicationContext)
            val repository = OfflineRepository(
                MobileDatabase.get(applicationContext),
                deviceIdProvider = { credentials.deviceId },
            )
            repository.completeProtocolStep(action.runId, action.stepId, action.operationId)
            if (AndroidWearProtocolPublisher(applicationContext, repository).publish(action.runId) !=
                WearStatePublishResult.STORED
            ) {
                return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
                else Result.failure(errorData("WEAR_ACK_FAILED"))
            }
            Wearable.getDataClient(applicationContext)
                .deleteDataItems(uri, DataClient.FILTER_LITERAL)
                .awaitResult()
            Result.success()
        } catch (error: IllegalArgumentException) {
            Result.failure(errorData("INVALID_ACTION"))
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
            else Result.failure(errorData("WEAR_PROTOCOL_ACTION_FAILED"))
        }
    }

    private fun errorData(code: String): Data = workDataOf(ERROR_KEY to code)

    companion object {
        const val URI_KEY = "wear_protocol_action_uri"
        const val PAYLOAD_KEY = "wear_protocol_action_payload"
        const val ERROR_KEY = "error_code"
        private const val MAX_ATTEMPTS = 5
        fun uniqueName(operationId: String): String = "agenda-wear-protocol-action-${UUID.fromString(operationId)}"
    }
}

class PhoneWearActionWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(URI_KEY)?.let(Uri::parse)
            ?: return Result.failure(errorData("INVALID_URI"))
        val payload = inputData.getByteArray(PAYLOAD_KEY)
            ?: return Result.failure(errorData("INVALID_PAYLOAD"))
        return try {
            val operationId = WearDataPaths.operationId(uri.path.orEmpty())
                ?: return Result.failure(errorData("INVALID_PATH"))
            val action = WearContractCodec.decodeAction(payload)
            require(action.operationId == operationId) { "Operação e caminho divergentes." }
            val store = AlertStore(MobileDatabase.get(applicationContext))
            val notificationAction = action.notificationAction()
            AlertActionProcessor(
                store = store,
                scheduling = AlertSchedulingCoordinator(applicationContext, store),
                publisher = AndroidAlertNotificationPublisher(applicationContext),
                deviceIdProvider = { DeviceCredentialStore(applicationContext).deviceId },
            ).process(notificationAction)
            if (AndroidWearStatePublisher(applicationContext, store).publish(action.alertId) !=
                WearStatePublishResult.STORED
            ) {
                return if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
                else Result.failure(errorData("WEAR_ACK_FAILED"))
            }
            Wearable.getDataClient(applicationContext)
                .deleteDataItems(uri, DataClient.FILTER_LITERAL)
                .awaitResult()
            Result.success()
        } catch (error: IllegalArgumentException) {
            Result.failure(errorData("INVALID_ACTION"))
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
            else Result.failure(errorData("WEAR_ACTION_FAILED"))
        }
    }

    private fun WearAlertAction.notificationAction() = AlertNotificationAction(
        operationId = operationId,
        alertId = alertId,
        action = when (action) {
            WearActionType.COMPLETE -> AlertActionType.COMPLETE
            WearActionType.SNOOZE -> AlertActionType.SNOOZE
        },
        occurredAt = Instant.parse(occurredAt),
        snoozeUntil = snoozeUntil?.let(Instant::parse),
        sourceDeviceId = sourceDeviceId,
    )

    private fun errorData(code: String): Data = workDataOf(ERROR_KEY to code)

    companion object {
        const val URI_KEY = "wear_action_uri"
        const val PAYLOAD_KEY = "wear_action_payload"
        const val ERROR_KEY = "error_code"
        private const val MAX_ATTEMPTS = 5
        fun uniqueName(operationId: String): String = "agenda-wear-action-${UUID.fromString(operationId)}"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> continuation.resume(task.result)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Falha Data Layer."))
        }
    }
}
