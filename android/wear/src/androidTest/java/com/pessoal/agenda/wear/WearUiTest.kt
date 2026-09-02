package com.pessoal.agenda.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.data.WearFeedback
import com.pessoal.agenda.wear.data.WearVisibleAlert
import com.pessoal.agenda.wear.data.WearVisibleProtocolStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStateHasNoActionButtons() {
        compose.setContent { AgendaWearApp(null, {}, { _, _ -> }, {}) }

        compose.onNodeWithText("Nenhum alerta ativo").assertIsDisplayed()
        compose.onNodeWithText("Concluir").assertDoesNotExist()
        compose.onNodeWithText("Adiar").assertDoesNotExist()
    }

    @Test
    fun alertOffersOnlyPrimaryActionsBeforeSnoozeChoice() {
        compose.setContent { AgendaWearApp(alert(), {}, { _, _ -> }, {}) }

        compose.onNodeWithText("Revisar compromisso").assertIsDisplayed()
        compose.onNodeWithText("Concluir").assertIsDisplayed()
        compose.onNodeWithText("Adiar").assertIsDisplayed()
        compose.onNodeWithText("10 min").assertDoesNotExist()
    }

    @Test
    fun snoozeUsesOnlyOptionsReceivedFromPhone() {
        var selected: Int? = null
        compose.setContent {
            AgendaWearApp(alert(), {}, { _, minutes -> selected = minutes }, {})
        }

        compose.onNodeWithText("Adiar").performClick()
        compose.onNodeWithText("10 min").assertIsDisplayed().performClick()

        assertEquals(10, selected)
        compose.onNodeWithText("30 min").assertDoesNotExist()
    }

    @Test
    fun terminalFeedbackIsBriefAndDismissed() {
        compose.mainClock.autoAdvance = false
        var dismissed: String? = null
        compose.setContent {
            AgendaWearApp(
                alert(feedback = WearFeedback(WearActionType.COMPLETE, null)),
                {},
                { _, _ -> },
                { dismissed = it },
            )
        }

        compose.onNodeWithText("Concluído").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_100)
        compose.waitForIdle()

        assertEquals(ALERT_ID, dismissed)
    }

    @Test
    fun protocolShowsOnlyCurrentStepAndCompletion() {
        var completed: String? = null
        compose.setContent {
            AgendaWearApp(
                alert = null,
                onComplete = {},
                onSnooze = { _, _ -> },
                onFeedbackShown = {},
                protocolStep = protocolStep(),
                onCompleteProtocolStep = { completed = it },
            )
        }

        compose.onNodeWithText("Chaves").assertIsDisplayed()
        compose.onNodeWithText("Etapa 1 de 4").assertIsDisplayed()
        compose.onNodeWithText("Concluir etapa").performClick()
        compose.onNodeWithText("Adiar").assertDoesNotExist()
        assertEquals(RUN_ID, completed)
    }

    @Test
    fun sensoryAlertHasPriorityOverProtocol() {
        compose.setContent {
            AgendaWearApp(alert(), {}, { _, _ -> }, {}, protocolStep())
        }

        compose.onNodeWithText("Revisar compromisso").assertIsDisplayed()
        compose.onNodeWithText("Chaves").assertDoesNotExist()
    }

    private fun alert(feedback: WearFeedback? = null) = WearVisibleAlert(
        alertId = ALERT_ID,
        text = "Revisar compromisso",
        reason = "Horário planejado",
        snoozeOptionsMinutes = listOf(5, 10),
        status = if (feedback == null) WearAlertStatus.PENDING else WearAlertStatus.COMPLETED,
        feedback = feedback,
    )

    private fun protocolStep() = WearVisibleProtocolStep(
        runId = RUN_ID,
        protocolTitle = "Saída rápida",
        stepId = "70000000-0000-4000-8000-000000000003",
        stepLabel = "Chaves",
        stepPosition = 1,
        stepCount = 4,
        feedback = false,
        actionPending = false,
    )

    private companion object {
        const val ALERT_ID = "70000000-0000-4000-8000-000000000001"
        const val RUN_ID = "70000000-0000-4000-8000-000000000002"
    }
}
