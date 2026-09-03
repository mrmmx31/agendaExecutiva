# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(recommendation): entregar controle e inspecao local |
| Data | 2026-09-02 22:23:43 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStatistics.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/RecommendationSettingsScreen.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/theme/Theme.kt
android/app/src/test/java/com/pessoal/agenda/mobile/recommendation/RecommendationStatisticsTest.kt
android/contracts/RECOMMENDATION_V1.md
docs/privacy/RECOMMENDATION_DATA_INVENTORY.md
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  32 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   4 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        | 120 +++++++
 .../recommendation/RecommendationStatistics.kt     |  49 +++
 .../mobile/recommendation/RecommendationStore.kt   |  30 ++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  49 ++-
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      | 104 +++++-
 .../mobile/ui/RecommendationSettingsScreen.kt      | 365 +++++++++++++++++++++
 .../com/pessoal/agenda/mobile/ui/theme/Theme.kt    |  15 +
 .../recommendation/RecommendationStatisticsTest.kt |  71 ++++
 android/contracts/RECOMMENDATION_V1.md             |   8 +
 docs/privacy/RECOMMENDATION_DATA_INVENTORY.md      |   2 +
 14 files changed, 835 insertions(+), 23 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
