package com.pessoal.agenda.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgendaMobileAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeShowsOfflineStateAndFictitiousTask() {
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(
                        tasks = listOf(
                            TaskReplicaEntity(
                                id = "task-id",
                                title = "Tarefa fictícia",
                                status = "PENDING",
                                revision = 1,
                                updatedAt = "2026-08-31T12:00:00Z",
                            ),
                        ),
                    ),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Agenda").assertIsDisplayed()
        compose.onNodeWithText("Somente neste telefone").assertIsDisplayed()
        compose.onNodeWithText("Tarefa fictícia").assertIsDisplayed()
    }

    @Test
    fun captureDraftIsSentAndOnlyClearedBySuccessCallback() {
        var received = ""
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(),
                    onSaveCapture = { text, onSaved ->
                        received = text
                        onSaved()
                    },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Capturar").performClick()
        compose.onNodeWithText("Texto livre").performTextInput("Ideia offline")
        compose.onNodeWithText("Salvar no telefone").assertIsEnabled().performClick()

        assertEquals("Ideia offline", received)
        compose.onNodeWithText("Ideia offline").assertDoesNotExist()
    }

    @Test
    fun pairedStateOffersExplicitSyncAction() {
        var requested = false
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(canSync = true),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = { requested = true },
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Pareado ao desktop").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sincronizar agora")
            .assertIsEnabled()
            .performClick()
        assertEquals(true, requested)
    }

    @Test
    fun offlineStateOffersPairingFormAndCancelableWaitingState() {
        var invitation = ""
        var code = ""
        var cancelled = false
        val pairing = mutableStateOf(false)
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(pairingInProgress = pairing.value),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { receivedInvitation, receivedCode ->
                        invitation = receivedInvitation
                        code = receivedCode
                        pairing.value = true
                    },
                    onCancelPairing = { cancelled = true },
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Conectar ao desktop").performClick()
        compose.onNodeWithText("Convite").performTextInput("agenda://pair?teste")
        compose.onNodeWithText("Código de seis dígitos").performTextInput("123456")
        compose.onNodeWithText("Conectar").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("agenda://pair?teste", invitation)
        assertEquals("123456", code)
        compose.onNodeWithText("Aguardando aprovação no desktop").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(true, cancelled)
    }

    @Test
    fun pairingDeepLinkOpensPrefilledForm() {
        val invitation = "agenda://pair?v=1&session_id=teste"
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(canSync = true),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                    initialPairingInvitation = invitation,
                )
            }
        }

        compose.onNodeWithText("Conectar ao desktop").assertIsDisplayed()
        compose.onNodeWithText(invitation).assertIsDisplayed()
    }
}
