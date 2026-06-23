# Guia de Desenvolvimento — Agenda Pessoal

> Documentação técnica das partes mecânicas do projeto.  
> Atualizar este arquivo junto com qualquer mudança estrutural.

---

## Estrutura de Pacotes

```
com.pessoal.agenda/
├── AgendaApp.java          — Entry point JavaFX, wiring de controllers e abas
├── Launcher.java           — Shim para JPMS (chama AgendaApp.main)
├── DatabaseService.java    — Serviço legado de consultas (migração em andamento)
│
├── app/
│   ├── AppContext.java         — Composition root: instancia e conecta todos os repositórios/serviços
│   ├── AppContextHolder.java   — Singleton de acesso global ao AppContext
│   └── SharedContext.java      — Estado reativo compartilhado entre todos os controllers de UI
│                                 (ObservableLists, Labels de KPI, callbacks de refresh)
│
├── infra/
│   └── Database.java           — Conexão SQLite + runMigrations() (criação/evolução de schema)
│
├── model/                      — Records imutáveis de domínio (sem lógica de negócio)
│   ├── Task.java
│   ├── TaskPriority.java / TaskStatus.java / ScheduleType.java
│   ├── Protocol.java / ProtocolExecutionType.java
│   ├── FinanceEntry.java
│   ├── ProjectIdea.java / IdeaChecklistItem.java
│   ├── ChecklistItem.java
│   ├── InventoryItem.java
│   ├── AttendanceDay.java / MonthSummary.java
│   ├── Category.java / CategoryDomain.java
│   └── TaskSession.java
│
├── repository/                 — Apenas SQL, sem regras de negócio
│   ├── TaskRepository.java
│   ├── TaskSessionRepository.java
│   ├── ProtocolRepository.java
│   ├── StudyEntryRepository.java
│   └── ...
│
├── service/                    — Regras de negócio, sem dependência de JavaFX
│   ├── TaskService.java
│   ├── CategoryService.java
│   ├── TaskTimerService.java       — Singleton de timer de sessão ativa
│   ├── PendencyNotificationService.java — Alertas periódicos de pendências (TDAH)
│   ├── GoogleAuthService.java / GoogleTasksService.java
│   └── ...
│
├── tools/
│   └── ICalendarExporter.java  — Exportação de tarefas para .ics
│
└── ui/
    ├── controller/
    │   ├── AgendaTabController.java    — Aba "Agenda e Prioridades"
    │   ├── DashboardController.java    — Aba "Dashboard" com KPIs e alertas TDAH
    │   ├── ChecklistController.java    — Aba "Protocolos Operacionais"
    │   ├── StudyController.java        — Aba "Estudos e Atividades"
    │   ├── FinanceController.java      — Aba "Financeiro e Pendências"
    │   ├── IdeasController.java        — Aba "Banco de Ideias"
    │   ├── SalesController.java        — Aba "Vendas"
    │   ├── ConfigController.java       — Aba "Configurações"
    │   └── UIHelper.java               — Factories de componentes reutilizáveis
    ├── view/                           — Janelas secundárias (Stage independentes)
    │   ├── Dialogs.java                — Factory central de Alert/Dialog (usa Modality.NONE)
    │   ├── ThemeManager.java           — Aplica/troca CSS de tema em tempo real
    │   ├── ProtocolExecutionWindow.java
    │   ├── TaskTimerWindow.java
    │   ├── SessionHistoryWindow.java
    │   └── ...
    └── util/
        └── PrintReportService.java
```

---

## Sistema de Temas (CSS)

### Arquivos

| Arquivo | Papel |
|---|---|
| `app.css` | Regras base + tokens do tema claro (variáveis `-t-*`) |
| `theme-dark.css` | Apenas redefine os tokens `-t-*` + overrides de controles nativos |
| `timer-inline.css` | Estilo do timer inline nas células de lista |

### Convenção de Tokens

Todos os tokens seguem o padrão `-t-<contexto>`. **Nunca usar cores hardcoded (`#hex`) em regras CSS**; sempre usar tokens.

| Token | Uso |
|---|---|
| `-t-app-bg` | Fundo geral da janela |
| `-t-surface` | Fundo de cards e painéis |
| `-t-surface-a/b/c/d` | Variantes sutis de surface |
| `-t-text` | Texto principal |
| `-t-text-m` / `-t-text-m2` | Texto secundário / hint |
| `-t-text-inv` | Texto sobre fundos coloridos (botões primários) |
| `-t-pri` / `-t-pri-dk` | Cor primária / escurecida |
| `-t-bd` / `-t-bd-lt` | Bordas principais / sutis |
| `-t-selected` / `-t-selected2` | Seleção ativa / sem foco |
| `-t-hover` | Hover de células |
| `-t-err` / `-t-err-bg` | Erro/perigo |
| `-t-cal-*` | Calendário de frequência |
| `-t-inp-bg` / `-t-inp-bd` | Inputs (TextField, DatePicker) |

### Regra de Seleção em Listas/Tabelas

Para garantir visibilidade em linhas alternadas, sempre cobrir os 3 estados:

```css
.minha-lista .list-cell:filled:selected,
.minha-lista .list-cell:filled:selected:odd,
.minha-lista .list-cell:filled:selected:even {
    -fx-background-color: -t-selected2; /* sem foco */
}
.minha-lista:focused .list-cell:filled:selected,
.minha-lista:focused .list-cell:filled:selected:odd,
.minha-lista:focused .list-cell:filled:selected:even {
    -fx-background-color: -t-selected; /* com foco */
}
```

---

## ThemeManager

`com.pessoal.agenda.ui.view.ThemeManager`

- Singleton: `ThemeManager.getInstance()`
- `applyTo(Scene scene)` — registra a cena e aplica tema atual
- `setTheme(Theme theme)` — troca o tema em **todas as cenas abertas** simultaneamente
- Temas disponíveis: `Theme.LIGHT` (padrão), `Theme.DARK`
- Hook global: `initGlobalWindowHook()` intercepta janelas novas e aplica tema automaticamente

---

## SharedContext — Estado Reativo

`com.pessoal.agenda.app.SharedContext`

Centraliza todo o estado que é compartilhado entre os controllers. Controllers nunca conversam entre si diretamente.

| Campo | Tipo | Uso |
|---|---|---|
| `openTasksValue` | `Label` | KPI de tarefas abertas no dashboard |
| `overdueTasksValue` | `Label` | KPI de tarefas atrasadas |
| `alertItems` | `ObservableList<String>` | Alertas de atraso (dashboard) |
| `upcomingItems` | `ObservableList<String>` | Próximos prazos (dashboard) |
| `todayTaskItems` | `ObservableList<String>` | **TDAH**: tarefas de hoje |
| `expiringProtocolItems` | `ObservableList<String>` | **TDAH**: protocolos periódicos |
| `tasksDueCountLabel` | `Label` | Contagem KPI de hoje |
| `protocolsExpiringCountLabel` | `Label` | Contagem KPI de protocolos |
| `taskCatNames` | `ObservableList<String>` | Categorias de tarefa (compartilhado entre Agenda e Config) |

### Callbacks de Refresh

```java
ctx.setDashboardRefreshCallback(() -> dashboardCtrl.refreshKpis(YearMonth.now()));
ctx.setAlertRefreshCallback(() -> { ... });
ctx.triggerDashboardRefresh(); // chama o callback registrado
```

---

## AppContext — Wiring de Dependências

`com.pessoal.agenda.app.AppContext`

Ponto central de construção de objetos. Controllers recebem dependências via construtor — nunca instanciam repositórios diretamente.

```java
AppContextHolder.get().taskService()             // TaskService
AppContextHolder.get().taskRepository()          // TaskRepository
AppContextHolder.get().taskSessionRepository()   // TaskSessionRepository
AppContextHolder.get().protocolRepository()      // ProtocolRepository
AppContextHolder.get().studyEntryRepository()    // StudyEntryRepository
AppContextHolder.get().categoryService()         // CategoryService
```

---

## Database — Migrações

`com.pessoal.agenda.infra.Database`

- `runMigrations()` executa todas as migrações na inicialização
- Migrações são **idempotentes** (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE` com try/catch)
- Nunca remover migrações existentes; sempre adicionar novas ao final

---

## PendencyNotificationService — Alertas TDAH

`com.pessoal.agenda.service.PendencyNotificationService`

Singleton que verifica pendências periodicamente:

```java
// Iniciar (geralmente em AgendaApp.start())
PendencyNotificationService.getInstance().start(
    5 * 60 * 1000,   // intervalo: 5 minutos
    () -> ctx.triggerDashboardRefresh()
);

// Sinalizar que há alertas (chamado por DashboardController após refresh)
PendencyNotificationService.getInstance().setHasAlerts(count > 0);

// Parar (ao fechar a aplicação)
PendencyNotificationService.getInstance().stop();
```

---

## Convenções de Código

| Regra | Detalhes |
|---|---|
| Repositório | Sem regra de negócio; só SQL |
| Service | Sem dependência de JavaFX |
| UI | Sem SQL direto |
| Cores em Java | Usar `setStyle("-fx-text-fill: -t-token;")` em vez de `#hex` |
| Diálogos | Sempre usar `Dialogs.*` (nunca `new Alert()`) para garantir `Modality.NONE` |
| Refresh após salvar | Sempre chamar `refreshCurrentView()` e `ctx.triggerDashboardRefresh()` |

