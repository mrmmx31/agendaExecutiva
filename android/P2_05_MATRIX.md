# Matriz P2-05 - Wear OS

Data de fechamento: 2026-09-02.

## Ambiente

- Telefone: `Agenda_Phone_API_34`, Android 14/API 34, serial usado no gate `emulator-5556`.
- Relogio: `Agenda_Wear_API_34`, Wear OS 5/API 34, serial usado no gate `emulator-5558`.
- Transporte: Wear Data Layer `20.0.1`, com os dois AVDs pareados pelo Device Manager.
- Aplicativos com o mesmo `applicationId` e assinatura de debug.
- Samsung fisico, banco desktop pessoal, audio, vibracao e dados pessoais fora da matriz.

## Resultado

| Cenario | Evidencia | Resultado |
|---|---|---|
| Inicializacao neutra | APK Wear aberto sem permissao, prompt, notificacao, audio ou vibracao | APROVADO |
| Descoberta pareada | `NodeClient.connectedNodes` verificado nos dois AVDs | APROVADO |
| Telefone para relogio | estado v1 gravado como `DataItem` urgente e ingerido no Room Wear | APROVADO |
| Payload e revisao | codec fechado recusa campo inesperado; revisao igual ou menor e ignorada | APROVADO |
| Interface curta | tela vazia, texto/motivo e somente `Concluir` e `Adiar`; ate tres presets recebidos | APROVADO |
| Concluir conectado | acao persistida antes do feedback, aplicada no telefone e removida apos ack exato | APROVADO |
| Adiar conectado | preset de 10 min aplicado; telefone agenda o retorno e publica estado `SNOOZED` | APROVADO |
| Telefone indisponivel | acao fica no outbox Wear; ao reabrir o telefone, a reconciliacao a aplica e confirma | APROVADO |
| Idempotencia | `operation_id` repetido nao duplica acao; ack referencia a operacao aceita | APROVADO |
| Pausa e desligamento | DataItems ativos sao removidos; acao local nao confirmada e protegida contra exclusao | APROVADO |
| Fallback espelhado | canal de notificacao Android e `PendingIntent` anteriores permanecem ativos | APROVADO |
| Tema e dimensoes | quatro testes Compose e inspecao de 384 x 384 no AVD validam presenca, ausencia e rotulos sem truncamento | APROVADO |

## Suites

- Android local: 47 testes, sem falhas.
- Wear local: 6 testes, sem falhas.
- Contrato Wear: 5 testes, sem falhas.
- Android instrumentado: 28 testes gerais mais 7 gates Data Layer, sem falhas na execucao final de cada caso.
- Wear instrumentado: 4 testes Compose mais 4 gates Data Layer, sem falhas.
- Desktop Maven: 155 testes, sem falhas.
- `./gradlew test lint assembleDebug`: aprovado; lint sem erros nos dois APKs.
- Schemas JSON e `git diff --check`: aprovados.

## Limites

- `DataItem` e persistencia local oferecem entrega posterior, nao garantia de prazo.
- O fallback espelhado depende do sistema e das configuracoes de notificacao do usuario.
- O pareamento dos AVDs e estado externo ao aplicativo. Depois de os dois emuladores serem encerrados pelo ambiente, o relogio reiniciado fora da porta original deixou de aparecer como no pareado; e necessario repetir `Pair Wearable` para uma nova matriz, sem alterar dados da Agenda.
- Relogio e telefone fisicos, bateria, Doze prolongado, Bluetooth real e permissao negada no dispositivo real permanecem para `P2-10`.
- Nenhum estimulo sensorial foi emitido automaticamente por teste.

## Decisao

`P2-05` esta concluida. A proxima fase autorizada e `P2-06 - Vou sair e protocolos moveis`.
