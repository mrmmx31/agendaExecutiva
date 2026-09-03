# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | docs(recommendation): definir telemetria local v1 |
| Data | 2026-09-02 21:10:35 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
PROJECT2_SPEC.md
SPEC.md
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/README.md
android/contracts/RECOMMENDATION_V1.md
android/contracts/fixtures/v1/recommendation-decision.valid.json
android/contracts/fixtures/v1/recommendation-event.valid.json
android/contracts/v1/recommendation-decision.schema.json
android/contracts/v1/recommendation-event.schema.json
docs/adr/0002-local-recommendation-telemetry.md
docs/models/rules-v1.md
docs/privacy/RECOMMENDATION_DATA_INVENTORY.md
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 PROJECT2_SPEC.md                                   | 26 +++++++++++++++---
 SPEC.md                                            |  4 +--
 .../mobile/sync/SharedContractFixtureTest.kt       |  6 +++++
 android/contracts/README.md                        |  3 +++
 android/contracts/RECOMMENDATION_V1.md             | 31 ++++++++++++++++++++++
 .../fixtures/v1/recommendation-decision.valid.json | 15 +++++++++++
 .../fixtures/v1/recommendation-event.valid.json    | 18 +++++++++++++
 .../v1/recommendation-decision.schema.json         | 31 ++++++++++++++++++++++
 .../contracts/v1/recommendation-event.schema.json  | 26 ++++++++++++++++++
 docs/adr/0002-local-recommendation-telemetry.md    | 18 +++++++++++++
 docs/models/rules-v1.md                            | 18 +++++++++++++
 docs/privacy/RECOMMENDATION_DATA_INVENTORY.md      | 20 ++++++++++++++
 .../contracts/SharedContractFixtureTest.java       |  6 +++++
 13 files changed, 217 insertions(+), 5 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
