# Changelog — Agenda Pessoal

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).  
Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não lançado]

### Adicionado
- `PendencyNotificationService`: serviço background que verifica pendências a cada 5 minutos e toca `sounds/reminder.wav` (com fallback para beep)
- Dashboard: cards "📋 Tarefas de HOJE" e "⚠️ Protocolos Vencendo" com destaque visual para apoio a TDAH
- `SharedContext`: novos campos `todayTaskItems`, `expiringProtocolItems`, `tasksDueCountLabel`, `protocolsExpiringCountLabel`
- `DashboardController`: métodos `updateTodayTasks()` e `updateExpiringProtocols()` chamados a cada refresh
- `AgendaApp`: atalho global `Ctrl/Cmd+S` para "lembrar-me agora" (força check imediato de pendências)
- `AgendaApp`: atalho alternativo `Ctrl/Cmd+Shift+S` para evitar conflito com hábito de salvar
- Barra de status: badge de alerta com animação piscante quando há pendências críticas
- Novo recurso de áudio: `src/main/resources/sounds/reminder.wav`
- Popover no clique do badge da barra de status com breakdown `A/H/P` e ação rápida "Lembrar agora"

### Corrigido
- `AgendaTabController.submitForm()`: adicionado `refreshCurrentView()` e `triggerDashboardRefresh()` após salvar tarefa — lista agora atualiza imediatamente sem trocar de aba
- `theme-dark.css`: cobertura completa do popup do `DatePicker` em modo escuro (botões de navegação de mês, labels, células de dias, dias adjacentes, hoje e selecionado)
- `app.css` / `theme-dark.css`: seleção de linhas em `ListView` e `TableView` agora visível em linhas pares e ímpares (`:filled:selected:odd` / `:filled:selected:even`)
- Badge da barra de status agora usa a mesma base visível da dashboard (Atrasos + Hoje + Protocolos) e mostra breakdown `A/H/P`
- Tooltip no badge de status detalha o significado de `A/H/P` e mostra os totais atuais
- `Agenda e Prioridades`: painel de formulário agora é preview-first; seleção da lista carrega pré-visualização read-only, edição só via botão `Editar selecionada`, `Esc` cancela edição/criação, e ação primária alterna entre `Nova tarefa` e `+ Adicionar tarefa`

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





