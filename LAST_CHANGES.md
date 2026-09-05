# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(android): adicionar plano diario e foco |
| Data | 2026-09-05 10:12:08 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/11.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/ui/TodayFocusTest.kt
```

## Diff Resumido

```diff
 .../11.json                                        | 2181 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   38 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |   97 +
 .../agenda/mobile/data/OfflineRepository.kt        |   69 +
 .../agenda/mobile/data/local/MobileDatabase.kt     |   45 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   57 +
 .../agenda/mobile/data/local/OfflineEntities.kt    |   51 +
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  290 ++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   93 +-
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |   81 +
 .../com/pessoal/agenda/mobile/ui/TodayFocusTest.kt |   45 +
 11 files changed, 3026 insertions(+), 21 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
