# Architecture Notes (MVC + Camadas)

## Objetivo
Reduzir acoplamento da UI e preparar manutencao evolutiva.

## Camadas
- `model/`: entidades de dominio (records)
- `infra/`: acesso de baixo nivel (SQLite, conexao e migracoes)
- `repository/`: consultas SQL por agregado
- `service/`: regras de negocio e consolidacoes
- `app/`: composition root (wiring de dependencias)
- `ui/`: controladores e views JavaFX (em migracao)

## Funcionalidades implementadas

### Recorrencia de tarefas (Palm Desktop / Outlook / MS-Project)

#### Modelo
- `ScheduleType` enum: `SINGLE` | `RANGE` | `WEEKLY`
- `Task` record: campos `scheduleType`, `endDate`, `recurrenceDays`
  - `recurrenceDays`: dias da semana no formato strftime('%w') do SQLite
    - `0`=Dom, `1`=Seg, `2`=Ter, `3`=Qua, `4`=Qui, `5`=Sex, `6`=Sab
    - Exemplo: `"1,3,5"` = Seg, Qua, Sex

#### Schema (migrations em `Database.runMigrations()`)
```sql
ALTER TABLE tasks ADD COLUMN schedule_type TEXT NOT NULL DEFAULT 'SINGLE';
ALTER TABLE tasks ADD COLUMN end_date TEXT;
ALTER TABLE tasks ADD COLUMN recurrence_days TEXT;
```

#### Logica de consulta (`TaskRepository`)
- `SINGLE`: `due_date = ?`
- `RANGE`:  `due_date <= ? AND end_date >= ?`
- `WEEKLY`: `due_date <= ? AND end_date >= ? AND instr(...recurrence_days...) > 0`

#### Validacao (`TaskService.createTask()`)
- RANGE/WEEKLY exigem `endDate`
- `endDate` deve ser posterior a `startDate`
- WEEKLY exige pelo menos um dia marcado

#### UI (`HelloApplication.buildAgendaSidePanel()`)
- ComboBox: "Dia unico" / "Intervalo de datas" / "Dias da semana"
- DatePicker de termino (aparece para RANGE e WEEKLY)
- Checkboxes de dias da semana Dom–Sab (aparece para WEEKLY)
- Visibilidade condicional via `setVisible + setManaged`
- Criacao via `AppContextHolder.get().taskService().createTask()`
- Conclusao via `AppContextHolder.get().taskService().markDone()`

### Métricas locais opt-in

- `LocalMetricsService`: consentimento persistente, duração da sessão e cálculo das medianas.
- `LocalMetricsRepository`: escrita e consulta de eventos numéricos no SQLite, com retenção máxima por tipo.
- `LocalMetricsPanel`: apresentação na vista `Revisar`; permanece fora do layout quando a coleta está desativada.
- Não existe transporte de rede, identificador de tarefa ou conteúdo textual nesse fluxo.

## Estado atual
- Estrutura de pacotes: model, infra, repository, service, app criados e compilando
- `Database` centraliza conexao e migracoes
- Repositorios por contexto implementados
- `TaskService`, `AlertService` e `DashboardService` adicionados
- `AppContext` centraliza o wiring; `Launcher` inicializa antes da UI
- **Criacao e conclusao de tarefas migradas para `TaskService`**
- Leitura/listagem ainda via `DatabaseService` legado (migracao pendente)

## Proxima fase
1. Preservar as fronteiras atuais do desktop e corrigir o legado apenas quando necessário.
2. Iniciar o Projeto 2 em um build Android separado, conforme `PROJECT2_SPEC.md`.
3. Compartilhar contratos versionados e fixtures, não banco ou classes de persistência.
4. Consultar `MAINTENANCE_MAP.md` antes de introduzir módulo, permissão, SDK ou modelo de IA.

O Android será local-first com Room e fila de operações. Desktop e smartphone se comunicam por contrato HTTPS pareado; telefone e Wear OS usam notificações da plataforma e, quando necessário, Data Layer. Nenhum dispositivo acessa diretamente o banco SQLite de outro.

## Convencoes
- Repositorio: sem regra de negocio
- Service: sem dependencia de JavaFX
- UI: sem SQL direto
- Mensagens de erro/validacao concentradas no service quando possivel
