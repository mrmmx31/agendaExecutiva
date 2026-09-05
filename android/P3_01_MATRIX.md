# A3-01 - Hoje, plano e foco

Data: 2026-09-05

## Escopo entregue

- `Agora` resolve o foco pela ordem manual, essencial do plano e sugestao automatica;
- plano diario normal aceita uma tarefa essencial e ate duas de apoio;
- plano reduzido aceita somente a tarefa essencial;
- encerramento aceita nota opcional e o dia pode ser reaberto;
- plano, itens e foco vivem no Room e sobrevivem a recriacao do processo;
- editor e dialogos usam estado salvavel e sobrevivem a recriacao da composicao;
- modo compacto usa navegacao lateral e remove a faixa sensorial e o subtitulo em paisagem para preservar a area de trabalho.

## Persistencia

O banco passou da versao 10 para 11 com as tabelas `daily_plans`,
`daily_plan_items` e `focus_selections`. A migracao preserva tarefas existentes,
usa chaves estrangeiras e impede duas tarefas na mesma funcao/posicao.

Nesta fase, plano e foco sao locais. Eles nao geram comandos que o desktop ainda
nao reconhece. A convergencia com tarefa, checklist, timer e conflitos entra no
gate A3-02, sem colocar comandos irrecuperaveis na fila atual.

## Evidencias

| Gate | Resultado |
|---|---|
| Testes JVM Android | 122 aprovados, 0 falhas |
| UI focalizada | criar plano reduzido, fechar/reabrir e restaurar dialogo aprovados no `Agenda_Phone_API_34` |
| Migracao 10 -> 11 | aprovada no `Agenda_Phone_API_34` |
| Processo morto | banco em arquivo fechado e reaberto por teste Robolectric, preservando plano e foco |
| Offline | operacoes locais aprovadas sem transporte e sem comando pendente desconhecido |
| Tema e orientacao | inspecao em claro, escuro e paisagem; texto legivel e sem sobreposicao |
| Schema Room | `11.json` valido e versionado |

A execucao indiscriminada de toda a instrumentacao nao e um gate valido: o teste
`P2_10ResilienceTest` requer uma fixture criada pelo script de resiliencia. A
matriz acima executa os casos A3-01 isoladamente e mantem esse pre-requisito
explicito.

## Proximo gate

A3-02 acrescenta alteracao de tarefa, checklist, desfazer, timer/sessoes e a
convergencia desses dados com o desktop.
