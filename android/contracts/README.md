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

O pareamento e o modelo de ameaça da fase seguinte estão em
`PAIRING_V1.md` e `THREAT_MODEL_P2_03.md`.

O envelope inclui UUID da operação, dispositivo, sequência monotônica,
instante, fuso, versão do contrato, comando, entidade, payload e SHA-256 do
payload. Alteração incompatível exige um novo diretório de versão, migração e
teste de compatibilidade.

Validação sintática local:

```bash
jq empty contracts/v1/*.json
```
