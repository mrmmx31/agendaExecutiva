# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(wear): iniciar modulo e contrato P2-05 |
| Data | 2026-09-01 20:42:08 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/test/java/com/pessoal/agenda/mobile/sync/SharedContractFixtureTest.kt
android/build.gradle.kts
android/contracts/README.md
android/contracts/WEAR_V1.md
android/contracts/fixtures/v1/wear-alert-state.invalid-extra-field.json
android/contracts/fixtures/v1/wear-alert-state.valid.json
android/contracts/v1/wear-alert-state.schema.json
android/settings.gradle.kts
android/wear-contract/build.gradle.kts
android/wear-contract/src/main/kotlin/com/pessoal/agenda/wear/contract/WearContracts.kt
android/wear-contract/src/test/kotlin/com/pessoal/agenda/wear/contract/WearContractsTest.kt
android/wear/build.gradle.kts
android/wear/proguard-rules.pro
android/wear/src/main/AndroidManifest.xml
android/wear/src/main/java/com/pessoal/agenda/wear/MainActivity.kt
android/wear/src/main/res/drawable/ic_launcher_foreground.xml
android/wear/src/main/res/mipmap-anydpi/ic_launcher.xml
android/wear/src/main/res/values/strings.xml
android/wear/src/main/res/values/themes.xml
android/wear/src/main/res/xml/backup_rules.xml
android/wear/src/main/res/xml/data_extraction_rules.xml
src/test/java/com/pessoal/agenda/contracts/SharedContractFixtureTest.java
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |   8 +-
 PROJECT2_SPEC.md                                   |  19 ++-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   7 +-
 .../mobile/sync/SharedContractFixtureTest.kt       |   7 +
 android/build.gradle.kts                           |   1 +
 android/contracts/README.md                        |   2 +
 android/contracts/WEAR_V1.md                       |  43 +++++++
 .../v1/wear-alert-state.invalid-extra-field.json   |  16 +++
 .../fixtures/v1/wear-alert-state.valid.json        |  15 +++
 android/contracts/v1/wear-alert-state.schema.json  |  37 ++++++
 android/settings.gradle.kts                        |   2 +-
 android/wear-contract/build.gradle.kts             |  20 +++
 .../pessoal/agenda/wear/contract/WearContracts.kt  | 142 +++++++++++++++++++++
 .../agenda/wear/contract/WearContractsTest.kt      |  80 ++++++++++++
 android/wear/build.gradle.kts                      |  50 ++++++++
 android/wear/proguard-rules.pro                    |   1 +
 android/wear/src/main/AndroidManifest.xml          |  27 ++++
 .../java/com/pessoal/agenda/wear/MainActivity.kt   |  49 +++++++
 .../main/res/drawable/ic_launcher_foreground.xml   |  12 ++
 .../src/main/res/mipmap-anydpi/ic_launcher.xml     |   5 +
 android/wear/src/main/res/values/strings.xml       |   3 +
 android/wear/src/main/res/values/themes.xml        |   8 ++
 android/wear/src/main/res/xml/backup_rules.xml     |   6 +
 .../src/main/res/xml/data_extraction_rules.xml     |  13 ++
 .../contracts/SharedContractFixtureTest.java       |   7 +
 27 files changed, 575 insertions(+), 10 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
