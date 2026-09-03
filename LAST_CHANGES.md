# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(recommendation): persistir telemetria local |
| Data | 2026-09-02 21:29:16 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/9.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/RecommendationEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/recommendation/RecommendationStoreTest.kt
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |    8 +-
 PROJECT2_SPEC.md                                   |   23 +-
 SPEC.md                                            |    4 +-
 android/README.md                                  |    5 +-
 .../9.json                                         | 1808 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   44 +-
 .../agenda/mobile/data/local/MobileDatabase.kt     |   43 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   45 +
 .../mobile/data/local/RecommendationEntities.kt    |   57 +
 .../mobile/recommendation/RecommendationStore.kt   |  235 +++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |    4 +
 .../recommendation/RecommendationStoreTest.kt      |  181 ++
 12 files changed, 2441 insertions(+), 16 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
