# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(sync): versionar contratos compartilhados P2-03 |
| Data | 2026-09-01 00:28:43 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/build.gradle.kts
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/contracts/README.md
android/contracts/SYNC_V1.md
android/contracts/fixtures/v1/conflict.valid.json
android/contracts/fixtures/v1/pairing-request.invalid-extra-field.json
android/contracts/fixtures/v1/pairing-request.valid.json
android/contracts/fixtures/v1/pairing-response.valid.json
android/contracts/fixtures/v1/snapshot-page.valid.json
android/contracts/fixtures/v1/sync-batch.valid.json
android/contracts/fixtures/v1/sync-result.invalid-status.json
android/contracts/fixtures/v1/sync-result.valid.json
android/contracts/v1/conflict.schema.json
android/contracts/v1/pairing-request.schema.json
android/contracts/v1/pairing-response.schema.json
android/contracts/v1/snapshot-page.schema.json
android/contracts/v1/sync-batch.schema.json
android/contracts/v1/sync-result.schema.json
pom.xml
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |  1 +
 MAINTENANCE_MAP.md                                 |  3 +-
 PROJECT2_SPEC.md                                   | 12 +++--
 SPEC.md                                            |  4 +-
 android/README.md                                  |  1 +
 android/app/build.gradle.kts                       |  1 +
 .../mobile/sync/SharedContractFixtureTest.kt       | 54 ++++++++++++++++++++
 android/contracts/README.md                        |  8 +++
 android/contracts/SYNC_V1.md                       | 30 +++++++++++
 android/contracts/fixtures/v1/conflict.valid.json  | 12 +++++
 .../v1/pairing-request.invalid-extra-field.json    |  5 ++
 .../fixtures/v1/pairing-request.valid.json         | 11 ++++
 .../fixtures/v1/pairing-response.valid.json        | 11 ++++
 .../contracts/fixtures/v1/snapshot-page.valid.json |  9 ++++
 .../contracts/fixtures/v1/sync-batch.valid.json    |  6 +++
 .../fixtures/v1/sync-result.invalid-status.json    |  7 +++
 .../contracts/fixtures/v1/sync-result.valid.json   |  7 +++
 android/contracts/v1/conflict.schema.json          | 19 +++++++
 android/contracts/v1/pairing-request.schema.json   | 18 +++++++
 android/contracts/v1/pairing-response.schema.json  | 18 +++++++
 android/contracts/v1/snapshot-page.schema.json     | 16 ++++++
 android/contracts/v1/sync-batch.schema.json        | 13 +++++
 android/contracts/v1/sync-result.schema.json       | 14 ++++++
 pom.xml                                            | 15 ++++++
 .../contracts/SharedContractFixtureTest.java       | 58 ++++++++++++++++++++++
 25 files changed, 345 insertions(+), 8 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
