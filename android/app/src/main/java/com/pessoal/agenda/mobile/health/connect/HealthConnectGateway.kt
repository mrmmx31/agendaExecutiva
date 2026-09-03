package com.pessoal.agenda.mobile.health.connect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pessoal.agenda.mobile.BuildConfig
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.HealthMetric
import com.pessoal.agenda.mobile.health.HealthMetricName
import com.pessoal.agenda.mobile.health.HealthMissingReason
import java.time.Instant

enum class HealthConnectStatus { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

data class ImportedHealthSummary(
    val category: HealthCategory,
    val periodStart: Instant,
    val periodEnd: Instant,
    val coverageStart: Instant?,
    val coverageEnd: Instant?,
    val sampleCount: Int,
    val metrics: List<HealthMetric>,
    val sourcePackages: List<String>,
    val missingReason: HealthMissingReason?,
)

interface HealthConnectGateway {
    fun status(): HealthConnectStatus
    fun permissionsFor(categories: Set<HealthCategory>): Set<String>
    suspend fun grantedPermissions(): Set<String>
    suspend fun readSummaries(
        categories: Set<HealthCategory>,
        start: Instant,
        end: Instant,
    ): List<ImportedHealthSummary>
}

class AndroidHealthConnectGateway(
    private val context: Context,
    private val dataOrigins: Set<DataOrigin> = configuredDataOrigins(BuildConfig.HEALTH_DATA_ORIGIN_FILTER),
) : HealthConnectGateway {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun status(): HealthConnectStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
        else -> HealthConnectStatus.UNAVAILABLE
    }

    override fun permissionsFor(categories: Set<HealthCategory>): Set<String> = categories.mapTo(linkedSetOf()) {
        permissionFor(it)
    }

    override suspend fun grantedPermissions(): Set<String> = if (status() == HealthConnectStatus.AVAILABLE) {
        client.permissionController.getGrantedPermissions()
    } else {
        emptySet()
    }

    override suspend fun readSummaries(
        categories: Set<HealthCategory>,
        start: Instant,
        end: Instant,
    ): List<ImportedHealthSummary> {
        require(start < end)
        require(categories.all { it in IMPORTABLE })
        check(status() == HealthConnectStatus.AVAILABLE) { "Health Connect indisponível." }
        val granted = grantedPermissions()
        check(granted.containsAll(permissionsFor(categories))) { "Permissões Health Connect incompletas." }
        return categories.sortedBy(HealthCategory::ordinal).map { category ->
            when (category) {
                HealthCategory.HEART_RATE -> readHeartRate(start, end)
                HealthCategory.RESTING_HEART_RATE -> readRestingHeartRate(start, end)
                HealthCategory.SLEEP -> readSleep(start, end)
                HealthCategory.ACTIVITY -> readSteps(start, end)
                else -> error("Categoria não importável.")
            }
        }
    }

    private suspend fun readHeartRate(start: Instant, end: Instant): ImportedHealthSummary {
        val records = client.readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        ).records
        val samples = records.flatMap(HeartRateRecord::samples)
        val values = samples.map { it.beatsPerMinute.toDouble() }
        return summary(
            category = HealthCategory.HEART_RATE, start = start, end = end,
            coverageStart = samples.minOfOrNull { it.time }, coverageEnd = samples.maxOfOrNull { it.time },
            sampleCount = samples.size,
            metrics = bpmMetrics(values),
            sources = records.map { it.metadata.dataOrigin.packageName },
        )
    }

    private suspend fun readRestingHeartRate(start: Instant, end: Instant): ImportedHealthSummary {
        val records = client.readRecords(
            ReadRecordsRequest(
                RestingHeartRateRecord::class,
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        ).records
        val values = records.map { it.beatsPerMinute.toDouble() }
        return summary(
            category = HealthCategory.RESTING_HEART_RATE, start = start, end = end,
            coverageStart = records.minOfOrNull { it.time }, coverageEnd = records.maxOfOrNull { it.time },
            sampleCount = records.size,
            metrics = bpmMetrics(values),
            sources = records.map { it.metadata.dataOrigin.packageName },
        )
    }

    private suspend fun readSleep(start: Instant, end: Instant): ImportedHealthSummary {
        val records = client.readRecords(
            ReadRecordsRequest(
                SleepSessionRecord::class,
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        ).records
        val total = client.aggregate(
            AggregateRequest(
                setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        )[SleepSessionRecord.SLEEP_DURATION_TOTAL]
        return summary(
            category = HealthCategory.SLEEP, start = start, end = end,
            coverageStart = records.minOfOrNull { it.startTime }, coverageEnd = records.maxOfOrNull { it.endTime },
            sampleCount = records.size,
            metrics = total?.let { listOf(HealthMetric(HealthMetricName.SLEEP_MINUTES, it.toMinutes().toDouble(), "min")) }.orEmpty(),
            sources = records.map { it.metadata.dataOrigin.packageName },
        )
    }

    private suspend fun readSteps(start: Instant, end: Instant): ImportedHealthSummary {
        val records = client.readRecords(
            ReadRecordsRequest(
                StepsRecord::class,
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        ).records
        val total = client.aggregate(
            AggregateRequest(
                setOf(StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOrigins,
            ),
        )[StepsRecord.COUNT_TOTAL]
        return summary(
            category = HealthCategory.ACTIVITY, start = start, end = end,
            coverageStart = records.minOfOrNull { it.startTime }, coverageEnd = records.maxOfOrNull { it.endTime },
            sampleCount = records.size,
            metrics = total?.let { listOf(HealthMetric(HealthMetricName.STEPS, it.toDouble(), "count")) }.orEmpty(),
            sources = records.map { it.metadata.dataOrigin.packageName },
        )
    }

    private fun summary(
        category: HealthCategory,
        start: Instant,
        end: Instant,
        coverageStart: Instant?,
        coverageEnd: Instant?,
        sampleCount: Int,
        metrics: List<HealthMetric>,
        sources: List<String>,
    ) = ImportedHealthSummary(
        category = category,
        periodStart = start,
        periodEnd = end,
        coverageStart = coverageStart,
        coverageEnd = coverageEnd,
        sampleCount = sampleCount,
        metrics = if (sampleCount == 0) emptyList() else metrics,
        sourcePackages = sources.distinct().sorted().take(20),
        missingReason = if (sampleCount == 0) HealthMissingReason.NO_DATA else null,
    )

    private fun bpmMetrics(values: List<Double>): List<HealthMetric> = if (values.isEmpty()) emptyList() else listOf(
        HealthMetric(HealthMetricName.AVERAGE_BPM, values.average(), "bpm"),
        HealthMetric(HealthMetricName.MINIMUM_BPM, values.min(), "bpm"),
        HealthMetric(HealthMetricName.MAXIMUM_BPM, values.max(), "bpm"),
    )

    companion object {
        val IMPORTABLE = setOf(
            HealthCategory.HEART_RATE,
            HealthCategory.RESTING_HEART_RATE,
            HealthCategory.SLEEP,
            HealthCategory.ACTIVITY,
        )

        fun permissionFor(category: HealthCategory): String = when (category) {
            HealthCategory.HEART_RATE -> HealthPermission.getReadPermission(HeartRateRecord::class)
            HealthCategory.RESTING_HEART_RATE -> HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            HealthCategory.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
            HealthCategory.ACTIVITY -> HealthPermission.getReadPermission(StepsRecord::class)
            else -> throw IllegalArgumentException("Categoria sem permissão Health Connect.")
        }
    }
}

internal fun configuredDataOrigins(raw: String): Set<DataOrigin> = raw
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .mapTo(linkedSetOf()) { DataOrigin(it) }
