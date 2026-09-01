# Contrato de sincronização local v1

## Sessão

Somente dispositivo pareado e não revogado pode abrir uma sessão HTTPS temporária. A autenticação vincula `device_id`, nonce da sessão e credencial; a resposta negocia `contract_min`, `contract_max`, papéis e capacidades. Nenhum endpoint permanece aberto após expiração ou cancelamento no desktop.

## Push

`POST /api/v1/sync/batches` aceita até 100 operações e 256 KiB antes do parse. O mesmo `operation_id` com mesmo comando, entidade e `payload_hash` devolve o resultado persistido. O mesmo ID com bytes diferentes recebe `REJECTED/ID_REUSED`. `(device_id, sequence)` também é único.

Cada chamada usa `X-Agenda-Device` e `Authorization: AgendaCredential <base64url>`. A credencial trafega somente no canal TLS preso à impressão digital do convite, nunca entra em URL ou log e é comparada ao hash persistido no desktop. A resposta do lote inclui resultados terminais e os detalhes completos dos conflitos citados naquele lote.

Estados retornados: `APPLIED`, `CONFLICT`, `REJECTED` e `RETRYABLE`. Somente `RETRYABLE` pode voltar automaticamente à fila. Conflito é terminal para aquela operação e exige nova decisão com novo UUID.

## Cursor e snapshot

O cursor do dispositivo avança somente pela maior sequência contígua terminal. Lacunas não são descartadas. O cursor do servidor é monotônico e seleciona alterações posteriores já autorizadas ao dispositivo.

Snapshot inicial é consistente por `snapshot_id`, paginado em até 200 tarefas e 50 protocolos. Um token pertence ao dispositivo, snapshot e expiração; não é offset SQL exposto. Reiniciar snapshot substitui apenas réplicas confirmadas, nunca captura ou operação local pendente.

## Conflitos

Texto, estrutura, estado ou tombstone divergente produz registro com versões local e desktop. Não existe resolução silenciosa por último escritor. A interface adequada oferece preservar local, preservar desktop ou reconstruir manualmente; cada resolução cria nova operação auditável.

## Retenção e limites

- operações aplicadas e conflitos: retenção mínima de 90 dias, revisável antes de produção;
- tombstones: mínimo de 90 dias e até todos os dispositivos ativos ultrapassarem o cursor;
- corpo de pareamento: 32 KiB;
- lote: 256 KiB e 100 operações;
- resposta/snapshot: 1 MiB por página;
- textos e payloads não entram em logs.
