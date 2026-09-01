package com.pessoal.agenda.mobile.alert

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertDefinition(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("alert_id") val alertId: String,
    val origin: AlertOrigin,
    @SerialName("reference_id") val referenceId: String?,
    val text: String,
    val reason: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("valid_until") val validUntil: String,
    val criticality: FunctionalCriticality,
    @SerialName("allowed_channels") val allowedChannels: Set<SensoryChannel>,
    @SerialName("repeat_policy") val repeatPolicy: AlertRepeatPolicy,
    val actions: Set<AlertActionType>,
) {
    fun validate() {
        require(contractVersion == CONTRACT_VERSION) { "Versão de alerta incompatível." }
        UUID.fromString(alertId)
        referenceId?.let(UUID::fromString)
        UUID.fromString(sourceDeviceId)
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) { "Texto de alerta inválido." }
        require(reason.isNotBlank() && reason.length <= MAX_REASON_LENGTH) { "Motivo de alerta inválido." }
        val scheduled = Instant.parse(scheduledAt)
        val expiration = Instant.parse(validUntil)
        require(expiration.isAfter(scheduled)) { "Janela do alerta inválida." }
        require(Duration.between(scheduled, expiration) <= MAX_VALID_WINDOW) { "Janela do alerta excede o limite." }
        require(allowedChannels.isNotEmpty()) { "Alerta sem canal permitido." }
        require(actions.isNotEmpty()) { "Alerta sem ação disponível." }
        repeatPolicy.validate()
    }

    companion object {
        const val CONTRACT_VERSION = 1
        const val MAX_TEXT_LENGTH = 160
        const val MAX_REASON_LENGTH = 160
        val MAX_VALID_WINDOW: Duration = Duration.ofDays(7)
    }
}

@Serializable
data class AlertRepeatPolicy(
    @SerialName("max_deliveries") val maxDeliveries: Int,
    @SerialName("minimum_interval_minutes") val minimumIntervalMinutes: Int,
) {
    fun validate() {
        require(maxDeliveries in 1..5) { "Quantidade de entregas fora do limite." }
        require(minimumIntervalMinutes in 5..1_440) { "Intervalo de repetição fora do limite." }
    }
}

@Serializable
data class SensoryProfile(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("global_enabled") val globalEnabled: Boolean,
    @SerialName("enabled_channels") val enabledChannels: Set<SensoryChannel>,
    @SerialName("quiet_hours") val quietHours: QuietHours?,
    @SerialName("paused_until") val pausedUntil: String?,
    @SerialName("cooldown_minutes") val cooldownMinutes: Int,
    @SerialName("audio_route") val audioRoute: AudioRoutePolicy,
) {
    fun validate() {
        require(contractVersion == AlertDefinition.CONTRACT_VERSION) { "Versão de perfil incompatível." }
        require(cooldownMinutes in 1..60) { "Cooldown fora do limite." }
        quietHours?.validate()
        pausedUntil?.let(Instant::parse)
        if (audioRoute == AudioRoutePolicy.VIBRATION_ONLY) {
            require(SensoryChannel.AUDIO !in enabledChannels) { "Perfil de vibração não pode habilitar áudio." }
        }
        if (audioRoute == AudioRoutePolicy.NONE) {
            require(enabledChannels.none(SensoryChannel::isSensory)) { "Perfil sem saída não pode habilitar estímulos." }
        }
    }

    companion object {
        fun installationDefault() = SensoryProfile(
            contractVersion = AlertDefinition.CONTRACT_VERSION,
            globalEnabled = false,
            enabledChannels = setOf(SensoryChannel.VISUAL),
            quietHours = QuietHours("22:30", "07:00"),
            pausedUntil = null,
            cooldownMinutes = 5,
            audioRoute = AudioRoutePolicy.SYSTEM_DEFAULT,
        )
    }
}

@Serializable
data class QuietHours(
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
) {
    fun validate() {
        require(start() != end()) { "Horário silencioso não pode cobrir o dia inteiro." }
    }

    fun contains(time: LocalTime): Boolean {
        val start = start()
        val end = end()
        return if (start < end) time >= start && time < end else time >= start || time < end
    }

    private fun start(): LocalTime = parseMinute(startsAt)
    private fun end(): LocalTime = parseMinute(endsAt)

    private fun parseMinute(value: String): LocalTime {
        require(TIME_PATTERN.matches(value)) { "Horário silencioso inválido." }
        return LocalTime.parse(value)
    }

    private companion object { val TIME_PATTERN = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]") }
}

@Serializable
data class SnoozePolicy(
    @SerialName("preset_minutes") val presetMinutes: List<Int>,
    @SerialName("minimum_minutes") val minimumMinutes: Int,
    @SerialName("maximum_minutes") val maximumMinutes: Int,
    @SerialName("maximum_count") val maximumCount: Int,
) {
    fun validate() {
        require(minimumMinutes >= 5) { "Adiamento mínimo deve ser de cinco minutos." }
        require(maximumMinutes in minimumMinutes..1_440) { "Adiamento máximo fora do limite." }
        require(maximumCount in 1..5) { "Quantidade de adiamentos fora do limite." }
        require(presetMinutes.size in 1..5 && presetMinutes == presetMinutes.distinct().sorted()) {
            "Presets de adiamento inválidos."
        }
        require(presetMinutes.all { it in minimumMinutes..maximumMinutes }) { "Preset fora do limite." }
    }

    companion object {
        fun cautiousDefault() = SnoozePolicy(listOf(10, 30, 60), 5, 240, 3)
    }
}

@Serializable
data class AlertActionCommand(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("operation_id") val operationId: String,
    @SerialName("alert_id") val alertId: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    val action: AlertActionType,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("snooze_until") val snoozeUntil: String?,
) {
    fun validate(policy: SnoozePolicy) {
        policy.validate()
        require(contractVersion == AlertDefinition.CONTRACT_VERSION) { "Versão de ação incompatível." }
        UUID.fromString(operationId)
        UUID.fromString(alertId)
        UUID.fromString(sourceDeviceId)
        val occurred = Instant.parse(occurredAt)
        when (action) {
            AlertActionType.COMPLETE -> require(snoozeUntil == null) { "Conclusão não aceita adiamento." }
            AlertActionType.SNOOZE -> {
                val target = Instant.parse(requireNotNull(snoozeUntil) { "Adiamento sem novo horário." })
                val minutes = Duration.between(occurred, target).toMinutes()
                require(minutes in policy.minimumMinutes.toLong()..policy.maximumMinutes.toLong()) {
                    "Novo horário fora dos limites de adiamento."
                }
            }
        }
    }
}

@Serializable enum class AlertOrigin { TASK, PROTOCOL, MANUAL }
@Serializable enum class FunctionalCriticality { ROUTINE, IMPORTANT }
@Serializable enum class AlertActionType { COMPLETE, SNOOZE }
@Serializable enum class AudioRoutePolicy { SYSTEM_DEFAULT, PREFER_HEADPHONES, PREFER_PHONE, VIBRATION_ONLY, NONE }

@Serializable
enum class SensoryChannel {
    VISUAL,
    PHONE_VIBRATION,
    WEAR_VIBRATION,
    AUDIO;

    fun isSensory(): Boolean = this != VISUAL
}
