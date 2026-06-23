# Changelog — Agenda Pessoal

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).  
Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não lançado]

### Adicionado
- `PendencyNotificationService`: serviço background que verifica pendências a cada 5 minutos e emite beep do sistema
- Dashboard: cards "📋 Tarefas de HOJE" e "⚠️ Protocolos Vencendo" com destaque visual para apoio a TDAH
- `SharedContext`: novos campos `todayTaskItems`, `expiringProtocolItems`, `tasksDueCountLabel`, `protocolsExpiringCountLabel`
- `DashboardController`: métodos `updateTodayTasks()` e `updateExpiringProtocols()` chamados a cada refresh

### Corrigido
- `AgendaTabController.submitForm()`: adicionado `refreshCurrentView()` e `triggerDashboardRefresh()` após salvar tarefa — lista agora atualiza imediatamente sem trocar de aba
- `theme-dark.css`: cobertura completa do popup do `DatePicker` em modo escuro (botões de navegação de mês, labels, células de dias, dias adjacentes, hoje e selecionado)
- `app.css` / `theme-dark.css`: seleção de linhas em `ListView` e `TableView` agora visível em linhas pares e ímpares (`:filled:selected:odd` / `:filled:selected:even`)

---

## [1.1.0] — 2026-05-13

### Corrigido
- SSL `handshake_failure` no Windows: forçado TLS 1.2/1.3 em `GoogleAuthService` e `GoogleTasksService`

---

## [1.0.0] — Inicial

### Adicionado
- Agenda com tarefas do dia, filtro por mês e marcação de concluídas
- Alertas de atrasos (tarefas vencidas e pagamentos vencidos)
- Checklists de protocolos e ações (Protocolos Operacionais)
- Financeiro (orçamentos, pendências, lançamentos)
- Vendas pessoais e controle básico de estoque
- Frequência de estudos e atividades
- Banco de ideias para projetos pessoais
- Temas claro e escuro com token CSS globais (`-t-*`)
- Dashboard com KPIs consolidados
- Exportação iCalendar (.ics) para Google Calendar
- Integração Google Tasks
- Recorrência de tarefas: `SINGLE`, `RANGE`, `WEEKLY`
- Sistema de timer de sessões por tarefa
- Impressão de relatórios

