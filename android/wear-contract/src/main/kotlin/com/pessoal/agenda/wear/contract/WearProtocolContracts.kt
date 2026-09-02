package com.pessoal.agenda.wear.contract

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WearProtocolStepState(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("run_id") val runId: String,
    @SerialName("protocol_id") val protocolId: String,
    val revision: Long,
    @SerialName("protocol_title") val protocolTitle: String,
    @SerialName("step_id") val stepId: String?,
    @SerialName("step_label") val stepLabel: String?,
    @SerialName("step_position") val stepPosition: Int?,
    @SerialName("step_count") val stepCount: Int,
    @SerialName("updated_at") val updatedAt: String,
    val status: WearProtocolStatus,
    @SerialName("acknowledged_operation_id") val acknowledgedOperationId: String?,
) {
    fun validate() {
        require(contractVersion == CONTRACT_VERSION)
        UUID.fromString(runId)
        UUID.fromString(protocolId)
        require(revision > 0)
        require(protocolTitle.isNotBlank() && protocolTitle.length <= 80)
        require(stepCount in 1..100)
        Instant.parse(updatedAt)
        acknowledgedOperationId?.let(UUID::fromString)
        if (status == WearProtocolStatus.ACTIVE) {
            UUID.fromString(requireNotNull(stepId))
            require(!stepLabel.isNullOrBlank() && stepLabel.length <= 120)
            require(requireNotNull(stepPosition) in 1..stepCount)
        } else {
            require(stepId == null && stepLabel == null && stepPosition == null)
        }
    }

    companion object { const val CONTRACT_VERSION = 1 }
}

@Serializable
data class WearProtocolStepAction(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("operation_id") val operationId: String,
    @SerialName("run_id") val runId: String,
    @SerialName("step_id") val stepId: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("occurred_at") val occurredAt: String,
) {
    fun validate() {
        require(contractVersion == WearProtocolStepState.CONTRACT_VERSION)
        UUID.fromString(operationId)
        UUID.fromString(runId)
        UUID.fromString(stepId)
        UUID.fromString(sourceDeviceId)
        Instant.parse(occurredAt)
    }
}

@Serializable
enum class WearProtocolStatus { ACTIVE, COMPLETED }

object WearProtocolCodec {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    fun encodeState(state: WearProtocolStepState): ByteArray = state.also(WearProtocolStepState::validate)
        .let { json.encodeToString(it).encodeToByteArray() }

    fun decodeState(payload: ByteArray): WearProtocolStepState = json
        .decodeFromString<WearProtocolStepState>(payload.decodeToString())
        .also(WearProtocolStepState::validate)

    fun encodeAction(action: WearProtocolStepAction): ByteArray = action.also(WearProtocolStepAction::validate)
        .let { json.encodeToString(it).encodeToByteArray() }

    fun decodeAction(payload: ByteArray): WearProtocolStepAction = json
        .decodeFromString<WearProtocolStepAction>(payload.decodeToString())
        .also(WearProtocolStepAction::validate)
}
