package com.pessoal.agenda.mobile.alert.scheduling

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pessoal.agenda.mobile.alert.AlertActionType
import com.pessoal.agenda.mobile.alert.AlertDefinition
import com.pessoal.agenda.mobile.alert.AlertOrigin
import com.pessoal.agenda.mobile.alert.AlertRepeatPolicy
import com.pessoal.agenda.mobile.alert.FunctionalCriticality
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.data.AlertStore
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerAlertEnqueuerTest {
    @Test
    fun workerRejectsInvalidAlertIdWithoutRetrying() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<AlertEvaluationWorker>(
            context = context,
            inputData = workDataOf(AlertEvaluationWorker.ALERT_ID_KEY to "invalid-id"),
        ).build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("INVALID_ALERT_ID", result.outputData.getString(AlertEvaluationWorker.ERROR_KEY))
    }

    @Test
    fun replacementAndCancellationKeepSingleActiveWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        val initial = store.ensureInstallationProfile()
        store.saveProfile(initial.profile.copy(globalEnabled = true, quietHours = null), initial.snoozePolicy)
        store.materialize(alert(alertId))
        val coordinator = AlertSchedulingCoordinator(context, store)
        val workManager = WorkManager.getInstance(context)

        assertTrue(coordinator.schedule(alertId, Instant.now().plusSeconds(3_600)))
        assertTrue(coordinator.schedule(alertId, Instant.now().plusSeconds(7_200)))

        val active = waitForWork(workManager, alertId) { infos ->
            infos.count { !it.state.isFinished } == 1
        }
        assertEquals(1, active.count { !it.state.isFinished })

        assertTrue(coordinator.cancel(alertId))
        val cancelled = waitForWork(workManager, alertId) { infos -> infos.all { it.state.isFinished } }
        assertTrue(cancelled.all { it.state.isFinished })
        assertEquals("CANCELLED", MobileDatabase.get(context).offline().alertMaterialization(alertId)?.state)
    }

    @Test
    fun reconciliationRestoresDurableWorkAfterCoordinatorRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alertId = UUID.randomUUID().toString()
        val store = AlertStore(MobileDatabase.get(context))
        val initial = store.ensureInstallationProfile()
        store.saveProfile(initial.profile.copy(globalEnabled = true, quietHours = null), initial.snoozePolicy)
        store.materialize(alert(alertId))
        val firstCoordinator = AlertSchedulingCoordinator(context, store)
        val workManager = WorkManager.getInstance(context)

        assertTrue(firstCoordinator.schedule(alertId, Instant.now().plusSeconds(7_200)))
        waitForWork(workManager, alertId) { infos -> infos.any { !it.state.isFinished } }
        workManager.cancelUniqueWork(WorkManagerAlertEnqueuer.uniqueName(alertId)).result.get(5, TimeUnit.SECONDS)
        waitForWork(workManager, alertId) { infos -> infos.all { it.state.isFinished } }

        val recreatedCoordinator = AlertSchedulingCoordinator(context, AlertStore(MobileDatabase.get(context)))
        assertTrue(recreatedCoordinator.reconcile() >= 1)
        val restored = waitForWork(workManager, alertId) { infos -> infos.any { !it.state.isFinished } }

        assertTrue(restored.any { !it.state.isFinished })
        assertTrue(recreatedCoordinator.cancel(alertId))
    }

    private fun waitForWork(
        workManager: WorkManager,
        alertId: String,
        condition: (List<androidx.work.WorkInfo>) -> Boolean,
    ): List<androidx.work.WorkInfo> {
        repeat(50) {
            val infos = workManager.getWorkInfosForUniqueWork(
                WorkManagerAlertEnqueuer.uniqueName(alertId),
            ).get(5, TimeUnit.SECONDS)
            if (infos.isNotEmpty() && condition(infos)) return infos
            Thread.sleep(100)
        }
        error("WorkManager não convergiu para o estado esperado.")
    }

    private fun alert(id: String) = AlertDefinition(
        contractVersion = 1,
        alertId = id,
        origin = AlertOrigin.MANUAL,
        referenceId = null,
        text = "Alerta fictício de agendamento",
        reason = "Teste instrumental sem entrega",
        sourceDeviceId = UUID.randomUUID().toString(),
        scheduledAt = Instant.now().plusSeconds(3_600).toString(),
        validUntil = Instant.now().plusSeconds(10_800).toString(),
        criticality = FunctionalCriticality.ROUTINE,
        allowedChannels = setOf(SensoryChannel.VISUAL),
        repeatPolicy = AlertRepeatPolicy(1, 15),
        actions = setOf(AlertActionType.COMPLETE, AlertActionType.SNOOZE),
    )
}
