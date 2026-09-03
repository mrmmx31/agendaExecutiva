# Matriz P2-08 - Recomendação local explicável

Data do gate: 2026-09-02. Ambiente autorizado: JVM local e
`Agenda_Phone_API_34` (`emulator-5554`), API 34. Eventos e contextos usados nos
testes são fictícios. Telefone físico, Agenda desktop e bancos pessoais ficam
fora dos comandos.

## Limites do gate

- ranking estatístico local por `rules-v1`; nenhum modelo aprendido ou runtime
  LiteRT/ONNX está ativo;
- nenhuma ação sugerida é executada sem comando explícito do usuário;
- nenhum texto, ID operacional, saúde, localização, conteúdo de tela, analytics
  ou inferência implícita de distração entra nos eventos;
- eventos, decisões e configurações não entram no sync desktop;
- bateria e memória de runtime aprendido pertencem a P2-09; P2-08 mede a regra
  Kotlin pura e confirma ausência de trabalho periódico para recomendação.

## Cenários

| Gate | Evidência executável | Resultado exigido |
|---|---|---|
| instalação neutra | `RecommendationStoreTest.installationStartsDisabledAndCollectsNothing` | opt-in desligado, retenção de 90 dias e zero coleta |
| contrato fechado | schemas/fixtures v1 e `SharedContractFixtureTest` | campos inesperados recusados e códigos versionados |
| minimização | tipos de `RecommendationEventInput` e busca estática indicada abaixo | somente enums, horário e números limitados; nenhum campo proibido |
| isolamento | `RecommendationStoreTest` e testes de telemetria em `AlertStoreTest`/`OfflineRepositoryTest` | fila de sync inalterada e falha de telemetria sem rollback operacional |
| idempotência | suítes de alertas e protocolos | repetir entrega/ação/passo não duplica evento |
| baseline e rollback | `disabledPersonalizationUsesCautiousBaselineWithoutHistory` e `disabledPersonalizationKeepsFallbackEvenWithEnoughHistory` | desligado sempre usa ordem cautelosa, inclusive com histórico |
| mínimo por contexto | `twelveMatchingSamplesEnableStableLocalRanking` e `samplesFromAnotherExplicitContextDoNotUnlockPersonalization` | 12 amostras exatas habilitam frequência; outro contexto não conta |
| limites de domínio | testes `quietHours...`, `watchIsNeverSuggested...` e `protocolShortcut...` | silêncio, disponibilidade e propósito filtram antes do ranking |
| explicabilidade | `RecommendationEngineTest` e tela Compose | toda opção tem razão visível e versão `rules-v1` |
| retenção e limpeza | `retentionAndExplicitClearCoverEventsAndDecisions` | eventos e decisões vencidos ou apagados; dados operacionais preservados |
| correção substitutiva | `correctionReplacesCategoricalValuesWithoutDuplicatingEvent` e teste Compose | mesmo UUID, contexto substituído e instante de correção visível |
| métricas prudentes | `RecommendationStatisticsTest` | mediana, correção, expiração e repetição estimada sem identidade de alerta |
| controles | dois testes de recomendação em `AgendaMobileAppTest` | opt-in, preferências, razões, correção e limpeza explícitos |
| tema e largura | inspeção no Pixel virtual em claro/escuro e 393 dp | texto e controles sem sobreposição; barras do sistema com contraste correto |
| custo da regra | `tenThousandObservationsRemainWithinInteractiveBudget` | 10 mil observações classificadas em menos de 1 segundo na JVM de teste |
| build | `test lint assembleDebug assembleDebugAndroidTest` | app e Wear compilam; lint sem erro |

## Comandos reproduzíveis

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew test lint assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.pessoal.agenda.mobile.ui.AgendaMobileAppTest
cd ..
./mvnw -Dtest=SharedContractFixtureTest test
find android/contracts -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
rg -n -i 'task(id|title|text)|alert(id|title|text)|protocol(id|title|text)|heart|sleep|medicat|substan|symptom|location|token|credential' \
  android/app/src/main/java/com/pessoal/agenda/mobile/recommendation
```

Todos os comandos ADB devem usar `-s emulator-5554`. Nunca usar seleção implícita
enquanto o Samsung físico estiver visível no `adb`.

## Restrições remanescentes

- decisões geradas nesta fase são regras/estatística local; comparação shadow,
  runtime aprendido e rollback de artefato pertencem a P2-09;
- a métrica de adiamento repetido é estimativa temporal, pois IDs operacionais
  são deliberadamente proibidos;
- medidas de bateria, Doze e comportamento em hardware real pertencem a P2-09 e
  P2-10, respectivamente;
- o companion Google Pixel Watch do AVD pode exibir falha externa por permissão
  Bluetooth; esse processo não faz parte do APK Agenda e não foi alterado.

## Decisão

`P2-08` está concluída. A próxima fase autorizada é `P2-09 - Personalização por
modelo`, mantendo saúde fora das features e começando em shadow mode local.
