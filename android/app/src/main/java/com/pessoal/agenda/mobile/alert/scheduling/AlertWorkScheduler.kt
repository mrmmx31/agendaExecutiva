package com.pessoal.agenda.mobile.alert.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pessoal.agenda.mobile.alert.notification.AlertDeliveryProcessor
import com.pessoal.agenda.mobile.alert.notification.AndroidAlertNotificationPublisher
import com.pessoal.agenda.mobile.alert.output.AndroidSensoryOutput
import com.pessoal.agenda.mobile.data.AlertSchedule
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.pairing.DeviceCredentialStore
import com.pessoal.agenda.mobile.wear.AndroidWearStatePublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class AlertSchedulingCoordinator(
    private val store: AlertStore,
    private val enqueuer: AlertWorkEnqueuer,
) {
    constructor(context: Context, store: AlertStore) : this(
        store,
        WorkManagerAlertEnqueuer(context.applicationContext),
    )

    suspend fun schedule(alertId: String, at: Instant): Boolean {
        val schedule = store.scheduleEvaluation(alertId, at) ?: return false
        if (store.ensureInstallationProfile().profile.globalEnabled) enqueuer.replace(schedule)
        return true
    }

    suspend fun reconcile(): Int {
        if (!store.ensureInstallationProfile().profile.globalEnabled) {
            enqueuer.cancelAll()
            return 0
        }
        val schedules = store.schedulesForReconciliation()
        schedules.forEach(enqueuer::replace)
        return schedules.size
    }

    suspend fun reactivate(): Int {
        check(store.ensureInstallationProfile().profile.globalEnabled) { "Alertas desativados." }
        val schedules = store.schedulesForActivation()
        schedules.forEach(enqueuer::replace)
        return schedules.size
    }

    fun pause() = enqueuer.cancelAll()

    suspend fun cancel(alertId: String): Boolean {
        val changed = store.cancelScheduling(alertId)
        enqueuer.cancel(alertId)
        return changed
    }
}

interface AlertWorkEnqueuer {
    fun replace(schedule: AlertSchedule)
    fun append(schedule: AlertSchedule)
    fun cancel(alertId: String)
    fun cancelAll()
}

class WorkManagerAlertEnqueuer(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val workManager: WorkManager = WorkManager.getInstance(context),
) : AlertWorkEnqueuer {
    override fun replace(schedule: AlertSchedule) = enqueue(schedule, ExistingWorkPolicy.REPLACE)

    override fun append(schedule: AlertSchedule) = enqueue(schedule, ExistingWorkPolicy.APPEND_OR_REPLACE)

    override fun cancel(alertId: String) {
        UUID.fromString(alertId)
        workManager.cancelUniqueWork(uniqueName(alertId))
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(ALERT_WORK_TAG)
    }

    private fun enqueue(schedule: AlertSchedule, policy: ExistingWorkPolicy) {
        UUID.fromString(schedule.alertId)
        val delay = Duration.between(Instant.now(clock), schedule.nextAt).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<AlertEvaluationWorker>()
            .setInputData(workDataOf(AlertEvaluationWorker.ALERT_ID_KEY to schedule.alertId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(ALERT_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(uniqueName(schedule.alertId), policy, request)
    }

    companion object {
        const val ALERT_WORK_TAG = "agenda-alert-evaluation"
        fun uniqueName(alertId: String): String = "agenda-alert-$alertId"
    }
}

class AlertEvaluationWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val alertId = inputData.getString(ALERT_ID_KEY) ?: return Result.failure(errorData("INVALID_ALERT_ID"))
        if (runCatching { UUID.fromString(alertId) }.isFailure) {
            return Result.failure(errorData("INVALID_ALERT_ID"))
        }
        return try {
            val store = AlertStore(MobileDatabase.get(applicationContext))
            store.ensureInstallationProfile()
            AlertDeliveryProcessor(
                store = store,
                enqueuer = WorkManagerAlertEnqueuer(applicationContext),
                publisher = AndroidAlertNotificationPublisher(applicationContext),
                sensoryOutput = AndroidSensoryOutput(applicationContext),
                deviceIdProvider = { DeviceCredentialStore(applicationContext).deviceId },
                wearPublisher = AndroidWearStatePublisher(applicationContext, store),
            ).process(alertId, id.toString())
            Result.success()
        } catch (_: IllegalArgumentException) {
            Result.failure(errorData("INVALID_ALERT_STATE"))
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry()
            else Result.failure(errorData("EVALUATION_FAILED"))
        }
    }

    private fun errorData(code: String): Data = workDataOf(ERROR_KEY to code)

    companion object {
        const val ALERT_ID_KEY = "alert_id"
        const val ERROR_KEY = "error_code"
        const val MAX_ATTEMPTS = 3
    }
}
