package com.pessoal.agenda.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.R
import com.pessoal.agenda.mobile.health.IntakeInput
import com.pessoal.agenda.mobile.health.IntakeKind
import com.pessoal.agenda.mobile.health.SymptomInput
import com.pessoal.agenda.mobile.health.SubjectiveKind
import com.pessoal.agenda.mobile.health.connect.AndroidHealthConnectGateway
import com.pessoal.agenda.mobile.health.connect.HealthConnectStatus
import com.pessoal.agenda.mobile.health.report.HealthReportEntryKind
import com.pessoal.agenda.mobile.health.report.HealthReportFormat
import java.time.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun HealthPrivacyScreen(
    state: HealthUiState,
    busy: Boolean,
    onConsentChanged: (HealthCategory, Boolean) -> Unit,
    onSaveIntake: (String?, IntakeInput) -> Unit,
    onDeleteIntake: (String) -> Unit,
    onSaveSymptom: (String?, SymptomInput) -> Unit,
    onDeleteSymptom: (String) -> Unit,
    onImportHealth: () -> Unit,
    onGenerateReport: (Int, Set<HealthCategory>) -> Unit,
    onReportSubjectChanged: (String) -> Unit,
    onToggleReportEntry: (String) -> Unit,
    onExportReport: (HealthReportFormat) -> Unit,
) {
    var intakeEditor by remember { mutableStateOf<Pair<String?, IntakeInput>?>(null) }
    var symptomEditor by remember { mutableStateOf<Pair<String?, SymptomInput>?>(null) }
    var deletion by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val enabled = state.consents.associate { HealthCategory.valueOf(it.category) to it.enabled }
    var reportDays by remember { mutableStateOf(7) }
    var reportCategories by remember(state.consents) {
        mutableStateOf(enabled.filterValues { it }.keys)
    }

    intakeEditor?.let { (id, value) ->
        IntakeDialog(value, id != null, { intakeEditor = null }) {
            onSaveIntake(id, it)
            intakeEditor = null
        }
    }
    symptomEditor?.let { (id, value) ->
        SymptomDialog(value, id != null, { symptomEditor = null }) {
            onSaveSymptom(id, it)
            symptomEditor = null
        }
    }
    deletion?.let { (id, symptom) ->
        AlertDialog(
            onDismissRequest = { deletion = null },
            title = { Text("Excluir registro?") },
            text = { Text("O conteúdo cifrado será apagado. A marca técnica de exclusão será mantida.") },
            confirmButton = {
                Button(onClick = {
                    if (symptom) onDeleteSymptom(id) else onDeleteIntake(id)
                    deletion = null
                }) { Text("Excluir") }
            },
            dismissButton = { OutlinedButton(onClick = { deletion = null }) { Text("Cancelar") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("health-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Privacidade e limites") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.health_privacy_notice))
                Text(
                    stringResource(R.string.health_non_medical_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle("Health Connect") }
        item {
            val importableEnabled = enabled.any { (category, active) ->
                active && category in AndroidHealthConnectGateway.IMPORTABLE
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state.connectStatus) {
                        HealthConnectStatus.AVAILABLE -> "Disponível • leitura manual dos últimos 7 dias"
                        HealthConnectStatus.UPDATE_REQUIRED -> "Atualização do Health Connect necessária"
                        HealthConnectStatus.UNAVAILABLE -> "Indisponível neste aparelho"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (importableEnabled) {
                    Text(
                        "Desligar uma categoria de sensor revoga todo o acesso ao Health Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onImportHealth,
                    enabled = !busy && importableEnabled && state.connectStatus == HealthConnectStatus.AVAILABLE,
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Outlined.Sync, null); Text("Importar resumos") }
                state.summaries.firstOrNull()?.let { latest ->
                    Text(
                        "Última leitura: ${latest.category.label()} • ${latest.sampleCount} amostras",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Amostras brutas não são copiadas. Permissões são solicitadas apenas ao importar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle("Permissões locais") }
        items(HealthCategory.entries, key = { it.name }) { category ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(category.label(), fontWeight = FontWeight.Medium)
                    Text(
                        if (enabled[category] == true) "Relatório revisável • desligue para revogar" else "Sem leitura • toque para autorizar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled[category] == true,
                    onCheckedChange = { onConsentChanged(category, it) },
                    enabled = !busy,
                    modifier = Modifier.semantics {
                        contentDescription = if (enabled[category] == true) "Revogar ${category.label()}" else "Ativar ${category.label()}"
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        item { SectionTitle("Registrar") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { intakeEditor = null to IntakeInput(IntakeKind.MEDICATION, "", occurredAt = Instant.now().toString()) },
                    enabled = !busy && enabled[HealthCategory.MEDICATION] == true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Adicionar medicação" },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) { Icon(Icons.Outlined.Add, null); Text("Medicação") }
                OutlinedButton(
                    onClick = { intakeEditor = null to IntakeInput(IntakeKind.SUBSTANCE, "", occurredAt = Instant.now().toString()) },
                    enabled = !busy && enabled[HealthCategory.SUBSTANCE] == true,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) { Icon(Icons.Outlined.Add, null); Text("Substância") }
            }
        }
        item {
            OutlinedButton(
                onClick = { symptomEditor = null to SymptomInput("", Instant.now().toString()) },
                enabled = !busy && enabled[HealthCategory.SYMPTOM] == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Outlined.Add, null); Text("Sintoma ou evento") }
        }
        item {
            OutlinedButton(
                onClick = { symptomEditor = null to SymptomInput("", Instant.now().toString(), kind = SubjectiveKind.ROUTINE_NOTE) },
                enabled = !busy && enabled[HealthCategory.ROUTINE_NOTE] == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Outlined.Add, null); Text("Nota de rotina") }
        }

        item { SectionTitle("Histórico local") }
        if (state.intakes.isEmpty() && state.symptoms.isEmpty()) {
            item { Text("Nenhum registro local", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(state.intakes, key = { "intake-${it.id}" }) { record ->
            HistoryRow(record.value.name, record.value.kind.label(), {
                intakeEditor = record.id to record.value
            }, { deletion = record.id to false })
        }
        items(state.symptoms, key = { "symptom-${it.id}" }) { record ->
            HistoryRow(record.value.label, if (record.value.kind == SubjectiveKind.ROUTINE_NOTE) "Nota de rotina" else "Sintoma ou evento", {
                symptomEditor = record.id to record.value
            }, { deletion = record.id to true })
        }

        item { SectionTitle("Relatório revisável") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Período", fontWeight = FontWeight.Medium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(7, 30, 90).forEachIndexed { index, days ->
                        SegmentedButton(
                            selected = reportDays == days,
                            onClick = { reportDays = days },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            label = { Text("$days dias") },
                        )
                    }
                }
                Text("Categorias", fontWeight = FontWeight.Medium)
                HealthCategory.entries.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = category in reportCategories,
                            onCheckedChange = { checked ->
                                reportCategories = reportCategories.toMutableSet().apply {
                                    if (checked) add(category) else remove(category)
                                }
                            },
                        )
                        Text(category.label())
                    }
                }
                Button(
                    onClick = { onGenerateReport(reportDays, reportCategories) },
                    enabled = !busy && reportCategories.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gerar prévia") }
            }
        }
        state.report.snapshot?.let { snapshot ->
            item {
                OutlinedTextField(
                    value = state.report.subjectLabel,
                    onValueChange = onReportSubjectChanged,
                    label = { Text("Identificação no relatório") },
                    supportingText = { Text("Pode ficar vazia ou ser corrigida antes da exportação") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Prévia: ${snapshot.entries.size - state.report.excludedEntryIds.size} de ${snapshot.entries.size} linhas incluídas",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Sensor, fato registrado e observação permanecem separados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(snapshot.entries, key = { "report-${it.id}" }) { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = entry.id !in state.report.excludedEntryIds,
                        onCheckedChange = { onToggleReportEntry(entry.id) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, maxLines = 2)
                        Text(
                            "${entry.kind.label()} • ${HealthCategory.valueOf(entry.category).label()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text(
                    "Este arquivo pode conter dados sensíveis. Revise as linhas e escolha conscientemente onde salvá-lo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthReportFormat.entries.forEach { format ->
                        OutlinedButton(
                            onClick = { onExportReport(format) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text(format.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(title: String, type: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Corrigir") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Excluir") }
    }
}

@Composable
private fun IntakeDialog(initial: IntakeInput, editing: Boolean, onDismiss: () -> Unit, onSave: (IntakeInput) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var amount by remember(initial) { mutableStateOf(initial.amount?.toString().orEmpty()) }
    var unit by remember(initial) { mutableStateOf(initial.unit.orEmpty()) }
    var context by remember(initial) { mutableStateOf(initial.context.orEmpty()) }
    var effect by remember(initial) { mutableStateOf(initial.perceivedEffect.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial.note.orEmpty()) }
    var plannedNow by remember(initial) { mutableStateOf(initial.plannedAt != null) }
    val parsedAmount = amount.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Corrigir ${initial.kind.label().lowercase()}" else "Registrar ${initial.kind.label().lowercase()}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(amount, { amount = it.take(20) }, label = { Text("Quantidade opcional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(unit, { unit = it.take(40) }, label = { Text("Unidade opcional") }, modifier = Modifier.fillMaxWidth()) }
                if (initial.kind == IntakeKind.MEDICATION) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Também era o horário planejado", modifier = Modifier.weight(1f))
                            Switch(checked = plannedNow, onCheckedChange = { plannedNow = it })
                        }
                    }
                }
                if (initial.kind == IntakeKind.SUBSTANCE) {
                    item { OutlinedTextField(context, { context = it.take(500) }, label = { Text("Contexto opcional") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(effect, { effect = it.take(500) }, label = { Text("Efeito percebido opcional") }, modifier = Modifier.fillMaxWidth()) }
                }
                item { OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Nota opcional") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && (amount.isBlank() || parsedAmount?.let { it > 0 } == true),
                onClick = { onSave(initial.copy(name = name, amount = parsedAmount, unit = unit.nullIfBlank(), plannedAt = if (plannedNow) initial.plannedAt ?: initial.occurredAt else null, context = context.nullIfBlank(), perceivedEffect = effect.nullIfBlank(), note = note.nullIfBlank())) },
            ) { Text("Salvar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SymptomDialog(initial: SymptomInput, editing: Boolean, onDismiss: () -> Unit, onSave: (SymptomInput) -> Unit) {
    var label by remember(initial) { mutableStateOf(initial.label) }
    var intensity by remember(initial) { mutableStateOf(initial.intensity?.toString().orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial.note.orEmpty()) }
    val parsedIntensity = intensity.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when {
            initial.kind == SubjectiveKind.ROUTINE_NOTE && editing -> "Corrigir nota de rotina"
            initial.kind == SubjectiveKind.ROUTINE_NOTE -> "Registrar nota de rotina"
            editing -> "Corrigir evento"
            else -> "Registrar evento"
        }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(label, { label = it.take(120) }, label = { Text(if (initial.kind == SubjectiveKind.ROUTINE_NOTE) "Título" else "Sintoma ou evento") }, modifier = Modifier.fillMaxWidth())
            if (initial.kind == SubjectiveKind.SYMPTOM) {
                OutlinedTextField(intensity, { intensity = it.filter(Char::isDigit).take(2) }, label = { Text("Intensidade opcional (0 a 10)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Nota opcional") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(enabled = label.isNotBlank() && (intensity.isBlank() || parsedIntensity in 0..10), onClick = { onSave(initial.copy(label = label, intensity = parsedIntensity, note = note.nullIfBlank())) }) { Text("Salvar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
private fun HealthCategory.label() = when (this) {
    HealthCategory.HEART_RATE -> "Frequência cardíaca"
    HealthCategory.RESTING_HEART_RATE -> "Frequência em repouso"
    HealthCategory.SLEEP -> "Sono"
    HealthCategory.ACTIVITY -> "Atividade"
    HealthCategory.MEDICATION -> "Medicação"
    HealthCategory.SUBSTANCE -> "Substância"
    HealthCategory.SYMPTOM -> "Sintoma"
    HealthCategory.ROUTINE_NOTE -> "Nota de rotina"
}
private fun IntakeKind.label() = if (this == IntakeKind.MEDICATION) "Medicação" else "Substância"
private fun HealthReportEntryKind.label() = when (this) {
    HealthReportEntryKind.SENSOR_AGGREGATE -> "Agregado de sensor"
    HealthReportEntryKind.RECORDED_FACT -> "Fato registrado"
    HealthReportEntryKind.USER_OBSERVATION -> "Observação do usuário"
}
private fun String.nullIfBlank() = trim().ifEmpty { null }
