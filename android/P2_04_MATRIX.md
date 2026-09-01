# Matriz de validação P2-04

Data: 2026-09-01  
Dispositivo: `emulator-5556`, AVD `Agenda_Phone_API_34`, Android 14/API 34.

## Resultado

| Cenário | Gate | Resultado |
|---|---|---|
| Instalação calma | perfil novo, abertura e espera sem interação | sem diálogo, som, vibração ou notificação |
| Permissão negada | toque no switch, diálogo Android real e `Não permitir` | perfil desligado, `granted=false`, zero notificações |
| Permissão concedida | toque no switch, diálogo Android real e `Permitir` | perfil ligado, `granted=true`, zero notificações imediatas |
| Horário silencioso | `AlertPilotMatrixTest.quietHoursRescheduleWithoutDeliveringAnyChannel` | reagendado para o fim da faixa, zero entregas |
| Cooldown global | `AlertPilotMatrixTest.sensoryCooldownUsesLastDeliveredChannelAcrossAlerts` | segundo alerta reagendado pelo último estímulo de outro alerta |
| Sobreposição | `AlertPilotMatrixTest.occupiedSensoryGateRejectsAudioTestWithoutPlaying` | rejeição `SENSORY_OVERLAP` antes do áudio |
| Concluir offline | `AlertNotificationPublisherTest.completePendingIntentPersistsOfflineActionAndClosesNotification` | ação persistida uma vez e notificação removida |
| Adiar offline | `AlertNotificationPublisherTest.snoozePendingIntentPersistsOfflineActionAndClosesNotification` | instante absoluto persistido, trabalho substituído e notificação removida |
| Reinício | recriação do coordenador e reinício externo da Activity | trabalho durável restaurado; retorno sem crash, diálogo ou notificação imediata |
| Mudança de rota | `AndroidSensoryOutputTest.unavailableHeadphonesExposeVisibleFallback` | transição fone preferido para automático e fallback explícito |

## Comandos

```bash
cd android
./gradlew test lint assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

Resultado consolidado: 44 testes locais e 28 instrumentados. A matriz instrumentada não reproduz tom; o caso de sobreposição ocupa o gate antes de chamar `AudioTrack`.

## Limites

- O telefone físico e o emulador Wear não foram usados.
- O AVD validou inventário, política, transição e fallback de rota sem fone conectado.
- Remoção durante playback, Bluetooth real, chamada e mídia concorrente permanecem para `P2-10` em dispositivos reais.
- WorkManager e Room cobrem recriação de processo; reboot completo do aparelho permanece na matriz de dispositivo real.
