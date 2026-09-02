# Contratos móveis

Este diretório cataloga os contratos versionados usados pelo núcleo móvel. Os
schemas não contêm dados pessoais e devem permanecer compatíveis dentro da
mesma versão.

## Versão 1

| Schema | Uso |
|---|---|
| `operation-envelope.schema.json` | envelope durável comum da fila offline |
| `capture-created.schema.json` | criação imutável de captura livre |
| `protocol-run-started.schema.json` | início de uma execução de protocolo |
| `protocol-step-completed.schema.json` | confirmação idempotente de um passo |
| `pairing-request.schema.json` | solicitação móvel antes da aprovação desktop |
| `pairing-response.schema.json` | estado pendente ou conclusão do pareamento |
| `sync-batch.schema.json` | lote limitado de operações e cursor conhecido |
| `sync-result.schema.json` | resultado terminal ou repetível por operação |
| `sync-batch-response.schema.json` | cursores, resultados e conflitos retornados pelo lote |
| `snapshot-page.schema.json` | página consistente da réplica inicial |
| `conflict.schema.json` | divergência explícita para revisão |
| `alert-definition.schema.json` | alerta materializado com janela, canais e ações |
| `sensory-profile.schema.json` | opt-in, silêncio, pausa, cooldown e rota desejada |
| `alert-action.schema.json` | comando idempotente de concluir ou adiar |
| `wear-alert-state.schema.json` | cópia mínima e revisionada de um alerta no relógio |

O pareamento e o modelo de ameaça da fase seguinte estão em
`PAIRING_V1.md` e `THREAT_MODEL_P2_03.md`.

Os limites sensoriais e as formas das ações estão em `ALERTS_V1.md`.
O transporte e os limites do relógio estão em `WEAR_V1.md`.

O envelope inclui UUID da operação, dispositivo, sequência monotônica,
instante, fuso, versão do contrato, comando, entidade, payload e SHA-256 do
payload. Alteração incompatível exige um novo diretório de versão, migração e
teste de compatibilidade.

Validação sintática local:

```bash
jq empty contracts/v1/*.json
./gradlew test
cd .. && ./mvnw -Dtest=SharedContractFixtureTest test
```
