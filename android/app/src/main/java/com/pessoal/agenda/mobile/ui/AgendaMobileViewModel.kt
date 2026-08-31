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
    val busy: Boolean = false,
    val feedback: String? = null,
)

class AgendaMobileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OfflineRepository(MobileDatabase.get(application))
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)

    private val content = combine(
        repository.tasks,
        repository.captures,
        repository.protocols,
        repository.activeRunSteps,
        repository.operations,
    ) { tasks, captures, protocols, activeRunSteps, operations ->
        OfflineContent(tasks, captures, protocols, activeRunSteps, operations)
    }

    val state: StateFlow<MobileUiState> = combine(content, busy, feedback) { content, busy, feedback ->
        MobileUiState(
            tasks = content.tasks,
            captures = content.captures,
            protocols = content.protocols,
            activeRunSteps = content.activeRunSteps,
            operations = content.operations,
            busy = busy,
            feedback = feedback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MobileUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.initializeFictitiousData() }
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
)
