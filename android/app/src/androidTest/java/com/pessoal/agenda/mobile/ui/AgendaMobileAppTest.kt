package com.pessoal.agenda.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.mutableStateOf
import androidx.test.espresso.Espresso.pressBack
import com.pessoal.agenda.mobile.alert.SensoryChannel
import com.pessoal.agenda.mobile.alert.SensoryProfile
import com.pessoal.agenda.mobile.alert.AudioRoutePolicy
import com.pessoal.agenda.mobile.alert.output.AudioOutputDevice
import com.pessoal.agenda.mobile.data.local.TaskReplicaEntity
import com.pessoal.agenda.mobile.data.local.ProtocolTemplateEntity
import com.pessoal.agenda.mobile.data.local.ActiveRunStepRow
import com.pessoal.agenda.mobile.data.local.DailyPlanEntity
import com.pessoal.agenda.mobile.data.local.DailyPlanTaskRow
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import com.pessoal.agenda.mobile.data.local.RecommendationEventEntity
import com.pessoal.agenda.mobile.recommendation.RecommendationActiveContext
import com.pessoal.agenda.mobile.recommendation.RecommendationCapacityContext
import com.pessoal.agenda.mobile.recommendation.RecommendationOption
import com.pessoal.agenda.mobile.recommendation.RecommendationOptionCode
import com.pessoal.agenda.mobile.recommendation.RecommendationReason
import com.pessoal.agenda.mobile.recommendation.RecommendationSettings
import com.pessoal.agenda.mobile.recommendation.RecommendationStatistics
import com.pessoal.agenda.mobile.recommendation.PersonalModelStatus
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
                            TaskReplicaEntity(
                                id = "completed-task-id",
                                title = "Tarefa concluída fictícia",
                                status = "COMPLETED",
                                revision = 2,
                                updatedAt = "2026-08-31T13:00:00Z",
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
        compose.onNodeWithContentDescription("Tarefa pendente").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tarefa concluída").assertIsDisplayed()
    }

    @Test
    fun todayCreatesReducedPlanFromCurrentFocus() {
        var saved: Triple<String, String, List<String>>? = null
        val task = TaskReplicaEntity(
            id = "task-id",
            title = "Tarefa essencial",
            status = "PENDING",
            revision = 1,
            updatedAt = "2026-09-05T12:00:00Z",
        )
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(tasks = listOf(task), today = TodayUiState(focusTask = task)),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSaveTodayPlan = { capacity, essential, supports ->
                        saved = Triple(capacity, essential, supports)
                    },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithTag("screen-Hoje").performScrollToNode(hasText("Começar meu dia"))
        compose.onNodeWithText("Começar meu dia").performClick()
        compose.onNodeWithText("Capacidade reduzida").performClick()
        compose.onNodeWithText("Salvar plano").performClick()

        assertEquals(Triple("REDUCED", "task-id", emptyList<String>()), saved)
    }

    @Test
    fun dailyPlanDialogSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val task = TaskReplicaEntity(
            id = "task-id",
            title = "Preparar relatório",
            status = "PENDING",
            revision = 1,
            updatedAt = "2026-09-05T12:00:00Z",
        )
        restoration.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(tasks = listOf(task), today = TodayUiState(focusTask = task)),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithTag("screen-Hoje").performScrollToNode(hasText("Começar meu dia"))
        compose.onNodeWithText("Começar meu dia").performClick()
        compose.onNodeWithText("Editar plano de hoje").assertDoesNotExist()
        compose.onNodeWithText("Salvar plano").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Salvar plano").assertIsDisplayed()
    }

    @Test
    fun closedDayCanBeReopened() {
        var reopened = false
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(today = TodayUiState(
                        plan = DailyPlanEntity(
                            planDate = "2026-09-05",
                            capacity = "NORMAL",
                            createdAt = "2026-09-05T12:00:00Z",
                            closedAt = "2026-09-05T20:00:00Z",
                            closingNote = "Continuar amanhã",
                        ),
                        planTasks = listOf(DailyPlanTaskRow(
                            "2026-09-05", "task-id", "ESSENTIAL", 0, "Tarefa", "PENDING",
                        )),
                    )),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onReopenToday = { reopened = true },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithTag("screen-Hoje").performScrollToNode(hasText("Dia encerrado"))
        compose.onNodeWithText("Dia encerrado").assertIsDisplayed()
        compose.onNodeWithText("Continuar amanhã").assertIsDisplayed()
        compose.onNodeWithText("Reabrir dia").performClick()
        assertEquals(true, reopened)
    }

    @Test
    fun systemBackReturnsBottomSectionToHomeBeforeActivityCanClose() {
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {},
                    onCompleteStep = { _, _ -> }, onSync = {}, onPair = { _, _ -> },
                    onCancelPairing = {}, onPairingCompletionShown = {}, onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Capturar").performClick()
        compose.onNodeWithText("Texto livre").assertIsDisplayed()
        pressBack()

        compose.onNodeWithText("Vou sair").assertIsDisplayed()
        compose.onNodeWithText("Texto livre").assertDoesNotExist()
    }

    @Test
    fun systemBackClosesSecondaryScreenAndKeepsHomeVisible() {
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {},
                    onCompleteStep = { _, _ -> }, onSync = {}, onPair = { _, _ -> },
                    onCancelPairing = {}, onPairingCompletionShown = {}, onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Configurações sensoriais").performClick()
        compose.onNodeWithText("Configurações sensoriais").assertIsDisplayed()
        pressBack()

        compose.onNodeWithText("Agenda").assertIsDisplayed()
        compose.onNodeWithText("Vou sair").assertIsDisplayed()
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
        compose.onNodeWithTag("health-list").performScrollToNode(hasContentDescription("Adicionar medicação"))
        compose.onNodeWithContentDescription("Adicionar medicação").performClick()
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

        compose.onNodeWithText(
            "Dados de saúde são opcionais, processados localmente e usados somente no relatório que você revisa. Nada é enviado automaticamente.",
        ).assertExists()
        compose.onNodeWithText(
            "A Agenda não diagnostica, não prescreve e não substitui orientação de profissional de saúde.",
        ).assertExists()
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
    fun activeProtocolCanBeKeptOrExplicitlyEnded() {
        var cancelledRun: String? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(activeRunSteps = listOf(ActiveRunStepRow(
                        runId = "run-id",
                        protocolId = "protocol-id",
                        protocolTitle = "Saída de casa",
                        stepId = "step-id",
                        position = 0,
                        label = "Chaves",
                        completedAt = null,
                    ))),
                    onSaveCapture = { _, _ -> },
                    onStartProtocol = {},
                    onCompleteStep = { _, _ -> },
                    onCancelProtocol = { cancelledRun = it },
                    onSync = {},
                    onPair = { _, _ -> },
                    onCancelPairing = {},
                    onPairingCompletionShown = {},
                    onFeedbackShown = {},
                )
            }
        }

        compose.onNodeWithText("Protocolos").performClick()
        compose.onNodeWithText("Encerrar protocolo").performClick()
        compose.onNodeWithText("Continuar protocolo").performClick()
        assertEquals(null, cancelledRun)
        compose.onNodeWithText("Encerrar protocolo").performClick()
        compose.onNodeWithText("Encerrar").performClick()
        assertEquals("run-id", cancelledRun)
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
                    onSaveSensorySettings = { profile, _, _ -> saved = profile },
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
    fun audioPreviewUsesDraftRouteWithoutEnablingGlobalAlerts() {
        var testedRoute: AudioRoutePolicy? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(
                        visualAlertsEnabled = false,
                        sensorySettings = SensorySettingsUiState(
                            profile = SensoryProfile.installationDefault(),
                        ),
                    ),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onTestAudio = { route, _ -> testedRoute = route },
                )
            }
        }

        compose.onNodeWithContentDescription("Configurações sensoriais").performClick()
        compose.onNodeWithText("Fone").performClick()
        compose.onNodeWithTag("sensory-settings-list")
            .performScrollToNode(hasText("Testar áudio"))
        compose.onNodeWithText("Testar áudio").assertIsEnabled().performClick()

        assertEquals(AudioRoutePolicy.PREFER_HEADPHONES, testedRoute)
    }

    @Test
    fun audioPreviewUsesExplicitConnectedDevice() {
        var testedDeviceKey: String? = null
        val device = AudioOutputDevice("8:MOTO XT220", "MOTO XT220", "Bluetooth")
        compose.setContent {
            AgendaMobileTheme {
                SensorySettingsScreen(
                    state = SensorySettingsUiState(
                        profile = SensoryProfile.installationDefault().copy(
                            enabledChannels = setOf(SensoryChannel.VISUAL, SensoryChannel.AUDIO),
                            audioRoute = AudioRoutePolicy.PREFER_HEADPHONES,
                        ),
                        availableAudioDevices = listOf(
                            device,
                            AudioOutputDevice("8:ZL02CPRO", "ZL02CPRO", "Bluetooth"),
                        ),
                    ),
                    alertsEnabled = false,
                    visualNotificationsAvailable = false,
                    busy = false,
                    onGlobalChanged = {},
                    onSave = { _, _, _ -> },
                    onPause = {},
                    onTestAudio = { _, key -> testedDeviceKey = key },
                    onRefreshRoute = {},
                    onOpenSystemSoundSettings = {},
                )
            }
        }

        compose.onNodeWithTag("audio-device-selector").performClick()
        compose.onNodeWithText("MOTO XT220 · Bluetooth").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("MOTO XT220 · Bluetooth").assertIsDisplayed()
        compose.onNodeWithTag("sensory-settings-list").performScrollToNode(hasText("Testar áudio"))
        compose.onNodeWithText("Testar áudio").assertIsDisplayed().performClick()

        assertEquals(device.key, testedDeviceKey)
    }

    @Test
    fun recommendationScreenExposesOptInAndExplainableBaseline() {
        var saved: RecommendationSettings? = null
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(recommendation = recommendationState()),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onSaveRecommendationSettings = { saved = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Recomendações locais").performClick()
        compose.onNodeWithText("Recomendações locais").assertIsDisplayed()
        compose.onNodeWithText("Regras padrão ativas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Personalização local").performClick()
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasText("Adiar 15 min"))
        compose.onNodeWithText("Adiar 15 min").assertIsDisplayed()
        compose.onNodeWithText("Padrão cauteloso").assertIsDisplayed()
        assertEquals(true, saved?.personalizationEnabled)
    }

    @Test
    fun recommendationHistoryCanBeCorrectedAndClearedExplicitly() {
        var correction: Triple<String, RecommendationActiveContext, RecommendationCapacityContext>? = null
        var cleared = false
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(recommendation = recommendationState()),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onCorrectRecommendationEvent = { id, active, capacity ->
                        correction = Triple(id, active, capacity)
                    },
                    onClearRecommendationHistory = { cleared = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Recomendações locais").performClick()
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasContentDescription("Corrigir contexto do evento"))
        compose.onNodeWithContentDescription("Corrigir contexto do evento").performClick()
        compose.onNodeWithText("Contexto paralelo").performClick()
        compose.onNodeWithText("Foco ativo").performClick()
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals(
            Triple(RECOMMENDATION_EVENT_ID, RecommendationActiveContext.FOCUS, RecommendationCapacityContext.PARALLEL_EXPLICIT),
            correction,
        )
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasText("Apagar histórico"))
        compose.onNodeWithText("Apagar histórico").performClick()
        compose.onNodeWithText("Apagar histórico local?").assertIsDisplayed()
        compose.onNodeWithText("Apagar").performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun personalModelTrainingAndActivationRequireExplicitCommands() {
        var trained = false
        var activated: String? = null
        val model = PersonalModelUiState(
            eligibleEventCount = 90,
            version = "local-v1",
            status = PersonalModelStatus.SHADOW,
            trainingSampleCount = 72,
            evaluationSampleCount = 18,
            top1Accuracy = 0.8,
            baselineTop1Accuracy = 0.6,
            eligibleForActivation = true,
            artifactHashPrefix = "abcdef012345",
            artifactSizeBytes = 2_048,
            approximateWeightBytes = 1_040,
            lastTrainingMillis = 12,
            inferenceMicros = 8,
        )
        val recommendation = recommendationState().copy(
            settings = recommendationState().settings.copy(personalizationEnabled = true),
            model = model,
        )
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(recommendation = recommendation),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onTrainPersonalModel = { trained = true },
                    onActivatePersonalModel = { activated = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Recomendações locais").performClick()
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasText("Treinar e avaliar"))
        compose.onNodeWithText("Treinar e avaliar").assertIsEnabled().performClick()
        assertEquals(true, trained)
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasText("Ativar modelo"))
        compose.onNodeWithText("Ativar modelo").performClick()
        compose.onNodeWithText("Ativar modelo pessoal?").assertIsDisplayed()
        assertEquals(null, activated)
        compose.onNodeWithText("Ativar").performClick()
        assertEquals("local-v1", activated)
    }

    @Test
    fun activePersonalModelCanRestoreRulesDirectly() {
        var restored = false
        val recommendation = recommendationState().copy(
            settings = recommendationState().settings.copy(personalizationEnabled = true),
            model = PersonalModelUiState(
                eligibleEventCount = 90,
                version = "local-v1",
                status = PersonalModelStatus.ACTIVE,
                activeVersion = "local-v1",
            ),
        )
        compose.setContent {
            AgendaMobileTheme {
                AgendaMobileScreen(
                    state = MobileUiState(recommendation = recommendation),
                    onSaveCapture = { _, _ -> }, onStartProtocol = {}, onCompleteStep = { _, _ -> },
                    onSync = {}, onPair = { _, _ -> }, onCancelPairing = {},
                    onPairingCompletionShown = {}, onFeedbackShown = {},
                    onRollbackPersonalModel = { restored = true },
                )
            }
        }

        compose.onNodeWithContentDescription("Recomendações locais").performClick()
        compose.onNodeWithTag("recommendation-list").performScrollToNode(hasText("Restaurar regras"))
        compose.onNodeWithText("Restaurar regras").performClick()
        assertEquals(true, restored)
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
        compose.onNodeWithText("Ler QR code").assertIsDisplayed()
        compose.onNodeWithText("Colar convite").assertIsDisplayed()
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

    private fun recommendationState() = RecommendationUiState(
        settings = RecommendationSettings(
            personalizationEnabled = false,
            retentionDays = 90,
            capacityContext = RecommendationCapacityContext.STANDARD,
            preferredSnoozeMinutes = null,
            preferredChannel = null,
        ),
        events = listOf(
            RecommendationEventEntity(
                id = RECOMMENDATION_EVENT_ID,
                contractVersion = 1,
                eventType = "ALERT_SNOOZED",
                occurredAt = "2026-09-02T15:00:00Z",
                localHour = 11,
                dayOfWeek = 3,
                sourceDevice = "PHONE",
                activeContext = "NONE",
                capacityContext = "STANDARD",
                alertKind = "TASK",
                deadlineBucket = "TODAY",
                channel = "VISUAL",
                responseLatencySeconds = 42,
                snoozeMinutes = 15,
                recommendationId = null,
                optionCode = "SNOOZE_15",
                correctedAt = null,
            ),
        ),
        statistics = RecommendationStatistics(
            totalEvents = 1,
            medianResponseLatencySeconds = 42,
            snoozeEvents = 1,
        ),
        baselineOptions = listOf(
            RecommendationOption(
                RecommendationOptionCode.SNOOZE_15,
                rank = 1,
                RecommendationReason.CAUTIOUS_DEFAULT,
            ),
        ),
    )

    private companion object {
        const val RECOMMENDATION_EVENT_ID = "f1000000-0000-4000-8000-000000000001"
    }
}
