# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(protocols): iniciar fluxo vou sair P2-06 |
| Data | 2026-09-02 12:01:55 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/ui/LeavingHomeSelectionTest.kt
android/contracts/README.md
android/contracts/fixtures/v1/wear-protocol-step-action.valid.json
android/contracts/fixtures/v1/wear-protocol-step-state.valid.json
android/contracts/v1/wear-protocol-step-action.schema.json
android/contracts/v1/wear-protocol-step-state.schema.json
android/wear-contract/src/main/kotlin/com/pessoal/agenda/wear/contract/WearContracts.kt
android/wear-contract/src/main/kotlin/com/pessoal/agenda/wear/contract/WearProtocolContracts.kt
android/wear-contract/src/test/kotlin/com/pessoal/agenda/wear/contract/WearProtocolContractsTest.kt
src/main/java/com/pessoal/agenda/ui/controller/DashboardController.java
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |  1 +
 MAINTENANCE_MAP.md                                 |  2 +-
 PROJECT2_SPEC.md                                   | 17 ++++-
 SPEC.md                                            |  2 +-
 android/README.md                                  |  1 +
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        | 33 +++++++++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 74 ++++++++++++++++++-
 .../mobile/sync/SharedContractFixtureTest.kt       |  4 +
 .../agenda/mobile/ui/LeavingHomeSelectionTest.kt   | 30 ++++++++
 android/contracts/README.md                        |  2 +
 .../v1/wear-protocol-step-action.valid.json        |  8 ++
 .../v1/wear-protocol-step-state.valid.json         | 14 ++++
 .../v1/wear-protocol-step-action.schema.json       | 16 ++++
 .../v1/wear-protocol-step-state.schema.json        | 22 ++++++
 .../pessoal/agenda/wear/contract/WearContracts.kt  | 17 +++++
 .../agenda/wear/contract/WearProtocolContracts.kt  | 85 ++++++++++++++++++++++
 .../wear/contract/WearProtocolContractsTest.kt     | 49 +++++++++++++
 .../agenda/ui/controller/DashboardController.java  | 19 ++++-
 .../contracts/SharedContractFixtureTest.java       |  4 +
 19 files changed, 393 insertions(+), 7 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
