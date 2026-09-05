# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(sync): estabilizar conexão móvel local |
| Data | 2026-09-05 05:36:36 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/app/build.gradle.kts
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
src/main/java/com/pessoal/agenda/AgendaApp.java
src/main/java/com/pessoal/agenda/app/AppContext.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalNetworkAddressSelector.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalPairingServer.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalSyncTlsIdentityStore.java
src/main/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessor.java
src/main/java/com/pessoal/agenda/repository/DesktopSyncRepository.java
src/main/java/com/pessoal/agenda/ui/controller/ConfigController.java
src/main/java/com/pessoal/agenda/ui/view/MobilePairingWindow.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalNetworkAddressSelectorTest.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalPairingServerTest.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalSyncTlsIdentityStoreTest.java
src/test/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessorTest.java
src/test/java/com/pessoal/agenda/ui/controller/ConfigMobilePairingFxTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   8 +-
 PROJECT2_SPEC.md                                   |  35 ++++-
 SPEC.md                                            |   4 +-
 android/app/build.gradle.kts                       |   1 +
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |   2 +
 .../agenda/mobile/pairing/DeviceCredentialStore.kt |  15 ++-
 .../pessoal/agenda/mobile/sync/SyncContracts.kt    |   6 +-
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  42 ++++++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  10 +-
 src/main/java/com/pessoal/agenda/AgendaApp.java    |   7 +
 .../java/com/pessoal/agenda/app/AppContext.java    |  22 ++++
 .../infra/pairing/LocalNetworkAddressSelector.java |  25 +++-
 .../agenda/infra/pairing/LocalPairingServer.java   |  91 +++++++++----
 .../infra/pairing/LocalSyncTlsIdentityStore.java   | 145 +++++++++++++++++++++
 .../agenda/infra/pairing/SyncBatchProcessor.java   |  15 ++-
 .../agenda/repository/DesktopSyncRepository.java   |   3 +-
 .../agenda/ui/controller/ConfigController.java     |  60 ++++++++-
 .../agenda/ui/view/MobilePairingWindow.java        |  17 ++-
 .../pairing/LocalNetworkAddressSelectorTest.java   |  15 +++
 .../infra/pairing/LocalPairingServerTest.java      |  31 ++++-
 .../pairing/LocalSyncTlsIdentityStoreTest.java     |  70 ++++++++++
 .../infra/pairing/SyncBatchProcessorTest.java      |   2 +
 .../ui/controller/ConfigMobilePairingFxTest.java   |  81 ++++++++++++
 23 files changed, 636 insertions(+), 71 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
