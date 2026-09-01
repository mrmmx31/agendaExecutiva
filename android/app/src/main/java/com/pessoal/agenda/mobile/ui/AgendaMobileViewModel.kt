package com.pessoal.agenda.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.CaptureEntity
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.data.local.SyncConflictEntity
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import com.pessoal.agenda.mobile.sync.HttpsSyncTransport
import com.pessoal.agenda.mobile.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MobileUiState(
    val tasks: List<TaskReplicaEntity> = emptyList(),
    val captures: List<CaptureEntity> = emptyList(),
    val protocols: List<ProtocolTemplateEntity> = emptyList(),
    val activeRunSteps: List<ActiveRunStepRow> = emptyList(),
    val operations: List<PendingOperationEntity> = emptyList(),
    val conflicts: List<SyncConflictEntity> = emptyList(),
    val busy: Boolean = false,
    val feedback: String? = null,
    val canSync: Boolean = false,
)

class AgendaMobileViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = DeviceCredentialStore(application)
    private val repository = OfflineRepository(
        MobileDatabase.get(application),
        deviceIdProvider = { credentialStore.deviceId },
    )
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val canSync = MutableStateFlow(credentialStore.syncBaseUrl() != null)

    private val queue = combine(repository.operations, MobileDatabase.get(application).offline().observeOpenConflicts()) {
            operations, conflicts -> operations to conflicts
    }

    private val content = combine(
        repository.tasks,
        repository.captures,
        repository.protocols,
        repository.activeRunSteps,
        queue,
    ) { tasks, captures, protocols, activeRunSteps, queue ->
        OfflineContent(tasks, captures, protocols, activeRunSteps, queue.first, queue.second)
    }

    val state: StateFlow<MobileUiState> = combine(
        content, busy, feedback, canSync,
    ) { content, busy, feedback, canSync ->
        MobileUiState(
            tasks = content.tasks,
            captures = content.captures,
            protocols = content.protocols,
            activeRunSteps = content.activeRunSteps,
            operations = content.operations,
            conflicts = content.conflicts,
            busy = busy,
            feedback = feedback,
            canSync = canSync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MobileUiState())

    init {
        viewModelScope.launch {
            runCatching {
                repository.alignDeviceIdentity()
                repository.initializeFictitiousData()
            }
                .onFailure { feedback.value = it.safeMessage("Não foi possível preparar os dados locais.") }
        }
    }

    fun saveCapture(text: String, onSaved: () -> Unit) = execute(
        successMessage = "Captura salva no telefone.",
        onSuccess = onSaved,
    ) { repository.createCapture(text) }

    fun startProtocol(protocolId: String) = execute(
        successMessage = "Protocolo iniciado offline.",
    ) { repository.startProtocol(protocolId) }

    fun completeStep(runId: String, stepId: String) = execute(
        successMessage = "Passo confirmado.",
    ) { repository.completeProtocolStep(runId, stepId) }

    fun syncNow() = execute(successMessage = "Sincronização concluída.") {
        check(canSync.value) { "Telefone ainda não pareado." }
        SyncRepository(
            MobileDatabase.get(getApplication()),
            HttpsSyncTransport(credentialStore),
        ).syncOnce()
    }

    fun clearFeedback() {
        feedback.value = null
    }

    private fun execute(
        successMessage: String,
        onSuccess: () -> Unit = {},
        action: suspend () -> Any,
    ) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            feedback.value = null
            runCatching { action() }
                .onSuccess {
                    feedback.value = successMessage
                    onSuccess()
                }
                .onFailure { feedback.value = it.safeMessage("A operação local não foi concluída.") }
            busy.value = false
        }
    }
}

private fun Throwable.safeMessage(fallback: String): String =
    if (this is IllegalArgumentException && !message.isNullOrBlank()) message!! else fallback

private data class OfflineContent(
    val tasks: List<TaskReplicaEntity>,
    val captures: List<CaptureEntity>,
    val protocols: List<ProtocolTemplateEntity>,
    val activeRunSteps: List<ActiveRunStepRow>,
    val operations: List<PendingOperationEntity>,
    val conflicts: List<SyncConflictEntity>,
)
