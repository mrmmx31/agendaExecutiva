# Contrato de sincronizacao local v2

V2 preserva transporte, idempotencia, cursores e conflitos de `SYNC_V1.md` e
ativa mutacoes operacionais de tarefas. Pareamentos novos negociam intervalo
`1..2`; um aparelho anteriormente pareado em v1 continua recebendo apenas o
snapshot v1 ate novo consentimento.

## Permissao

`TASKS_WRITE` e separada de `TASKS_READ` e autoriza criar, editar, mudar estado,
remover, substituir checklist e registrar sessoes. A permissao nunca e
acrescentada automaticamente a um aparelho existente.

## Comandos

- `TASK_CREATED`: cria UUID, titulo, notas, prazo, prioridade e estado;
- `TASK_UPDATED`: altera os campos editaveis e exige `base_revision`;
- `TASK_STATUS_CHANGED`: altera estado e exige `base_revision`;
- `CHECKLIST_ITEM_CHANGED`: substitui a lista ordenada e exige `base_revision`;
- `TASK_DELETED`: exclusao confirmada e sujeita a conflito de tombstone;
- `SESSION_RECORDED`: fato imutavel com inicio, fim, duracao e nota.

Campos mutaveis aceitos no telefone: titulo ate 240 caracteres, notas ate 4000,
prazo ISO local, prioridade `LOW|NORMAL|HIGH` e estado
`PENDING|IN_PROGRESS|COMPLETED|BLOCKED|CANCELLED`. Checklist tem no maximo 200
itens, texto de 240 caracteres, UUID e posicao unica. Sessao dura de 1 segundo a
24 horas e sua nota tem ate 1000 caracteres.

## Snapshot e compatibilidade

O snapshot v2 acrescenta `notes`, `due_date`, `priority` e `checklist` dentro de
cada tarefa. O servidor consulta a versao maxima autorizada no pareamento e
remove esses campos para aparelhos v1. Lotes v1 continuam aceitos, mas comandos
introduzidos aqui recebem `REJECTED/CONTRACT_VERSION`.

Alteracoes concorrentes geram `TEXT_DIVERGED`, `STATE_DIVERGED`,
`STRUCTURE_DIVERGED` ou `TOMBSTONE_DIVERGED`; a replica do servidor prevalece
somente depois que as duas versoes forem preservadas no registro de conflito.
