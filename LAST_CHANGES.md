# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(pairing): concluir P2-03 no Android |
| Data | 2026-09-01 11:00:38 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/build.gradle.kts
android/app/src/androidTest/java/com/pessoal/agenda/mobile/pairing/HttpsPairingTransportTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/MainActivity.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/PairingClient.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/HttpsSyncTransport.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/PinnedHttpsConnectionFactory.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/sync/SyncRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/pairing/PairingClientTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SyncRepositoryTest.kt
src/main/java/com/pessoal/agenda/infra/pairing/LocalPairingServer.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalPairingAndroidGate.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalPairingServerTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |   4 +-
 PROJECT2_SPEC.md                                   |  14 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   6 +-
 android/app/build.gradle.kts                       |   2 +
 .../mobile/pairing/HttpsPairingTransportTest.kt    | 179 ++++++++++++++++
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  72 +++++++
 android/app/src/main/AndroidManifest.xml           |   6 +
 .../java/com/pessoal/agenda/mobile/MainActivity.kt |  16 +-
 .../agenda/mobile/pairing/DeviceCredentialStore.kt |  63 +++++-
 .../pessoal/agenda/mobile/pairing/PairingClient.kt | 236 +++++++++++++++++++++
 .../agenda/mobile/sync/HttpsSyncTransport.kt       |  34 +--
 .../mobile/sync/PinnedHttpsConnectionFactory.kt    |  38 ++++
 .../pessoal/agenda/mobile/sync/SyncContracts.kt    |   2 +-
 .../pessoal/agenda/mobile/sync/SyncRepository.kt   |   1 +
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 102 ++++++++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  73 ++++++-
 .../agenda/mobile/pairing/PairingClientTest.kt     | 132 ++++++++++++
 .../agenda/mobile/sync/SyncRepositoryTest.kt       |   8 +-
 .../agenda/infra/pairing/LocalPairingServer.java   |  14 +-
 .../infra/pairing/LocalPairingAndroidGate.java     |  85 ++++++++
 .../infra/pairing/LocalPairingServerTest.java      |  68 ++++++
 23 files changed, 1099 insertions(+), 61 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
