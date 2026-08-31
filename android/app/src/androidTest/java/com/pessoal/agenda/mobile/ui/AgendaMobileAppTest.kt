package com.pessoal.agenda.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AgendaMobileAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstScreenShowsCurrentStateWithoutUnavailableActions() {
        compose.setContent { AgendaMobileApp() }

        compose.onNodeWithText("Agenda").assertIsDisplayed()
        compose.onNodeWithText("Agora").assertIsDisplayed()
        compose.onNodeWithText("Nenhum alerta ativo").assertIsDisplayed()
        compose.onNodeWithText("Telefone").assertIsDisplayed()
        compose.onNodeWithText("Relógio").assertIsDisplayed()
    }
}
