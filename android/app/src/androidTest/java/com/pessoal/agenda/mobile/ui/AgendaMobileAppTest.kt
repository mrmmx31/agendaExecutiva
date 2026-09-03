package com.pessoal.agenda.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.mutableStateOf
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.ui.theme.AgendaMobileTheme
import com.pessoal.agenda.mobile.data.local.HealthConsentEntity
import com.pessoal.agenda.mobile.health.HealthCategory
import com.pessoal.agenda.mobile.health.IntakeInput
import com.pessoal.agenda.mobile.health.report.HealthReportBuilder
import com.pessoal.agenda.mobile.health.report.HealthReportEntry
import com.pessoal.agenda.mobile.health.report.HealthReportEntryKind
import com.pessoal.agenda.mobile.health.report.HealthReportFormat
import com.pessoal.agenda.mobile.health.report.HealthReportPermission
import com.pessoal.agenda.mobile.health.report.HealthReportReview
import com.pessoal.agenda.mobile.health.report.HealthReportSnapshot
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
    fun healthScreenRequiresCategoryOptInBeforeManualEntry() {
        var requested: Pair<HealthCategory, Boolean>? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(health = HealthUiState(consents = listOf(
                        HealthConsentEntity(
                            id = "consent-id", category = HealthCategory.MEDICATION.name,
                            purpose = "USER_REVIEWABLE_REPORT", enabled = false,
                            foregroundOnly = true, retentionDays = 365,
                            grantedAt = null, revokedAt = null, updatedAt = "2026-09-02T12:00:00Z",
                        ),
                    ))),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onHealthConsentChanged = { category, value -> requested = category to value },
                )
            }
        }

        compose.onNodeWithContentDescription("Saúde e privacidade").performClick()
        compose.onNodeWithText("Saúde e privacidade").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ativar Medicação").performClick()
        assertEquals(HealthCategory.MEDICATION to true, requested)
    }

    @Test
    fun healthManualEntryIsSentOnlyAfterExplicitSave() {
        var saved: IntakeInput? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(health = HealthUiState(consents = listOf(
                        HealthConsentEntity(
                            id = "consent-id", category = HealthCategory.MEDICATION.name,
                            purpose = "USER_REVIEWABLE_REPORT", enabled = true,
                            foregroundOnly = true, retentionDays = 365,
                            grantedAt = "2026-09-02T12:00:00Z", revokedAt = null,
                            updatedAt = "2026-09-02T12:00:00Z",
                        ),
                    ))),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onSaveIntake = { _, value -> saved = value },
                )
            }
        }

        compose.onNodeWithContentDescription("Saúde e privacidade").performClick()
        compose.onAllNodesWithText("Medicação")[1].performClick()
        compose.onNodeWithText("Nome").performTextInput("Item fictício de interface")
        assertEquals(null, saved)
        compose.onNodeWithText("Salvar").performClick()
        assertEquals("Item fictício de interface", saved?.name)
    }

    @Test
    fun healthReportRequiresPreviewAndExplicitExportChoice() {
        var generated: Pair<Int, Set<HealthCategory>>? = null
        var exported: HealthReportFormat? = null
        val consent = HealthConsentEntity(
            id = "consent-id", category = HealthCategory.SYMPTOM.name,
            purpose = "USER_REVIEWABLE_REPORT", enabled = true,
            foregroundOnly = true, retentionDays = 365,
            grantedAt = "2026-09-02T12:00:00Z", revokedAt = null,
            updatedAt = "2026-09-02T12:00:00Z",
        )
        val snapshot = HealthReportSnapshot(
            snapshotId = "b5000000-0000-4000-8000-000000000001",
            generatedAt = "2026-09-02T15:00:00Z", periodStart = "2026-08-26T15:00:00Z",
            periodEnd = "2026-09-02T15:00:00Z", timeZone = "America/Manaus",
            subjectLabel = "Pessoa fictícia", selectedCategories = listOf("SYMPTOM"),
            permissions = listOf(HealthReportPermission("SYMPTOM", true, true, 365)),
            sources = listOf("MANUAL"), limitations = HealthReportBuilder.LIMITATIONS,
            entries = listOf(HealthReportEntry(
                "b5000000-0000-4000-8000-000000000002", HealthReportEntryKind.USER_OBSERVATION,
                "SYMPTOM", "2026-09-01T14:00:00Z", title = "Evento fictício", sources = listOf("MANUAL"),
            )),
        )
        compose.setContent {
            AgendaMobileTheme {
                HealthPrivacyScreen(
                    state = HealthUiState(listOf(consent), report = HealthReportReview(snapshot, "Pessoa fictícia")),
                    busy = false, onConsentChanged = { _, _ -> }, onSaveIntake = { _, _ -> },
                    onDeleteIntake = {}, onSaveSymptom = { _, _ -> }, onDeleteSymptom = {},
                    onImportHealth = {}, onGenerateReport = { days, categories -> generated = days to categories },
                    onReportSubjectChanged = {}, onToggleReportEntry = {}, onExportReport = { exported = it },
                )
            }
        }

        repeat(4) { compose.onRoot().performTouchInput { swipeUp() } }
        compose.onNodeWithText("Gerar prévia").performClick()
        assertEquals(7 to setOf(HealthCategory.SYMPTOM), generated)
        repeat(3) { compose.onRoot().performTouchInput { swipeUp() } }
        compose.onNodeWithText("Evento fictício").assertIsDisplayed()
        compose.onNodeWithText("PDF").performClick()
        assertEquals(HealthReportFormat.PDF, exported)
    }

    @Test
    fun leavingHomeStartsSingleExitProtocolDirectly() {
        var started: String? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(
                        protocols = listOf(ProtocolTemplateEntity(
                            id = "protocol-id",
                            title = "Saída de casa",
                            revision = 1,
                            createdAt = "2026-09-02T12:00:00Z",
                            updatedAt = "2026-09-02T12:00:00Z",
                        )),
                    ),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = { started = it },
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Vou sair").assertIsDisplayed().performClick()

        assertEquals("protocol-id", started)
    }

    @Test
    fun protocolStepSuggestionRequiresExplicitReviewSubmission() {
        var proposal: Pair<String, String>? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(protocols = listOf(ProtocolTemplateEntity(
                        id = "protocol-id",
                        title = "Saída de casa",
                        revision = 1,
                        createdAt = "2026-09-02T12:00:00Z",
                        updatedAt = "2026-09-02T12:00:00Z",
                    ))),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                    onProposeProtocolStep = { id, label -> proposal = id to label },
                )
            }
        }

        compose.onNodeWithText("Protocolos").performClick()
        compose.onNodeWithContentDescription("Sugerir item").performClick()
        compose.onNodeWithText("Novo item para Saída de casa").performTextInput("Conferir crachá")
        assertEquals(null, proposal)
        compose.onNodeWithText("Enviar para revisão").performClick()
        assertEquals("protocol-id" to "Conferir crachá", proposal)
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
    fun visualAlertsRequireExplicitToggleInteraction() {
        var requested: Boolean? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(visualAlertsEnabled = false),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                    onVisualAlertsChanged = { requested = it },
                )
            }
        }

        compose.onNodeWithText("Desativados").assertIsDisplayed()
        assertEquals(null, requested)
        compose.onNodeWithContentDescription("Ativar alertas").performClick()
        assertEquals(true, requested)
    }

    @Test
    fun sensorySettingsRequireExplicitSaveAndExposePauseControls() {
        var saved: SensoryProfile? = null
        var pausedFor: Int? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(
                        visualAlertsEnabled = true,
                        sensorySettings = SensorySettingsUiState(
                            profile = SensoryProfile.installationDefault().copy(globalEnabled = true),
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
                    onSaveSensorySettings = { profile, _ -> saved = profile },
                    onPauseSensoryAlerts = { pausedFor = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Configurações sensoriais").performClick()
        compose.onNodeWithText("Configurações sensoriais").assertIsDisplayed()
        compose.onNodeWithText("Saída efetiva").assertIsDisplayed()
        assertEquals(null, saved)
        compose.onNodeWithText("Pausar 30 min").performClick()
        assertEquals(30, pausedFor)
        compose.onNodeWithText("Discreto").performClick()
        repeat(4) { compose.onRoot().performTouchInput { swipeUp() } }
        compose.onNodeWithText("Salvar perfil").performClick()
        assertEquals(
            setOf(SensoryChannel.VISUAL, SensoryChannel.PHONE_VIBRATION),
            saved?.enabledChannels,
        )
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
