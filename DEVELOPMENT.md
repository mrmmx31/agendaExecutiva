# Guia de Desenvolvimento — Agenda Pessoal

> Documentação técnica das partes mecânicas do projeto.  
> Atualizar este arquivo junto com qualquer mudança estrutural.
>
> Requisitos de produto, UX, critérios de aceite e ordem de entrega estão em [`SPEC.md`](SPEC.md).

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
│   ├── DailyPlan.java / DailyPlanItem.java
│   ├── DailyPlanCapacity.java / DailyPlanRole.java
│   ├── InboxCapture.java / InboxCaptureKind.java
│   ├── QuickCaptureShortcut.java
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
│   ├── DailyPlanRepository.java
│   ├── InboxCaptureRepository.java
│   ├── TaskSessionRepository.java
│   ├── ProtocolRepository.java
│   ├── StudyEntryRepository.java
│   └── ...
│
├── service/                    — Regras de negócio, sem dependência de JavaFX
│   ├── TaskService.java
│   ├── DailyPlanService.java
│   ├── InboxCaptureService.java
│   ├── QuickCapturePreferences.java
│   ├── FocusSelectionService.java — Ordem determinística das origens do foco atual
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
    │   ├── DailyPlanPanel.java          — Estados, resumo e fluxo guiado do plano diário
    │   ├── QuickCaptureWindow.java      — Captura universal modeless com confirmação e recuperação de erro
    │   ├── QuickCaptureShortcutBinding.java — Registra e substitui o acelerador configurável da captura
    │   ├── InboxTriageWindow.java        — Lista e converte capturas pendentes de forma transacional
    │   ├── Dialogs.java                — Factory central de Alert/Dialog com owner e modalidade consistentes
    │   ├── ThemeManager.java           — Aplica/troca CSS de tema em tempo real
    │   ├── WindowManager.java          — Owner, modalidade, registro, geometria e tema de Stages
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
- `applyTo(Scene scene)` — registra fracamente a raiz e aplica o tema atual
- `setTheme(Theme theme)` — troca a folha e a classe `theme-dark` em **todas as cenas abertas**, força recálculo de CSS e relayout
- Temas disponíveis: `Theme.CLARO` (padrão) e `Theme.ESCURO`
- Hook global: `initGlobalWindowHook()` intercepta janelas novas e aplica tema automaticamente
- Controles novos devem usar tokens `-t-*`; fundos e textos de interface não devem ser fixados em hexadecimal inline

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
AppContextHolder.get().dayReviewService()        // DayReviewService
AppContextHolder.get().dayReviewRepository()     // DayReviewRepository
AppContextHolder.get().localMetricsService()     // LocalMetricsService
AppContextHolder.get().localMetricsRepository()  // LocalMetricsRepository
AppContextHolder.get().protocolRepository()      // ProtocolRepository
AppContextHolder.get().studyEntryRepository()    // StudyEntryRepository
AppContextHolder.get().categoryService()         // CategoryService
AppContextHolder.get().focusContextService()     // FocusContextService
```

## Encerramento do Plano Diário

- `DailyPlanService.findByDate()` representa somente o plano ativo: tarefas concluídas ou removidas podem ser omitidas dessa visão, sem reescrever o registro persistido.
- `DayReviewService.summary()` é a fonte para revisão histórica e combina o plano original com tarefas e sessões do dia.
- Encerrar é idempotente: repetir a operação não altera `closed_at` nem duplica dados.
- `DayReviewRepository.applyAndClose()` executa decisões, captura devolvida, plano de amanhã e fechamento na mesma transação.
- A tarefa inicial de amanhã deve ter decisão `TOMORROW`; é opcional e nunca substitui silenciosamente outro plano diário.
- `RETURN_TO_INBOX` cria captura não classificada com título e notas antes de cancelar a tarefa estruturada.
- Reabrir limpa somente `closed_at` e a nota de fechamento; itens do plano, tarefas e sessões permanecem intactos.
- Erros de persistência na janela de revisão devem manter nota e decisões e oferecer nova tentativa.

## Revisão de Tarefas Vencidas

- `OverdueAgeBand.fromPendingDays()` é a fonte dos limites: `1–7`, `8–30` e `31+` dias.
- A vista `Revisar` deve manter todas as tarefas vencidas nas faixas, sem corte silencioso por quantidade.
- Metadados usam `pendente há N dias`; não introduzir linguagem culpabilizante ou de urgência sem prazo/prioridade que a justifique.
- Tarefas vencidas normais não entram na seleção automática do `Agora`; escolha manual e plano diário continuam válidos.
- Prioridade `CRITICA` é a única exceção automática para tarefas vencidas.
- O painel geral de pendências repete somente tarefas com até sete dias ou prioridade crítica.

## Métricas Locais

- `LocalMetricsService.isEnabled()` é `false` por padrão; todo ponto de coleta deve consultar o serviço, nunca a preferência diretamente.
- `local_metric_events` armazena apenas `metric_type`, valor inteiro e horário, sem texto, tarefa, sessão identificável ou destino remoto.
- Tipos atuais: `FOCUS_START_SECONDS`, `QUICK_CAPTURE_ACTIONS` e `INTERRUPTION_RESUME_ACTIONS`.
- Foco registra somente a primeira abertura/inicialização bem-sucedida por sessão da aplicação.
- Captura e retomada contam tentativas válidas até o sucesso; falhas não geram evento isolado.
- O snapshot usa no máximo os 30 registros mais recentes e o repositório retém até 200 eventos por tipo.
- Desativar interrompe imediatamente a coleta e oculta o painel, mas não apaga dados; `clear()` é a operação explícita de exclusão.

---

## Database — Migrações

`com.pessoal.agenda.infra.Database`

- `runMigrations()` executa todas as migrações na inicialização
- Migrações são **idempotentes** (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE` com try/catch)
- Nunca remover migrações existentes; sempre adicionar novas ao final
- `focus_context` guarda no máximo uma pista corrente; substituição e remoção passam por `FocusContextService`

---

## Interrupção do Timer

- `Fui interrompido` pausa o timer antes de abrir a captura da pista.
- Salvar mantém a tarefa ativa e pausada; não encerra nem contabiliza a sessão.
- Cancelar retoma apenas quando o timer estava rodando antes da captura.
- Erro ou texto vazio mantém o diálogo aberto e preserva integralmente o campo.
- Diálogos devem passar por `WindowManager.prepare()`, que aplica owner, modalidade, maximização e tema diretamente ao `DialogPane`.
- Uma pista válida assume temporariamente o bloco `Agora`; enquanto ela existir, mostrar apenas `Retomar` e `Abrir tarefa`.
- `Retomar` remove a pista somente depois de iniciar o timer e abrir sua janela com sucesso.
- `TaskTimerRecoveryService` grava o primeiro segundo, depois a cada cinco segundos, em mudanças de estado e no fechamento normal.
- O rastreamento só começa depois de resolver um checkpoint anterior; nunca sobrescrever uma recuperação pendente silenciosamente.
- Recuperar sempre restaura o valor persistido com timer pausado; não calcular tempo de parede enquanto a aplicação esteve fechada.
- Parar ou descartar limpa `timer_recovery`; nenhuma dessas ações cria sessão automaticamente.

---

## PendencyNotificationService — Alertas TDAH

`com.pessoal.agenda.service.PendencyNotificationService`

Singleton que verifica pendências periodicamente:

- Preferências persistentes: ativação, som, animação do badge e intervalo.
- Intervalos aceitos: 5, 15, 30 ou 60 minutos; padrão de 15 minutos.
- Som e animação ficam desativados por padrão.
- A primeira verificação ocorre somente após o intervalo, não durante a inicialização.
- Quando o som está habilitado, tenta tocar `src/main/resources/sounds/reminder.wav` e usa beep como fallback.
- `snoozeForMinutes(30)` pausa estímulos sem esconder as contagens.
- O controle geral cancela o timer e interrompe a saída sonora imediatamente, sem apagar as preferências de som, animação ou intervalo.
- Toda saída sonora, inclusive o beep de fallback, revalida controle geral, som, pausa e horário silencioso antes de executar.
- Um clip ativo nunca é reiniciado ou sobreposto; uma nova tentativa retorna `ALREADY_PLAYING`.
- Horário silencioso é opt-in, usa início inclusivo e fim exclusivo e aceita faixas que atravessam meia-noite, como `22:00` até `07:00`.
- `testSound()` é a única prévia deliberada do áudio e usa as mesmas barreiras dos lembretes reais.
- Testes devem usar o construtor de pacote com `Preferences` e `SoundOutput` isolados; nunca usar preferências pessoais nem áudio real.

```java
// Iniciar (geralmente em AgendaApp.start())
PendencyNotificationService notifications = PendencyNotificationService.getInstance();
notifications.start(() -> {
    refreshAlertsAndUpcoming();
    refreshDashboardKpis();
    updateCriticalBadge();
});

// Sinalizar que há alertas (chamado por DashboardController após refresh)
notifications.setHasAlerts(count > 0);

// Preferências alteradas pela aba Configurações
notifications.setEnabled(true);
notifications.setIntervalMinutes(15);
notifications.setSoundEnabled(false);
notifications.setBadgeAnimationEnabled(false);

// Parar (ao fechar a aplicação)
notifications.stop();
```

### Barra de status — badge crítico

- Label secundário na barra de status: `statusAlertBadge`
- Estados visuais:
  - `status-alert-ok` (sem pendências críticas)
  - `status-alert-critical` (com pendências críticas)
- O badge permanece estático por padrão.
- A animação só é executada quando o controle geral e a preferência correspondente estão habilitados e não há pausa ativa.
- `StatusAlertAnimator` executa exatamente três ciclos e restaura opacidade integral ao terminar ou ser interrompido.
- O menu do badge permite lembrar agora, pausar por 30 minutos e retomar.

### Atalho de lembrete manual

- Atalho global: `Ctrl+Shift+R` (Linux/Windows) / `Cmd+Shift+R` (macOS).
- `Ctrl/Cmd+S` não é usado para evitar disparos pelo hábito de salvar.
- Implementação via `KeyCodeCombination(KeyCode.R, SHORTCUT_DOWN, SHIFT_DOWN)`.
- O binding fica isolado em `ReminderShortcutBinding` para impedir regressão para `Ctrl/Cmd+S`.
- Ação:
  1. Atualiza alertas e KPIs
  2. Força `PendencyNotificationService.forceCheck()`
  3. Exibe status textual de lembrete manual

---

## WindowManager — Janelas Secundárias

`com.pessoal.agenda.ui.view.WindowManager`

- Inicializado em `AgendaApp.start(stage)` com a janela principal.
- `createModelessStage()` cria monitores e ferramentas não bloqueantes.
- `createModalStage()` cria formulários `WINDOW_MODAL` com owner ativo.
- `prepare(dialog)` aplica owner e modalidade a diálogos.
- `show(stage)` registra, aplica o tema, limita à área útil do monitor, centraliza sobre o owner e preserva a maximização da principal.
- `prepare(dialog)` também revalida a maximização após o fechamento. No KWin/Wayland, a superfície pode perder o tamanho maximizado sem alterar `Stage.isMaximized()`; não remover a transição nativa controlada nem o rastreamento dos últimos limites restaurados.
- `preservePlacement(stage)` é reservado para janelas arrastáveis, como o timer compacto.
- `closeAll()` fecha um snapshot das secundárias no encerramento.
- `WindowPlacementCalculator` contém o cálculo puro e testável de tamanho, mínimos e posição; não adicionar regras geométricas diretamente ao `Stage`.

Toda janela secundária deve ser criada e exibida por essa API. Não usar `new Stage()` ou `stage.show()` diretamente em controllers/views de produto.

---

## Sincronização Google Tasks

- `GoogleTasksService` implementa `GoogleTasksGateway` e contém somente autenticação, HTTP e conversão dos recursos Google.
- `GoogleTasksSyncService` calcula o plano e aplica importações, exportações, mudanças de status e atualizações.
- `GoogleTasksSyncRepository` é o único caminho para importar tarefa Google: tarefa local e mapeamento usam a mesma transação SQLite.
- Identidade de sincronização é sempre `google_list_id + google_task_id`; título nunca identifica uma tarefa.
- Importação repetida retorna o mapeamento existente sem criar tarefa.
- Exportação repetida consulta o mapeamento antes da API; falha ao mapear uma criação remota dispara exclusão compensatória.
- O mapeamento guarda título, notas, data e status da última sincronização. Mudança unilateral é propagada; divergência concorrente no mesmo campo recebe estado `CONFLICT`.
- Exclusão local ou remota nunca apaga automaticamente o outro lado. O mapeamento recebe `LOCAL_DELETED` ou `REMOTE_DELETED` e aguarda revisão.
- Tarefas concluídas podem ser reabertas em qualquer lado; a mudança é propagada quando somente um lado divergiu da fotografia.
- `GoogleTasksService` percorre `nextPageToken` em listas e tarefas e solicita tombstones com `showDeleted=true`.
- Transporte Tasks usa timeout de conexão de 10 segundos e de requisição de 20 segundos. Corpos de erro da API nunca entram em exceções, logs ou diálogos.
- `GET`, `PATCH` e `DELETE` podem repetir uma vez após falha transitória. `POST` de criação não repete após resultado ambíguo; qualquer método pode repetir uma vez após `401`, depois de invalidar o access token.
- Falhas são classificadas por `GoogleSyncException`: autenticação, limite, timeout, rede, servidor, resposta inválida, configuração ou requisição.
- Resposta truncada, item sem ID e ciclo de `nextPageToken` abortam a leitura antes do planejamento, preservando banco e mapeamentos.
- OAuth valida status antes de interpretar JSON. Falha de renovação por token revogado remove os tokens locais; falha transitória os preserva para nova tentativa.
- Credenciais e tokens recebem permissão POSIX `600` ao carregar ou salvar. Nunca incluir seus conteúdos em testes, mensagens ou logs.
- `prepareSync()` lê os dois lados e produz `PreparedSync` com `SyncPreview`; preparar nunca altera dados. `applyPrepared()` revalida ambos os lados, aceita a prévia uma única vez e rejeita estado vencido.
- `GoogleOperationGuard` é global entre instâncias da janela. Conexão, carregamento, sync, importação, exportação, duplicatas e resolução não podem executar em paralelo.
- Mapeamentos `CONFLICT`, `LOCAL_DELETED` e `REMOTE_DELETED` aparecem em `Revisar pendências`. Cada resolução exige escolher `USE_LOCAL` ou `USE_GOOGLE` e informa a consequência concreta antes de aplicar.
- Testes usam `GoogleTasksGateway` falso e banco temporário. Não usar tokens, credenciais ou conta pessoal em testes automatizados.
- A validação automatizada cobre transporte/gateway simulados e SQLite temporário. Teste ao vivo só pode usar lista descartável, backup e confirmação explícita do usuário.

---

## Convenções de Código

| Regra | Detalhes |
|---|---|
| Repositório | Sem regra de negócio; só SQL |
| Service | Sem dependência de JavaFX |
| UI | Sem SQL direto |
| Cores em Java | Usar `setStyle("-fx-text-fill: -t-token;")` em vez de `#hex` |
| Janelas | Sempre usar `WindowManager`; não criar/exibir `Stage` diretamente |
| Diálogos | Usar `Dialogs.*` ou `Dialogs.prepare(dialog)` para garantir owner e modalidade |
| Refresh após salvar | Sempre chamar `refreshCurrentView()` e `ctx.triggerDashboardRefresh()` |

---

## Toolchain e Warnings Conhecidos

- O projeto compila com `maven.compiler.release=21`; não voltar a combinar `source` e `target` separadamente.
- O Surefire libera acesso nativo apenas para `org.xerial.sqlitejdbc`, necessário aos testes de persistência.
- O `javafx-maven-plugin` recebe opções de VM pelo elemento `options`; `jvmArgs` não é um parâmetro suportado pela versão 0.0.8.
- O Maven Wrapper atual é 3.8.5. Quando o próprio Maven é iniciado com JDK 24, o Guava empacotado pelo Maven pode emitir aviso sobre `sun.misc.Unsafe::objectFieldOffset`. Esse aviso ocorre antes do build e não é produzido pelo código da aplicação.
- Avisos de `sun.misc.Unsafe::allocateMemory` ao executar JavaFX vêm da implementação Marlin/OpenJFX utilizada, não de chamadas diretas do projeto.
- A execução do Diário usa WebKit; manter `javafx.web` na lista de módulos com acesso nativo do plugin e da configuração do IntelliJ.
- A pré-visualização mantém o HTML com aparência de papel, inclusive no tema escuro. O diálogo de impressão é nativo do sistema e sua aparência/escala depende do ambiente e do driver instalado.
- Warnings originados no código-fonte do projeto devem ser corrigidos; não adicionar supressão global para esconder warnings de dependências.

### Testes JavaFX

A suíte padrão não inicia o toolkit e pode rodar sem servidor gráfico:

```bash
./mvnw test
```

Os contratos reais de `Stage`, `Dialog`, owner, modalidade, tema e layouts responsivos usam a tag `javafx-ui` e um perfil separado:

```bash
./mvnw -Pjavafx-ui-tests test
```

Esse perfil requer uma sessão gráfica funcional (`DISPLAY`/Wayland com compatibilidade X11). O cálculo de geometria permanece coberto na suíte padrão por `WindowPlacementCalculatorTest`.

Para a matriz manual, use o perfil `manual-ui-validation` descrito em [`UI_VALIDATION.md`](UI_VALIDATION.md). Ele direciona banco, preferências e credenciais para um `user.home` isolado escolhido na execução; não valide a interface com dados pessoais.
