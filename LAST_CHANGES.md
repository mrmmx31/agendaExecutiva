# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | docs(model): fechar fronteira do ranking pessoal |
| Data | 2026-09-02 22:44:09 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/PERSONAL_MODEL_V1.md
android/contracts/README.md
android/contracts/fixtures/v1/personal-model-manifest.valid.json
android/contracts/fixtures/v1/personal-ranking-dataset.valid.json
android/contracts/v1/personal-model-manifest.schema.json
android/contracts/v1/personal-ranking-dataset.schema.json
docs/adr/0003-personal-ranking-runtime.md
docs/models/personal-snooze-ranker/v1/MODEL_CARD.md
docs/privacy/PERSONAL_MODEL_DATA_INVENTORY.md
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |  7 ++--
 PROJECT2_SPEC.md                                   | 31 ++++++++++++++--
 SPEC.md                                            |  4 +-
 android/README.md                                  |  3 +-
 .../mobile/sync/SharedContractFixtureTest.kt       | 15 ++++++++
 android/contracts/PERSONAL_MODEL_V1.md             | 31 ++++++++++++++++
 android/contracts/README.md                        |  3 ++
 .../fixtures/v1/personal-model-manifest.valid.json | 20 ++++++++++
 .../v1/personal-ranking-dataset.valid.json         | 22 +++++++++++
 .../v1/personal-model-manifest.schema.json         | 33 +++++++++++++++++
 .../v1/personal-ranking-dataset.schema.json        | 35 ++++++++++++++++++
 docs/adr/0003-personal-ranking-runtime.md          | 43 ++++++++++++++++++++++
 .../models/personal-snooze-ranker/v1/MODEL_CARD.md | 36 ++++++++++++++++++
 docs/privacy/PERSONAL_MODEL_DATA_INVENTORY.md      | 25 +++++++++++++
 .../contracts/SharedContractFixtureTest.java       | 15 ++++++++
 15 files changed, 313 insertions(+), 10 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
