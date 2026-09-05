# Mapa de Manutenção

Este documento é o ponto de entrada para manutenção. Ele reduz a necessidade de reconstruir contexto pelo histórico de conversas.

## 1. Leitura mínima por tipo de trabalho

| Trabalho | Ler nesta ordem |
|---|---|
| Correção desktop existente | `MAINTENANCE_MAP.md` → seção relevante de `DEVELOPMENT.md` → código/testes locais |
| Regra ou UX do produto atual | `SPEC.md` → `ARCHITECTURE.md` → código/testes |
| Projeto Android/Wear | `PROJECT2_SPEC.md` → `android/contracts/README.md` → `android/README.md` |
| Expansão funcional Android | `ANDROID_EXPANSION_SPEC.md` → domínio correspondente no `SPEC.md` → contrato móvel → código/testes |
| IA ou personalização | `PROJECT2_SPEC.md` seções 14, 15, 20 e 21 → `docs/adr/0003-personal-ranking-runtime.md` → model card correspondente |
| Saúde ou relatório médico | `PROJECT2_SPEC.md` seções 2, 12, 13, 15 e 21 |
| Google Tasks | `SPEC.md` seções 9.9 e 26 → classes `Google*` → testes correspondentes |
| Backup Google Drive | `docs/integrations/GOOGLE_DRIVE_SETUP.md` → `GoogleAuthService` → `GoogleDriveAppDataService` → `SigningKeyBackupCrypto` → `SigningKeyDriveBackupService` → seção Integrações |
| Tema/janelas | `DEVELOPMENT.md` → `ThemeManager`, `WindowManager`, CSS e testes JavaFX |
| Ver estado recente | `LAST_CHANGES.md` → `git log` → `git status` |

Não usar `LAST_CHANGES.md` como especificação; ele é gerado pelo hook após commits.

## 2. Fontes de verdade

| Documento | Autoridade | Não deve conter |
|---|---|---|
| `SPEC.md` | produto desktop atual, requisitos e percentuais | detalhes extensos do Projeto 2 |
| `PROJECT2_SPEC.md` | Android, Wear, sync móvel, saúde e IA | código implementado fictício |
| `ANDROID_EXPANSION_SPEC.md` | Projeto 3, paridade funcional Android e checklist percentual | capacidades ainda não testadas como concluídas |
| `ARCHITECTURE.md` | limites técnicos do desktop | decisões de produto não aprovadas |
| `DEVELOPMENT.md` | mecânica e convenções do desktop | backlog ou hipóteses |
| `PILOT.md` | evidência e decisões do piloto encerrado | conteúdo pessoal |
| `UI_VALIDATION.md` | matriz manual reproduzível | dados reais |
| `CHANGELOG.md` | mudanças entregues | planos ainda não implementados como fatos |
| `LAST_CHANGES.md` | resumo automático do último commit | edição manual |

## 3. Estado atual resumido

- Desktop JavaFX: fases 0 a 5 concluídas.
- Estabilização: concluída.
- Google Tasks ao vivo: 100%.
- Piloto: encerrado em 100% com cinco decisões `SEM EVIDÊNCIA`.
- Projeto 2: concluído em 100%; `P2-01` a `P2-10` aprovadas para o release pessoal `0.1.0`.
- Projeto 3: 20%; `A3-00` e `A3-01` concluídas, `A3-02` é a próxima fase.
- Aplicação desktop pessoal pode estar aberta durante manutenção; confirmar processo antes de limpar `target/` ou reiniciar.
- Banco pessoal: `~/.agenda-pessoal/agenda.db`.
- Tokens Google: `~/.agenda/google-tokens.json`, permissão esperada `600`.

## 4. Mapa do desktop

| Área | Modelo/estado | Persistência | Regra | UI | Testes principais |
|---|---|---|---|---|---|
| Tarefas | `Task` | `TaskRepository` | `TaskService` | `AgendaTabController` | `DatabaseServiceTest`, services |
| Plano diário | `DailyPlan*` | `DailyPlanRepository` | `DailyPlanService` | `DailyPlanPanel` | `DailyPlanServiceTest`, `DailyPlanPanelFxTest` |
| Encerramento | review records | `DayReviewRepository` | `DayReviewService` | `DayReviewWindow` | `DayReviewServiceTest`, FX |
| Captura universal | `InboxCapture` | `InboxCaptureRepository` | `InboxCaptureService` | `QuickCaptureWindow`, `InboxTriageWindow` | services e FX |
| Foco/retomada | `FocusContext` | repository próprio | `FocusSelectionService`, `FocusContextService` | Dashboard/timer | services e FX |
| Alertas | preferências/contagens | Preferences | `PendencyNotificationService` | Config/Dashboard/status | service e FX |
| Protocolos | `Protocol*` | `ProtocolRepository` | serviços/controllers atuais | Checklist e execução | repository/FX |
| Google Tasks | mappings/snapshots | repositories Google | auth, gateway e sync services | Config e sync window | transport, sync e FX |
| Sync móvel local | réplicas/fila Room | `DesktopSyncRepository` | `LocalPairingServer`, `LocalSyncTlsIdentityStore`, `SyncBatchProcessor`, transporte HTTPS | status conectado nas duas aplicações e pareamento desktop | protocolo, reconexão, reinício e fila real aprovados em P2-03 |
| Janelas | estado JavaFX | Preferences quando aplicável | `WindowManager` | views | `WindowManagerFxTest`, responsive tests |
| Tema | tokens CSS | preferência visual | `ThemeManager` | todas as scenes | testes FX de contraste/tema |

Regra: UI não executa SQL; repository não contém regra de negócio; service não depende de JavaFX.

## 5. Estrutura do Projeto 2

O scaffold `android/` foi criado em `P2-01`. Não criar arquivos vazios para fases futuras; a estrutura alvo continua:

```text
agenda/
├── android/
│   ├── settings.gradle.kts
│   ├── app/                  # smartphone: Compose, Room, repositório e fila offline
│   ├── contracts/            # schemas móveis versionados; compartilhados a partir da P2-03
│   ├── wear/                 # APK Wear OS: UI curta e recepção Data Layer
│   ├── wear-contract/        # contrato Kotlin puro compartilhado entre os APKs
│   └── README.md
├── docs/
│   ├── adr/                  # decisões arquiteturais numeradas
│   ├── models/               # model cards
│   └── privacy/              # inventário de dados/permissões
├── src/main/java/...         # desktop existente
├── PROJECT2_SPEC.md
└── MAINTENANCE_MAP.md
```

Não criar um módulo Java/Kotlin compartilhado entre Maven desktop e Android antes de existir duplicação real. O contrato compartilhado inicial é schema + fixtures.

## 6. Componentes móveis planejados

Implementado até a conclusão de `P2-09`:

| Área | Local |
|---|---|
| Room v10, entidades, DAO e migrações | `android/app/src/main/java/com/pessoal/agenda/mobile/data/local/` |
| Transações, fixtures e fila | `android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt` |
| Estado e ações da UI | `android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt` |
| Telas Compose | `android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt` |
| Contrato v1 | `android/contracts/README.md` e `android/contracts/v1/` |
| Pareamento v1 e ameaça | `android/contracts/PAIRING_V1.md` e `android/contracts/THREAT_MODEL_P2_03.md` |
| Parser de convite | `android/app/src/main/java/com/pessoal/agenda/mobile/pairing/PairingInvitation.kt` |
| Persistência desktop de sync | `Database.applyMobileSyncMigration()` e `DesktopSyncRepository` |
| Servidor e UI de pareamento desktop | `infra/pairing/` e `MobilePairingWindow` |
| Credencial Android | `android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt` |
| Cliente e UI de pareamento Android | `pairing/PairingClient.kt`, `MainActivity.kt` e `AgendaMobileApp.kt` |
| Transporte e máquina de estados Android | `android/app/src/main/java/com/pessoal/agenda/mobile/sync/` |
| Lotes e snapshots desktop | `SyncBatchProcessor` e `DesktopSyncRepository` |
| Testes | `android/app/src/test/` e `android/app/src/androidTest/` |
| Gate desktop + AVD | `LocalPairingAndroidGate` em `src/test`; executar somente com SQLite temporário e `adb reverse` |
| Contratos e política de alertas | `android/.../alert/`, `contracts/v1/alert-*.schema.json`, `sensory-profile.schema.json` e `ALERTS_V1.md` |
| Persistência de alertas | `AlertStore.kt`, `AlertEntities.kt`, `OfflineDao.kt` e `MobileDatabase.MIGRATION_3_4` |
| Agendamento de alertas | `alert/scheduling/AlertWorkScheduler.kt`; nome único `agenda-alert-<uuid>` e tag `agenda-alert-evaluation` |
| Notificação e ações Android | `alert/notification/`; canal visual v1, publisher, processor e receiver interno |
| Saída sensorial Android | `alert/output/AndroidSensoryOutput.kt` + `AudioOutputPreferenceStore.kt`; tom curto, vibração, foco transitório, catálogo de rotas, preferência local sem MAC e bloqueio de sobreposição |
| Configuração sensorial | `ui/SensorySettingsScreen.kt` + `AgendaMobileViewModel.kt`; presets explícitos, pausa, silêncio, cooldown, seleção da saída conectada, prévia isolada e fallback |
| Matriz P2-04 | `android/P2_04_MATRIX.md`; cenários, gates, resultados e limites do AVD |
| Contrato Wear v1 | `android/wear-contract/`, `contracts/WEAR_V1.md` e `wear-alert-state.schema.json` |
| Aplicativo Wear | `android/wear/`; mesmo `applicationId` e assinatura do telefone, sem segredo mestre |
| Transporte e ações Wear | `mobile/wear/`, `wear/sync/` e `wear/data/`; DataItems duráveis, revisão monotônica e outbox Room |
| Contratos e privacidade de saúde | `android/contracts/v1/health-consent.schema.json`, `intake-log.schema.json`, `symptom-log.schema.json`, `docs/privacy/HEALTH_DATA_INVENTORY.md` e ADR `0001` |
| Persistência sensível Android | `health/HealthStore.kt`, `HealthDataCipher.kt`, `data/local/HealthEntities.kt` e Room v7; AES-GCM/Keystore, revisão, tombstone e auditoria sem texto |
| UI de saúde e privacidade | `ui/HealthPrivacyScreen.kt` e estado/ações em `AgendaMobileViewModel.kt`; oito opt-ins e gestão local de entradas cifradas |
| Importação Health Connect | `health/connect/HealthConnectGateway.kt`; produção aceita origens consentidas e `fieldTest` restringe leituras ao Health Connect Toolbox por `BuildConfig.HEALTH_DATA_ORIGIN_FILTER`; desligar um sensor chama `revokeAllPermissions()`, desliga todos os consentimentos importáveis e preserva os manuais |
| Contratos e governança de recomendação | `android/contracts/RECOMMENDATION_V1.md`, `docs/privacy/RECOMMENDATION_DATA_INVENTORY.md`, ADR `0002` e `docs/models/rules-v1.md` |
| Persistência de recomendação | `recommendation/RecommendationStore.kt`, `data/local/RecommendationEntities.kt` e Room v9; opt-in, retenção, correção e limpeza locais |
| Motor determinístico | `recommendation/RecommendationEngine.kt` e `docs/models/rules-v1.md`; interface pura, baseline, mínimo por contexto e razões explicáveis |
| Instrumentação minimizada | `recommendation/RecommendationTelemetry.kt`, `AlertStore.kt` e `OfflineRepository.kt`; grava após sucesso sem IDs operacionais ou texto |
| Controle e inspeção local | `ui/RecommendationSettingsScreen.kt`, `RecommendationStatistics.kt` e estado no `AgendaMobileViewModel.kt`; opt-in, preferências, métricas, correção, limpeza e baseline |
| Matriz P2-08 | `android/P2_08_MATRIX.md`; privacidade, regras, rollback, retenção, temas, custo e limites para P2-09 |
| Contratos do modelo pessoal | `contracts/PERSONAL_MODEL_V1.md`, schemas de dataset/manifesto, ADR `0003`, inventário e model card `personal-snooze-ranker/v1` |
| Treino e avaliação offline | `recommendation/PersonalRankingModel.kt`; linear auditável, split temporal, baseline e gate de promoção |
| Shadow mode | `recommendation/ShadowingRecommendationEngine`; saída primária intacta, mínimo contextual, teto de treino e métricas apenas em memória |
| Artefato e rollback | `recommendation/PersonalModelArtifactStore.kt` e Room v10; JSON canônico, SHA-256, promoção transacional e métricas agregadas |
| Ativação e inspeção | `ui/RecommendationSettingsScreen.kt`, `AgendaMobileViewModel.kt` e `PersonalSnoozeOptionRanker.kt`; opt-in confirmado, métricas e presets Wear limitados |
| Matriz e benchmark P2-09 | `android/P2_09_MATRIX.md` e `android/P2_09_RUNTIME_BENCHMARK.md`; privacidade, qualidade, integridade, custo, runtime, fallback e limites físicos |
| Release e regulação P2-10 | `android/P2_10_MATRIX.md`, `android/scripts/p2_10_static_gate.sh`, `docs/privacy/PRIVACY_NOTICE.md` e `docs/release/`; progresso, APK/permissões, declarações e limites de distribuição |
| Gates virtuais P2-10 | `android/scripts/p2_10_emulator_gate.sh`, `p2_10_resilience_gate.sh` e `P2_10ResilienceTest`; suites pareadas, rede, processo, idle, reboot e baseline sem aparelho físico |
| Gate físico P2-10 | build `fieldTest`, `android/scripts/p2_10_physical_gate.sh` e `docs/release/PHYSICAL_TEST_RUNBOOK.md`; pacote isolado, trava de autorização e critérios 6-9 |
| Assinatura P2-10 | `android/scripts/p2_10_release_candidate.sh` e `docs/release/SIGNING.md`; segredo somente no ambiente, certificado comum telefone/Wear e checksums fora do Git |
| Aceite e distribuição P2-10 | `docs/release/ACCEPTANCE.md`; v0.1 pessoal por sideload, chave definitiva manual e nova revisão obrigatória antes de venda/loja |
| Smartband real | `docs/release/SMARTBAND_COMPATIBILITY.md` e gate 8 do runbook; Mertto ZL02D/ZL02CPro usa espelhamento BLE, sem APK Wear ou Data Layer |
| Resultados físicos P2-10 | `docs/release/PHYSICAL_TEST_RESULTS.md`; checklist incremental sem serial, MAC, conta ou dump bruto |
| Matriz P2-05 | `android/P2_05_MATRIX.md`; conexão, ações conectadas, reconciliação offline, UI e limites |

| Domínio | Android | Wear | Desktop |
|---|---|---|---|
| Pareamento | `pairing/` + Keystore | nenhum segredo mestre | servidor temporário + aprovação |
| Sync | Room queue + WorkManager | Data Layer mínimo | endpoint local + journal |
| Alertas | scheduler, notification, sensory | notification/action listener | definição e materialização |
| Áudio | capability + route policy | háptico/áudio do Wear se permitido | configuração sincronizável |
| Protocolos | réplica e execução offline | passo atual e confirmar | editor e reconciliação |
| Captura | persistência local imediata | fora do MVP | triagem completa |
| Saúde | Health Connect + entrada manual | Health Services só se validado | agregados e relatório |
| IA | engine, features, runtime adapter | opções pré-calculadas | análise/model registry opcional |

## 7. Catálogo de recursos externos

Atualizar esta tabela quando uma dependência for adicionada ou removida.

| Recurso | Uso previsto | Dados acessados | Obrigatório? | Alternativa/saída |
|---|---|---|---|---|
| Android SDK / Compose | app móvel | UI local | sim | nenhuma no Android nativo |
| Room | banco offline | dados locais do app | sim | SQLite direto, não recomendado |
| WorkManager 2.9.1 | alertas duráveis e reconciliação | UUID e instante elegível | P2-04 | fixado no SDK 34; 2.11.x exige compileSdk 35 |
| Android Keystore | chaves do aparelho | chaves não exportáveis | sim | nenhuma aceitável |
| Wear notification bridge | alertas iniciais | texto/ações do alerta | MVP | app Wear dedicado |
| Wear Data Layer 20.0.1 | telefone ↔ relógio | payload mínimo do app | P2-05 | notificação espelhada |
| Compose Wear Material 3 1.5.6 | UI curta do relógio | estado local do alerta | P2-05 | 1.6.2 exige atualização isolada de AGP/lint; fallback espelhado |
| Health Connect | saúde autorizada | tipos escolhidos | opcional | entrada manual/nenhuma coleta |
| Health Services | sensor Wear próprio | sinal autorizado | não inicial | Health Connect |
| LiteRT | inferência local | features minimizadas | futuro | regras ou ONNX Runtime |
| ONNX Runtime Mobile | inferência/treino portátil | features minimizadas | alternativa | LiteRT ou regras |
| Provedor LLM | resumo textual revisável | somente seleção consentida | não | processamento local/manual |
| Google Tasks | sync de tarefas | tarefas autorizadas | opcional atual | uso só local |
| Gson 2.10.1 | codec do contrato JSON desktop | convites e respostas locais | P2-03 | substituir exige teste de compatibilidade de schema |
| Bouncy Castle 1.78.1 | certificado ECDSA persistente do sync local | PKCS#12 e segredo locais com modo `600` | P2-03 | JSSE não fornece builder X.509 público |
| ZXing Core 3.5.3 e ZXing Android Embedded 4.3.0 | gerar e ler QR do convite local | URI temporária de pareamento | P2-03 | convite também pode ser colado como texto |

Para cada SDK novo registrar: versão fixa, licença, origem, permissões, rede, telemetria, tamanho, política de atualização, CVEs e teste de remoção.

## 8. Catálogo de permissões Android

Nenhuma permissão entra “para uso futuro”. Cada uma exige requisito, tela e teste de negação.

| Permissão/capacidade | Fase | Justificativa | Comportamento negado |
|---|---|---|---|
| `INTERNET` | P2-03 | sync HTTPS local fixado | sem sessão desktop o app continua local; nenhum host externo é usado |
| `CAMERA` | P2-03 | ler o QR do convite após comando explícito | colar o convite continua disponível; câmera é opcional para instalação |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | P2-04 | permissões normais transitivas do WorkManager para persistência | sem trabalho após suspensão/reboot |
| `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` | P2-04 | capacidades normais declaradas pelo runtime WorkManager; o worker atual não usa rede nem foreground | revisar ao atualizar/remover WorkManager |
| `POST_NOTIFICATIONS` | P2-04 | alertas visuais opt-in após switch contextual | perfil permanece desligado e entregas são suprimidas sem consumir repetição |
| `VIBRATE` | P2-04 | pulso curto opt-in no canal `PHONE_VIBRATION` | canal de vibração fica indisponível; visual e áudio seguem independentes |
| Data Layer Wear (sem permissão runtime própria) | P2-05 | comunicação oficial com Wear | telefone continua funcional e usa notificação espelhada |
| Health Connect por tipo | P2-07 | relatório autorizado | categoria fica vazia |
| histórico/background health | posterior | somente hipótese aprovada | leitura limitada ao permitido |
| sensores Wear | posterior | medição própria validada | usar dados já disponíveis |

Localização não é requisito inicial. “Vou sair” não justifica pedir localização até existir caso concreto aprovado.

## 9. Contratos que não podem mudar silenciosamente

- semântica de `Concluir`, `Adiar`, `Reabrir` e tombstone;
- identidade UUID e idempotência de `operation_id`;
- versão mínima/máxima do protocolo de sync;
- unidade e fuso de todo instante e medida;
- categorias de consentimento;
- códigos de razão da recomendação;
- schema e proveniência de relatório;
- usos proibidos de dados de saúde;
- fallback sensorial e horário silencioso.

Mudança incompatível exige nova versão de contrato, fixture de compatibilidade, migração e ADR.

## 10. Registro de decisões

Criar um ADR em `docs/adr/NNNN-titulo.md` quando houver:

- novo transporte ou backend;
- mudança de topologia local/cloud;
- novo runtime ou provedor de IA;
- uso novo de dado de saúde;
- alteração de política de conflito;
- nova permissão sensível;
- mudança regulatória ou de finalidade;
- compartilhamento de código com outro projeto.

Formato mínimo: contexto, decisão, alternativas, consequências, dados afetados, rollback e links para requisitos.

## 11. Model cards

Todo modelo não determinístico usa `docs/models/<model_id>/<version>/MODEL_CARD.md` com:

- finalidade e proibições;
- features, unidades e minimização;
- dados de treino e janela;
- baseline determinístico;
- métricas e subgrupos relevantes;
- calibração;
- latência, memória e bateria;
- versão/hash do artefato;
- shadow mode;
- critério de ativação/desativação;
- rollback;
- incidentes conhecidos.

Sem model card e teste de fallback, o modelo não entra em release.

## 12. Receitas de manutenção

### Corrigir alerta

1. Identificar `alert_id`, dispositivo, canal e estado, sem copiar conteúdo sensível.
2. Reproduzir com relógio/tempo fake.
3. Testar silêncio, pausa, sobreposição, concluir e adiar.
4. Verificar idempotência e sync offline.
5. Atualizar requisito e regressão.

### Alterar sync

1. Ler contrato e matriz de compatibilidade.
2. Adicionar fixture antes do código.
3. Testar repetição, reorder, timeout, corpo truncado e conflito.
4. Validar versão anterior.
5. Nunca testar primeiro no banco pessoal.

### Alterar saúde

1. Citar finalidade e requisito.
2. Confirmar tipo e permissão oficial.
3. Definir minimização, retenção e exclusão.
4. Testar negação/revogação.
5. Revisar relatório e alegação clínica.
6. Atualizar inventário de dados e análise regulatória.

### Alterar IA

1. Comparar com regra baseline.
2. Atualizar features e model card.
3. Rodar avaliação offline e shadow mode.
4. Verificar domínio/guardrails.
5. Medir bateria/latência.
6. Demonstrar rollback para regras.

### Alterar áudio

1. Limitar escopo à Agenda.
2. Inventariar destinos disponíveis pela API.
3. Distinguir acessório Bluetooth conectado de rota realmente exposta ao áudio.
4. Testar fone Bluetooth, com fio, speaker e remoção durante playback.
5. Testar silencioso, DND, chamada e mídia concorrente.
6. Não alterar rota global permanentemente; abrir o painel Android quando a
   intenção envolver outros aplicativos.

## 13. Quality gates

### Desktop

```bash
./mvnw test
./mvnw -Pjavafx-ui-tests test
git diff --check
```

### Android

```bash
cd android
./gradlew test lint assembleDebug
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

Definir sempre `ANDROID_SERIAL` quando houver dispositivo físico conectado. Em `P2-01`, os AVDs reservados são `Agenda_Phone_API_34` e `Agenda_Wear_API_34`; o telefone físico permanece fora do gate.

### Gate documental

- requisito e aceite atualizados;
- percentual coerente;
- CHANGELOG descreve somente o entregue;
- mapa de manutenção aponta o componente novo;
- dependência/permissão catalogada;
- ADR/model card quando aplicável;
- nenhum segredo, dado pessoal, token, URL OAuth ou relatório real versionado.

## 14. Checklist para retomar uma sessão de trabalho

1. Ler `git status --short --branch` e não reverter mudanças desconhecidas.
2. Ler `LAST_CHANGES.md` e os três commits mais recentes.
3. Localizar a fase ativa na spec correspondente.
4. Confirmar se a aplicação pessoal está aberta.
5. Confirmar banco, backup e dispositivo alvo antes de teste mutável.
6. Ler apenas os módulos do mapa da área.
7. Atualizar checklist durante o avanço.
8. Executar gates proporcionais ao risco.
9. Atualizar documentos no mesmo commit.
10. Conferir `HEAD`, `origin/master` e worktree após o push.

## 15. Informações proibidas no repositório

- banco pessoal e backups;
- credenciais/tokens Google;
- chaves Android ou certificados privados;
- relatórios médicos reais;
- nomes/doses/substâncias do usuário;
- dados brutos de sensores;
- URLs de pareamento ativas;
- fingerprints vinculadas a sessões reais;
- API keys de IA;
- dumps de log com conteúdo de tarefas.

Fixtures usam dados fictícios inequívocos e IDs reservados para teste.
