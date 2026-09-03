# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(recommendation): instrumentar eventos operacionais |
| Data | 2026-09-02 21:50:52 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDelivery.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationTelemetry.kt
android/app/src/main/java/com/pessoal/agenda/mobile/wear/AndroidWearDataLayer.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/AlertStoreTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/data/OfflineRepositoryTest.kt
android/contracts/RECOMMENDATION_V1.md
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  22 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   1 +
 .../notification/AlertNotificationDelivery.kt      |   8 +-
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   | 364 +++++++++++++++------
 .../agenda/mobile/data/OfflineRepository.kt        | 121 ++++---
 .../mobile/recommendation/RecommendationStore.kt   |   2 +
 .../recommendation/RecommendationTelemetry.kt      |  39 +++
 .../agenda/mobile/wear/AndroidWearDataLayer.kt     |   8 +-
 .../pessoal/agenda/mobile/data/AlertStoreTest.kt   |  81 +++++
 .../agenda/mobile/data/OfflineRepositoryTest.kt    |  44 +++
 android/contracts/RECOMMENDATION_V1.md             |  14 +
 13 files changed, 552 insertions(+), 161 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
