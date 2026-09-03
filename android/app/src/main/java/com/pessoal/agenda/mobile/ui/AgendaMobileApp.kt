package com.pessoal.agenda.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.health.report.HealthReportFormat
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.Normalizer

private enum class MobileSection(val label: String, val icon: ImageVector) {
    TODAY("Hoje", Icons.Outlined.Home),
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
            onSaveSensorySettings = { profile, snooze ->
                viewModel.saveSensorySettings(profile, snooze)
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
    onSync: () -> Unit,
    onPair: (String, String) -> Unit,
    onCancelPairing: () -> Unit,
    onPairingCompletionShown: () -> Unit,
    onFeedbackShown: () -> Unit,
    initialPairingInvitation: String? = null,
    onVisualAlertsChanged: (Boolean) -> Unit = {},
    onSaveSensorySettings: (com.pessoal.agenda.mobile.alert.SensoryProfile, com.pessoal.agenda.mobile.alert.SnoozePolicy) -> Unit = { _, _ -> },
    onPauseSensoryAlerts: (Int?) -> Unit = {},
    onTestAudio: () -> Unit = {},
    onRefreshAudioRoute: () -> Unit = {},
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
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showPairing by rememberSaveable { mutableStateOf(false) }
    var showSensorySettings by rememberSaveable { mutableStateOf(false) }
    var showHealth by rememberSaveable { mutableStateOf(false) }
    var showRecommendations by rememberSaveable { mutableStateOf(false) }
    var showLeavingChoices by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
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
                        if (!showSensorySettings && !showHealth && !showRecommendations) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!showSensorySettings && !showHealth && !showRecommendations) {
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
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                )
            } else {
                OfflineStatusBand(
                    state.operations.count { it.status in setOf("PENDING", "RETRYABLE", "IN_FLIGHT") },
                    state.canSync,
                    state.busy,
                    onSync,
                    onPair = { showPairing = true },
                )
                AlertsOptInBand(
                    enabled = state.sensorySettings.profile.globalEnabled,
                    busy = state.busy,
                    onChanged = onVisualAlertsChanged,
                )
                when (MobileSection.entries[selected]) {
                    MobileSection.TODAY -> TodayScreen(
                        tasks = state.tasks,
                        busy = state.busy,
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
                    MobileSection.CAPTURE -> CaptureScreen(state, onSaveCapture)
                    MobileSection.PROTOCOLS -> ProtocolScreen(
                        state.protocols,
                        state.activeRunSteps,
                        state.busy,
                        onStartProtocol,
                        onCompleteStep,
                        onProposeProtocolStep,
                    )
                    MobileSection.QUEUE -> QueueScreen(state.operations, state.conflicts)
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
    busy: Boolean,
    onLeavingHome: () -> Unit,
) {
    ScreenList(title = "Hoje") {
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
        if (tasks.isEmpty()) item { EmptyState("Nenhuma tarefa local") }
        items(tasks, key = { it.id }) { task ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
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
    onProposeStep: (String, String) -> Unit,
) {
    var proposalProtocol by remember { mutableStateOf<ProtocolTemplateEntity?>(null) }
    var proposalLabel by remember { mutableStateOf("") }
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
        modifier = Modifier.fillMaxSize(),
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
        AgendaMobileScreen(MobileUiState(), { _, _ -> }, {}, { _, _ -> }, {}, { _, _ -> }, {}, {}, {})
    }
}
