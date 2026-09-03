# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(model): controlar ativacao pessoal |
| Data | 2026-09-02 23:32:01 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/PersonalModelArtifactStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/PersonalRankingModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/PersonalSnoozeOptionRanker.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/RecommendationSettingsScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/recommendation/ActivePersonalModelRecommendationEngineTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/recommendation/PersonalSnoozeOptionRankerTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/RECOMMENDATION_V1.md
android/contracts/fixtures/v1/recommendation-decision-model.valid.json
android/contracts/v1/recommendation-decision.schema.json
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  31 ++++-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   3 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  79 +++++++++++
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   |  16 ++-
 .../recommendation/PersonalModelArtifactStore.kt   |   7 +
 .../mobile/recommendation/PersonalRankingModel.kt  | 153 +++++++++++++++------
 .../recommendation/PersonalSnoozeOptionRanker.kt   |  66 +++++++++
 .../mobile/recommendation/RecommendationStore.kt   |  10 +-
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |   9 ++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      | 119 +++++++++++++++-
 .../mobile/ui/RecommendationSettingsScreen.kt      |  90 +++++++++++-
 .../ActivePersonalModelRecommendationEngineTest.kt | 116 ++++++++++++++++
 .../PersonalSnoozeOptionRankerTest.kt              | 114 +++++++++++++++
 .../mobile/sync/SharedContractFixtureTest.kt       |   2 +
 android/contracts/RECOMMENDATION_V1.md             |   1 +
 .../v1/recommendation-decision-model.valid.json    |  15 ++
 .../v1/recommendation-decision.schema.json         |   6 +-
 .../contracts/SharedContractFixtureTest.java       |   2 +
 20 files changed, 778 insertions(+), 70 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
