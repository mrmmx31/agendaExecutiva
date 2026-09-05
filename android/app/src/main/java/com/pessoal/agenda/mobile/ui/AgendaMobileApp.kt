package com.pessoal.agenda.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.data.local.SyncConflictEntity
import com.pessoal.agenda.mobile.data.OfflineRepository
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.health.report.HealthReportFormat
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.Normalizer
import kotlinx.coroutines.launch

private enum class MobileSection(val label: String, val icon: ImageVector) {
    TODAY("Hoje", Icons.Outlined.Home),
    TASKS("Tarefas", Icons.Outlined.CheckCircle),
    CAPTURE("Capturar", Icons.Outlined.Add),
    PROTOCOLS("Protocolos", Icons.Outlined.Checklist),
    QUEUE("Fila", Icons.Outlined.Inbox),
}

@Composable
fun AgendaMobileApp(
    initialPairingInvitation: String? = null,
    viewModel: AgendaMobileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setVisualAlertsEnabled(true)
        else viewModel.notificationPermissionDenied()
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
        viewModel::importHealthConnect,
    )
    val jsonReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HealthReportFormat.JSON.mimeType)) {
        it?.let { uri -> viewModel.exportHealthReport(uri, HealthReportFormat.JSON) }
    }
    val csvReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HealthReportFormat.CSV.mimeType)) {
        it?.let { uri -> viewModel.exportHealthReport(uri, HealthReportFormat.CSV) }
    }
    val pdfReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HealthReportFormat.PDF.mimeType)) {
        it?.let { uri -> viewModel.exportHealthReport(uri, HealthReportFormat.PDF) }
    }
    AgendaMobileTheme {
        AgendaMobileScreen(
            state = state,
            onSaveCapture = viewModel::saveCapture,
            onStartProtocol = viewModel::startProtocol,
            onCompleteStep = viewModel::completeStep,
            onCancelProtocol = viewModel::cancelProtocol,
            onSaveTodayPlan = viewModel::saveTodayPlan,
            onSelectFocus = viewModel::selectFocus,
            onCloseToday = viewModel::closeToday,
            onReopenToday = viewModel::reopenToday,
            onCreateTask = viewModel::createTask,
            onUpdateTask = viewModel::updateTask,
            onChangeTaskStatus = viewModel::changeTaskStatus,
            onDeleteTask = viewModel::deleteTask,
            onAddChecklistItem = viewModel::addChecklistItem,
            onSetChecklistItemDone = viewModel::setChecklistItemDone,
            onDeleteChecklistItem = viewModel::deleteChecklistItem,
            onStartTaskTimer = viewModel::startTaskTimer,
            onInterruptTaskTimer = viewModel::interruptTaskTimer,
            onResumeTaskTimer = viewModel::resumeTaskTimer,
            onFinishTaskTimer = viewModel::finishTaskTimer,
            onProposeProtocolStep = viewModel::proposeProtocolStep,
            onSync = viewModel::syncNow,
            onPair = viewModel::pairDesktop,
            onCancelPairing = viewModel::cancelPairing,
            onPairingCompletionShown = viewModel::acknowledgePairingCompletion,
            onFeedbackShown = viewModel::clearFeedback,
            onVisualAlertsChanged = { enabled ->
                if (!enabled) {
                    viewModel.setVisualAlertsEnabled(false)
                } else if (
                    SensoryChannel.VISUAL in state.sensorySettings.profile.enabledChannels
                    &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setVisualAlertsEnabled(true)
                }
            },
            onSaveSensorySettings = { profile, snooze, selectedAudioDeviceKey ->
                viewModel.saveSensorySettings(profile, snooze, selectedAudioDeviceKey)
                if (
                    state.sensorySettings.profile.globalEnabled
                    && SensoryChannel.VISUAL in profile.enabledChannels
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onPauseSensoryAlerts = viewModel::pauseSensoryAlerts,
            onTestAudio = viewModel::toggleAudioTest,
            onRefreshAudioRoute = viewModel::refreshAudioRoute,
            onOpenSystemSoundSettings = {
                context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            },
            onHealthConsentChanged = viewModel::setHealthConsent,
            onSaveIntake = viewModel::saveIntake,
            onDeleteIntake = viewModel::deleteIntake,
            onSaveSymptom = viewModel::saveSymptom,
            onDeleteSymptom = viewModel::deleteSymptom,
            onImportHealth = { healthPermissionLauncher.launch(viewModel.healthPermissionsForEnabled()) },
            onGenerateHealthReport = viewModel::generateHealthReport,
            onHealthReportSubjectChanged = viewModel::setHealthReportSubject,
            onToggleHealthReportEntry = viewModel::toggleHealthReportEntry,
            onExportHealthReport = { format ->
                val name = "agenda-saude.${format.extension}"
                when (format) {
                    HealthReportFormat.JSON -> jsonReportLauncher.launch(name)
                    HealthReportFormat.CSV -> csvReportLauncher.launch(name)
                    HealthReportFormat.PDF -> pdfReportLauncher.launch(name)
                }
            },
            onRefreshRecommendations = viewModel::refreshRecommendationState,
            onSaveRecommendationSettings = viewModel::saveRecommendationSettings,
            onCorrectRecommendationEvent = viewModel::correctRecommendationEvent,
            onClearRecommendationHistory = viewModel::clearRecommendationHistory,
            onTrainPersonalModel = viewModel::trainPersonalModel,
            onActivatePersonalModel = viewModel::activatePersonalModel,
            onRollbackPersonalModel = viewModel::rollbackPersonalModel,
            initialPairingInvitation = initialPairingInvitation,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendaMobileScreen(
    state: MobileUiState,
    onSaveCapture: (String, () -> Unit) -> Unit,
    onStartProtocol: (String) -> Unit,
    onCompleteStep: (String, String) -> Unit,
    onCancelProtocol: (String) -> Unit = {},
    onSaveTodayPlan: (String, String, List<String>) -> Unit = { _, _, _ -> },
    onSelectFocus: (String?) -> Unit = {},
    onCloseToday: (String) -> Unit = {},
    onReopenToday: () -> Unit = {},
    onCreateTask: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    onUpdateTask: (String, String, String, String?, String) -> Unit = { _, _, _, _, _ -> },
    onChangeTaskStatus: (String, String) -> Unit = { _, _ -> },
    onDeleteTask: (String) -> Unit = {},
    onAddChecklistItem: (String, String) -> Unit = { _, _ -> },
    onSetChecklistItemDone: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteChecklistItem: (String) -> Unit = {},
    onStartTaskTimer: (String) -> Unit = {},
    onInterruptTaskTimer: () -> Unit = {},
    onResumeTaskTimer: () -> Unit = {},
    onFinishTaskTimer: (String) -> Unit = {},
    onSync: () -> Unit,
    onPair: (String, String) -> Unit,
    onCancelPairing: () -> Unit,
    onPairingCompletionShown: () -> Unit,
    onFeedbackShown: () -> Unit,
    initialPairingInvitation: String? = null,
    onVisualAlertsChanged: (Boolean) -> Unit = {},
    onSaveSensorySettings: (com.pessoal.agenda.mobile.alert.SensoryProfile, com.pessoal.agenda.mobile.alert.SnoozePolicy, String?) -> Unit = { _, _, _ -> },
    onPauseSensoryAlerts: (Int?) -> Unit = {},
    onTestAudio: (com.pessoal.agenda.mobile.alert.AudioRoutePolicy, String?) -> Unit = { _, _ -> },
    onRefreshAudioRoute: () -> Unit = {},
    onOpenSystemSoundSettings: () -> Unit = {},
    onProposeProtocolStep: (String, String) -> Unit = { _, _ -> },
    onHealthConsentChanged: (com.pessoal.agenda.mobile.health.HealthCategory, Boolean) -> Unit = { _, _ -> },
    onSaveIntake: (String?, com.pessoal.agenda.mobile.health.IntakeInput) -> Unit = { _, _ -> },
    onDeleteIntake: (String) -> Unit = {},
    onSaveSymptom: (String?, com.pessoal.agenda.mobile.health.SymptomInput) -> Unit = { _, _ -> },
    onDeleteSymptom: (String) -> Unit = {},
    onImportHealth: () -> Unit = {},
    onGenerateHealthReport: (Int, Set<com.pessoal.agenda.mobile.health.HealthCategory>) -> Unit = { _, _ -> },
    onHealthReportSubjectChanged: (String) -> Unit = {},
    onToggleHealthReportEntry: (String) -> Unit = {},
    onExportHealthReport: (HealthReportFormat) -> Unit = {},
    onRefreshRecommendations: () -> Unit = {},
    onSaveRecommendationSettings: (com.pessoal.agenda.mobile.recommendation.RecommendationSettings) -> Unit = {},
    onCorrectRecommendationEvent: (String, com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext, com.pessoal.agenda.mobile.recommendation.RecommendationCapacityContext) -> Unit = { _, _, _ -> },
    onClearRecommendationHistory: () -> Unit = {},
    onTrainPersonalModel: () -> Unit = {},
    onActivatePersonalModel: (String) -> Unit = {},
    onRollbackPersonalModel: () -> Unit = {},
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var showSensorySettings by rememberSaveable { mutableStateOf(false) }
    var showHealth by rememberSaveable { mutableStateOf(false) }
    var showRecommendations by rememberSaveable { mutableStateOf(false) }
    var showLeavingChoices by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            onFeedbackShown()
        }
    }
    LaunchedEffect(state.pairingCompletion) {
        if (state.pairingCompletion > 0) {
            showPairing = false
            onPairingCompletionShown()
        }
    }
    LaunchedEffect(initialPairingInvitation) {
        if (!initialPairingInvitation.isNullOrBlank()) showPairing = true
    }
    LaunchedEffect(showRecommendations) {
        if (showRecommendations) onRefreshRecommendations()
    }
    val secondaryScreenVisible = showSensorySettings || showHealth || showRecommendations
    BackHandler(
        enabled = showPairing || showLeavingChoices || secondaryScreenVisible
            || selected != MobileSection.TODAY.ordinal,
    ) {
        when {
            showPairing -> {
                if (state.pairingInProgress) onCancelPairing()
                showPairing = false
            }
            showLeavingChoices -> showLeavingChoices = false
            secondaryScreenVisible -> {
                showSensorySettings = false
                showHealth = false
                showRecommendations = false
            }
            else -> selected = MobileSection.TODAY.ordinal
        }
    }
    if (showPairing) {
        PairingDialog(
            inProgress = state.pairingInProgress,
            initialInvitation = initialPairingInvitation.orEmpty(),
            onPair = onPair,
            onCancel = {
                if (state.pairingInProgress) onCancelPairing()
                showPairing = false
            },
        )
    }
    val leavingCandidates = remember(state.protocols) { leavingHomeCandidates(state.protocols) }
    if (showLeavingChoices) {
        AlertDialog(
            onDismissRequest = { showLeavingChoices = false },
            title = { Text("Vou sair") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    leavingCandidates.forEach { protocol ->
                        OutlinedButton(
                            onClick = {
                                showLeavingChoices = false
                                onStartProtocol(protocol.id)
                                selected = MobileSection.PROTOCOLS.ordinal
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(protocol.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showLeavingChoices = false }) { Text("Cancelar") }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(when {
                            showHealth -> "Saúde e privacidade"
                            showRecommendations -> "Recomendações locais"
                            showSensorySettings -> "Configurações sensoriais"
                            else -> "Agenda"
                        })
                        if (!compactHeight && !showSensorySettings && !showHealth && !showRecommendations) {
                            Text(
                                text = "Núcleo offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showSensorySettings || showHealth || showRecommendations) {
                        IconButton(onClick = {
                            showSensorySettings = false
                            showHealth = false
                            showRecommendations = false
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (!showSensorySettings && !showHealth && !showRecommendations) {
                        IconButton(onClick = { showHealth = true; showRecommendations = false; showSensorySettings = false }) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Saúde e privacidade")
                        }
                        IconButton(onClick = { showRecommendations = true; showHealth = false; showSensorySettings = false }) {
                            Icon(Icons.Outlined.Insights, contentDescription = "Recomendações locais")
                        }
                        IconButton(onClick = { showSensorySettings = true; showHealth = false; showRecommendations = false }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Configurações sensoriais")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!compactHeight && !showSensorySettings && !showHealth && !showRecommendations) {
                NavigationBar {
                    MobileSection.entries.forEachIndexed { index, section ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(section.icon, contentDescription = null) },
                            label = { Text(section.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (compactHeight && !showSensorySettings && !showHealth && !showRecommendations) {
                NavigationRail {
                    MobileSection.entries.forEachIndexed { index, section ->
                        NavigationRailItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(section.icon, contentDescription = section.label) },
                        )
                    }
                }
            }
            Column(Modifier.fillMaxSize().weight(1f)) {
            if (showHealth) {
                HealthPrivacyScreen(
                    state = state.health,
                    busy = state.busy,
                    onConsentChanged = onHealthConsentChanged,
                    onSaveIntake = onSaveIntake,
                    onDeleteIntake = onDeleteIntake,
                    onSaveSymptom = onSaveSymptom,
                    onDeleteSymptom = onDeleteSymptom,
                    onImportHealth = onImportHealth,
                    onGenerateReport = onGenerateHealthReport,
                    onReportSubjectChanged = onHealthReportSubjectChanged,
                    onToggleReportEntry = onToggleHealthReportEntry,
                    onExportReport = onExportHealthReport,
                )
            } else if (showRecommendations) {
                RecommendationSettingsScreen(
                    state = state.recommendation,
                    busy = state.busy,
                    onSaveSettings = onSaveRecommendationSettings,
                    onCorrectEvent = onCorrectRecommendationEvent,
                    onClearHistory = onClearRecommendationHistory,
                    onTrainModel = onTrainPersonalModel,
                    onActivateModel = onActivatePersonalModel,
                    onRollbackModel = onRollbackPersonalModel,
                )
            } else if (showSensorySettings) {
                SensorySettingsScreen(
                    state = state.sensorySettings,
                    alertsEnabled = state.sensorySettings.profile.globalEnabled,
                    visualNotificationsAvailable = state.visualAlertsEnabled,
                    busy = state.busy,
                    onGlobalChanged = onVisualAlertsChanged,
                    onSave = onSaveSensorySettings,
                    onPause = onPauseSensoryAlerts,
                    onTestAudio = onTestAudio,
                    onRefreshRoute = onRefreshAudioRoute,
                    onOpenSystemSoundSettings = onOpenSystemSoundSettings,
                )
            } else {
                OfflineStatusBand(
                    state.operations.count { it.status in setOf("PENDING", "RETRYABLE", "IN_FLIGHT") },
                    state.canSync,
                    state.busy,
                    onSync,
                    onPair = { showPairing = true },
                )
                if (!compactHeight) {
                    AlertsOptInBand(
                        enabled = state.sensorySettings.profile.globalEnabled,
                        busy = state.busy,
                        onChanged = onVisualAlertsChanged,
                    )
                }
                when (MobileSection.entries[selected]) {
                    MobileSection.TODAY -> TodayScreen(
                        tasks = state.tasks,
                        today = state.today,
                        busy = state.busy,
                        onSavePlan = onSaveTodayPlan,
                        onSelectFocus = onSelectFocus,
                        onCloseDay = onCloseToday,
                        onReopenDay = onReopenToday,
                        onLeavingHome = {
                            when {
                                state.activeRunSteps.isNotEmpty() -> selected = MobileSection.PROTOCOLS.ordinal
                                leavingCandidates.size == 1 -> {
                                    onStartProtocol(leavingCandidates.single().id)
                                    selected = MobileSection.PROTOCOLS.ordinal
                                }
                                leavingCandidates.isNotEmpty() -> showLeavingChoices = true
                                else -> selected = MobileSection.PROTOCOLS.ordinal
                            }
                        },
                    )
                    MobileSection.TASKS -> TaskScreen(
                        state = state,
                        onCreate = onCreateTask,
                        onUpdate = onUpdateTask,
                        onStatus = { task, status ->
                            val previous = task.status
                            onChangeTaskStatus(task.id, status)
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = "Estado alterado para ${status.userLabel()}.",
                                    actionLabel = "Desfazer",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onChangeTaskStatus(task.id, previous)
                                }
                            }
                        },
                        onDelete = onDeleteTask,
                        onAddChecklist = onAddChecklistItem,
                        onChecklistDone = onSetChecklistItemDone,
                        onDeleteChecklist = onDeleteChecklistItem,
                        onStartTimer = onStartTaskTimer,
                        onInterruptTimer = onInterruptTaskTimer,
                        onResumeTimer = onResumeTaskTimer,
                        onFinishTimer = onFinishTaskTimer,
                    )
                    MobileSection.CAPTURE -> CaptureScreen(state, onSaveCapture)
                    MobileSection.PROTOCOLS -> ProtocolScreen(
                        state.protocols,
                        state.activeRunSteps,
                        state.busy,
                        onStartProtocol,
                        onCompleteStep,
                        onCancelProtocol,
                        onProposeProtocolStep,
                    )
                    MobileSection.QUEUE -> QueueScreen(state.operations, state.conflicts)
                }
            }
        }
        }
    }
}

@Composable
private fun AlertsOptInBand(
    enabled: Boolean,
    busy: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null)
                Column {
                    Text("Alertas sensoriais", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (enabled) "Ativados conforme o perfil" else "Desativados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChanged,
                enabled = !busy,
                modifier = Modifier.semantics {
                    contentDescription = if (enabled) "Desativar alertas" else "Ativar alertas"
                },
            )
        }
    }
}

@Composable
private fun OfflineStatusBand(
    pendingCount: Int,
    canSync: Boolean,
    busy: Boolean,
    onSync: () -> Unit,
    onPair: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null)
                Text(
                    if (canSync) "Pareado ao desktop" else "Somente neste telefone",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$pendingCount na fila", style = MaterialTheme.typography.bodySmall)
                if (canSync) {
                    IconButton(onClick = onSync, enabled = !busy) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sincronizar agora")
                    }
                } else {
                    IconButton(onClick = onPair, enabled = !busy) {
                        Icon(Icons.Outlined.Link, contentDescription = "Conectar ao desktop")
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingDialog(
    inProgress: Boolean,
    initialInvitation: String,
    onPair: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var invitation by rememberSaveable(initialInvitation) { mutableStateOf(initialInvitation) }
    var code by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.startsWith("agenda://pair?") }?.let { invitation = it }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Conectar ao desktop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = invitation,
                    onValueChange = { invitation = it.take(4096) },
                    enabled = !inProgress,
                    label = { Text("Convite") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = {
                            scanner.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Leia o convite exibido pela Agenda no desktop")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(true),
                            )
                        },
                        enabled = !inProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Text("Ler QR code")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.getText()?.text
                                ?.takeIf { it.startsWith("agenda://pair?") }
                                ?.let { invitation = it.take(4096) }
                        },
                        enabled = !inProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                        Text("Colar convite")
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                    enabled = !inProgress,
                    label = { Text("Código de seis dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (inProgress) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                        Text("Aguardando aprovação no desktop")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPair(invitation, code) },
                enabled = !inProgress && invitation.isNotBlank() && code.length == 6,
            ) { Text("Conectar") }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
        },
    )
}

@Composable
private fun TodayScreen(
    tasks: List<TaskReplicaEntity>,
    today: TodayUiState,
    busy: Boolean,
    onSavePlan: (String, String, List<String>) -> Unit,
    onSelectFocus: (String?) -> Unit,
    onCloseDay: (String) -> Unit,
    onReopenDay: () -> Unit,
    onLeavingHome: () -> Unit,
) {
    val orderedTasks = remember(tasks) { orderTasksForToday(tasks) }
    val openTasks = remember(tasks) {
        tasks.filter { !it.tombstone && it.status !in setOf("COMPLETED", "CANCELLED") }
    }
    var showPlanEditor by rememberSaveable { mutableStateOf(false) }
    var showFocusPicker by rememberSaveable { mutableStateOf(false) }
    var showCloseDay by rememberSaveable { mutableStateOf(false) }
    var reducedCapacity by rememberSaveable { mutableStateOf(false) }
    var essentialTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var supportTaskIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var closingNote by rememberSaveable { mutableStateOf("") }

    if (showFocusPicker) {
        AlertDialog(
            onDismissRequest = { showFocusPicker = false },
            title = { Text("Escolher foco") },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(openTasks, key = { it.id }) { task ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = today.focusSource == "MANUAL" && today.focusTask?.id == task.id,
                                onClick = {
                                    onSelectFocus(task.id)
                                    showFocusPicker = false
                                },
                                enabled = !busy,
                            )
                            Text(task.title, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        onSelectFocus(null)
                        showFocusPicker = false
                    },
                    enabled = !busy,
                ) { Text("Usar automático") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFocusPicker = false }) { Text("Cancelar") }
            },
        )
    }
    if (showPlanEditor) {
        AlertDialog(
            onDismissRequest = { showPlanEditor = false },
            title = { Text(if (today.plan == null) "Começar meu dia" else "Editar plano de hoje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            reducedCapacity = !reducedCapacity
                            if (reducedCapacity) supportTaskIds = emptyList()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = reducedCapacity,
                            onCheckedChange = {
                                reducedCapacity = it
                                if (it) supportTaskIds = emptyList()
                            },
                        )
                        Text("Capacidade reduzida")
                    }
                    Text("Tarefa essencial", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(openTasks, key = { it.id }) { task ->
                            val essential = essentialTaskId == task.id
                            val support = task.id in supportTaskIds
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = essential,
                                        onClick = {
                                            essentialTaskId = task.id
                                            supportTaskIds = supportTaskIds - task.id
                                        },
                                    )
                                    Text(task.title, modifier = Modifier.weight(1f), maxLines = 2)
                                }
                                if (!reducedCapacity && !essential) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = support,
                                            onCheckedChange = { checked ->
                                                supportTaskIds = when {
                                                    checked && supportTaskIds.size < 2 -> supportTaskIds + task.id
                                                    !checked -> supportTaskIds - task.id
                                                    else -> supportTaskIds
                                                }
                                            },
                                        )
                                        Text("Apoio")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePlan(
                            if (reducedCapacity) "REDUCED" else "NORMAL",
                            requireNotNull(essentialTaskId),
                            supportTaskIds,
                        )
                        showPlanEditor = false
                    },
                    enabled = essentialTaskId != null && !busy,
                ) { Text("Salvar plano") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPlanEditor = false }) { Text("Cancelar") }
            },
        )
    }
    if (showCloseDay) {
        AlertDialog(
            onDismissRequest = { showCloseDay = false },
            title = { Text("Encerrar meu dia?") },
            text = {
                OutlinedTextField(
                    value = closingNote,
                    onValueChange = { closingNote = it.take(OfflineRepository.MAX_CLOSING_NOTE_LENGTH) },
                    label = { Text("Nota opcional") },
                    minLines = 3,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCloseDay(closingNote)
                        showCloseDay = false
                    },
                    enabled = !busy,
                ) { Text("Encerrar dia") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseDay = false }) { Text("Continuar o dia") }
            },
        )
    }
    ScreenList(title = "Hoje") {
        item {
            Text("Agora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            today.focusTask?.let { focus ->
                Text(focus.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    when (today.focusSource) {
                        "MANUAL" -> "Escolhido por você"
                        "PLAN" -> "Essencial do plano de hoje"
                        else -> "Sugestão automática"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text("Nenhuma tarefa aberta para focar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showFocusPicker = true },
                    enabled = openTasks.isNotEmpty() && !busy,
                ) { Text("Escolher foco") }
                if (today.focusSource == "MANUAL") {
                    OutlinedButton(onClick = { onSelectFocus(null) }, enabled = !busy) {
                        Text("Usar automático")
                    }
                }
            }
        }
        item {
            Button(
                onClick = onLeavingHome,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Home, contentDescription = null)
                Text("Vou sair")
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Plano de hoje", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                today.plan == null -> {
                    Text("Escolha uma tarefa essencial e, se couber, até duas de apoio.")
                    Button(
                        onClick = {
                            reducedCapacity = false
                            essentialTaskId = today.focusTask?.id ?: openTasks.firstOrNull()?.id
                            supportTaskIds = emptyList()
                            showPlanEditor = true
                        },
                        enabled = openTasks.isNotEmpty() && !busy,
                    ) { Text("Começar meu dia") }
                }
                today.plan.closedAt != null -> {
                    Text("Dia encerrado")
                    today.plan.closingNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onClick = onReopenDay, enabled = !busy) { Text("Reabrir dia") }
                }
                else -> {
                    Text(if (today.plan.capacity == "REDUCED") "Capacidade reduzida" else "Capacidade normal")
                    today.planTasks.forEach { item ->
                        Text(if (item.role == "ESSENTIAL") "Essencial: ${item.title}" else "Apoio: ${item.title}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                reducedCapacity = today.plan.capacity == "REDUCED"
                                essentialTaskId = today.planTasks.firstOrNull { it.role == "ESSENTIAL" }?.taskId
                                supportTaskIds = today.planTasks.filter { it.role == "SUPPORT" }.map { it.taskId }
                                showPlanEditor = true
                            },
                            enabled = !busy,
                        ) { Text("Editar plano") }
                        OutlinedButton(
                            onClick = {
                                closingNote = ""
                                showCloseDay = true
                            },
                            enabled = !busy,
                        ) { Text("Encerrar dia") }
                    }
                }
            }
        }
        if (tasks.isEmpty()) item { EmptyState("Nenhuma tarefa local") }
        items(orderedTasks, key = { it.id }) { task ->
            val completed = task.status == "COMPLETED"
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (completed) Icons.Outlined.CheckCircle
                    else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (completed) "Tarefa concluída" else "Tarefa pendente",
                    tint = if (completed) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (completed) TextDecoration.LineThrough
                            else TextDecoration.None,
                        ),
                        color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        task.status.userLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

internal fun orderTasksForToday(tasks: List<TaskReplicaEntity>): List<TaskReplicaEntity> =
    tasks.sortedBy { it.status == "COMPLETED" }

internal fun leavingHomeCandidates(protocols: List<ProtocolTemplateEntity>): List<ProtocolTemplateEntity> =
    protocols.asSequence()
        .filterNot(ProtocolTemplateEntity::tombstone)
        .sortedWith(
            compareByDescending<ProtocolTemplateEntity> { protocol ->
                val normalized = Normalizer.normalize(protocol.title, Normalizer.Form.NFD)
                    .replace("\\p{M}+".toRegex(), "")
                    .lowercase()
                normalized.contains("saida") || normalized.contains("sair")
            }.thenBy(ProtocolTemplateEntity::title).thenBy(ProtocolTemplateEntity::id),
        )
        .take(3)
        .toList()

@Composable
private fun TaskScreen(
    state: MobileUiState,
    onCreate: (String, String, String?, String) -> Unit,
    onUpdate: (String, String, String, String?, String) -> Unit,
    onStatus: (TaskReplicaEntity, String) -> Unit,
    onDelete: (String) -> Unit,
    onAddChecklist: (String, String) -> Unit,
    onChecklistDone: (String, Boolean) -> Unit,
    onDeleteChecklist: (String) -> Unit,
    onStartTimer: (String) -> Unit,
    onInterruptTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onFinishTimer: (String) -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val detailTask = state.tasks.firstOrNull { it.id == detailId }

    if (creating || editingId != null) {
        val editing = state.tasks.firstOrNull { it.id == editingId }
        TaskEditorDialog(
            task = editing,
            busy = state.busy,
            onDismiss = { creating = false; editingId = null },
            onSave = { title, notes, due, priority ->
                if (editing == null) onCreate(title, notes, due, priority)
                else onUpdate(editing.id, title, notes, due, priority)
                creating = false
                editingId = null
            },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Remover tarefa?") },
            text = { Text("A tarefa será removida também após a próxima sincronização.") },
            confirmButton = {
                Button(onClick = { onDelete(id); deletingId = null; detailId = null }) { Text("Remover") }
            },
            dismissButton = { OutlinedButton(onClick = { deletingId = null }) { Text("Cancelar") } },
        )
    }
    detailTask?.let { task ->
        TaskDetailDialog(
            task = task,
            checklist = state.taskChecklist.filter { it.taskId == task.id },
            sessions = state.taskSessions.filter { it.taskId == task.id },
            timer = state.activeTaskTimer?.takeIf { it.taskId == task.id },
            anotherTimerActive = state.activeTaskTimer != null && state.activeTaskTimer.taskId != task.id,
            busy = state.busy,
            onDismiss = { detailId = null },
            onEdit = { editingId = task.id },
            onDelete = { deletingId = task.id },
            onStatus = { onStatus(task, it) },
            onAddChecklist = { onAddChecklist(task.id, it) },
            onChecklistDone = onChecklistDone,
            onDeleteChecklist = onDeleteChecklist,
            onStartTimer = { onStartTimer(task.id) },
            onInterruptTimer = onInterruptTimer,
            onResumeTimer = onResumeTimer,
            onFinishTimer = onFinishTimer,
        )
    }

    ScreenList("Tarefas") {
        item {
            Button(
                onClick = { creating = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("task-add"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Nova tarefa", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.tasks.isEmpty()) item { EmptyState("Nenhuma tarefa disponível.") }
        items(state.tasks, key = { it.id }) { task ->
            val completed = task.status == "COMPLETED"
            val conflicted = state.conflicts.any { it.entityType == "task" && it.entityId == task.id }
            val overdue = !completed && task.dueDate?.let {
                runCatching { java.time.LocalDate.parse(it).isBefore(java.time.LocalDate.now()) }.getOrDefault(false)
            } == true
            Row(
                Modifier.fillMaxWidth().clickable { detailId = task.id }
                    .padding(vertical = 12.dp).testTag("task-${task.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    when {
                        conflicted -> Icons.Outlined.CloudOff
                        completed -> Icons.Outlined.CheckCircle
                        else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = when { conflicted -> "Conflito"; overdue -> "Atrasada"; else -> task.status.userLabel() },
                    tint = when {
                        conflicted || overdue -> MaterialTheme.colorScheme.error
                        completed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (completed) TextDecoration.LineThrough else null,
                    )
                    Text(
                        listOfNotNull(
                            when { conflicted -> "Conflito"; overdue -> "Atrasada"; else -> task.status.userLabel() },
                            task.dueDate?.let { "Prazo $it" }, task.priority.priorityLabel(),
                        )
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TaskEditorDialog(
    task: TaskReplicaEntity?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String) -> Unit,
) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var notes by rememberSaveable(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var dueDate by rememberSaveable(task?.id) { mutableStateOf(task?.dueDate.orEmpty()) }
    var priority by rememberSaveable(task?.id) { mutableStateOf(task?.priority ?: "NORMAL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Nova tarefa" else "Editar tarefa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    dueDate, { dueDate = it }, label = { Text("Prazo (AAAA-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Prioridade", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LOW", "NORMAL", "HIGH").forEach { value ->
                        OutlinedButton(onClick = { priority = value }, enabled = !busy) {
                            Text(if (priority == value) "✓ ${value.priorityLabel()}" else value.priorityLabel())
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, notes, dueDate.takeIf(String::isNotBlank), priority) },
                enabled = !busy && title.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun TaskDetailDialog(
    task: TaskReplicaEntity,
    checklist: List<com.pessoal.agenda.mobile.data.local.TaskChecklistItemEntity>,
    sessions: List<com.pessoal.agenda.mobile.data.local.TaskSessionEntity>,
    timer: com.pessoal.agenda.mobile.data.local.ActiveTaskTimerEntity?,
    anotherTimerActive: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatus: (String) -> Unit,
    onAddChecklist: (String) -> Unit,
    onChecklistDone: (String, Boolean) -> Unit,
    onDeleteChecklist: (String) -> Unit,
    onStartTimer: () -> Unit,
    onInterruptTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onFinishTimer: (String) -> Unit,
) {
    var newItem by rememberSaveable(task.id) { mutableStateOf("") }
    var sessionNotes by rememberSaveable(task.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (task.notes.isNotBlank()) item { Text(task.notes) }
                item {
                    Text("Estado", style = MaterialTheme.typography.titleSmall)
                    Column {
                        listOf("PENDING", "IN_PROGRESS", "COMPLETED", "BLOCKED", "CANCELLED").forEach { value ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = task.status == value,
                                    onClick = { onStatus(value) },
                                    enabled = !busy,
                                    modifier = Modifier.semantics { contentDescription = "Definir ${value.userLabel()}" },
                                )
                                Text(value.userLabel())
                            }
                        }
                    }
                }
                item { Text("Checklist", style = MaterialTheme.typography.titleSmall) }
                items(checklist, key = { it.id }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.done, { onChecklistDone(item.id, it) }, enabled = !busy)
                        Text(item.text, Modifier.weight(1f), textDecoration = if (item.done) TextDecoration.LineThrough else null)
                        IconButton(onClick = { onDeleteChecklist(item.id) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remover item")
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newItem, { newItem = it }, label = { Text("Novo item") }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onAddChecklist(newItem); newItem = "" }, enabled = !busy && newItem.isNotBlank()) {
                            Icon(Icons.Outlined.Add, contentDescription = "Adicionar item")
                        }
                    }
                }
                item { HorizontalDivider(); Text("Sessão de foco", style = MaterialTheme.typography.titleSmall) }
                item {
                    when {
                        timer == null -> Button(onClick = onStartTimer, enabled = !busy && !anotherTimerActive) {
                            Icon(Icons.Outlined.Timer, contentDescription = null); Text("Iniciar", Modifier.padding(start = 6.dp))
                        }
                        timer.startedAt != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onInterruptTimer, enabled = !busy) {
                                Icon(Icons.Outlined.PauseCircle, contentDescription = null); Text("Interromper")
                            }
                        }
                        else -> OutlinedButton(onClick = onResumeTimer, enabled = !busy) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null); Text("Retomar")
                        }
                    }
                    if (anotherTimerActive) Text("Há outra tarefa com cronômetro ativo.", color = MaterialTheme.colorScheme.error)
                }
                if (timer != null) item {
                    OutlinedTextField(sessionNotes, { sessionNotes = it }, label = { Text("Nota da sessão") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onFinishTimer(sessionNotes); sessionNotes = "" }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null); Text("Finalizar e registrar", Modifier.padding(start = 6.dp))
                    }
                }
                if (sessions.isNotEmpty()) item {
                    val minutes = sessions.sumOf { it.durationSeconds } / 60
                    Text("${sessions.size} sessões · $minutes min registrados", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null); Text("Editar") } },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Remover tarefa") }
                OutlinedButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

private fun String.priorityLabel(): String = when (this) {
    "LOW" -> "Baixa"
    "HIGH" -> "Alta"
    else -> "Normal"
}

@Composable
private fun CaptureScreen(state: MobileUiState, onSave: (String, () -> Unit) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Capturar") }
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 4000) draft = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                label = { Text("Texto livre") },
                supportingText = { Text("${draft.length}/4000") },
            )
        }
        item {
            Button(
                onClick = { onSave(draft) { draft = "" } },
                enabled = draft.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Salvar no telefone")
            }
        }
        item { Text("Capturas recentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (state.captures.isEmpty()) item { EmptyState("Nenhuma captura salva") }
        items(state.captures, key = { it.id }) { capture ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(capture.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    capture.createdAt.userDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ProtocolScreen(
    protocols: List<ProtocolTemplateEntity>,
    activeSteps: List<ActiveRunStepRow>,
    busy: Boolean,
    onStart: (String) -> Unit,
    onComplete: (String, String) -> Unit,
    onCancel: (String) -> Unit,
    onProposeStep: (String, String) -> Unit,
) {
    var proposalProtocol by remember { mutableStateOf<ProtocolTemplateEntity?>(null) }
    var proposalLabel by remember { mutableStateOf("") }
    var runPendingCancellation by remember { mutableStateOf<String?>(null) }
    runPendingCancellation?.let { runId ->
        AlertDialog(
            onDismissRequest = { runPendingCancellation = null },
            title = { Text("Encerrar protocolo?") },
            text = { Text("Os passos já confirmados permanecerão registrados. Você poderá iniciar o protocolo novamente.") },
            confirmButton = {
                Button(
                    onClick = {
                        onCancel(runId)
                        runPendingCancellation = null
                    },
                    enabled = !busy,
                ) { Text("Encerrar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { runPendingCancellation = null }) { Text("Continuar protocolo") }
            },
        )
    }
    proposalProtocol?.let { protocol ->
        AlertDialog(
            onDismissRequest = { proposalProtocol = null },
            title = { Text("Sugerir item") },
            text = {
                OutlinedTextField(
                    value = proposalLabel,
                    onValueChange = { proposalLabel = it.take(120) },
                    label = { Text("Novo item para ${protocol.title}") },
                    singleLine = false,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onProposeStep(protocol.id, proposalLabel)
                        proposalProtocol = null
                        proposalLabel = ""
                    },
                    enabled = proposalLabel.isNotBlank() && !busy,
                ) { Text("Enviar para revisão") }
            },
            dismissButton = {
                OutlinedButton(onClick = { proposalProtocol = null }) { Text("Cancelar") }
            },
        )
    }
    ScreenList(title = "Protocolos") {
        if (activeSteps.isNotEmpty()) {
            item {
                Text(activeSteps.first().protocolTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Execução local em andamento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { runPendingCancellation = activeSteps.first().runId },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null)
                    Text("Encerrar protocolo")
                }
            }
            items(activeSteps, key = { it.stepId }) { step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = step.completedAt != null,
                        onCheckedChange = { checked -> if (checked) onComplete(step.runId, step.stepId) },
                        enabled = step.completedAt == null && !busy,
                    )
                    Text(step.label, modifier = Modifier.weight(1f))
                }
            }
        } else {
            if (protocols.isEmpty()) item { EmptyState("Nenhum protocolo local") }
            items(protocols, key = { it.id }) { protocol ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(protocol.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Revisão ${protocol.revision}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onStart(protocol.id) }, enabled = !busy) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Text("Iniciar")
                    }
                    IconButton(
                        onClick = {
                            proposalLabel = ""
                            proposalProtocol = protocol
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Sugerir item")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun QueueScreen(
    operations: List<PendingOperationEntity>,
    conflicts: List<SyncConflictEntity>,
) {
    ScreenList(title = "Fila offline") {
        if (operations.isEmpty() && conflicts.isEmpty()) item { EmptyState("Nenhuma operação registrada") }
        if (conflicts.isNotEmpty()) {
            item { Text("Conflitos para revisar", style = MaterialTheme.typography.titleMedium) }
            items(conflicts, key = { it.conflictId }) { conflict ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(conflict.reason.conflictLabel(), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Versão local: ${conflict.localValueJson}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Versão desktop: ${conflict.serverValueJson}",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Text("Histórico da fila", style = MaterialTheme.typography.titleMedium) }
        }
        items(operations, key = { it.operationId }) { operation ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(operation.commandType.userCommandLabel(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "#${operation.sequence} · ${operation.status.userLabel()} · contrato v${operation.contractVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ScreenList(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen-$title"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle(title) }
        content()
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun String.userLabel(): String = when (this) {
    "PENDING" -> "Pendente"
    "COMPLETED" -> "Concluída"
    "IN_PROGRESS" -> "Em andamento"
    "BLOCKED" -> "Bloqueada"
    "CANCELLED" -> "Cancelada"
    "IN_FLIGHT" -> "Enviando"
    "APPLIED" -> "Aplicada"
    "CONFLICT" -> "Conflito"
    "REJECTED" -> "Rejeitada"
    "RETRYABLE" -> "Aguardando nova tentativa"
    else -> "Estado local"
}

private fun String.conflictLabel(): String = when (this) {
    "TEXT_DIVERGED" -> "Texto alterado nos dois dispositivos"
    "STRUCTURE_DIVERGED" -> "Estrutura alterada nos dois dispositivos"
    "STATE_DIVERGED" -> "Estado alterado nos dois dispositivos"
    "TOMBSTONE_DIVERGED" -> "Exclusão divergente"
    else -> "Conflito de sincronização"
}

private fun String.userCommandLabel(): String = when (this) {
    "CAPTURE_CREATED" -> "Captura criada"
    "PROTOCOL_RUN_STARTED" -> "Protocolo iniciado"
    "PROTOCOL_STEP_COMPLETED" -> "Passo confirmado"
    "PROTOCOL_RUN_CANCELLED" -> "Protocolo encerrado"
    else -> "Operação local"
}

private fun String.userDateTime(): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(this))
}.getOrDefault(this)

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun MobileHomePreview() {
    AgendaMobileTheme {
        AgendaMobileScreen(
            state = MobileUiState(),
            onSaveCapture = { _, _ -> },
            onStartProtocol = {},
            onCompleteStep = { _, _ -> },
            onSync = {},
            onPair = { _, _ -> },
            onCancelPairing = {},
            onPairingCompletionShown = {},
            onFeedbackShown = {},
        )
    }
}
