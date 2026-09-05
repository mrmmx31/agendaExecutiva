# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | fix(android): permitir encerrar protocolo ativo |
| Data | 2026-09-05 07:53:47 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/MainActivity.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
docs/release/RELEASE_0.1.md
src/main/java/com/pessoal/agenda/infra/Database.java
src/main/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessor.java
src/main/java/com/pessoal/agenda/repository/DesktopSyncRepository.java
src/test/java/com/pessoal/agenda/infra/pairing/SyncBatchProcessorTest.java
src/test/java/com/pessoal/agenda/repository/DesktopSyncRepositoryTest.java
```

## Diff Resumido

```diff
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        | 38 +++++++++++++++++
 .../java/com/pessoal/agenda/mobile/MainActivity.kt |  4 +-
 .../agenda/mobile/data/OfflineRepository.kt        | 24 ++++++++++-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |  3 ++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 46 +++++++++++++++++++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  7 ++++
 .../agenda/mobile/data/OfflineRepositoryTest.kt    | 19 +++++++++
 docs/release/RELEASE_0.1.md                        |  8 ++--
 .../java/com/pessoal/agenda/infra/Database.java    | 38 ++++++++++++++++-
 .../agenda/infra/pairing/SyncBatchProcessor.java   | 16 +++++++
 .../agenda/repository/DesktopSyncRepository.java   |  2 +-
 .../infra/pairing/SyncBatchProcessorTest.java      | 49 ++++++++++++++++++++++
 .../repository/DesktopSyncRepositoryTest.java      | 35 ++++++++++++++++
 13 files changed, 280 insertions(+), 9 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
