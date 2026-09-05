# Contrato de expansao funcional v1

Este contrato cataloga os dominios que podem atravessar a fronteira desktop/Android no Projeto 3. O catalogo executavel fica em `fixtures/v1/expansion-domain-catalog.valid.json` e sua forma fechada em `v1/expansion-domain-catalog.schema.json`.

## Ownership

- Desktop e a autoridade estrutural para modelos de protocolo, categorias, grades de estudo, relatorios e operacoes em massa.
- Android pode criar eventos e editar os campos explicitamente liberados por cada comando.
- Execucoes, capturas e sessoes sao fatos imutaveis; correcao gera novo evento ou revisao, nunca reescrita silenciosa.
- Configuracoes sensoriais e dados de saude locais nao entram no sync de produtividade sem novo contrato e consentimento.

## Revisao e conflito

Toda entidade mutavel usa UUID, `revision`, `updated_at` e tombstone quando exclusao for sincronizavel. Comando de edicao leva `base_revision`. Divergencia de texto, estrutura, estado ou tombstone usa o fluxo de conflito v1 existente.

Eventos imutaveis usam apenas idempotencia por `operation_id` e identidade do fato. Repeticao identica devolve o resultado armazenado; identidade reutilizada com payload diferente e rejeitada.

## Ativacao por fase

O catalogo nao habilita comandos automaticamente. Cada comando passa a ser aceito somente quando possuir schema de payload, fixture, persistencia Android, processamento desktop, teste de repeticao, regra de conflito e compatibilidade com o cliente anterior.

Ordem prevista:

1. `TODAY`, `TASKS` e `CAPTURE`;
2. `PROTOCOLS` e `STUDIES`;
3. `IDEAS`;
4. `FINANCE` e `COMMERCE`;
5. `SETTINGS` e `REPORTING` somente nos limites catalogados.

## Migracao

- Mudanca aditiva preserva `contract_version=1` somente quando clientes antigos ignoram a nova capacidade por negociacao.
- Campo obrigatorio novo, semantica alterada ou enum fechado ampliado sem negociacao exige `v2`.
- Snapshot anuncia capacidades por dominio antes de enviar novos tipos.
- Desktop deve aceitar por pelo menos uma versao a fila criada pelo release anterior.
- Downgrade nao remove fila local; operacao desconhecida fica bloqueada e explicada, nunca descartada.

## Privacidade sensorial

`PAYMENT`, `LOW_STOCK` e `RECEIVABLE` usam texto neutro na tela bloqueada. Dados de saude, medicamentos, substancias, valores e notas privadas nao podem entrar em notificacao espelhada pelo Da Fit.

