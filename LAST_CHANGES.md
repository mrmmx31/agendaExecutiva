# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(protocols): integrar fluxo movel com Wear |
| Data | 2026-09-02 13:07:24 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
README.md
SPEC.md
android/P2_06_MATRIX.md
android/README.md
android/app/build.gradle.kts
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/6.json
android/app/src/androidTest/AndroidManifest.xml
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/wear/PhoneWearPairedGateTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/wear/AndroidWearDataLayer.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/wear/schemas/com.pessoal.agenda.wear.data.WearDatabase/2.json
android/wear/src/androidTest/java/com/pessoal/agenda/wear/WearPairedGateTest.kt
android/wear/src/androidTest/java/com/pessoal/agenda/wear/WearUiTest.kt
android/wear/src/androidTest/java/com/pessoal/agenda/wear/data/WearDatabaseMigrationTest.kt
android/wear/src/main/AndroidManifest.xml
android/wear/src/main/java/com/pessoal/agenda/wear/MainActivity.kt
android/wear/src/main/java/com/pessoal/agenda/wear/WearAgendaViewModel.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearDao.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearDatabase.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearEntities.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearProtocolStore.kt
android/wear/src/main/java/com/pessoal/agenda/wear/sync/WearDataLayer.kt
android/wear/src/main/res/values/strings.xml
android/wear/src/test/java/com/pessoal/agenda/wear/data/WearProtocolStoreTest.kt
src/main/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessor.java
src/test/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessorTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |    1 +
 MAINTENANCE_MAP.md                                 |    6 +-
 PROJECT2_SPEC.md                                   |   18 +-
 README.md                                          |    2 +-
 SPEC.md                                            |    4 +-
 android/P2_06_MATRIX.md                            |   41 +
 android/README.md                                  |    8 +-
 android/app/build.gradle.kts                       |    1 +
 .../6.json                                         | 1188 ++++++++++++++++++++
 android/app/src/androidTest/AndroidManifest.xml    |    8 +
 .../data/local/MobileDatabaseMigrationTest.kt      |   18 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |   34 +
 .../agenda/mobile/wear/PhoneWearPairedGateTest.kt  |   26 +
 android/app/src/main/AndroidManifest.xml           |    7 +
 .../agenda/mobile/data/OfflineRepository.kt        |   63 +-
 .../agenda/mobile/data/local/MobileDatabase.kt     |   13 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   23 +
 .../agenda/mobile/data/local/OfflineEntities.kt    |    2 +
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |   42 +
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   15 +-
 .../agenda/mobile/wear/AndroidWearDataLayer.kt     |  124 +-
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |   33 +
 .../2.json                                         |  389 +++++++
 .../com/pessoal/agenda/wear/WearPairedGateTest.kt  |   28 +
 .../java/com/pessoal/agenda/wear/WearUiTest.kt     |   44 +
 .../agenda/wear/data/WearDatabaseMigrationTest.kt  |   28 +
 android/wear/src/main/AndroidManifest.xml          |    7 +
 .../java/com/pessoal/agenda/wear/MainActivity.kt   |   77 +-
 .../com/pessoal/agenda/wear/WearAgendaViewModel.kt |   22 +-
 .../java/com/pessoal/agenda/wear/data/WearDao.kt   |   30 +
 .../com/pessoal/agenda/wear/data/WearDatabase.kt   |   39 +-
 .../com/pessoal/agenda/wear/data/WearEntities.kt   |   31 +
 .../pessoal/agenda/wear/data/WearProtocolStore.kt  |  116 ++
 .../com/pessoal/agenda/wear/sync/WearDataLayer.kt  |   54 +-
 android/wear/src/main/res/values/strings.xml       |    4 +
 .../agenda/wear/data/WearProtocolStoreTest.kt      |   98 ++
 .../agenda/infra/pairing/SyncBatchProcessor.java   |   25 +
 .../infra/pairing/SyncBatchProcessorTest.java      |   55 +
 38 files changed, 2667 insertions(+), 57 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
