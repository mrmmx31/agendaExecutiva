package com.pessoal.agenda.mobile.recommendation

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PersonalDayPart { MORNING, AFTERNOON, EVENING, NIGHT }

@Serializable
enum class PersonalDayGroup { WEEKDAY, WEEKEND }

@Serializable
data class PersonalRankingSample(
    @SerialName("day_part") val dayPart: PersonalDayPart,
    @SerialName("day_group") val dayGroup: PersonalDayGroup,
    @SerialName("source_device") val sourceDevice: RecommendationSourceDevice,
    @SerialName("active_context") val activeContext: RecommendationActiveContext,
    @SerialName("capacity_context") val capacityContext: RecommendationCapacityContext,
    @SerialName("alert_kind") val alertKind: RecommendationAlertKind,
    @SerialName("deadline_bucket") val deadlineBucket: RecommendationDeadlineBucket,
    @SerialName("chosen_option") val chosenOption: RecommendationOptionCode,
) {
    fun featureNames(): List<String> = listOf(
        "day_part=${dayPart.name}",
        "day_group=${dayGroup.name}",
        "source_device=${sourceDevice.name}",
        "active_context=${activeContext.name}",
        "capacity_context=${capacityContext.name}",
        "alert_kind=${alertKind.name}",
        "deadline_bucket=${deadlineBucket.name}",
    )
}

@Serializable
data class PersonalRankingDataset(
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("dataset_id") val datasetId: String,
    val purpose: RecommendationPurpose,
    val source: PersonalDatasetSource,
    @SerialName("generated_at") val generatedAt: String,
    val samples: List<PersonalRankingSample>,
) {
    fun validatedSamples(): List<PersonalRankingSample> {
        require(contractVersion == 1)
        UUID.fromString(datasetId)
        Instant.parse(generatedAt)
        require(purpose == RecommendationPurpose.SNOOZE_PRESET)
        require(samples.isNotEmpty())
        require(samples.all { it.chosenOption in AuditableLinearModel.SNOOZE_OPTIONS })
        return samples
    }
}

@Serializable
enum class PersonalDatasetSource { LOCAL_EVENTS, SYNTHETIC_FIXTURE }

data class AuditableLinearModel(
    val featureNames: List<String>,
    val weights: Map<RecommendationOptionCode, Map<String, Double>>,
) {
    fun rank(sample: PersonalRankingSample): List<RecommendationOptionCode> {
        val active = sample.featureNames()
        return SNOOZE_OPTIONS.sortedWith(
            compareByDescending<RecommendationOptionCode> { option ->
                val optionWeights = weights.getValue(option)
                optionWeights.getValue(INTERCEPT) + active.sumOf { optionWeights.getValue(it) }
            }.thenBy { SNOOZE_OPTIONS.indexOf(it) },
        )
    }

    fun contributions(
        sample: PersonalRankingSample,
        option: RecommendationOptionCode,
    ): Map<String, Double> {
        val optionWeights = weights.getValue(option)
        return (listOf(INTERCEPT) + sample.featureNames()).associateWith(optionWeights::getValue)
    }

    companion object {
        const val INTERCEPT = "intercept"
        val SNOOZE_OPTIONS = listOf(
            RecommendationOptionCode.SNOOZE_5,
            RecommendationOptionCode.SNOOZE_10,
            RecommendationOptionCode.SNOOZE_15,
            RecommendationOptionCode.SNOOZE_30,
            RecommendationOptionCode.SNOOZE_60,
        )
    }
}

class AuditableLinearTrainer(
    private val epochs: Int = 24,
    private val learningRate: Double = 0.1,
) {
    fun train(samples: List<PersonalRankingSample>): AuditableLinearModel {
        require(samples.size >= MINIMUM_TRAINING_SAMPLES)
        require(epochs in 1..1_000)
        require(learningRate > 0.0 && learningRate <= 1.0)
        require(samples.all { it.chosenOption in AuditableLinearModel.SNOOZE_OPTIONS })

        val featureNames = PersonalFeatureVocabulary.ALL
        val allFeatures = listOf(AuditableLinearModel.INTERCEPT) + featureNames
        val mutableWeights = AuditableLinearModel.SNOOZE_OPTIONS.associateWith {
            allFeatures.associateWith { 0.0 }.toMutableMap()
        }
        repeat(epochs) {
            samples.forEach { sample ->
                val model = AuditableLinearModel(featureNames, mutableWeights)
                val predicted = model.rank(sample).first()
                if (predicted != sample.chosenOption) {
                    update(mutableWeights.getValue(sample.chosenOption), sample, learningRate)
                    update(mutableWeights.getValue(predicted), sample, -learningRate)
                }
            }
        }
        return AuditableLinearModel(
            featureNames = featureNames,
            weights = mutableWeights.mapValues { (_, value) -> value.toSortedMap().toMap() },
        )
    }

    private fun update(weights: MutableMap<String, Double>, sample: PersonalRankingSample, delta: Double) {
        weights[AuditableLinearModel.INTERCEPT] = weights.getValue(AuditableLinearModel.INTERCEPT) + delta
        sample.featureNames().forEach { feature -> weights[feature] = weights.getValue(feature) + delta }
    }

    companion object {
        const val MINIMUM_TRAINING_SAMPLES = 60
    }
}

private object PersonalFeatureVocabulary {
    val ALL = buildList {
        PersonalDayPart.entries.forEach { add("day_part=${it.name}") }
        PersonalDayGroup.entries.forEach { add("day_group=${it.name}") }
        RecommendationSourceDevice.entries.forEach { add("source_device=${it.name}") }
        RecommendationActiveContext.entries.forEach { add("active_context=${it.name}") }
        RecommendationCapacityContext.entries.forEach { add("capacity_context=${it.name}") }
        RecommendationAlertKind.entries.forEach { add("alert_kind=${it.name}") }
        RecommendationDeadlineBucket.entries.forEach { add("deadline_bucket=${it.name}") }
    }.sorted()
}

data class PersonalModelEvaluation(
    val trainingSampleCount: Int,
    val evaluationSampleCount: Int,
    val modelTop1Accuracy: Double,
    val baselineTop1Accuracy: Double,
    val eligibleForPromotion: Boolean,
)

class OfflinePersonalModelEvaluator(
    private val trainer: AuditableLinearTrainer = AuditableLinearTrainer(),
) {
    fun evaluate(samplesInTimeOrder: List<PersonalRankingSample>): PersonalModelEvaluation {
        require(samplesInTimeOrder.size >= MINIMUM_DATASET_SAMPLES)
        val split = (samplesInTimeOrder.size * TRAINING_FRACTION).toInt()
        require(split >= AuditableLinearTrainer.MINIMUM_TRAINING_SAMPLES)
        val training = samplesInTimeOrder.take(split)
        val evaluation = samplesInTimeOrder.drop(split)
        val model = trainer.train(training)
        val modelAccuracy = accuracy(evaluation) { model.rank(it).first() }
        val baselineAccuracy = accuracy(evaluation, RulesV1SnoozeBaseline::predict)
        return PersonalModelEvaluation(
            trainingSampleCount = training.size,
            evaluationSampleCount = evaluation.size,
            modelTop1Accuracy = modelAccuracy,
            baselineTop1Accuracy = baselineAccuracy,
            eligibleForPromotion = evaluation.size >= MINIMUM_EVALUATION_SAMPLES &&
                modelAccuracy - baselineAccuracy >= MINIMUM_ABSOLUTE_GAIN,
        )
    }

    private fun accuracy(
        samples: List<PersonalRankingSample>,
        predict: (PersonalRankingSample) -> RecommendationOptionCode,
    ): Double = samples.count { predict(it) == it.chosenOption }.toDouble() / samples.size

    companion object {
        const val MINIMUM_DATASET_SAMPLES = 75
        const val MINIMUM_EVALUATION_SAMPLES = 30
        const val MINIMUM_ABSOLUTE_GAIN = 0.05
        private const val TRAINING_FRACTION = 0.8
    }
}

object RulesV1SnoozeBaseline {
    fun predict(sample: PersonalRankingSample): RecommendationOptionCode = when {
        sample.deadlineBucket in URGENT_DEADLINES -> RecommendationOptionCode.SNOOZE_5
        sample.capacityContext == RecommendationCapacityContext.REDUCED_EXPLICIT ->
            RecommendationOptionCode.SNOOZE_15
        else -> RecommendationOptionCode.SNOOZE_15
    }

    private val URGENT_DEADLINES = setOf(
        RecommendationDeadlineBucket.OVERDUE,
        RecommendationDeadlineBucket.UNDER_15_MIN,
    )
}

data class ShadowComparison(
    val trainingSampleCount: Int,
    val ruleTopOption: RecommendationOptionCode,
    val modelTopOption: RecommendationOptionCode,
    val agreesWithRule: Boolean,
)

data class ShadowMetrics(
    val evaluatedCount: Int,
    val agreementCount: Int,
) {
    val agreementRate: Double
        get() = if (evaluatedCount == 0) 0.0 else agreementCount.toDouble() / evaluatedCount
}

class ShadowMetricsAccumulator {
    private val evaluated = AtomicInteger()
    private val agreements = AtomicInteger()

    fun record(comparison: ShadowComparison) {
        evaluated.incrementAndGet()
        if (comparison.agreesWithRule) agreements.incrementAndGet()
    }

    fun snapshot() = ShadowMetrics(evaluated.get(), agreements.get())
}

class ShadowingRecommendationEngine(
    private val primary: RecommendationEngine,
    private val trainer: AuditableLinearTrainer = AuditableLinearTrainer(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val onComparison: (ShadowComparison) -> Unit = {},
) : RecommendationEngine {
    override fun recommend(
        context: RecommendationContext,
        settings: RecommendationSettings,
        observations: List<RecommendationObservation>,
    ): RecommendationDecision? {
        val visibleDecision = primary.recommend(context, settings, observations)
        runCatching { compare(context, settings, observations, visibleDecision) }
            .getOrNull()
            ?.let { comparison -> runCatching { onComparison(comparison) } }
        return visibleDecision
    }

    private fun compare(
        context: RecommendationContext,
        settings: RecommendationSettings,
        observations: List<RecommendationObservation>,
        visibleDecision: RecommendationDecision?,
    ): ShadowComparison? {
        if (!settings.personalizationEnabled || context.purpose != RecommendationPurpose.SNOOZE_PRESET) return null
        val ruleTop = visibleDecision?.options?.firstOrNull()?.optionCode ?: return null
        val training = observations
            .sortedBy { it.occurredAt }
            .mapNotNull(::trainingSample)
            .takeLast(MAXIMUM_TRAINING_SAMPLES)
        if (training.size < AuditableLinearTrainer.MINIMUM_TRAINING_SAMPLES) return null
        val current = PersonalRankingSample(
            dayPart = dayPart(context.generatedAt.atZone(zoneId).hour),
            dayGroup = dayGroup(context.generatedAt.atZone(zoneId).dayOfWeek.value),
            sourceDevice = context.sourceDevice,
            activeContext = context.activeContext,
            capacityContext = context.capacityContext,
            alertKind = context.alertKind ?: RecommendationAlertKind.OTHER,
            deadlineBucket = context.deadlineBucket ?: RecommendationDeadlineBucket.NONE,
            chosenOption = ruleTop.takeIf { it in AuditableLinearModel.SNOOZE_OPTIONS }
                ?: return null,
        )
        if (training.count { it.matchesContext(current) } < DeterministicRecommendationEngine.MINIMUM_SAMPLES) {
            return null
        }
        val modelTop = trainer.train(training).rank(current).first()
        return ShadowComparison(training.size, ruleTop, modelTop, ruleTop == modelTop)
    }

    private fun trainingSample(observation: RecommendationObservation): PersonalRankingSample? {
        if (observation.eventType != RecommendationEventType.ALERT_SNOOZED) return null
        val chosen = observation.optionCode?.takeIf { it in AuditableLinearModel.SNOOZE_OPTIONS } ?: return null
        return PersonalRankingSample(
            dayPart = dayPart(observation.localHour),
            dayGroup = dayGroup(observation.dayOfWeek),
            sourceDevice = observation.sourceDevice,
            activeContext = observation.activeContext,
            capacityContext = observation.capacityContext,
            alertKind = observation.alertKind ?: return null,
            deadlineBucket = observation.deadlineBucket ?: return null,
            chosenOption = chosen,
        )
    }

    private fun dayGroup(day: Int) = if (day in 1..5) PersonalDayGroup.WEEKDAY else PersonalDayGroup.WEEKEND

    private fun dayPart(hour: Int) = when (hour) {
        in 5..11 -> PersonalDayPart.MORNING
        in 12..17 -> PersonalDayPart.AFTERNOON
        in 18..22 -> PersonalDayPart.EVENING
        else -> PersonalDayPart.NIGHT
    }

    private fun PersonalRankingSample.matchesContext(other: PersonalRankingSample): Boolean =
        dayPart == other.dayPart &&
            dayGroup == other.dayGroup &&
            activeContext == other.activeContext &&
            capacityContext == other.capacityContext &&
            alertKind == other.alertKind

    companion object {
        const val MAXIMUM_TRAINING_SAMPLES = 2_000
    }
}
