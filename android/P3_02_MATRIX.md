# P3-02 - Tarefas, checklist e timer

| Campo | Estado |
|---|---|
| Fase | A3-02 |
| Implementacao | concluida |
| Gate automatizado | aprovado |
| Aceite no Moto | pendente de pareamento v2 e sync real |
| Progresso da fase | 75% (3 de 4 itens) |

## Entrega

- Room v12 preserva tarefas existentes e acrescenta notas, prazo, prioridade,
  checklist, cronometro persistente e sessoes;
- a tela `Tarefas` cria, edita e remove com confirmacao, mostra estado por texto
  e icone e oferece desfazer para mudanca de estado;
- checklist, interrupcao, retomada e finalizacao funcionam offline e geram fila
  duravel;
- `SYNC_V2.md` separa `TASKS_WRITE`, preserva clientes v1, aplica revisoes e
  registra conflitos sem sobrescrever silenciosamente;
- sessoes sao fatos imutaveis e entram no historico desktop com idempotencia.

## Evidencia automatizada

- suite JVM desktop completa: aprovada;
- suite Android `testDebugUnitTest`: aprovada com JBR 21;
- migracoes Room ate v12: cinco casos aprovados em conjunto e o sexto aprovado
  isoladamente no `Agenda_Phone_API_34`;
- fluxo Compose de tarefa, checklist, estado e criacao: aprovado no
  `Agenda_Phone_API_34`;
- APK release assinado instalado por atualizacao no Moto Edge 60, sem desinstalar
  a versao anterior.

O JDK Debian 21 deste host falha ao criar a tarefa Gradle de teste com
`TypeNotPresentException: Type T`; o mesmo gate passa com o JBR 21 do IntelliJ.
Isso e uma restricao reproduzivel do executor local, nao falha do aplicativo.

## Aceite restante

- [ ] abrir a versao instalada e confirmar a migracao dos dados existentes;
- [ ] revogar/reparear concedendo explicitamente `TASKS_WRITE`;
- [ ] criar uma tarefa no telefone, sincronizar e confirmar no desktop;
- [ ] editar a mesma tarefa nos dois lados e confirmar conflito explicito;
- [ ] registrar uma sessao no telefone e confirmar no historico desktop.

Ao aprovar os cinco passos, o quarto item da A3-02 fecha, a fase passa a 100% e
o progresso geral do Projeto 3 passa de 20% para 30%.
