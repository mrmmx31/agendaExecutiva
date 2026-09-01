package com.pessoal.agenda.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.CaptureEntity
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.data.local.SyncConflictEntity
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import com.pessoal.agenda.mobile.pairing.HttpsPairingTransport
import com.pessoal.agenda.mobile.pairing.PairingClient
import com.pessoal.agenda.mobile.pairing.PairingException
import com.pessoal.agenda.mobile.sync.HttpsSyncTransport
import com.pessoal.agenda.mobile.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val pairingInProgress: Boolean = false,
    val pairingCompletion: Long = 0,
)

class AgendaMobileViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = DeviceCredentialStore(application)
    private val repository = OfflineRepository(
        MobileDatabase.get(application),
        deviceIdProvider = { credentialStore.deviceId },
    )
    private val alertStore = AlertStore(MobileDatabase.get(application))
    private val alertScheduling = AlertSchedulingCoordinator(application, alertStore)
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val canSync = MutableStateFlow(false)
    private val pairingInProgress = MutableStateFlow(false)
    private val pairingCompletion = MutableStateFlow(0L)
    private var pairingJob: Job? = null
    private var pairingClient: PairingClient? = null

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

    private val pairingState = combine(pairingInProgress, pairingCompletion) { inProgress, completion ->
        PairingUiState(inProgress, completion)
    }

    val state: StateFlow<MobileUiState> = combine(
        content, busy, feedback, canSync, pairingState,
    ) { content, busy, feedback, canSync, pairingState ->
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
            pairingInProgress = pairingState.inProgress,
            pairingCompletion = pairingState.completion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MobileUiState())

    init {
        viewModelScope.launch {
            runCatching {
                canSync.value = withContext(Dispatchers.IO) {
                    credentialStore.syncBaseUrl() != null
                }
                repository.alignDeviceIdentity()
                repository.initializeFictitiousData()
                alertScheduling.reconcile()
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

    fun pairDesktop(invitation: String, code: String) {
        if (busy.value) return
        val client = PairingClient(credentialStore, HttpsPairingTransport())
        pairingClient = client
        pairingJob = viewModelScope.launch {
            busy.value = true
            pairingInProgress.value = true
            feedback.value = null
            try {
                client.pair(invitation, code)
                repository.alignDeviceIdentity()
                canSync.value = true
                pairingCompletion.value += 1
                feedback.value = "Desktop conectado."
            } catch (error: Exception) {
                if (pairingInProgress.value) {
                    feedback.value = error.safeMessage("Não foi possível conectar ao desktop.")
                }
            } finally {
                pairingClient = null
                pairingJob = null
                pairingInProgress.value = false
                busy.value = false
            }
        }
    }

    fun cancelPairing() {
        if (!pairingInProgress.value) return
        pairingInProgress.value = false
        pairingClient?.cancel()
        pairingJob?.cancel()
        pairingClient = null
        pairingJob = null
        busy.value = false
        feedback.value = "Pareamento cancelado."
    }

    fun clearFeedback() {
        feedback.value = null
    }

    fun acknowledgePairingCompletion() {
        pairingCompletion.value = 0
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
    if ((this is IllegalArgumentException || this is PairingException) && !message.isNullOrBlank()) message!! else fallback

private data class OfflineContent(
    val tasks: List<TaskReplicaEntity>,
    val captures: List<CaptureEntity>,
    val protocols: List<ProtocolTemplateEntity>,
    val activeRunSteps: List<ActiveRunStepRow>,
    val operations: List<PendingOperationEntity>,
    val conflicts: List<SyncConflictEntity>,
)

private data class PairingUiState(val inProgress: Boolean, val completion: Long)
