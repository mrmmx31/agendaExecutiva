# Matriz P2-06 - Vou sair e protocolos moveis

Data do gate local: 2026-09-02.

## Ambiente

- Telefone: `Agenda_Phone_API_34`, Android 14/API 34, `emulator-5554`.
- Relogio: `Agenda_Wear_API_34`, Wear OS 5/API 34, `emulator-5556`.
- Dados: fixtures deterministicas e ficticias; sem localizacao, saude ou banco pessoal.
- Samsung fisico e Agenda desktop aberta ficaram fora dos comandos de teste.

## Resultado atual

| Cenario | Evidencia | Resultado |
|---|---|---|
| Entrada direta | `Vou sair` abre a unica opcao ou oferece no maximo tres candidatas ordenadas | APROVADO |
| Execucao offline | inicio e conclusao persistem execucao e operacoes na mesma transacao Room | APROVADO |
| Passo atual Wear | estado v1 contem somente titulo curto, passo, posicao, contagem, revisao e ack | APROVADO |
| Confirmacao offline Wear | outbox e feedback sao persistidos antes do envio; novo toque fica bloqueado enquanto pendente | APROVADO |
| Convergencia | ack exato remove a operacao Wear e a revisao seguinte avanca o passo | APROVADO |
| Prioridade visual | alerta sensorial oculta temporariamente o protocolo; protocolo nao oferece `Adiar` | APROVADO |
| Mudanca estrutural | `Sugerir item` cria operacao de revisao sem alterar nenhum template | APROVADO |
| Protecao desktop | servidor grava `STRUCTURE_DIVERGED` e preserva integralmente o template desktop | APROVADO |
| Tema e dimensoes | testes Compose do telefone e sete testes Wear em 384 x 384, sem texto sobreposto | APROVADO |
| Pareamento ponta a ponta | descoberta bilateral; publicar passo, concluir no relogio, receber ack e validar passo seguinte nos dois lados | APROVADO |

## Suites

- Contratos e stores Kotlin/Robolectric: aprovados.
- Desktop Maven, incluindo proposta estrutural: aprovado.
- Telefone instrumentado: 11 testes de migracao e Compose aprovados apos boot desbloqueado.
- Relogio instrumentado: 7 testes de migracao e Compose aprovados.
- Lint e montagem dos dois APKs: aprovados.

## Gate pareado final

Com os dois AVDs em `RUNNING_UNLOCKED`, `pairedNodeIsReachable` passou primeiro no telefone e depois no relogio. O percurso funcional passou nesta ordem:

1. `publishProtocolStepFixture`: telefone iniciou fixture e publicou o primeiro passo;
2. `completeProtocolStepAndAwaitAcknowledgement`: relogio recebeu, persistiu a conclusao, enviou a operacao e observou o segundo passo depois do ack;
3. `assertProtocolAdvancedAfterWearConfirmation`: telefone confirmou a posicao 2 e o `operation_id` reconhecido.

Cada gate terminou com `OK (1 test)`. Os comandos usaram explicitamente `emulator-5554` ou `emulator-5556`; o Samsung fisico nao recebeu comandos.

## Incidente de ambiente resolvido

O primeiro boot simultaneo fez o telefone permanecer em `RUNNING_LOCKED`, pois o emulador tentou comandos internos com `adb -e` enquanto havia dois AVDs. As falhas resultantes ocorreram antes da aplicacao: diretorio Room e Activity indisponiveis. O telefone virtual foi apagado e iniciado sozinho, chegou a `RUNNING_UNLOCKED` e os 11 testes passaram sem mudanca funcional. Isso apagou somente dados ficticios e removeu temporariamente o pareamento externo dos AVDs. O pareamento foi refeito e o gate final acima encerrou o incidente.
