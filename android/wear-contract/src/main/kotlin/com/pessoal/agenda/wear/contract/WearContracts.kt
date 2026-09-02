package com.pessoal.agenda.wear.contract

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WearAlertState(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("alert_id") val alertId: String,
    val revision: Long,
    val text: String,
    val reason: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("valid_until") val validUntil: String,
    @SerialName("updated_at") val updatedAt: String,
    val criticality: WearCriticality,
    val actions: List<WearActionType>,
    @SerialName("snooze_options_minutes") val snoozeOptionsMinutes: List<Int>,
    val status: WearAlertStatus,
) {
    fun validate() {
        require(contractVersion == CONTRACT_VERSION) { "Versão Wear incompatível." }
        UUID.fromString(alertId)
        UUID.fromString(sourceDeviceId)
        require(revision > 0) { "Revisão Wear inválida." }
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) { "Texto Wear inválido." }
        require(reason.isNotBlank() && reason.length <= MAX_REASON_LENGTH) { "Motivo Wear inválido." }

        val scheduled = Instant.parse(scheduledAt)
        val expiration = Instant.parse(validUntil)
        Instant.parse(updatedAt)
        require(expiration.isAfter(scheduled)) { "Janela Wear inválida." }
        require(Duration.between(scheduled, expiration) <= MAX_VALID_WINDOW) {
            "Janela Wear excede o limite."
        }
        require(actions == REQUIRED_ACTIONS) { "Wear aceita somente Concluir e Adiar." }
        require(
            snoozeOptionsMinutes.size in 1..MAX_SNOOZE_OPTIONS &&
                snoozeOptionsMinutes == snoozeOptionsMinutes.distinct().sorted() &&
                snoozeOptionsMinutes.all { it in MIN_SNOOZE_MINUTES..MAX_SNOOZE_MINUTES },
        ) { "Sugestões de adiamento Wear inválidas." }
    }

    companion object {
        const val CONTRACT_VERSION = 1
        const val MAX_TEXT_LENGTH = 160
        const val MAX_REASON_LENGTH = 160
        const val MAX_SNOOZE_OPTIONS = 3
        const val MIN_SNOOZE_MINUTES = 5
        const val MAX_SNOOZE_MINUTES = 240
        val MAX_VALID_WINDOW: Duration = Duration.ofDays(7)
        val REQUIRED_ACTIONS = listOf(WearActionType.COMPLETE, WearActionType.SNOOZE)
    }
}

@Serializable
data class WearAlertAction(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("operation_id") val operationId: String,
    @SerialName("alert_id") val alertId: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    val action: WearActionType,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("snooze_until") val snoozeUntil: String?,
) {
    fun validate() {
        require(contractVersion == WearAlertState.CONTRACT_VERSION) { "Versão de ação Wear incompatível." }
        UUID.fromString(operationId)
        UUID.fromString(alertId)
        UUID.fromString(sourceDeviceId)
        val occurred = Instant.parse(occurredAt)
        when (action) {
            WearActionType.COMPLETE -> require(snoozeUntil == null) { "Conclusão não aceita adiamento." }
            WearActionType.SNOOZE -> {
                val target = Instant.parse(requireNotNull(snoozeUntil) { "Adiamento sem novo horário." })
                val minutes = Duration.between(occurred, target).toMinutes()
                require(minutes in WearAlertState.MIN_SNOOZE_MINUTES.toLong()..WearAlertState.MAX_SNOOZE_MINUTES.toLong()) {
                    "Adiamento Wear fora dos limites."
                }
            }
        }
    }
}

@Serializable
enum class WearCriticality { ROUTINE, IMPORTANT }

@Serializable
enum class WearActionType { COMPLETE, SNOOZE }

@Serializable
enum class WearAlertStatus { PENDING, COMPLETED, SNOOZED, CANCELLED, EXPIRED }

object WearDataPaths {
    const val ALERT_STATE_PREFIX = "/agenda/v1/alerts/"
    const val ACTION_MESSAGE = "/agenda/v1/actions"
    const val PHONE_CAPABILITY = "agenda_phone_v1"
    const val WEAR_CAPABILITY = "agenda_wear_v1"

    fun alertState(alertId: String): String {
        UUID.fromString(alertId)
        return ALERT_STATE_PREFIX + alertId
    }

    fun alertId(path: String): String? {
        if (!path.startsWith(ALERT_STATE_PREFIX)) return null
        val value = path.removePrefix(ALERT_STATE_PREFIX)
        if (value.contains('/')) return null
        return runCatching { UUID.fromString(value).toString() }.getOrNull()
    }
}

object WearContractCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encodeState(state: WearAlertState): ByteArray {
        state.validate()
        return json.encodeToString(state).encodeToByteArray()
    }

    fun decodeState(payload: ByteArray): WearAlertState =
        json.decodeFromString<WearAlertState>(payload.decodeToString()).also(WearAlertState::validate)

    fun encodeAction(action: WearAlertAction): ByteArray {
        action.validate()
        return json.encodeToString(action).encodeToByteArray()
    }

    fun decodeAction(payload: ByteArray): WearAlertAction =
        json.decodeFromString<WearAlertAction>(payload.decodeToString()).also(WearAlertAction::validate)
}
