# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(health): integrar resumos do Health Connect |
| Data | 2026-09-02 19:52:24 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/app/build.gradle.kts
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/8.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/HealthEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/HealthStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/connect/HealthConnectGateway.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/connect/HealthConnectImportCoordinator.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/connect/HealthRationaleActivity.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/HealthPrivacyScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/health/HealthStoreTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/build.gradle.kts
android/contracts/README.md
android/contracts/fixtures/v1/health-summary.valid.json
android/contracts/v1/health-summary.schema.json
android/gradle/wrapper/gradle-wrapper.properties
docs/adr/0001-health-data-security-boundary.md
docs/privacy/HEALTH_DATA_INVENTORY.md
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |    4 +-
 PROJECT2_SPEC.md                                   |   12 +-
 SPEC.md                                            |    4 +-
 android/app/build.gradle.kts                       |    4 +-
 .../8.json                                         | 1523 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   11 +-
 android/app/src/main/AndroidManifest.xml           |   25 +
 .../agenda/mobile/data/local/HealthEntities.kt     |   14 +
 .../agenda/mobile/data/local/MobileDatabase.kt     |   18 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |    9 +
 .../pessoal/agenda/mobile/health/HealthStore.kt    |  108 ++
 .../mobile/health/connect/HealthConnectGateway.kt  |  188 +++
 .../connect/HealthConnectImportCoordinator.kt      |   45 +
 .../health/connect/HealthRationaleActivity.kt      |   51 +
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |    8 +
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   32 +
 .../agenda/mobile/ui/HealthPrivacyScreen.kt        |   36 +
 .../agenda/mobile/health/HealthStoreTest.kt        |   66 +
 .../mobile/sync/SharedContractFixtureTest.kt       |    3 +
 android/build.gradle.kts                           |    2 +-
 android/contracts/README.md                        |    1 +
 .../fixtures/v1/health-summary.valid.json          |   19 +
 android/contracts/v1/health-summary.schema.json    |   35 +
 android/gradle/wrapper/gradle-wrapper.properties   |    2 +-
 docs/adr/0001-health-data-security-boundary.md     |    9 +-
 docs/privacy/HEALTH_DATA_INVENTORY.md              |   11 +-
 .../contracts/SharedContractFixtureTest.java       |    3 +
 27 files changed, 2217 insertions(+), 26 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
