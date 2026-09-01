# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(alerts): agendar reavaliacao com WorkManager |
| Data | 2026-09-01 16:15:05 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/build.gradle.kts
android/app/src/androidTest/java/com/pessoal/agenda/mobile/alert/scheduling/WorkManagerAlertEnqueuerTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/AlertContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/AlertPolicy.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/scheduling/AlertWorkScheduler.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/AlertContractsTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/scheduling/AlertSchedulingTest.kt
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |  11 +-
 PROJECT2_SPEC.md                                   |  12 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   8 +-
 android/app/build.gradle.kts                       |   3 +
 .../scheduling/WorkManagerAlertEnqueuerTest.kt     |  96 +++++++++++++
 .../pessoal/agenda/mobile/alert/AlertContracts.kt  |  13 ++
 .../com/pessoal/agenda/mobile/alert/AlertPolicy.kt |   2 +-
 .../mobile/alert/scheduling/AlertWorkScheduler.kt  | 123 ++++++++++++++++
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   | 124 +++++++++++++++-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |  30 ++++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |   4 +-
 .../agenda/mobile/alert/AlertContractsTest.kt      |   4 +
 .../mobile/alert/scheduling/AlertSchedulingTest.kt | 160 +++++++++++++++++++++
 15 files changed, 578 insertions(+), 17 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
