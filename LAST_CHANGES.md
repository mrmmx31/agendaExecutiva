# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(android): selecionar saida de audio conectada |
| Data | 2026-09-03 16:23:19 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
android/P2_10_MATRIX.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/output/AndroidSensoryOutput.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/output/AudioOutputPreferenceStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/SensorySettingsScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/output/AudioOutputPreferenceStoreTest.kt
docs/integrations/GOOGLE_DRIVE_SETUP.md
docs/release/ACCEPTANCE.md
docs/release/PHYSICAL_TEST_RESULTS.md
docs/release/RELEASE_0.1.md
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 | 13 ++--
 PROJECT2_SPEC.md                                   | 19 ++++++
 android/P2_10_MATRIX.md                            | 11 +++-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        | 45 ++++++++++++-
 .../mobile/alert/output/AndroidSensoryOutput.kt    | 75 +++++++++++++++++-----
 .../alert/output/AudioOutputPreferenceStore.kt     | 20 ++++++
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 15 +++--
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      | 38 +++++++++--
 .../agenda/mobile/ui/SensorySettingsScreen.kt      | 61 +++++++++++++++++-
 .../alert/output/AudioOutputPreferenceStoreTest.kt | 32 +++++++++
 docs/integrations/GOOGLE_DRIVE_SETUP.md            | 39 +++++++++++
 docs/release/ACCEPTANCE.md                         |  2 +-
 docs/release/PHYSICAL_TEST_RESULTS.md              | 25 +++++++-
 docs/release/RELEASE_0.1.md                        |  6 ++
 14 files changed, 359 insertions(+), 42 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
