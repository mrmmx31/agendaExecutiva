# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(alerts): publicar notificacoes visuais opt-in |
| Data | 2026-09-01 16:47:41 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
SPEC.md
android/README.md
android/app/src/androidTest/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationPublisherTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/alert/scheduling/WorkManagerAlertEnqueuerTest.kt
android/app/src/androidTest/java/com/pessoal/agenda/mobile/ui/AgendaMobileAppTest.kt
android/app/src/main/AndroidManifest.xml
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationActionReceiver.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDelivery.kt
android/app/src/main/java/com/pessoal/agenda/mobile/alert/scheduling/AlertWorkScheduler.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/AlertStore.kt
android/app/src/main/java/com/pessoal/agenda/mobile/data/local/OfflineDao.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt
android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt
android/app/src/main/res/values/strings.xml
android/app/src/test/java/com/pessoal/agenda/mobile/alert/notification/AlertNotificationDeliveryTest.kt
android/app/src/test/java/com/pessoal/agenda/mobile/alert/scheduling/AlertSchedulingTest.kt
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   3 +-
 MAINTENANCE_MAP.md                                 |   5 +-
 PROJECT2_SPEC.md                                   |  14 +-
 SPEC.md                                            |   4 +-
 android/README.md                                  |   9 +-
 .../notification/AlertNotificationPublisherTest.kt | 157 +++++++++++
 .../scheduling/WorkManagerAlertEnqueuerTest.kt     |   3 +-
 .../agenda/mobile/ui/AgendaMobileAppTest.kt        |  26 ++
 android/app/src/main/AndroidManifest.xml           |   4 +
 .../AlertNotificationActionReceiver.kt             |  59 ++++
 .../notification/AlertNotificationDelivery.kt      | 312 +++++++++++++++++++++
 .../mobile/alert/scheduling/AlertWorkScheduler.kt  |  41 ++-
 .../com/pessoal/agenda/mobile/data/AlertStore.kt   |  66 ++++-
 .../pessoal/agenda/mobile/data/local/OfflineDao.kt |  11 +
 .../pessoal/agenda/mobile/ui/AgendaMobileApp.kt    |  77 +++++
 .../agenda/mobile/ui/AgendaMobileViewModel.kt      |  59 +++-
 android/app/src/main/res/values/strings.xml        |   6 +
 .../notification/AlertNotificationDeliveryTest.kt  | 199 +++++++++++++
 .../mobile/alert/scheduling/AlertSchedulingTest.kt |  42 ++-
 19 files changed, 1052 insertions(+), 45 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
