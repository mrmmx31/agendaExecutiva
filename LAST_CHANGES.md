# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(alerts): persistir estado sensorial no Room |
| Data | 2026-09-01 14:53:44 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/schemas/com.pessoal.agenda.mobile.data.local.MobileDatabase/4.json
android/app/src/androidTest/java/com/pessoal/agenda/mobile/data/local/MobileDatabaseMigrationTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/AlertEntities.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/MobileDatabase.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/AlertStoreTest.kt
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |    1 +
 MAINTENANCE_MAP.md                                 |    3 +-
 PROJECT2_SPEC.md                                   |   12 +-
 SPEC.md                                            |    4 +-
 android/README.md                                  |    3 +-
 .../4.json                                         | 1170 ++++++++++++++++++++
 .../data/local/MobileDatabaseMigrationTest.kt      |   30 +-
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   |  263 +++++
 .../agenda/mobile/data/local/AlertEntities.kt      |  108 ++
 .../agenda/mobile/data/local/MobileDatabase.kt     |   73 +-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   54 +
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |    3 +
 .../pessoal/agenda/mobile/data/AlertStoreTest.kt   |  191 ++++
 13 files changed, 1898 insertions(+), 17 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
