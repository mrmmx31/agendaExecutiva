# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(android): adicionar tarefas checklist e sessoes |
| Data | 2026-09-05 12:06:05 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/12.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/PairingClient.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/PairingInvitation.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SyncRepositoryTest.kt
android/contracts/README.md
android/contracts/SYNC_V2.md
src/main/java/com/pessoal/agenda/infra/Database.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalPairingServer.java
src/main/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessor.java
src/main/java/com/pessoal/agenda/repository/DesktopSyncRepository.java
src/main/java/com/pessoal/agenda/ui/view/MobilePairingWindow.java
```

## Diff Resumido

```diff
 .../12.json                                        | 2422 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   22 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |   39 +
 .../agenda/mobile/data/OfflineRepository.kt        |  194 +-
 .../agenda/mobile/data/local/MobileDatabase.kt     |   43 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   51 +
 .../agenda/mobile/data/local/OfflineEntities.kt    |   58 +
 .../agenda/mobile/pairing/DeviceCredentialStore.kt |    2 +-
 .../pessoal/agenda/mobile/pairing/PairingClient.kt |    2 +-
 .../agenda/mobile/pairing/PairingInvitation.kt     |    4 +-
 .../pessoal/agenda/mobile/sync/SyncContracts.kt    |   12 +
 .../pessoal/agenda/mobile/sync/SyncRepository.kt   |   23 +-
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  337 +++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   76 +-
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |   28 +
 .../agenda/mobile/sync/SyncRepositoryTest.kt       |    6 +-
 android/contracts/README.md                        |    2 +
 android/contracts/SYNC_V2.md                       |   38 +
 .../java/com/pessoal/agenda/infra/Database.java    |   35 +-
 .../agenda/infra/pairing/LocalPairingServer.java   |   35 +-
 .../agenda/infra/pairing/SyncBatchProcessor.java   |  165 +-
 .../agenda/repository/DesktopSyncRepository.java   |  165 +-
 .../agenda/ui/view/MobilePairingWindow.java        |    1 +
 23 files changed, 3715 insertions(+), 45 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
