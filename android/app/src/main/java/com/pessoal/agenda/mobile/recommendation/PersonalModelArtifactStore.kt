package com.pessoal.agenda.mobile.recommendation

import androidx.room.withTransaction
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.data.local.PersonalModelArtifactEntity
import com.pessoal.agenda.mobile.data.local.PersonalModelShadowMetricsEntity
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class StoredPersonalModel(
    val modelVersion: String,
    val status: PersonalModelStatus,
    val model: AuditableLinearModel,
    val evaluation: PersonalModelEvaluation,
    val artifactSha256: String,
    val artifactSizeBytes: Int,
)

enum class PersonalModelStatus { SHADOW, ACTIVE, ROLLED_BACK }

class PersonalModelArtifactStore(
    private val database: MobileDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = database.offline()
    private val json = Json { encodeDefaults = true }

    suspend fun stage(
        modelVersion: String,
        model: AuditableLinearModel,
        evaluation: PersonalModelEvaluation,
        trainedAt: Instant = Instant.now(clock),
    ): StoredPersonalModel = database.withTransaction {
        validateVersion(modelVersion)
        require(evaluation.trainingSampleCount >= AuditableLinearTrainer.MINIMUM_TRAINING_SAMPLES)
        require(evaluation.evaluationSampleCount > 0)
        val payload = json.encodeToString(model.toArtifactPayload())
        val now = Instant.now(clock).toString()
        val entity = PersonalModelArtifactEntity(
            modelId = MODEL_ID,
            modelVersion = modelVersion,
            contractVersion = CONTRACT_VERSION,
            purpose = RecommendationPurpose.SNOOZE_PRESET.name,
            runtime = RUNTIME,
            featureContractVersion = FEATURE_CONTRACT_VERSION,
            artifactFormat = ARTIFACT_FORMAT,
            artifactJson = payload,
            artifactSha256 = sha256(payload),
            trainedAt = trainedAt.toString(),
            trainingSampleCount = evaluation.trainingSampleCount,
            status = PersonalModelStatus.SHADOW.name,
            evaluationSampleCount = evaluation.evaluationSampleCount,
            top1Accuracy = evaluation.modelTop1Accuracy,
            baselineTop1Accuracy = evaluation.baselineTop1Accuracy,
            rollbackModelId = null,
            activatedAt = null,
            updatedAt = now,
        )
        dao.insertPersonalModelArtifact(entity)
        requireNotNull(entity.toStoredOrNull())
    }

    suspend fun activate(modelVersion: String): StoredPersonalModel = database.withTransaction {
        validateVersion(modelVersion)
        val candidate = requireNotNull(dao.personalModelArtifact(MODEL_ID, modelVersion))
        val stored = requireNotNull(candidate.toStoredOrNull())
        require(stored.status == PersonalModelStatus.SHADOW)
        require(stored.evaluation.eligibleForPromotion)
        val now = Instant.now(clock).toString()
        dao.rollbackActivePersonalModel(MODEL_ID, now)
        check(dao.activatePersonalModel(MODEL_ID, modelVersion, now) == 1)
        requireNotNull(dao.personalModelArtifact(MODEL_ID, modelVersion)?.toStoredOrNull())
    }

    suspend fun active(): StoredPersonalModel? = database.withTransaction {
        val active = dao.activePersonalModelArtifact(MODEL_ID) ?: return@withTransaction null
        active.toStoredOrNull() ?: run {
            dao.rollbackActivePersonalModel(MODEL_ID, Instant.now(clock).toString())
            null
        }
    }

    suspend fun rollbackToRules(): Boolean = database.withTransaction {
        dao.rollbackActivePersonalModel(MODEL_ID, Instant.now(clock).toString()) > 0
    }

    suspend fun versions(): List<PersonalModelArtifactEntity> = dao.personalModelArtifacts(MODEL_ID)

    suspend fun load(modelVersion: String): StoredPersonalModel? = database.withTransaction {
        validateVersion(modelVersion)
        dao.personalModelArtifact(MODEL_ID, modelVersion)?.toStoredOrNull()
    }

    suspend fun recordShadow(comparison: ShadowComparison) = database.withTransaction {
        val current = dao.personalModelShadowMetrics(MODEL_ID)
        dao.upsertPersonalModelShadowMetrics(
            PersonalModelShadowMetricsEntity(
                modelId = MODEL_ID,
                evaluatedCount = (current?.evaluatedCount ?: 0) + 1,
                agreementCount = (current?.agreementCount ?: 0) + if (comparison.agreesWithRule) 1 else 0,
                lastRuleOption = comparison.ruleTopOption.name,
                lastModelOption = comparison.modelTopOption.name,
                updatedAt = Instant.now(clock).toString(),
            ),
        )
    }

    suspend fun shadowMetrics(): ShadowMetrics {
        val value = dao.personalModelShadowMetrics(MODEL_ID) ?: return ShadowMetrics(0, 0)
        return ShadowMetrics(value.evaluatedCount, value.agreementCount)
    }

    private fun PersonalModelArtifactEntity.toStoredOrNull(): StoredPersonalModel? = runCatching {
        require(contractVersion == CONTRACT_VERSION)
        require(purpose == RecommendationPurpose.SNOOZE_PRESET.name)
        require(runtime == RUNTIME && featureContractVersion == FEATURE_CONTRACT_VERSION)
        require(artifactFormat == ARTIFACT_FORMAT)
        require(MessageDigest.isEqual(artifactSha256.toByteArray(), sha256(artifactJson).toByteArray()))
        val payload = json.decodeFromString<LinearArtifactPayload>(artifactJson)
        StoredPersonalModel(
            modelVersion = modelVersion,
            status = PersonalModelStatus.valueOf(status),
            model = payload.toModel(),
            evaluation = PersonalModelEvaluation(
                trainingSampleCount,
                evaluationSampleCount,
                top1Accuracy,
                baselineTop1Accuracy,
                evaluationSampleCount >= OfflinePersonalModelEvaluator.MINIMUM_EVALUATION_SAMPLES &&
                    top1Accuracy - baselineTop1Accuracy >= OfflinePersonalModelEvaluator.MINIMUM_ABSOLUTE_GAIN,
            ),
            artifactSha256 = artifactSha256,
            artifactSizeBytes = artifactJson.toByteArray(Charsets.UTF_8).size,
        )
    }.getOrNull()

    private fun validateVersion(value: String) {
        require(value.matches(Regex("[a-zA-Z0-9._-]{1,64}")))
    }

    companion object {
        const val MODEL_ID = "personal-snooze-ranker"
        const val CONTRACT_VERSION = 1
        const val FEATURE_CONTRACT_VERSION = 1
        const val RUNTIME = "AUDITABLE_LINEAR_KOTLIN"
        const val ARTIFACT_FORMAT = "AGENDA_LINEAR_JSON_V1"

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
