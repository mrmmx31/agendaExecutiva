package com.pessoal.agenda.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pessoal.agenda.mobile.data.local.MobileDatabase
import com.pessoal.agenda.mobile.recommendation.RecommendationCapacityContext
import com.pessoal.agenda.mobile.recommendation.RecommendationChannel
import com.pessoal.agenda.mobile.recommendation.RecommendationSettings
import com.pessoal.agenda.mobile.recommendation.RecommendationSourceDevice
import com.pessoal.agenda.mobile.recommendation.RecommendationStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.pessoal.agenda.wear.contract.WearProtocolStatus
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineRepositoryTest {
    private lateinit var database: MobileDatabase
    private lateinit var ids: ArrayDeque<String>
    private lateinit var repository: OfflineRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MobileDatabase::class.java,
        ).allowMainThreadQueries().build()
        ids = ArrayDeque(
            (1..20).map { "10000000-0000-4000-8000-${it.toString().padStart(12, '0')}" },
        )
        repository = OfflineRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneId.of("America/Manaus"),
            newId = { ids.removeFirst() },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun captureAndOperationArePersistedWithCanonicalHash() = runBlocking {
        repository.createCapture("  Lembrar de revisar o exemplo  ")

        val capture = repository.captures.first().single()
        val operation = repository.operations.first().single()
        assertEquals("Lembrar de revisar o exemplo", capture.text)
        assertEquals(capture.id, operation.entityId)
        assertEquals("CAPTURE_CREATED", operation.commandType)
        assertEquals(1, operation.sequence)
        assertEquals(OfflineRepository.sha256(operation.payloadJson), operation.payloadHash)
        assertEquals("America/Manaus", operation.timeZone)
        assertTrue(operation.payloadJson.contains(capture.id))
    }

    @Test
    fun fixturesAreDeterministicAndDoNotGenerateOperations() = runBlocking {
        repository.initializeFictitiousData()
        repository.initializeFictitiousData()

        assertEquals(2, repository.tasks.first().size)
        assertEquals(1, repository.protocols.first().size)
        assertTrue(repository.operations.first().isEmpty())
    }

    @Test
    fun taskChecklistStatusAndSessionAreOfflineFirstAndQueued() = runBlocking {
        val taskId = repository.createTask(
            "  Preparar materiais  ", "Separar por ordem", "2026-09-06", "HIGH",
        )
        repository.addChecklistItem(taskId, "Carregador")
        val item = repository.checklist(taskId).first().single()
        repository.setChecklistItemDone(item.id, true)
        repository.changeTaskStatus(taskId, "IN_PROGRESS")
        repository.startTaskTimer(taskId)
        repository.interruptTaskTimer()
        repository.resumeTaskTimer()
        repository.finishTaskTimer("Sessão curta de teste")

        val task = repository.tasks.first().single()
        assertEquals("Preparar materiais", task.title)
        assertEquals("HIGH", task.priority)
        assertEquals("IN_PROGRESS", task.status)
        assertTrue(repository.checklist(taskId).first().single().done)
        assertEquals(1, repository.sessions(taskId).first().size)
        assertEquals(null, repository.activeTaskTimer.first())
        assertEquals(
            listOf("TASK_CREATED", "CHECKLIST_ITEM_CHANGED", "CHECKLIST_ITEM_CHANGED",
                "TASK_STATUS_CHANGED", "SESSION_RECORDED"),
            repository.operations.first().sortedBy { it.sequence }.map { it.commandType },
        )
    }

    @Test
    fun dailyPlanFocusClosingAndReopeningWorkOffline() = runBlocking {
        repository.initializeFictitiousData()
        val tasks = repository.tasks.first()

        repository.saveTodayPlan("NORMAL", tasks[0].id, listOf(tasks[1].id))
        repository.selectFocus(tasks[1].id)

        assertEquals("NORMAL", repository.todayPlan.first()?.capacity)
        assertEquals(listOf("ESSENTIAL", "SUPPORT"), repository.todayPlanTasks.first().map { it.role })
        assertEquals(tasks[1].id, repository.focusSelection.first()?.taskId)
        assertTrue(repository.operations.first().isEmpty())

        repository.closeTodayPlan("  Retomar amanhã  ")
        assertEquals("Retomar amanhã", repository.todayPlan.first()?.closingNote)
        assertTrue(repository.todayPlan.first()?.closedAt != null)

        repository.reopenTodayPlan()
        assertEquals(null, repository.todayPlan.first()?.closedAt)
        assertEquals(null, repository.todayPlan.first()?.closingNote)
        repository.selectFocus(null)
        assertEquals(null, repository.focusSelection.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun reducedDailyPlanRejectsSupportTasks() = runBlocking {
        repository.initializeFictitiousData()
        val tasks = repository.tasks.first()
        repository.saveTodayPlan("REDUCED", tasks[0].id, listOf(tasks[1].id))
    }

    @Test
    fun dailyPlanAndFocusSurviveDatabaseRecreation() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val databaseName = "agenda-mobile-today-recreation-test.db"
            context.deleteDatabase(databaseName)
            val clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC)
            val zone = ZoneId.of("America/Manaus")

            try {
                val firstDatabase = Room.databaseBuilder(context, MobileDatabase::class.java, databaseName)
                    .allowMainThreadQueries()
                    .build()
                try {
                    val firstRepository = OfflineRepository(
                        database = firstDatabase,
                        clock = clock,
                        zoneId = zone,
                    )
                    firstRepository.initializeFictitiousData()
                    val task = firstRepository.tasks.first().first()
                    firstRepository.saveTodayPlan("REDUCED", task.id, emptyList())
                    firstRepository.selectFocus(task.id)
                } finally {
                    firstDatabase.close()
                }

                val restoredDatabase = Room.databaseBuilder(context, MobileDatabase::class.java, databaseName)
                    .allowMainThreadQueries()
                    .build()
                try {
                    val restoredRepository = OfflineRepository(
                        database = restoredDatabase,
                        clock = clock,
                        zoneId = zone,
                    )
                    assertEquals("REDUCED", restoredRepository.todayPlan.first()?.capacity)
                    assertEquals(
                        restoredRepository.todayPlanTasks.first().single().taskId,
                        restoredRepository.focusSelection.first()?.taskId,
                    )
                } finally {
                    restoredDatabase.close()
                }
            } finally {
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun completingProtocolStepIsIdempotentAndFinishesRun() = runBlocking {
        repository.initializeFictitiousData()
        val runId = repository.startProtocol(OfflineRepository.FIXTURE_PROTOCOL)
        val steps = repository.activeRunSteps.first { it.isNotEmpty() }

        assertTrue(repository.completeProtocolStep(runId, steps[0].stepId))
        assertFalse(repository.completeProtocolStep(runId, steps[0].stepId))
        steps.drop(1).forEach { assertTrue(repository.completeProtocolStep(runId, it.stepId)) }

        assertTrue(repository.activeRunSteps.first().isEmpty())
        val operations = repository.operations.first()
        assertEquals(5, operations.size)
        assertEquals((1L..5L).toList(), operations.sortedBy { it.sequence }.map { it.sequence })
        assertEquals(0, database.offline().recommendationEventCount())
    }

    @Test
    fun protocolWearStateAdvancesAndAcknowledgesExactOperation() = runBlocking {
        repository.initializeFictitiousData()
        val runId = repository.startProtocol(OfflineRepository.FIXTURE_PROTOCOL)
        val first = requireNotNull(repository.protocolWearState(runId))
        assertEquals(WearProtocolStatus.ACTIVE, first.status)
        assertEquals("Chaves", first.stepLabel)
        assertEquals(1, first.stepPosition)

        val operationId = "30000000-0000-4000-8000-000000000001"
        assertTrue(repository.completeProtocolStep(runId, requireNotNull(first.stepId), operationId))

        val next = requireNotNull(repository.protocolWearState(runId))
        assertEquals(2, next.revision)
        assertEquals(operationId, next.acknowledgedOperationId)
        assertEquals("Carteira", next.stepLabel)
        assertEquals(2, next.stepPosition)
    }

    @Test
    fun cancellingProtocolClearsActiveRunQueuesEventAndEndsWearState() = runBlocking {
        repository.initializeFictitiousData()
        val runId = repository.startProtocol(OfflineRepository.FIXTURE_PROTOCOL)
        val firstStep = repository.activeRunSteps.first { it.isNotEmpty() }.first()
        assertTrue(repository.completeProtocolStep(runId, firstStep.stepId))

        assertTrue(repository.cancelProtocol(runId))
        assertFalse(repository.cancelProtocol(runId))

        assertTrue(repository.activeRunSteps.first().isEmpty())
        assertEquals(WearProtocolStatus.COMPLETED, repository.protocolWearState(runId)?.status)
        val cancellation = repository.operations.first().single {
            it.commandType == "PROTOCOL_RUN_CANCELLED"
        }
        assertEquals(runId, cancellation.entityId)
        assertTrue(cancellation.payloadJson.contains("cancelled_at"))
    }

    @Test
    fun protocolEventsDoNotContainRunStepOrTemplateIdentity() = runBlocking {
        RecommendationStore(database).saveSettings(
            RecommendationSettings(
                personalizationEnabled = true,
                retentionDays = 90,
                capacityContext = RecommendationCapacityContext.PARALLEL_EXPLICIT,
                preferredSnoozeMinutes = null,
                preferredChannel = RecommendationChannel.VISUAL,
            ),
        )
        repository.initializeFictitiousData()
        val runId = repository.startProtocol(OfflineRepository.FIXTURE_PROTOCOL)
        val firstStep = repository.activeRunSteps.first { it.isNotEmpty() }.first()

        assertTrue(repository.completeProtocolStep(
            runId,
            firstStep.stepId,
            "30000000-0000-4000-8000-000000000009",
            RecommendationSourceDevice.WATCH,
        ))
        assertFalse(repository.completeProtocolStep(
            runId,
            firstStep.stepId,
            "30000000-0000-4000-8000-000000000009",
            RecommendationSourceDevice.WATCH,
        ))

        val events = database.offline().recommendationEvents().associateBy { it.eventType }
        assertEquals(setOf("PROTOCOL_STARTED", "PROTOCOL_STEP_COMPLETED"), events.keys)
        assertEquals("PHONE", events.getValue("PROTOCOL_STARTED").sourceDevice)
        assertEquals("WATCH", events.getValue("PROTOCOL_STEP_COMPLETED").sourceDevice)
        assertTrue(events.values.all {
            it.alertKind == null && it.recommendationId == null &&
                it.id !in setOf(runId, firstStep.stepId, OfflineRepository.FIXTURE_PROTOCOL)
        })
    }

    @Test
    fun mobileProtocolChangeIsQueuedForReviewWithoutMutatingTemplate() = runBlocking {
        repository.initializeFictitiousData()

        repository.proposeProtocolStep(OfflineRepository.FIXTURE_PROTOCOL, "  Conferir crachá  ")

        val operation = repository.operations.first().single()
        assertEquals("PROTOCOL_STRUCTURE_PROPOSED", operation.commandType)
        assertEquals(1L, operation.baseRevision)
        assertTrue(operation.payloadJson.contains("Conferir crachá"))
        assertEquals(4, database.offline().protocolSteps(OfflineRepository.FIXTURE_PROTOCOL).size)
    }

    @Test
    fun keystoreDeviceIdentityReplacesPrePairingLocalIdentityWithoutLosingQueue() = runBlocking {
        repository.createCapture("Criada antes do pareamento")
        val pairedId = "20000000-0000-4000-8000-000000000001"
        val pairedRepository = OfflineRepository(
            database = database,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneId.of("America/Manaus"),
            newId = { ids.removeFirst() },
            deviceIdProvider = { pairedId },
        )

        pairedRepository.createCapture("Criada depois do pareamento")

        val operations = pairedRepository.operations.first().sortedBy { it.sequence }
        assertEquals(listOf(1L, 2L), operations.map { it.sequence })
        assertTrue(operations.all { it.deviceId == pairedId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankCaptureIsRejectedBeforePersistence() {
        runBlocking { repository.createCapture("   ") }
    }
}
