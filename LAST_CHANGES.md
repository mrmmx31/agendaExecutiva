# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(health): adicionar relatorio revisavel |
| Data | 2026-09-02 20:46:08 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/health/report/HealthReportPdfExporterTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/report/HealthReport.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/report/HealthReportExporter.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/HealthPrivacyScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/health/report/HealthReportTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/README.md
android/contracts/fixtures/v1/health-report.valid.json
android/contracts/v1/health-report.schema.json
docs/privacy/HEALTH_DATA_INVENTORY.md
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   4 +-
 PROJECT2_SPEC.md                                   |  12 +-
 SPEC.md                                            |   4 +-
 .../health/report/HealthReportPdfExporterTest.kt   |  38 +++++
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  51 +++++++
 .../agenda/mobile/health/report/HealthReport.kt    | 155 +++++++++++++++++++++
 .../mobile/health/report/HealthReportExporter.kt   | 118 ++++++++++++++++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  29 ++++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  43 ++++++
 .../agenda/mobile/ui/HealthPrivacyScreen.kt        | 113 +++++++++++++++
 .../mobile/health/report/HealthReportTest.kt       |  88 ++++++++++++
 .../mobile/sync/SharedContractFixtureTest.kt       |   3 +
 android/contracts/README.md                        |   1 +
 .../contracts/fixtures/v1/health-report.valid.json |  29 ++++
 android/contracts/v1/health-report.schema.json     |  50 +++++++
 docs/privacy/HEALTH_DATA_INVENTORY.md              |   2 +
 .../contracts/SharedContractFixtureTest.java       |   3 +
 17 files changed, 734 insertions(+), 9 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
