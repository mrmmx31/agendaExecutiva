# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(model): integrar ranking em shadow mode |
| Data | 2026-09-02 22:58:12 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/PersonalRankingModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationEngine.kt
android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/test/java/com/pessoal/agenda/mobile/recommendation/ShadowingRecommendationEngineTest.kt
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  23 ++-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   3 +-
 .../mobile/recommendation/PersonalRankingModel.kt  | 114 +++++++++++++++
 .../mobile/recommendation/RecommendationEngine.kt  |   4 +
 .../mobile/recommendation/RecommendationStore.kt   |   3 +
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  12 +-
 .../ShadowingRecommendationEngineTest.kt           | 155 +++++++++++++++++++++
 9 files changed, 311 insertions(+), 12 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
