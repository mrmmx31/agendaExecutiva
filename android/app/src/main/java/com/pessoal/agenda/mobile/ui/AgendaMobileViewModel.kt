package com.pessoal.agenda.mobile.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
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
import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import com.pessoal.agenda.mobile.pairing.HttpsPairingTransport
import com.pessoal.agenda.mobile.pairing.PairingClient
import com.pessoal.agenda.mobile.pairing.PairingException
import com.pessoal.agenda.mobile.sync.HttpsSyncTransport
import com.pessoal.agenda.mobile.sync.SyncRepository
import com.pessoal.agenda.mobile.wear.AndroidWearStateCleaner
import com.pessoal.agenda.mobile.wear.AndroidWearProtocolPublisher
import com.pessoal.agenda.mobile.health.AndroidKeystoreHealthDataCipher
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.HealthStore
import com.pessoal.agenda.mobile.recommendation.RecommendationStore
import com.pessoal.agenda.mobile.recommendation.DeterministicRecommendationEngine
import com.pessoal.agenda.mobile.recommendation.RecommendationEngine
import com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext
import com.pessoal.agenda.mobile.recommendation.RecommendationCapacityContext
import com.pessoal.agenda.mobile.recommendation.RecommendationContext
import com.pessoal.agenda.mobile.recommendation.RecommendationOption
import com.pessoal.agenda.mobile.recommendation.RecommendationPurpose
import com.pessoal.agenda.mobile.recommendation.RecommendationSettings
import com.pessoal.agenda.mobile.recommendation.RecommendationStatistics
import com.pessoal.agenda.mobile.recommendation.RecommendationStatisticsCalculator
import com.pessoal.agenda.mobile.recommendation.ShadowMetrics
import com.pessoal.agenda.mobile.recommendation.ShadowMetricsAccumulator
import com.pessoal.agenda.mobile.recommendation.ShadowingRecommendationEngine
import com.pessoal.agenda.mobile.health.IntakeInput
import com.pessoal.agenda.mobile.health.SymptomInput
import com.pessoal.agenda.mobile.health.VersionedHealthRecord
import com.pessoal.agenda.mobile.health.HealthSummary
import com.pessoal.agenda.mobile.health.connect.AndroidHealthConnectGateway
import com.pessoal.agenda.mobile.health.connect.HealthConnectImportCoordinator
import com.pessoal.agenda.mobile.health.connect.HealthConnectStatus
import com.pessoal.agenda.mobile.health.report.HealthReportBuilder
import com.pessoal.agenda.mobile.health.report.HealthReportExporter
import com.pessoal.agenda.mobile.health.report.HealthReportFormat
import com.pessoal.agenda.mobile.health.report.HealthReportReview
import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
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
    val health: HealthUiState = HealthUiState(),
    val recommendation: RecommendationUiState = RecommendationUiState(),
)

data class RecommendationUiState(
    val settings: RecommendationSettings = RecommendationSettings(
        personalizationEnabled = false,
        retentionDays = RecommendationStore.DEFAULT_RETENTION_DAYS,
        capacityContext = RecommendationCapacityContext.STANDARD,
        preferredSnoozeMinutes = null,
        preferredChannel = null,
    ),
    val events: List<RecommendationEventEntity> = emptyList(),
    val statistics: RecommendationStatistics = RecommendationStatistics(),
    val baselineOptions: List<RecommendationOption> = emptyList(),
    val shadowMetrics: ShadowMetrics = ShadowMetrics(0, 0),
)

data class HealthUiState(
    val consents: List<HealthConsentEntity> = emptyList(),
    val intakes: List<VersionedHealthRecord<IntakeInput>> = emptyList(),
    val symptoms: List<VersionedHealthRecord<SymptomInput>> = emptyList(),
    val summaries: List<HealthSummary> = emptyList(),
    val connectStatus: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val report: HealthReportReview = HealthReportReview(),
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
    private val healthStore = HealthStore(
        MobileDatabase.get(application),
        AndroidKeystoreHealthDataCipher(),
    )
    private val recommendationStore = RecommendationStore(MobileDatabase.get(application))
    private val shadowMetrics = ShadowMetricsAccumulator()
    private val recommendationEngine: RecommendationEngine = ShadowingRecommendationEngine(
        primary = DeterministicRecommendationEngine(),
        onComparison = shadowMetrics::record,
    )
    private val healthConnect = AndroidHealthConnectGateway(application)
    private val healthImporter = HealthConnectImportCoordinator(healthConnect, healthStore)
    private val healthReportBuilder = HealthReportBuilder()
    private val alertScheduling by lazy(LazyThreadSafetyMode.NONE) {
        AlertSchedulingCoordinator(application, alertStore)
    }
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val canSync = MutableStateFlow(false)
    private val pairingInProgress = MutableStateFlow(false)
    private val pairingCompletion = MutableStateFlow(0L)
    private val visualAlertsEnabled = MutableStateFlow(false)
    private val sensorySettings = MutableStateFlow(SensorySettingsUiState())
    private val health = MutableStateFlow(HealthUiState())
    private val recommendation = MutableStateFlow(RecommendationUiState())
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

    private val privateData = combine(health, recommendation) { healthState, recommendationState ->
        healthState to recommendationState
    }

    private val pairingState = combine(
        pairingInProgress,
        pairingCompletion,
        visualAlertsEnabled,
        sensorySettings,
        privateData,
    ) { inProgress, completion, alertsEnabled, settings, privateState ->
        PairingUiState(inProgress, completion, alertsEnabled, settings, privateState.first, privateState.second)
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
            health = pairingState.health,
            recommendation = pairingState.recommendation,
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
                healthStore.initializeConsentCatalog()
                healthStore.enforceRetention()
                recommendationStore.ensureSettings()
                recommendationStore.enforceRetention()
                refreshRecommendations()
                refreshHealth()
                val storedProfile = alertStore.ensureInstallationProfile()
                visualAlertsEnabled.value = storedProfile.profile.globalEnabled && notificationsAllowed()
                sensorySettings.value = storedProfile.settingsState()
                withContext(Dispatchers.IO) { alertScheduling.reconcile() }
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
    ) {
        val runId = repository.startProtocol(protocolId)
        AndroidWearProtocolPublisher(getApplication(), repository).publish(runId)
    }

    fun completeStep(runId: String, stepId: String) = execute(
        successMessage = "Passo confirmado.",
    ) {
        repository.completeProtocolStep(runId, stepId)
        AndroidWearProtocolPublisher(getApplication(), repository).publish(runId)
    }

    fun proposeProtocolStep(protocolId: String, label: String) = execute(
        successMessage = "Sugestão enviada para a fila de revisão.",
    ) { repository.proposeProtocolStep(protocolId, label) }

    fun setHealthConsent(category: HealthCategory, enabled: Boolean) = execute(
        successMessage = if (enabled) "Categoria ativada." else "Categoria revogada.",
    ) {
        healthStore.setConsent(category, enabled)
        refreshHealth()
    }

    fun saveIntake(id: String?, input: IntakeInput) = execute(
        successMessage = if (id == null) "Registro salvo localmente." else "Registro corrigido.",
    ) {
        if (id == null) healthStore.createIntake(input) else healthStore.updateIntake(id, input)
        refreshHealth()
    }

    fun deleteIntake(id: String) = execute(successMessage = "Registro excluído.") {
        healthStore.deleteIntake(id)
        refreshHealth()
    }

    fun saveSymptom(id: String?, input: SymptomInput) = execute(
        successMessage = if (id == null) "Evento salvo localmente." else "Evento corrigido.",
    ) {
        if (id == null) healthStore.createSymptom(input) else healthStore.updateSymptom(id, input)
        refreshHealth()
    }

    fun deleteSymptom(id: String) = execute(successMessage = "Evento excluído.") {
        healthStore.deleteSymptom(id)
        refreshHealth()
    }

    fun healthPermissionsForEnabled(): Set<String> = healthConnect.permissionsFor(
        health.value.consents.filter { it.enabled }.mapNotNullTo(linkedSetOf()) {
            HealthCategory.valueOf(it.category).takeIf(AndroidHealthConnectGateway.IMPORTABLE::contains)
        },
    )

    fun importHealthConnect(granted: Set<String>) {
        val required = healthPermissionsForEnabled()
        if (required.isEmpty()) {
            feedback.value = "Ative ao menos uma categoria importável."
            return
        }
        if (!granted.containsAll(required)) {
            feedback.value = "Permissão não concedida; nenhum dado foi lido."
            return
        }
        execute(successMessage = "Resumos de saúde atualizados.") {
            healthImporter.importEnabled()
            refreshHealth()
        }
    }

    fun generateHealthReport(days: Int, categories: Set<HealthCategory>) = execute(
        successMessage = "Prévia do relatório gerada.",
    ) {
        val current = health.value
        val snapshot = healthReportBuilder.build(
            days = days, categories = categories, subjectLabel = current.report.subjectLabel,
            consents = healthStore.consents(), summaries = healthStore.healthSummaries(),
            intakes = healthStore.intakes(), symptoms = healthStore.symptoms(),
        )
        health.value = current.copy(report = HealthReportReview(snapshot, current.report.subjectLabel))
    }

    fun setHealthReportSubject(value: String) {
        if (value.length <= 120) health.value = health.value.copy(report = health.value.report.copy(subjectLabel = value))
    }

    fun toggleHealthReportEntry(id: String) {
        val report = health.value.report
        val excluded = report.excludedEntryIds.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        health.value = health.value.copy(report = report.copy(excludedEntryIds = excluded))
    }

    fun exportHealthReport(uri: Uri, format: HealthReportFormat) = execute(
        successMessage = "Relatório ${format.name} salvo no destino escolhido.",
    ) {
        val snapshot = requireNotNull(health.value.report.reviewedSnapshot()) { "Gere uma prévia antes de exportar." }
        val bytes = HealthReportExporter.export(snapshot, format)
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
        }
    }

    fun syncNow() = execute(successMessage = "Sincronização concluída.") {
        check(canSync.value) { "Telefone ainda não pareado." }
        SyncRepository(
            MobileDatabase.get(getApplication()),
            HttpsSyncTransport(credentialStore),
        ).syncOnce()
    }

    fun refreshRecommendationState() {
        viewModelScope.launch {
            runCatching { refreshRecommendations() }
                .onFailure { feedback.value = it.safeMessage("Não foi possível ler o histórico local.") }
        }
    }

    fun saveRecommendationSettings(settings: RecommendationSettings) = execute(
        successMessage = if (settings.personalizationEnabled) {
            "Personalização local ativada."
        } else {
            "Regras padrão restauradas."
        },
    ) {
        recommendationStore.saveSettings(settings)
        recommendationStore.enforceRetention()
        refreshRecommendations()
    }

    fun correctRecommendationEvent(
        id: String,
        activeContext: RecommendationActiveContext,
        capacityContext: RecommendationCapacityContext,
    ) = execute(successMessage = "Contexto do evento corrigido.") {
        recommendationStore.correctEventContext(id, activeContext, capacityContext)
        refreshRecommendations()
    }

    fun clearRecommendationHistory() = execute(successMessage = "Histórico de recomendações apagado.") {
        recommendationStore.clearHistory()
        refreshRecommendations()
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
            AndroidWearStateCleaner(getApplication()).clearAll()
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
        val now = Instant.now()
        val temporarilySilent = effective.pausedUntil?.let(Instant::parse)?.isAfter(now) == true ||
            effective.quietHours?.contains(now.atZone(ZoneId.systemDefault()).toLocalTime()) == true
        if (effective.globalEnabled && !temporarilySilent) {
            alertScheduling.reactivate()
        } else {
            alertScheduling.pause()
            AndroidWearStateCleaner(getApplication()).clearAll()
        }
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
        if (minutes != null) AndroidWearStateCleaner(getApplication()).clearAll()
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

    private suspend fun refreshHealth() {
        health.value = HealthUiState(
            consents = healthStore.consents(),
            intakes = healthStore.intakes(),
            symptoms = healthStore.symptoms(),
            summaries = healthStore.healthSummaries(),
            connectStatus = healthConnect.status(),
            report = health.value.report,
        )
    }

    private suspend fun refreshRecommendations() {
        val settings = recommendationStore.settings()
        val events = recommendationStore.events()
        val activeContext = if (MobileDatabase.get(getApplication()).offline().activeRun() == null) {
            RecommendationActiveContext.NONE
        } else {
            RecommendationActiveContext.PROTOCOL
        }
        val preview = recommendationEngine.recommend(
            context = RecommendationContext(
                purpose = RecommendationPurpose.SNOOZE_PRESET,
                generatedAt = Instant.now(),
                activeContext = activeContext,
                capacityContext = settings.capacityContext,
            ),
            settings = settings,
            observations = recommendationStore.observations(),
        )
        recommendation.value = RecommendationUiState(
            settings = settings,
            events = events,
            statistics = RecommendationStatisticsCalculator.calculate(events),
            baselineOptions = preview?.options.orEmpty(),
            shadowMetrics = shadowMetrics.snapshot(),
        )
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
    val health: HealthUiState,
    val recommendation: RecommendationUiState,
)
