# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(wear): concluir P2-05 com acoes offline |
| Data | 2026-09-02 08:40:13 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
LAST_CHANGES.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/P2_05_MATRIX.md
android/README.md
android/app/build.gradle.kts
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/5.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/wear/PhoneWearPairedGateTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/MainActivity.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationActionReceiver.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDelivery.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/scheduling/AlertWorkScheduler.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/AlertEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/wear/AndroidWearDataLayer.kt
android/app/src/main/res/values/wear.xml
android/app/src/test/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDeliveryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/wear/AndroidWearStatePublisherTest.kt
android/contracts/WEAR_V1.md
android/contracts/fixtures/v1/wear-alert-state.invalid-extra-field.json
android/contracts/fixtures/v1/wear-alert-state.valid.json
android/contracts/v1/wear-alert-state.schema.json
android/wear-contract/src/main/kotlin/com/pessoal/agenda/wear/contract/WearContracts.kt
android/wear-contract/src/test/kotlin/com/pessoal/agenda/wear/contract/WearContractsTest.kt
android/wear/build.gradle.kts
android/wear/schemas/com.pessoal.agenda.wear.data.WearDatabase/1.json
android/wear/src/androidTest/java/com/pessoal/agenda/wear/WearPairedGateTest.kt
android/wear/src/androidTest/java/com/pessoal/agenda/wear/WearUiTest.kt
android/wear/src/main/AndroidManifest.xml
android/wear/src/main/java/com/pessoal/agenda/wear/MainActivity.kt
android/wear/src/main/java/com/pessoal/agenda/wear/WearAgendaViewModel.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearAlertStore.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearDao.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearDatabase.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearDeviceIdentity.kt
android/wear/src/main/java/com/pessoal/agenda/wear/data/WearEntities.kt
android/wear/src/main/java/com/pessoal/agenda/wear/sync/WearDataLayer.kt
android/wear/src/main/res/values/strings.xml
android/wear/src/main/res/values/wear.xml
android/wear/src/test/java/com/pessoal/agenda/wear/data/WearAlertStoreTest.kt
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |    2 +-
 LAST_CHANGES.md                                    |  122 +-
 MAINTENANCE_MAP.md                                 |   10 +-
 PROJECT2_SPEC.md                                   |   18 +-
 SPEC.md                                            |    4 +-
 android/P2_05_MATRIX.md                            |   51 +
 android/README.md                                  |    8 +-
 android/app/build.gradle.kts                       |    2 +
 .../5.json                                         | 1176 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   14 +-
 .../agenda/mobile/wear/PhoneWearPairedGateTest.kt  |  117 ++
 android/app/src/main/AndroidManifest.xml           |   15 +-
 .../java/com/pessoal/agenda/mobile/MainActivity.kt |    7 +
 .../AlertNotificationActionReceiver.kt             |    2 +
 .../notification/AlertNotificationDelivery.kt      |    9 +-
 .../mobile/alert/scheduling/AlertWorkScheduler.kt  |    2 +
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   |   43 +
 .../agenda/mobile/data/local/AlertEntities.kt      |    1 +
 .../agenda/mobile/data/local/MobileDatabase.kt     |   12 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   17 +-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   13 +-
 .../agenda/mobile/wear/AndroidWearDataLayer.kt     |  221 ++++
 android/app/src/main/res/values/wear.xml           |    5 +
 .../notification/AlertNotificationDeliveryTest.kt  |   17 +
 .../mobile/sync/SharedContractFixtureTest.kt       |    2 +-
 .../mobile/wear/AndroidWearStatePublisherTest.kt   |  134 +++
 android/contracts/WEAR_V1.md                       |   17 +-
 .../v1/wear-alert-state.invalid-extra-field.json   |    1 +
 .../fixtures/v1/wear-alert-state.valid.json        |    3 +-
 android/contracts/v1/wear-alert-state.schema.json  |    5 +-
 .../pessoal/agenda/wear/contract/WearContracts.kt  |   16 +-
 .../agenda/wear/contract/WearContractsTest.kt      |    5 +
 android/wear/build.gradle.kts                      |   30 +
 .../1.json                                         |  212 ++++
 .../com/pessoal/agenda/wear/WearPairedGateTest.kt  |   89 ++
 .../java/com/pessoal/agenda/wear/WearUiTest.kt     |   84 ++
 android/wear/src/main/AndroidManifest.xml          |   15 +-
 .../java/com/pessoal/agenda/wear/MainActivity.kt   |  151 ++-
 .../com/pessoal/agenda/wear/WearAgendaViewModel.kt |   50 +
 .../com/pessoal/agenda/wear/data/WearAlertStore.kt |  166 +++
 .../java/com/pessoal/agenda/wear/data/WearDao.kt   |   47 +
 .../com/pessoal/agenda/wear/data/WearDatabase.kt   |   29 +
 .../pessoal/agenda/wear/data/WearDeviceIdentity.kt |   19 +
 .../com/pessoal/agenda/wear/data/WearEntities.kt   |   41 +
 .../com/pessoal/agenda/wear/sync/WearDataLayer.kt  |  118 ++
 android/wear/src/main/res/values/strings.xml       |    7 +
 android/wear/src/main/res/values/wear.xml          |    5 +
 .../pessoal/agenda/wear/data/WearAlertStoreTest.kt |  155 +++
 .../contracts/SharedContractFixtureTest.java       |    2 +-
 49 files changed, 3191 insertions(+), 100 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
