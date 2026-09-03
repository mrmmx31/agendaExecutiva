package com.pessoal.agenda.mobile.recommendation

import java.time.Instant

class RecommendationTelemetry(
    private val store: RecommendationStore,
) {
    suspend fun record(
        eventType: RecommendationEventType,
        occurredAt: Instant,
        sourceDevice: RecommendationSourceDevice,
        activeContext: RecommendationActiveContext,
        alertKind: RecommendationAlertKind? = null,
        deadlineBucket: RecommendationDeadlineBucket? = null,
        channel: RecommendationChannel? = null,
        responseLatencySeconds: Int? = null,
        snoozeMinutes: Int? = null,
        recommendationId: String? = null,
        optionCode: RecommendationOptionCode? = null,
    ): Boolean = runCatching {
        val settings = store.settings()
        store.recordEvent(
            RecommendationEventInput(
                eventType = eventType,
                occurredAt = occurredAt,
                sourceDevice = sourceDevice,
                activeContext = activeContext,
                capacityContext = settings.capacityContext,
                alertKind = alertKind,
                deadlineBucket = deadlineBucket,
                channel = channel,
                responseLatencySeconds = responseLatencySeconds,
                snoozeMinutes = snoozeMinutes,
                recommendationId = recommendationId,
                optionCode = optionCode,
            ),
        ) != null
    }.getOrDefault(false)
}
