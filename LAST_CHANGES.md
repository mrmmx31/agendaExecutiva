# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(alerts): configurar perfil sensorial e audio |
| Data | 2026-09-01 18:30:11 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationPublisherTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/alert/output/AndroidSensoryOutputTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDelivery.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/output/AndroidSensoryOutput.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/scheduling/AlertWorkScheduler.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/SensorySettingsScreen.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDeliveryTest.kt
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   1 +
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  14 +-
 SPEC.md                                            |   2 +-
 android/README.md                                  |   3 +-
 .../notification/AlertNotificationPublisherTest.kt |   2 +
 .../alert/output/AndroidSensoryOutputTest.kt       |  40 +++
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  49 ++-
 android/app/src/main/AndroidManifest.xml           |   1 +
 .../notification/AlertNotificationDelivery.kt      |  63 ++--
 .../mobile/alert/output/AndroidSensoryOutput.kt    | 259 ++++++++++++++
 .../mobile/alert/scheduling/AlertWorkScheduler.kt  |   2 +
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   |  10 +-
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    | 135 ++++---
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      | 132 ++++++-
 .../agenda/mobile/ui/SensorySettingsScreen.kt      | 387 +++++++++++++++++++++
 .../notification/AlertNotificationDeliveryTest.kt  |  61 +++-
 17 files changed, 1081 insertions(+), 85 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
