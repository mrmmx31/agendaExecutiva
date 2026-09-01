# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(alerts): definir contratos sensoriais P2-04 |
| Data | 2026-09-01 14:33:17 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/main/java/com/pessoal/agenda/mobile/alert/AlertContracts.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/AlertPolicy.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/AlertContractsTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/ALERTS_V1.md
android/contracts/README.md
android/contracts/fixtures/v1/alert-action.valid.json
android/contracts/fixtures/v1/alert-definition.valid.json
android/contracts/fixtures/v1/sensory-profile.valid.json
android/contracts/v1/alert-action.schema.json
android/contracts/v1/alert-definition.schema.json
android/contracts/v1/sensory-profile.schema.json
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |   3 +-
 PROJECT2_SPEC.md                                   |  19 ++-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   3 +-
 .../pessoal/agenda/mobile/alert/AlertContracts.kt  | 188 +++++++++++++++++++++
 .../com/pessoal/agenda/mobile/alert/AlertPolicy.kt |  71 ++++++++
 .../agenda/mobile/alert/AlertContractsTest.kt      | 142 ++++++++++++++++
 .../mobile/sync/SharedContractFixtureTest.kt       |  11 +-
 android/contracts/ALERTS_V1.md                     |  34 ++++
 android/contracts/README.md                        |   5 +
 .../contracts/fixtures/v1/alert-action.valid.json  |   9 +
 .../fixtures/v1/alert-definition.valid.json        |  18 ++
 .../fixtures/v1/sensory-profile.valid.json         |  12 ++
 android/contracts/v1/alert-action.schema.json      |  16 ++
 android/contracts/v1/alert-definition.schema.json  |  40 +++++
 android/contracts/v1/sensory-profile.schema.json   |  28 +++
 .../contracts/SharedContractFixtureTest.java       |   8 +
 18 files changed, 604 insertions(+), 8 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
