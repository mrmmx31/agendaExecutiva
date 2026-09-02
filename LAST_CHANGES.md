# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(health): definir contratos e fronteira de dados |
| Data | 2026-09-02 17:14:49 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
README.md
SPEC.md
android/README.md
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/README.md
android/contracts/fixtures/v1/health-consent.valid.json
android/contracts/fixtures/v1/intake-log.valid.json
android/contracts/fixtures/v1/symptom-log.valid.json
android/contracts/v1/health-consent.schema.json
android/contracts/v1/intake-log.schema.json
android/contracts/v1/symptom-log.schema.json
docs/adr/0001-health-data-security-boundary.md
docs/privacy/HEALTH_DATA_INVENTORY.md
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |  1 +
 MAINTENANCE_MAP.md                                 |  5 ++-
 PROJECT2_SPEC.md                                   | 19 ++++++--
 README.md                                          |  2 +-
 SPEC.md                                            |  4 +-
 android/README.md                                  |  3 +-
 .../mobile/sync/SharedContractFixtureTest.kt       |  9 ++++
 android/contracts/README.md                        |  6 +++
 .../fixtures/v1/health-consent.valid.json          | 12 +++++
 .../contracts/fixtures/v1/intake-log.valid.json    | 18 ++++++++
 .../contracts/fixtures/v1/symptom-log.valid.json   | 13 ++++++
 android/contracts/v1/health-consent.schema.json    | 19 ++++++++
 android/contracts/v1/intake-log.schema.json        | 25 +++++++++++
 android/contracts/v1/symptom-log.schema.json       | 20 +++++++++
 docs/adr/0001-health-data-security-boundary.md     | 46 +++++++++++++++++++
 docs/privacy/HEALTH_DATA_INVENTORY.md              | 51 ++++++++++++++++++++++
 .../contracts/SharedContractFixtureTest.java       |  9 ++++
 17 files changed, 253 insertions(+), 9 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
