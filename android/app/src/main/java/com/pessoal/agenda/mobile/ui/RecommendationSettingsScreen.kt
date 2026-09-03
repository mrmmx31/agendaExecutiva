package com.pessoal.agenda.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext
import com.pessoal.agenda.mobile.recommendation.RecommendationCapacityContext
import com.pessoal.agenda.mobile.recommendation.RecommendationChannel
import com.pessoal.agenda.mobile.recommendation.RecommendationOption
import com.pessoal.agenda.mobile.recommendation.RecommendationReason
import com.pessoal.agenda.mobile.recommendation.RecommendationSettings
import com.pessoal.agenda.mobile.recommendation.PersonalModelStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun RecommendationSettingsScreen(
    state: RecommendationUiState,
    busy: Boolean,
    onSaveSettings: (RecommendationSettings) -> Unit,
    onCorrectEvent: (String, RecommendationActiveContext, RecommendationCapacityContext) -> Unit,
    onClearHistory: () -> Unit,
    onTrainModel: () -> Unit,
    onActivateModel: (String) -> Unit,
    onRollbackModel: () -> Unit,
) {
    var correction by remember { mutableStateOf<RecommendationEventEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmActivation by remember { mutableStateOf(false) }

    correction?.let { event ->
        EventContextCorrectionDialog(
            event = event,
            onDismiss = { correction = null },
            onConfirm = { active, capacity ->
                onCorrectEvent(event.id, active, capacity)
                correction = null
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar histórico local?") },
            text = { Text("Eventos, decisões, modelos e métricas locais serão removidos. Tarefas, alertas, protocolos e saúde não serão alterados.") },
            confirmButton = {
                Button(onClick = { onClearHistory(); confirmClear = false }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text("Apagar")
                }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    if (confirmActivation && state.model.version != null) {
        AlertDialog(
            onDismissRequest = { confirmActivation = false },
            title = { Text("Ativar modelo pessoal?") },
            text = { Text("A ordem dos adiamentos poderá mudar. Limites das regras e ações explícitas continuam obrigatórios.") },
            confirmButton = {
                Button(onClick = { onActivateModel(state.model.version); confirmActivation = false }) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Text("Ativar")
                }
            },
            dismissButton = { OutlinedButton(onClick = { confirmActivation = false }) { Text("Cancelar") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("recommendation-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RecommendationSectionTitle("Controle") }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Personalização local", fontWeight = FontWeight.Medium)
                    Text(
                        if (state.settings.personalizationEnabled) "Ativa neste aparelho" else "Regras padrão ativas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.settings.personalizationEnabled,
                    onCheckedChange = { onSaveSettings(state.settings.copy(personalizationEnabled = it)) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Personalização local" },
                )
            }
        }

        item { RecommendationSectionTitle("Modelo pessoal") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow("Estado", state.model.status.statusLabel(state.model.activeVersion))
                MetricRow(
                    "Adiamentos elegíveis",
                    "${state.model.eligibleEventCount}/${com.pessoal.agenda.mobile.recommendation.OfflinePersonalModelEvaluator.MINIMUM_DATASET_SAMPLES}",
                )
                Button(
                    onClick = onTrainModel,
                    enabled = !busy && state.settings.personalizationEnabled &&
                        state.model.eligibleEventCount >= com.pessoal.agenda.mobile.recommendation.OfflinePersonalModelEvaluator.MINIMUM_DATASET_SAMPLES,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("Treinar e avaliar")
                }
                if (state.model.version != null) {
                    MetricRow("Versão", state.model.version)
                    MetricRow("Treino / avaliação", "${state.model.trainingSampleCount} / ${state.model.evaluationSampleCount}")
                    MetricRow("Top-1 modelo", state.model.top1Accuracy.percentLabel())
                    MetricRow("Top-1 regras", state.model.baselineTop1Accuracy.percentLabel())
                    MetricRow("Shadow", "${state.shadowMetrics.agreementCount}/${state.shadowMetrics.evaluatedCount} concordâncias")
                    MetricRow("SHA-256", state.model.artifactHashPrefix ?: "Indisponível")
                    MetricRow("Artefato", state.model.artifactSizeBytes.byteLabel())
                    MetricRow("Pesos em memória", state.model.approximateWeightBytes.byteLabel())
                    MetricRow("Último treino", state.model.lastTrainingMillis.millisLabel())
                    MetricRow("Inferência", state.model.inferenceMicros.microsLabel())
                }
                if (state.model.eligibleForActivation && state.model.version != null) {
                    Button(
                        onClick = { confirmActivation = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text("Ativar modelo")
                    }
                }
                if (state.model.activeVersion != null) {
                    OutlinedButton(
                        onClick = onRollbackModel,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
                        Text("Restaurar regras")
                    }
                }
            }
        }

        item { RecommendationSectionTitle("Contexto informado") }
        items(RecommendationCapacityContext.entries, key = { "capacity-${it.name}" }) { value ->
            PreferenceRow(
                label = value.label(),
                selected = state.settings.capacityContext == value,
                enabled = !busy,
                onClick = { onSaveSettings(state.settings.copy(capacityContext = value)) },
            )
        }

        item { RecommendationSectionTitle("Preferência de adiamento") }
        items(listOf<Int?>(null, 5, 10, 15, 30, 60), key = { "snooze-${it ?: 0}" }) { value ->
            PreferenceRow(
                label = value?.let { "$it min" } ?: "Automática pelas regras",
                selected = state.settings.preferredSnoozeMinutes == value,
                enabled = !busy,
                onClick = { onSaveSettings(state.settings.copy(preferredSnoozeMinutes = value)) },
            )
        }

        item { RecommendationSectionTitle("Preferência de canal") }
        items(PREFERRED_CHANNELS, key = { "channel-${it?.name ?: "AUTO"}" }) { value ->
            PreferenceRow(
                label = value?.label() ?: "Automática pelas regras",
                selected = state.settings.preferredChannel == value,
                enabled = !busy,
                onClick = { onSaveSettings(state.settings.copy(preferredChannel = value)) },
            )
        }

        item { RecommendationSectionTitle("Retenção local") }
        items(listOf(30, 90, 180), key = { "retention-$it" }) { days ->
            PreferenceRow(
                label = "$days dias",
                selected = state.settings.retentionDays == days,
                enabled = !busy,
                onClick = { onSaveSettings(state.settings.copy(retentionDays = days)) },
            )
        }

        item { RecommendationSectionTitle("Sugestões atuais") }
        if (state.baselineOptions.isEmpty()) {
            item { MutedText("Nenhuma sugestão disponível neste contexto") }
        } else {
            items(state.baselineOptions, key = { "option-${it.optionCode.name}" }) { option ->
                RecommendationOptionRow(option)
            }
        }

        item { RecommendationSectionTitle("Indicadores locais") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricRow("Eventos", state.statistics.totalEvents.toString())
                MetricRow("Latência mediana", state.statistics.medianResponseLatencySeconds?.let(::secondsLabel) ?: "Sem amostra")
                MetricRow("Correções explícitas", "${state.statistics.correctedEvents} (${state.statistics.correctionRatePercent}%)")
                MetricRow("Adiamentos", state.statistics.snoozeEvents.toString())
                MetricRow("Sequências estimadas", state.statistics.repeatedSnoozeEstimate.toString())
                MetricRow("Alertas expirados", state.statistics.missedAlerts.toString())
                MetricRow("Bateria", "A medir no gate P2-09")
            }
        }

        item { RecommendationSectionTitle("Histórico inspecionável") }
        if (state.events.isEmpty()) {
            item { MutedText("Nenhum evento local") }
        } else {
            items(state.events, key = { it.id }) { event ->
                RecommendationEventRow(event, enabled = !busy, onEdit = { correction = event })
            }
        }
        item {
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !busy && state.events.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text("Apagar histórico")
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecommendationOptionRow(option: RecommendationOption) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${option.rank}.", fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            Text(option.optionCode.label())
            MutedText(option.reasonCode.label())
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecommendationEventRow(event: RecommendationEventEntity, enabled: Boolean, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.eventType.eventLabel(), fontWeight = FontWeight.Medium)
            MutedText("${event.occurredAt.localLabel()} • ${event.sourceDevice.sourceLabel()}")
            MutedText("${event.activeContext.contextLabel()} • ${event.capacityContext.capacityLabel()}${event.optionCode?.let { " • ${it.optionLabel()}" }.orEmpty()}")
            if (event.correctedAt != null) MutedText("Contexto corrigido")
        }
        IconButton(
            onClick = onEdit,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Corrigir contexto do evento" },
        ) { Icon(Icons.Outlined.Edit, contentDescription = null) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EventContextCorrectionDialog(
    event: RecommendationEventEntity,
    onDismiss: () -> Unit,
    onConfirm: (RecommendationActiveContext, RecommendationCapacityContext) -> Unit,
) {
    var active by remember(event.id) { mutableStateOf(RecommendationActiveContext.valueOf(event.activeContext)) }
    var capacity by remember(event.id) { mutableStateOf(RecommendationCapacityContext.valueOf(event.capacityContext)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrigir contexto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Atividade", fontWeight = FontWeight.Medium)
                RecommendationActiveContext.entries.forEach { value ->
                    PreferenceRow(value.label(), active == value, true) { active = value }
                }
                Text("Capacidade informada", fontWeight = FontWeight.Medium)
                RecommendationCapacityContext.entries.forEach { value ->
                    PreferenceRow(value.label(), capacity == value, true) { capacity = value }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(active, capacity) }) { Text("Aplicar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RecommendationSectionTitle(value: String) = Text(
    value,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 8.dp),
)

@Composable
private fun MutedText(value: String) = Text(
    value,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun RecommendationCapacityContext.label() = when (this) {
    RecommendationCapacityContext.STANDARD -> "Padrão"
    RecommendationCapacityContext.REDUCED_EXPLICIT -> "Capacidade reduzida"
    RecommendationCapacityContext.PARALLEL_EXPLICIT -> "Contexto paralelo"
}

private fun RecommendationActiveContext.label() = when (this) {
    RecommendationActiveContext.NONE -> "Nenhuma atividade"
    RecommendationActiveContext.FOCUS -> "Foco ativo"
    RecommendationActiveContext.PROTOCOL -> "Protocolo ativo"
}

private fun RecommendationChannel.label() = when (this) {
    RecommendationChannel.VISUAL -> "Visual"
    RecommendationChannel.PHONE_AUDIO -> "Áudio do telefone"
    RecommendationChannel.PHONE_VIBRATION -> "Vibração do telefone"
    RecommendationChannel.WATCH -> "Relógio"
}

private fun RecommendationReason.label() = when (this) {
    RecommendationReason.CAUTIOUS_DEFAULT -> "Padrão cauteloso"
    RecommendationReason.MANUAL_PREFERENCE -> "Preferência manual"
    RecommendationReason.ENOUGH_LOCAL_HISTORY -> "Histórico local suficiente"
    RecommendationReason.QUIET_HOURS_GUARD -> "Horário silencioso"
    RecommendationReason.DEVICE_AVAILABLE -> "Dispositivo disponível"
    RecommendationReason.ACTIVE_PROTOCOL -> "Protocolo ativo"
    RecommendationReason.DOMAIN_LIMIT_APPLIED -> "Limite de segurança aplicado"
    RecommendationReason.PERSONAL_MODEL -> "Modelo pessoal ativo"
}

private fun com.pessoal.agenda.mobile.recommendation.RecommendationOptionCode.label() = name.optionLabel()
private fun String.optionLabel() = when {
    startsWith("SNOOZE_") -> "Adiar ${removePrefix("SNOOZE_")} min"
    this == "CHANNEL_VISUAL" -> "Canal visual"
    this == "CHANNEL_AUDIO" -> "Canal de áudio"
    this == "CHANNEL_WATCH" -> "Canal do relógio"
    this == "PROTOCOL_EXIT" -> "Protocolo de saída"
    else -> this
}

private fun String.eventLabel() = when (this) {
    "ALERT_PRESENTED" -> "Alerta apresentado"
    "ALERT_COMPLETED" -> "Alerta concluído"
    "ALERT_SNOOZED" -> "Alerta adiado"
    "ALERT_EXPIRED" -> "Alerta expirado"
    "PROTOCOL_STARTED" -> "Protocolo iniciado"
    "PROTOCOL_STEP_COMPLETED" -> "Etapa concluída"
    "RECOMMENDATION_SHOWN" -> "Sugestão apresentada"
    "RECOMMENDATION_ACCEPTED" -> "Sugestão aceita"
    "RECOMMENDATION_CORRECTED" -> "Sugestão corrigida"
    else -> this
}

private fun String.sourceLabel() = if (this == "WATCH") "Relógio" else "Telefone"
private fun String.contextLabel() = RecommendationActiveContext.valueOf(this).label()
private fun String.capacityLabel() = RecommendationCapacityContext.valueOf(this).label()
private fun String.localLabel(): String = DATE_TIME.format(Instant.parse(this).atZone(ZoneId.systemDefault()))
private fun secondsLabel(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min"
private fun PersonalModelStatus?.statusLabel(activeVersion: String?): String = when {
    activeVersion != null -> "Ativo: $activeVersion"
    this == PersonalModelStatus.SHADOW -> "Em observação"
    this == PersonalModelStatus.ROLLED_BACK -> "Regras restauradas"
    else -> "Ainda não treinado"
}
private fun Double?.percentLabel() = this?.let { "%.1f%%".format(it * 100) } ?: "Sem medição"
private fun Int?.byteLabel() = this?.let { "$it bytes" } ?: "Sem medição"
private fun Long?.millisLabel() = this?.let { "$it ms" } ?: "Somente nesta execução"
private fun Long?.microsLabel() = this?.let { "$it µs" } ?: "Sem medição"

private val PREFERRED_CHANNELS = listOf<RecommendationChannel?>(
    null,
    RecommendationChannel.VISUAL,
    RecommendationChannel.PHONE_AUDIO,
    RecommendationChannel.WATCH,
)
private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
