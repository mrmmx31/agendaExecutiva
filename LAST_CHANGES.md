# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(sync): proteger pareamento local P2-03 |
| Data | 2026-09-01 07:21:59 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStoreTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/pairing/DeviceCredentialStore.kt
pom.xml
src/main/java/com/pessoal/agenda/app/AppContext.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalNetworkAddressSelector.java
src/main/java/com/pessoal/agenda/infra/pairing/LocalPairingServer.java
src/main/java/com/pessoal/agenda/infra/pairing/PairingSession.java
src/main/java/com/pessoal/agenda/infra/pairing/PendingPairingRequest.java
src/main/java/com/pessoal/agenda/repository/DesktopSyncRepository.java
src/main/java/com/pessoal/agenda/ui/controller/ConfigController.java
src/main/java/com/pessoal/agenda/ui/view/MobilePairingWindow.java
src/main/java/module-info.java
src/test/java/com/pessoal/agenda/infra/pairing/LocalPairingServerTest.java
src/test/java/com/pessoal/agenda/repository/DesktopSyncRepositoryTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   2 +
 MAINTENANCE_MAP.md                                 |   8 +-
 PROJECT2_SPEC.md                                   |  18 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   7 +-
 .../mobile/pairing/DeviceCredentialStoreTest.kt    | 102 ++++++
 .../agenda/mobile/pairing/DeviceCredentialStore.kt | 167 +++++++++
 pom.xml                                            |  11 +-
 .../java/com/pessoal/agenda/app/AppContext.java    |   6 +
 .../infra/pairing/LocalNetworkAddressSelector.java |  35 ++
 .../agenda/infra/pairing/LocalPairingServer.java   | 404 +++++++++++++++++++++
 .../agenda/infra/pairing/PairingSession.java       |   5 +
 .../infra/pairing/PendingPairingRequest.java       |   7 +
 .../agenda/repository/DesktopSyncRepository.java   |  24 ++
 .../agenda/ui/controller/ConfigController.java     |  25 +-
 .../agenda/ui/view/MobilePairingWindow.java        | 328 +++++++++++++++++
 src/main/java/module-info.java                     |   9 +-
 .../infra/pairing/LocalPairingServerTest.java      | 222 +++++++++++
 .../repository/DesktopSyncRepositoryTest.java      |  16 +
 19 files changed, 1382 insertions(+), 18 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
