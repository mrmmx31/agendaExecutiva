package com.pessoal.agenda.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.QuietHours
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.alert.SnoozePolicy
import java.time.Instant

@Composable
internal fun SensorySettingsScreen(
    state: SensorySettingsUiState,
    alertsEnabled: Boolean,
    visualNotificationsAvailable: Boolean,
    busy: Boolean,
    onGlobalChanged: (Boolean) -> Unit,
    onSave: (SensoryProfile, SnoozePolicy) -> Unit,
    onPause: (Int?) -> Unit,
    onTestAudio: () -> Unit,
    onRefreshRoute: () -> Unit,
) {
    var draft by remember(state.profile) { mutableStateOf(state.profile) }
    var quietEnabled by remember(state.profile) { mutableStateOf(state.profile.quietHours != null) }
    var quietStart by remember(state.profile) { mutableStateOf(state.profile.quietHours?.startsAt ?: "22:30") }
    var quietEnd by remember(state.profile) { mutableStateOf(state.profile.quietHours?.endsAt ?: "07:00") }
    var snoozeText by remember(state.snoozePolicy) {
        mutableStateOf(state.snoozePolicy.presetMinutes.joinToString(", "))
    }
    var routeMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefreshRoute() }

    val snoozePresets = parseSnoozePresets(snoozeText)
    val quiet = if (quietEnabled) runCatching { QuietHours(quietStart, quietEnd).also(QuietHours::validate) }.getOrNull()
        else null
    val candidate = draft.copy(quietHours = quiet)
    val valid = snoozePresets != null
        && (!quietEnabled || quiet != null)
        && runCatching { candidate.validate() }.isSuccess
    val paused = draft.pausedUntil?.let { runCatching { Instant.parse(it).isAfter(Instant.now()) }.getOrDefault(false) }
        ?: false

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsSection("Controle") {
                SettingsSwitchRow(
                    title = "Alertas gerais",
                    detail = if (alertsEnabled) "Ativos" else "Desativados",
                    checked = alertsEnabled,
                    enabled = !busy,
                    onChanged = onGlobalChanged,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPause(30) }, enabled = alertsEnabled && !busy) {
                        Text("Pausar 30 min")
                    }
                    OutlinedButton(onClick = { onPause(60) }, enabled = alertsEnabled && !busy) {
                        Text("Pausar 1 h")
                    }
                    if (paused) {
                        Button(onClick = { onPause(null) }, enabled = !busy) { Text("Retomar") }
                    }
                }
                draft.pausedUntil?.takeIf { paused }?.let { Text("Pausado até ${it.localTimeLabel()}") }
            }
        }
        item {
            SettingsSection("Perfil") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SensoryPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset.matches(draft),
                            onClick = {
                                draft = preset.applyTo(draft)
                                quietEnabled = true
                                quietStart = "22:30"
                                quietEnd = "07:00"
                            },
                            label = { Text(preset.label) },
                        )
                    }
                }
                Text("Canais", style = MaterialTheme.typography.labelLarge)
                SettingsSwitchRow(
                    "Visual",
                    if (alertsEnabled && !visualNotificationsAvailable) {
                        "Permissão Android necessária para notificar"
                    } else {
                        "Notificação privada com Concluir e Adiar"
                    },
                    SensoryChannel.VISUAL in draft.enabledChannels,
                    true,
                ) { draft = draft.withChannel(SensoryChannel.VISUAL, it) }
                SettingsSwitchRow(
                    "Vibração do telefone",
                    "Pulso único curto",
                    SensoryChannel.PHONE_VIBRATION in draft.enabledChannels,
                    true,
                ) { enabled ->
                    draft = draft.withChannel(SensoryChannel.PHONE_VIBRATION, enabled).let {
                        if (enabled && it.audioRoute == AudioRoutePolicy.NONE) {
                            it.copy(audioRoute = AudioRoutePolicy.VIBRATION_ONLY)
                        } else it
                    }
                }
                SettingsSwitchRow(
                    "Áudio da Agenda",
                    "Tom curto, sem alterar a rota de outros aplicativos",
                    SensoryChannel.AUDIO in draft.enabledChannels,
                    true,
                ) { enabled ->
                    draft = draft.withChannel(SensoryChannel.AUDIO, enabled).let {
                        if (enabled && it.audioRoute in setOf(AudioRoutePolicy.NONE, AudioRoutePolicy.VIBRATION_ONLY)) {
                            it.copy(audioRoute = AudioRoutePolicy.SYSTEM_DEFAULT)
                        } else it
                    }
                }
                SettingsSwitchRow(
                    "Vibração do relógio",
                    "Disponível na fase Wear OS",
                    false,
                    false,
                    onChanged = {},
                )
            }
        }
        item {
            SettingsSection("Rota de áudio") {
                Column {
                    OutlinedButton(
                        onClick = { routeMenuOpen = true },
                        enabled = SensoryChannel.AUDIO in draft.enabledChannels,
                    ) {
                        Text(draft.audioRoute.routeLabel())
                    }
                    DropdownMenu(expanded = routeMenuOpen, onDismissRequest = { routeMenuOpen = false }) {
                        availableRoutes(state).forEach { route ->
                            DropdownMenuItem(
                                text = { Text(route.routeLabel()) },
                                onClick = {
                                    draft = draft.copy(
                                        audioRoute = route,
                                        enabledChannels = when (route) {
                                            AudioRoutePolicy.NONE -> (draft.enabledChannels
                                                - SensoryChannel.AUDIO - SensoryChannel.PHONE_VIBRATION
                                                - SensoryChannel.WEAR_VIBRATION)
                                            AudioRoutePolicy.VIBRATION_ONLY -> (draft.enabledChannels
                                                - SensoryChannel.AUDIO + SensoryChannel.PHONE_VIBRATION)
                                            else -> draft.enabledChannels + SensoryChannel.AUDIO
                                        },
                                    )
                                    routeMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Saída efetiva", style = MaterialTheme.typography.labelLarge)
                        Text(state.routeStatus.effectiveLabel)
                        state.routeStatus.fallbackReason?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = onTestAudio,
                    enabled = alertsEnabled && SensoryChannel.AUDIO in state.profile.enabledChannels && !busy,
                ) {
                    Icon(
                        if (state.audioTestRunning) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                    )
                    Text(if (state.audioTestRunning) "Interromper teste" else "Testar áudio")
                }
            }
        }
        item {
            SettingsSection("Limites") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = quietEnabled, onCheckedChange = { quietEnabled = it })
                    Text("Horário silencioso")
                }
                if (quietEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quietStart,
                            onValueChange = { quietStart = it.take(5) },
                            label = { Text("Início") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = quietEnd,
                            onValueChange = { quietEnd = it.take(5) },
                            label = { Text("Fim") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("Cooldown: ${draft.cooldownMinutes} min")
                Slider(
                    value = draft.cooldownMinutes.toFloat(),
                    onValueChange = { draft = draft.copy(cooldownMinutes = it.toInt().coerceIn(1, 60)) },
                    valueRange = 1f..60f,
                    steps = 58,
                )
                OutlinedTextField(
                    value = snoozeText,
                    onValueChange = { snoozeText = it.take(24) },
                    label = { Text("Adiamentos em minutos") },
                    supportingText = { Text("De 5 a 240 min; até cinco opções") },
                    isError = snoozePresets == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        candidate,
                        state.snoozePolicy.copy(presetMinutes = requireNotNull(snoozePresets)),
                    )
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Text("Salvar perfil")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
        HorizontalDivider()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled)
    }
}

private enum class SensoryPreset(val label: String) {
    VISUAL("Visual"),
    DISCREET("Discreto"),
    HEADPHONES("Fone");

    fun applyTo(profile: SensoryProfile): SensoryProfile = when (this) {
        VISUAL -> profile.copy(
            enabledChannels = setOf(SensoryChannel.VISUAL),
            quietHours = QuietHours("22:30", "07:00"),
            cooldownMinutes = 10,
            audioRoute = AudioRoutePolicy.SYSTEM_DEFAULT,
        )
        DISCREET -> profile.copy(
            enabledChannels = setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION),
            quietHours = QuietHours("22:30", "07:00"),
            cooldownMinutes = 10,
            audioRoute = AudioRoutePolicy.VIBRATION_ONLY,
        )
        HEADPHONES -> profile.copy(
            enabledChannels = setOf(SensoryChannel.VISUAL, SensoryChannel.AUDIO),
            quietHours = QuietHours("22:30", "07:00"),
            cooldownMinutes = 5,
            audioRoute = AudioRoutePolicy.PREFER_HEADPHONES,
        )
    }

    fun matches(profile: SensoryProfile): Boolean = when (this) {
        VISUAL -> profile.enabledChannels == setOf(SensoryChannel.VISUAL) && profile.cooldownMinutes == 10
        DISCREET -> profile.enabledChannels == setOf(
            SensoryChannel.VISUAL,
            SensoryChannel.PHONE_VIBRATION,
        ) && profile.audioRoute == AudioRoutePolicy.VIBRATION_ONLY
        HEADPHONES -> profile.enabledChannels == setOf(
            SensoryChannel.VISUAL,
            SensoryChannel.AUDIO,
        ) && profile.audioRoute == AudioRoutePolicy.PREFER_HEADPHONES
    }
}

private fun SensoryProfile.withChannel(channel: SensoryChannel, enabled: Boolean) = copy(
    enabledChannels = if (enabled) enabledChannels + channel else enabledChannels - channel,
)

private fun parseSnoozePresets(value: String): List<Int>? = runCatching {
    value.split(',').map(String::trim).filter(String::isNotEmpty).map(String::toInt)
        .also { require(it.size in 1..5 && it == it.distinct().sorted() && it.all { minute -> minute in 5..240 }) }
}.getOrNull()

private fun availableRoutes(state: SensorySettingsUiState): List<AudioRoutePolicy> = buildList {
    add(AudioRoutePolicy.SYSTEM_DEFAULT)
    if (state.routeStatus.headphonesAvailable || state.profile.audioRoute == AudioRoutePolicy.PREFER_HEADPHONES) {
        add(AudioRoutePolicy.PREFER_HEADPHONES)
    }
    if (state.routeStatus.phoneSpeakerAvailable || state.profile.audioRoute == AudioRoutePolicy.PREFER_PHONE) {
        add(AudioRoutePolicy.PREFER_PHONE)
    }
    add(AudioRoutePolicy.VIBRATION_ONLY)
    add(AudioRoutePolicy.NONE)
}

private fun AudioRoutePolicy.routeLabel(): String = when (this) {
    AudioRoutePolicy.SYSTEM_DEFAULT -> "Automática do sistema"
    AudioRoutePolicy.PREFER_HEADPHONES -> "Priorizar fone"
    AudioRoutePolicy.PREFER_PHONE -> "Priorizar telefone"
    AudioRoutePolicy.VIBRATION_ONLY -> "Somente vibração"
    AudioRoutePolicy.NONE -> "Sem saída sensorial"
}

private fun String.localTimeLabel(): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(Instant.parse(this))
}.getOrDefault(this)
