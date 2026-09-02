package com.pessoal.agenda.wear.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pessoal.agenda.wear.contract.WearContractCodec
import com.pessoal.agenda.wear.contract.WearDataPaths
import com.pessoal.agenda.wear.contract.WearProtocolCodec
import com.pessoal.agenda.wear.data.WearAlertStore
import com.pessoal.agenda.wear.data.WearDatabase
import com.pessoal.agenda.wear.data.WearDeviceIdentity
import com.pessoal.agenda.wear.data.WearProtocolStore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class WearStateListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) = runBlocking(Dispatchers.IO) {
        val store = store()
        val protocolStore = protocolStore()
        dataEvents.forEach { event ->
            val path = event.dataItem.uri.path.orEmpty()
            val alertId = WearDataPaths.alertId(path)
            val runId = WearDataPaths.protocolRunId(path)
            when {
                alertId != null && event.type == DataEvent.TYPE_CHANGED -> event.dataItem.data
                    ?.takeIf { it.size <= MAX_STATE_BYTES }
                    ?.let { runCatching { store.ingest(WearContractCodec.decodeState(it)) } }
                    ?.onFailure { Log.w(TAG, "Estado Wear inválido: ${it.javaClass.simpleName}") }
                alertId != null && event.type == DataEvent.TYPE_DELETED -> store.removeRemoteState(alertId)
                runId != null && event.type == DataEvent.TYPE_CHANGED -> event.dataItem.data
                    ?.takeIf { it.size <= MAX_STATE_BYTES }
                    ?.let { runCatching { protocolStore.ingest(WearProtocolCodec.decodeState(it)) } }
                    ?.onFailure { Log.w(TAG, "Protocolo Wear inválido: ${it.javaClass.simpleName}") }
                runId != null && event.type == DataEvent.TYPE_DELETED -> protocolStore.removeRemoteState(runId)
            }
        }
    }

    private fun store(): WearAlertStore {
        val identity = WearDeviceIdentity(this)
        return WearAlertStore(WearDatabase.get(this), { identity.deviceId })
    }

    private fun protocolStore(): WearProtocolStore {
        val identity = WearDeviceIdentity(this)
        return WearProtocolStore(WearDatabase.get(this), { identity.deviceId })
    }

    private companion object {
        const val TAG = "AgendaWearListener"
        const val MAX_STATE_BYTES = 8 * 1024
    }
}

class WearOutboxScheduler(private val context: Context) {
    fun enqueue() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WearOutboxWorker>().build(),
        )
    }

    private companion object { const val WORK_NAME = "agenda-wear-outbox" }
}

class WearOutboxWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val identity = WearDeviceIdentity(applicationContext)
        val store = WearAlertStore(WearDatabase.get(applicationContext), { identity.deviceId })
        val protocolStore = WearProtocolStore(WearDatabase.get(applicationContext), { identity.deviceId })
        return try {
            val client = Wearable.getDataClient(applicationContext)
            store.actionsForSync().forEach { action ->
                val request = PutDataRequest.create(WearDataPaths.action(action.operationId))
                    .setData(action.payload)
                    .setUrgent()
                client.putDataItem(request).awaitResult()
                store.markActionStored(action.operationId)
            }
            protocolStore.actionsForSync().forEach { action ->
                val request = PutDataRequest.create(WearDataPaths.protocolAction(action.operationId))
                    .setData(action.payload)
                    .setUrgent()
                client.putDataItem(request).awaitResult()
                protocolStore.markActionStored(action.operationId)
            }
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Outbox Wear pendente: ${error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private companion object { const val TAG = "AgendaWearOutbox" }
}

class WearInitialStateReader(private val context: Context) {
    suspend fun refresh(store: WearAlertStore) {
        read(WearDataPaths.ALERT_STATE_PREFIX) { payload -> store.ingest(WearContractCodec.decodeState(payload)) }
    }

    suspend fun refresh(store: WearAlertStore, protocolStore: WearProtocolStore) {
        read(WearDataPaths.ALERT_STATE_PREFIX) { payload -> store.ingest(WearContractCodec.decodeState(payload)) }
        read(WearDataPaths.PROTOCOL_STATE_PREFIX) { payload -> protocolStore.ingest(WearProtocolCodec.decodeState(payload)) }
    }

    private suspend fun read(prefix: String, ingest: suspend (ByteArray) -> Unit) {
        val items = Wearable.getDataClient(context)
            .getDataItems(Uri.parse("wear://*$prefix"), DataClient.FILTER_PREFIX)
            .awaitResult()
        try {
            items.forEach { item -> item.data?.takeIf { it.size <= MAX_STATE_BYTES }?.let { runCatching { ingest(it) } } }
        } finally { items.release() }
    }

    private companion object { const val MAX_STATE_BYTES = 8 * 1024 }
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
