# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(sync): implementar transporte local P2-03 |
| Data | 2026-09-01 09:32:36 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/3.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStoreTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/HttpsSyncTransport.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SyncRepositoryTest.kt
android/contracts/README.md
android/contracts/SYNC_V1.md
android/contracts/fixtures/v1/sync-batch-response.valid.json
android/contracts/v1/sync-batch-response.schema.json
src/main/java/com/pessoal/agenda/infra/Database.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalPairingServer.java
src/main/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessor.java
src/main/java/com/pessoal/agenda/repository/DesktopSyncRepository.java
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalPairingServerTest.java
src/test/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessorTest.java
src/test/java/com/pessoal/agenda/repository/DesktopSyncRepositoryTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   3 +
 MAINTENANCE_MAP.md                                 |   8 +-
 PROJECT2_SPEC.md                                   |  18 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |  10 +-
 .../3.json                                         | 684 +++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |  43 +-
 .../mobile/pairing/DeviceCredentialStoreTest.kt    |   8 +
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  26 +
 android/app/src/main/AndroidManifest.xml           |   2 +
 .../agenda/mobile/data/OfflineRepository.kt        |  16 +-
 .../agenda/mobile/data/local/MobileDatabase.kt     |  32 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |  47 ++
 .../agenda/mobile/data/local/OfflineEntities.kt    |  22 +
 .../agenda/mobile/pairing/DeviceCredentialStore.kt |  29 +
 .../agenda/mobile/sync/HttpsSyncTransport.kt       | 115 ++++
 .../pessoal/agenda/mobile/sync/SyncContracts.kt    | 101 +++
 .../pessoal/agenda/mobile/sync/SyncRepository.kt   | 178 ++++++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  89 ++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  43 +-
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |  19 +
 .../mobile/sync/SharedContractFixtureTest.kt       |   2 +
 .../agenda/mobile/sync/SyncRepositoryTest.kt       | 159 +++++
 android/contracts/README.md                        |   1 +
 android/contracts/SYNC_V1.md                       |   2 +
 .../fixtures/v1/sync-batch-response.valid.json     |  13 +
 .../contracts/v1/sync-batch-response.schema.json   |  14 +
 .../java/com/pessoal/agenda/infra/Database.java    |  60 ++
 .../agenda/infra/pairing/LocalPairingServer.java   | 141 ++++-
 .../agenda/infra/pairing/SyncBatchProcessor.java   | 283 +++++++++
 .../agenda/repository/DesktopSyncRepository.java   | 498 +++++++++++++++
 .../contracts/SharedContractFixtureTest.java       |   2 +
 .../infra/pairing/LocalPairingServerTest.java      | 120 +++-
 .../infra/pairing/SyncBatchProcessorTest.java      |  90 +++
 .../repository/DesktopSyncRepositoryTest.java      |  53 +-
 35 files changed, 2892 insertions(+), 43 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
