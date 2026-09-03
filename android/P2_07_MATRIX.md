# Matriz P2-07 - Saúde e relatório básico

Data do gate: 2026-09-02. Ambiente autorizado: `Agenda_Phone_API_34`
(`emulator-5554`), API 34. Todos os registros e fixtures são fictícios. O
telefone físico, a Agenda desktop aberta e bancos pessoais ficam fora dos
comandos.

## Limites do gate

- nenhuma permissão Health Connect de escrita, histórico ampliado ou background;
- nenhuma amostra bruta copiada para Room, sync ou relatório;
- nenhuma recomendação, correlação causal, diagnóstico ou ajuste terapêutico;
- nenhuma exportação ou compartilhamento automático;
- nenhum dado de saúde enviado ao desktop ou a serviço de IA.

## Cenários

| Gate | Evidência executável | Resultado exigido |
|---|---|---|
| instalação neutra | `HealthStoreTest.catalogStartsWithEveryCategoryDisabled` | oito categorias desligadas e nenhum pedido espontâneo |
| permissão negada | `deniedHealthPermissionDoesNotReadOrChangeManualRecords` | zero chamadas de leitura; entrada manual preservada |
| revogação granular | `categoryRevocationBlocksFutureSummaryWrites` e teste Compose existente | nova gravação bloqueada; demais funções continuam |
| lacuna de sensor | `importerReadsOnlyEnabledImportableCategoriesAndPreservesNoData` | `NO_DATA`, sem métrica zero inventada |
| retenção | `retentionTombstonesManualContentAndDeletesExpiredSummary` | plaintext/IV manual limpos, tombstone e auditoria; resumo removido |
| migração | `MobileDatabaseMigrationTest` | schemas anteriores convergem para Room v8 sem perda operacional |
| cifra | `AndroidKeystoreHealthDataCipherTest` | AES-GCM real e AAD incorreta rejeitada no AVD |
| snapshot | `HealthReportTest.snapshotFiltersPeriodAndSeparatesKinds` | período e categorias filtrados; sensor/fato/observação separados |
| revisão | `HealthReportTest.reviewAndExportsUseSameReducedSnapshot` | identificação corrigida e linha retirada nos formatos derivados |
| JSON/CSV | `HealthReportTest` e fixtures compartilhadas | metadados completos; CSV neutraliza fórmula |
| PDF | `HealthReportPdfExporterTest` | arquivo Android começa por `%PDF` e contém corpo não vazio |
| exportação explícita | `AgendaMobileAppTest.healthReportRequiresPreviewAndExplicitExportChoice` | prévia antes do comando e formato escolhido pelo usuário |
| temas e largura | mesmo teste Compose em modos claro/escuro no AVD de 360 dp | controles localizáveis, habilitados e sem truncar comandos |
| isolamento do sync | `healthWritesNeverEnterTheDesktopSyncQueue` | `pending_operations` permanece vazia após gravação sensível |
| logs | busca por `Log.` nos pacotes `health` e `health/report` | nenhum conteúdo de saúde enviado ao log |

## Comandos reproduzíveis

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test lint assembleDebug
ANDROID_SERIAL=emulator-5554 JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:connectedDebugAndroidTest
cd ..
./mvnw -Dtest=SharedContractFixtureTest test
find android/contracts -type f -name '*.json' -print0 | xargs -0 -n1 jq empty
```

Todos os comandos ADB devem usar `-s emulator-5554`. Não usar seleção implícita
quando o Samsung físico estiver conectado.

## Restrições remanescentes

- dados reais e permissões no telefone físico pertencem a `P2-10`;
- seletor de destino não oferece senha para PDF/CSV/JSON nesta versão; a tela
  avisa que o arquivo é sensível e o usuário escolhe o armazenamento;
- FHIR, envio ao médico e IA clínica não fazem parte deste gate;
- Health Services no relógio continua desativado, pois não há medição própria.
