package com.pessoal.agenda.mobile.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.alert.scheduling.AlertSchedulingCoordinator
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.alert.SnoozePolicy
import com.pessoal.agenda.mobile.alert.notification.AndroidAlertNotificationPublisher
import com.pessoal.agenda.mobile.alert.output.AndroidSensoryOutput
import com.pessoal.agenda.mobile.alert.output.AudioRouteStatus
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
import java.time.Instant
import java.time.ZoneId

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
    val visualAlertsEnabled: Boolean = false,
    val sensorySettings: SensorySettingsUiState = SensorySettingsUiState(),
)

data class SensorySettingsUiState(
    val profile: SensoryProfile = SensoryProfile.installationDefault(),
    val snoozePolicy: SnoozePolicy = SnoozePolicy.cautiousDefault(),
    val routeStatus: AudioRouteStatus = AudioRouteStatus(
        policy = SensoryProfile.installationDefault().audioRoute,
        effectiveLabel = "Rota automática do sistema",
        fallbackReason = null,
        headphonesAvailable = false,
        phoneSpeakerAvailable = true,
    ),
    val audioTestRunning: Boolean = false,
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
    private val visualAlertsEnabled = MutableStateFlow(false)
    private val sensorySettings = MutableStateFlow(SensorySettingsUiState())
    private val sensoryOutput = AndroidSensoryOutput(application)
    private var audioTestJob: Job? = null
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

    private val pairingState = combine(
        pairingInProgress,
        pairingCompletion,
        visualAlertsEnabled,
        sensorySettings,
    ) { inProgress, completion, alertsEnabled, settings ->
        PairingUiState(inProgress, completion, alertsEnabled, settings)
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
            visualAlertsEnabled = pairingState.visualAlertsEnabled,
            sensorySettings = pairingState.sensorySettings,
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
                val storedProfile = alertStore.ensureInstallationProfile()
                visualAlertsEnabled.value = storedProfile.profile.globalEnabled && notificationsAllowed()
                sensorySettings.value = storedProfile.settingsState()
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

    fun setVisualAlertsEnabled(enabled: Boolean) = execute(
        successMessage = if (enabled) "Alertas visuais ativados." else "Alertas visuais desativados.",
    ) {
        val stored = alertStore.ensureInstallationProfile()
        val updatedProfile = stored.profile.copy(globalEnabled = enabled)
        alertStore.saveProfile(
            updatedProfile,
            stored.snoozePolicy,
        )
        visualAlertsEnabled.value = enabled
        sensorySettings.value = sensorySettings.value.copy(profile = updatedProfile)
        if (enabled) {
            alertScheduling.reactivate()
        } else {
            audioTestJob?.cancel()
            alertScheduling.pause()
            AndroidAlertNotificationPublisher(getApplication()).cancelAllVisualAlerts()
        }
    }

    fun notificationPermissionDenied() {
        visualAlertsEnabled.value = false
        feedback.value = "Permissão não concedida; notificações visuais continuam indisponíveis."
    }

    fun saveSensorySettings(profile: SensoryProfile, snoozePolicy: SnoozePolicy) = execute(
        successMessage = "Perfil sensorial salvo.",
    ) {
        val current = alertStore.ensureInstallationProfile()
        val effective = profile.copy(globalEnabled = current.profile.globalEnabled)
        alertStore.saveProfile(effective, snoozePolicy)
        sensorySettings.value = SensorySettingsUiState(
            profile = effective,
            snoozePolicy = snoozePolicy,
            routeStatus = sensoryOutput.routeStatus(effective.audioRoute),
        )
        if (effective.globalEnabled) alertScheduling.reactivate() else alertScheduling.pause()
    }

    fun pauseSensoryAlerts(minutes: Int?) = execute(
        successMessage = if (minutes == null) "Alertas retomados." else "Alertas pausados temporariamente.",
    ) {
        val stored = alertStore.ensureInstallationProfile()
        val pausedUntil = minutes?.let { Instant.now().plusSeconds(it * 60L).toString() }
        val profile = stored.profile.copy(pausedUntil = pausedUntil)
        alertStore.saveProfile(profile, stored.snoozePolicy)
        audioTestJob?.cancel()
        alertScheduling.pause()
        AndroidAlertNotificationPublisher(getApplication()).cancelAllVisualAlerts()
        sensorySettings.value = SensorySettingsUiState(
            profile = profile,
            snoozePolicy = stored.snoozePolicy,
            routeStatus = sensoryOutput.routeStatus(profile.audioRoute),
        )
        if (profile.globalEnabled) alertScheduling.reactivate()
    }

    fun refreshAudioRoute() {
        val current = sensorySettings.value
        sensorySettings.value = current.copy(routeStatus = sensoryOutput.routeStatus(current.profile.audioRoute))
    }

    fun toggleAudioTest() {
        audioTestJob?.let {
            it.cancel()
            audioTestJob = null
            sensorySettings.value = sensorySettings.value.copy(audioTestRunning = false)
            feedback.value = "Teste de áudio interrompido."
            return
        }
        val profile = sensorySettings.value.profile
        if (!profile.globalEnabled || SensoryChannel.AUDIO !in profile.enabledChannels) {
            feedback.value = "Ative os alertas e o canal de áudio antes do teste."
            return
        }
        val now = Instant.now()
        if (profile.pausedUntil?.let(Instant::parse)?.isAfter(now) == true) {
            feedback.value = "Os alertas estão pausados; retome-os antes do teste."
            return
        }
        if (profile.quietHours?.contains(now.atZone(ZoneId.systemDefault()).toLocalTime()) == true) {
            feedback.value = "Teste bloqueado pelo horário silencioso configurado."
            return
        }
        audioTestJob = viewModelScope.launch {
            sensorySettings.value = sensorySettings.value.copy(audioTestRunning = true)
            try {
                val result = withContext(Dispatchers.IO) { sensoryOutput.testTone(profile.audioRoute) }
                result.routeStatus?.let { status ->
                    sensorySettings.value = sensorySettings.value.copy(routeStatus = status)
                }
                feedback.value = when {
                    SensoryChannel.AUDIO in result.deliveredChannels -> "Teste de áudio concluído."
                    result.reason == com.pessoal.agenda.mobile.data.AlertDeliveryReason.SYSTEM_POLICY ->
                        "Teste bloqueado pelo modo silencioso ou Não perturbe."
                    result.reason == com.pessoal.agenda.mobile.data.AlertDeliveryReason.SENSORY_OVERLAP ->
                        "Já existe uma saída sensorial em andamento."
                    else -> "Não foi possível reproduzir o teste na rota atual."
                }
            } finally {
                audioTestJob = null
                sensorySettings.value = sensorySettings.value.copy(audioTestRunning = false)
            }
        }
    }

    private fun com.pessoal.agenda.mobile.data.StoredSensoryProfile.settingsState() = SensorySettingsUiState(
        profile = profile,
        snoozePolicy = snoozePolicy,
        routeStatus = sensoryOutput.routeStatus(profile.audioRoute),
    )

    private fun notificationsAllowed(): Boolean {
        val application = getApplication<Application>()
        val manager = application.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED)
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

private data class PairingUiState(
    val inProgress: Boolean,
    val completion: Long,
    val visualAlertsEnabled: Boolean,
    val sensorySettings: SensorySettingsUiState,
)
