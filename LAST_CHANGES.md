# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(health): adicionar gestao local e consentimentos |
| Data | 2026-09-02 18:13:55 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
README.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/health/HealthStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/HealthPrivacyScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/health/HealthStoreTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/fixtures/v1/symptom-log.valid.json
android/contracts/v1/symptom-log.schema.json
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  12 +-
 README.md                                          |   2 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   3 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  63 +++++
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |   6 +
 .../pessoal/agenda/mobile/health/HealthStore.kt    |  21 +-
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  39 ++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  67 +++++-
 .../agenda/mobile/ui/HealthPrivacyScreen.kt        | 261 +++++++++++++++++++++
 .../agenda/mobile/health/HealthStoreTest.kt        |  14 ++
 .../mobile/sync/SharedContractFixtureTest.kt       |   2 +-
 .../contracts/fixtures/v1/symptom-log.valid.json   |   1 +
 android/contracts/v1/symptom-log.schema.json       |   3 +-
 .../contracts/SharedContractFixtureTest.java       |   2 +-
 17 files changed, 479 insertions(+), 27 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
