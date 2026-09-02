package com.pessoal.agenda.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pessoal.agenda.wear.contract.WearAlertStatus
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.data.WearFeedback
import com.pessoal.agenda.wear.data.WearVisibleAlert
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

    private fun alert(feedback: WearFeedback? = null) = WearVisibleAlert(
        alertId = ALERT_ID,
        text = "Revisar compromisso",
        reason = "Horário planejado",
        snoozeOptionsMinutes = listOf(5, 10),
        status = if (feedback == null) WearAlertStatus.PENDING else WearAlertStatus.COMPLETED,
        feedback = feedback,
    )

    private companion object {
        const val ALERT_ID = "70000000-0000-4000-8000-000000000001"
    }
}
