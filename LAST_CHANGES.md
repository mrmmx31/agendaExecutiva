# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(android): concluir nucleo movel offline P2-02 |
| Data | 2026-08-31 16:25:31 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/build.gradle.kts
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/2.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseTest.kt
android/build.gradle.kts
android/contracts/README.md
android/contracts/v1/capture-created.schema.json
android/contracts/v1/operation-envelope.schema.json
android/contracts/v1/protocol-run-started.schema.json
android/contracts/v1/protocol-step-completed.schema.json
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   4 +
 MAINTENANCE_MAP.md                                 |  22 +-
 PROJECT2_SPEC.md                                   |  21 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |  10 +-
 android/app/build.gradle.kts                       |   6 +
 .../2.json                                         | 561 +++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |  45 ++
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  62 ++-
 .../agenda/mobile/data/OfflineRepository.kt        | 224 ++++++++
 .../agenda/mobile/data/local/MobileDatabase.kt     | 110 +++-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt | 112 ++++
 .../agenda/mobile/data/local/OfflineEntities.kt    | 129 +++++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 399 +++++++++++----
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      | 110 ++++
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |  93 ++++
 .../agenda/mobile/data/local/MobileDatabaseTest.kt |   8 +-
 android/build.gradle.kts                           |   1 +
 android/contracts/README.md                        |  25 +
 android/contracts/v1/capture-created.schema.json   |  13 +
 .../contracts/v1/operation-envelope.schema.json    |  34 ++
 .../contracts/v1/protocol-run-started.schema.json  |  14 +
 .../v1/protocol-step-completed.schema.json         |  13 +
 23 files changed, 1871 insertions(+), 149 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
