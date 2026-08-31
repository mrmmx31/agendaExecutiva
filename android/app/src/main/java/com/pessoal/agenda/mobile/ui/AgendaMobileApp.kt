package com.pessoal.agenda.mobile.ui

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.PendingOperationEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class MobileSection(val label: String, val icon: ImageVector) {
    TODAY("Hoje", Icons.Outlined.Home),
    CAPTURE("Capturar", Icons.Outlined.Add),
    PROTOCOLS("Protocolos", Icons.Outlined.Checklist),
    QUEUE("Fila", Icons.Outlined.Inbox),
}

@Composable
fun AgendaMobileApp(viewModel: AgendaMobileViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgendaMobileTheme {
        AgendaMobileScreen(
            state = state,
            onSaveCapture = viewModel::saveCapture,
            onStartProtocol = viewModel::startProtocol,
            onCompleteStep = viewModel::completeStep,
            onFeedbackShown = viewModel::clearFeedback,
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
    onFeedbackShown: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            onFeedbackShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agenda")
                        Text(
                            text = "Núcleo offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
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
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineStatusBand(state.operations.count { it.status == "PENDING" })
            when (MobileSection.entries[selected]) {
                MobileSection.TODAY -> TodayScreen(state.tasks)
                MobileSection.CAPTURE -> CaptureScreen(state, onSaveCapture)
                MobileSection.PROTOCOLS -> ProtocolScreen(
                    state.protocols,
                    state.activeRunSteps,
                    state.busy,
                    onStartProtocol,
                    onCompleteStep,
                )
                MobileSection.QUEUE -> QueueScreen(state.operations)
            }
        }
    }
}

@Composable
private fun OfflineStatusBand(pendingCount: Int) {
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
                Text("Somente neste telefone", style = MaterialTheme.typography.labelLarge)
            }
            Text("$pendingCount na fila", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TodayScreen(tasks: List<TaskReplicaEntity>) {
    ScreenList(title = "Hoje") {
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
                        "Dados fictícios · ${task.status.userLabel()}",
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
) {
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
                            "Revisão ${protocol.revision} · dados fictícios",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onStart(protocol.id) }, enabled = !busy) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Text("Iniciar")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun QueueScreen(operations: List<PendingOperationEntity>) {
    ScreenList(title = "Fila offline") {
        if (operations.isEmpty()) item { EmptyState("Nenhuma operação pendente") }
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
    else -> "Estado local"
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
        AgendaMobileScreen(MobileUiState(), { _, _ -> }, {}, { _, _ -> }, {})
    }
}
