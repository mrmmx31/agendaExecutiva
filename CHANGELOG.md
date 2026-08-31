# Changelog — Agenda Pessoal

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).  
Versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não lançado]

Esta seção acompanha a implementação definida em [`SPEC.md`](SPEC.md).

### Adicionado
- Contrato de foco como âncora não exclusiva: atividades paralelas não pausam sessões nem são classificadas automaticamente como distração
- Scaffold Android do Projeto 2 com Kotlin/Compose, Room técnico com schema exportado, testes, lint e APK validados
- AVDs próprios Android 14 com Play Store e Wear OS 5, mantendo dispositivo físico fora dos testes da P2-01
- Pareamento validado entre os AVDs de telefone e Wear OS pelo assistente do Android Studio, concluindo a P2-01
- Especificação do Projeto 2 para Android/Wear OS, alertas sensoriais, sync offline, áudio, protocolos móveis, Health Connect, relatório e IA evolutiva
- Mapa de manutenção com fontes de verdade, catálogo de componentes/permissões, contratos, ADRs, model cards, receitas e quality gates
- Encerramento rastreável do piloto com cinco decisões `SEM EVIDÊNCIA`, sem inferir validação a partir de ausência de uso
- Piloto de uso real com protocolo de baixa carga e critérios explícitos para as cinco hipóteses restantes
- Fase 0 concluída com baseline humano inicial de foco, captura e retomada, além da referência técnica de janelas
- Baseline: referência técnica atual de janelas e protocolo de até cinco amostras humanas para foco, captura e retomada
- Validação manual: perfil Maven com dados/preferências isolados e checklist reproduzível de ambiente, janelas e evidências
- Documentação: checklist percentual separado para o fechamento residual da Fase 0, sem alterar os 100% das fases funcionais
- Encerramento e revisão P5-04: métricas locais opt-in para tempo até o foco, ações da captura e ações da retomada
- Configurações: ativação persistente e limpeza confirmada do histórico local, sem coleta enquanto desativado
- Dashboard: medianas dos últimos 30 registros, painel invisível por padrão e retenção limitada a 200 eventos numéricos por tipo
- Fase 5 concluída com planejamento, foco, interrupção, retomada, encerramento, preparação de amanhã e revisão neutra
- Encerramento e revisão P5-03: tarefas vencidas agrupadas em `Até 7 dias`, `8–30 dias` e `Mais de 30 dias`, com contagens visíveis
- Revisão por período com linguagem descritiva, acesso direto às tarefas e cobertura de largura reduzida e tema escuro
- Foco automático exclui tarefas vencidas normais e mantém somente a exceção de prioridade crítica
- Encerramento e revisão P5-02: decisões `Amanhã`, `Manter data`, `Voltar à caixa de entrada` e `Concluir` para cada item aberto
- Preparação opcional de uma tarefa inicial para amanhã, sem sobrescrever um plano diferente já existente
- Aplicação transacional das decisões, criação de captura, plano de amanhã e fechamento, com rollback integral em conflitos
- Encerramento e revisão P5-01: resumo do plano original, tarefas concluídas, sessões registradas e itens ainda abertos
- Dashboard: ações `Encerrar meu dia` e `Revisar encerramento`, com nota opcional, fechamento persistente e reabertura do mesmo dia
- Preservação histórica do plano diário, mantendo tarefas concluídas no registro original sem recolocá-las no foco ativo
- Interrupção e retomada P4-04: checkpoints persistentes do timer e decisão obrigatória entre recuperar pausado ou descartar
- Recuperação restaura somente segundos efetivamente contados, sem somar o período em que a aplicação permaneceu fechada
- Fase 4 concluída com captura da interrupção, pista no Dashboard e recuperação controlada após encerramento
- Interrupção e retomada P4-03: pista restaurada no bloco `Agora`, com tarefa, próximo passo e ação direta `Retomar`
- Dashboard reduz o estado de retomada a duas ações e só remove a pista após iniciar o timer e abrir sua janela
- Interrupção e retomada P4-02: ação `Fui interrompido` nos timers normal e compacto, com pista persistente e retry sem perda do texto
- Interrupção pausa o contador durante a captura, mantém a tarefa pausada ao salvar e restaura a execução anterior ao cancelar
- Interrupção e retomada P4-01: contexto persistente único, vinculado a uma tarefa aberta e preservado entre execuções
- Serviço de pista de retomada com substituição atômica e rollback, descarte ao retomar e integridade referencial com tarefas
- Google Tasks GSYNC-04: prévia confirmável, aplicação única com revalidação e rejeição de prévia vencida
- Revisão assistida para escolher versão local/Google em conflitos e aceitar, restaurar ou recriar exclusões conscientemente
- Bloqueio global de operações Google concorrentes, inclusive após fechar e reabrir a janela
- Google Tasks GSYNC-03: timeouts, transporte injetável, erros classificados e mensagens de recuperação sem corpos ou tokens da API
- Repetição segura para leituras e mutações idempotentes, renovação única após `401` e contagem de itens processados no resumo
- Proteção POSIX `600` para credenciais e tokens Google, incluindo migração durante carregamento
- Google Tasks GSYNC-02: paginação completa de listas e tarefas, snapshots de último estado e estados explícitos de revisão para conflito e exclusão
- Sincronização bidirecional conservadora para edição unilateral, conclusão e reabertura, com cobertura de 150 tarefas sem acesso à rede
- Google Tasks GSYNC-01: gateway de transporte, serviço de planejamento/aplicação e repositório transacional para importação com identidade estável
- Testes simulados de sincronização para repetição, títulos iguais, importação manual, atualização única, rollback e compensação remota
- Auditoria de alertas ALT-03: animador de três ciclos, binding isolado do atalho e contratos JavaFX das configurações com preferências temporárias
- Spec da auditoria Google Tasks com requisitos de idempotência, atomicidade, paginação, conflitos, recuperação e testes simulados
- Auditoria de alertas ALT-02: horário silencioso persistente, seletores em intervalos de 30 minutos e teste explícito de som nas Configurações
- Testes com relógio e áudio simulados para inicialização calma, pausa, faixas diurnas/noturnas, sobreposição e retorno do teste sonoro
- Auditoria de alertas ALT-01: testes isolados para controle geral, timer, saída sonora, preferências preservadas e permissão de animação
- Captura universal: tabela e índice idempotentes, modelo, repository e service para guardar texto livre como não classificado sem alterar seu conteúdo
- `AppContext`: composição do núcleo da caixa de entrada universal, com listagem recente e contagem de capturas pendentes
- `QuickCaptureWindow`: campo único com salvamento por `Enter`, nova linha por `Shift+Enter`, descarte confirmado por `Esc` e nova tentativa sem perda do texto
- Captura universal: confirmação textual breve e silenciosa, janela modeless responsiva e cobertura de tema escuro
- Captura universal: ação `Capturar` no cabeçalho comum a todas as abas, sem alterar a aba ou a geometria principal
- Captura universal: atalho persistente e configurável, com padrão `Ctrl/Cmd+Shift+Espaço`, alternativas, desativação e restauração do padrão
- Caixa de entrada universal: contagem no cabeçalho e janela de triagem para tarefa, ideia, nota de interrupção ou arquivo
- Triagem: criação transacional de tarefa e ideia com vínculo `target_id`, texto integral preservado e rollback sem duplicação em caso de falha
- Captura universal e Ideias: itens classificados aparecem na revisão especializada sem migrar ou duplicar ideias anteriores
- Fase 3 da spec concluída com captura global, atalho configurável, triagem transacional e compatibilidade de dados validada
- Plano diário: migração idempotente, modelos, repository transacional e service para uma tarefa essencial e até duas de apoio
- Plano diário: capacidade reduzida no domínio, integridade referencial e invalidação segura quando a tarefa essencial é removida
- Dashboard: bloco `Meu dia` na vista Hoje, com entrada `Começar meu dia`, restauração do plano persistido e estados de carregamento, vazio e erro
- `DailyPlanPanel`: componente testável e responsivo para resumir tarefa essencial, apoios e capacidade sem abrir modal automaticamente
- Plano diário: fluxo embutido de três etapas para revisar o dia, escolher essencial e apoios e confirmar a primeira ação
- Plano diário: edição restaura as escolhas existentes e permite limpar ou reordenar apoios sem modificar as tarefas originais
- Dashboard: tarefa essencial do plano integrada ao bloco `Agora` depois de timer ativo e escolha manual, com origem `Plano de hoje`
- Dashboard: `Iniciar foco` inicia diretamente o contador; timers pausados são retomados e mudanças do timer atualizam o foco exibido
- `FocusSelectionService`: precedência determinística entre timer, escolha manual, plano diário e sugestão automática
- Plano diário: seletor de `Capacidade reduzida` limita o dia à tarefa essencial e remove apoios da seleção
- Dashboard: capacidade reduzida suaviza a decoração de estudos, pendências antigas e indicadores secundários sem esconder conteúdo ou reduzir contraste
- Fase 2 da spec concluída com o ciclo completo entre planejamento, restauração, edição, foco e timer
- Fase 1 da spec concluída, com matriz visual, contratos JavaFX e validação nativa no KDE/KWin Wayland
- `PendencyNotificationService`: serviço em background com intervalos configuráveis de 5, 15, 30 ou 60 minutos, som opcional e pausa temporária
- Dashboard: cards "📋 Tarefas de HOJE" e "⚠️ Protocolos Vencendo" com destaque visual para apoio a TDAH
- `SharedContext`: novos campos `todayTaskItems`, `expiringProtocolItems`, `tasksDueCountLabel`, `protocolsExpiringCountLabel`
- `DashboardController`: métodos `updateTodayTasks()` e `updateExpiringProtocols()` chamados a cada refresh
- `AgendaApp`: atalho global `Ctrl/Cmd+Shift+R` para forçar uma verificação manual sem conflitar com o hábito de salvar
- Barra de status: badge de pendências estático por padrão, com animação opcional e menu para pausar ou retomar lembretes
- Novo recurso de áudio: `src/main/resources/sounds/reminder.wav`
- Popover no clique do badge da barra de status com breakdown `A/H/P` e ação rápida "Lembrar agora"
- Configurações: controles persistentes para ativação, som, animação e intervalo dos lembretes
- `WindowManager`: owner, modalidade, registro, tema, centralização, limite à área útil e preservação da maximização para janelas secundárias
- Dashboard: bloco `Agora` com foco automático determinístico ou escolha manual persistente e acesso direto ao timer
- Dashboard: vistas internas `Hoje`, `Organizar` e `Revisar`, com indicadores gerais recolhidos por padrão
- Testes JUnit para vínculo de sessões por tarefa, consultas de histórico e formatação CSV
- `Agenda e Prioridades`: novo modo de `captura rápida` no formulário de criação (`Nova tarefa`) para registrar tarefa com menos campos (título/data/notas), preenchendo categoria/prioridade/status com padrões seguros
- `Agenda e Prioridades`: destaque visual sutil para estado ativo da `captura rápida`, facilitando identificação do modo simplificado
- `Agenda e Prioridades`: tarefas agora podem ter um `protocolo` associado opcionalmente no formulário completo, sem impactar a `captura rápida`
- `Protocolos Operacionais`: ação rápida `🏠 Protocolo saída de casa` que cria/abre template padrão com itens essenciais (carteira, chave, celular, carregador e complementares)
- `Protocolos Operacionais`: ação rápida `🧳 Protocolo reunião` com template padrão para saídas externas (relatório, documentos, notebook, carregador e itens essenciais)
- `Protocolos Operacionais`: botão `▶ Iniciar selecionado` na toolbar e botão `▶` por linha da lista para abrir execução sem depender de duplo clique
- `Dashboard`: novo card `🏠 Protocolos mais recorrentes` com atalho `Iniciar` direto para protocolos críticos do dia a dia
- `Dashboard`: novo card `⏰ Protocolos de agora` para rotinas imediatamente acionáveis (saída, reunião, remédios, protocolos ligados a tarefa de hoje ou já em execução)
- `Dashboard`: tarefas em destaque e foco principal agora mostram também quando a tarefa possui `🔗 protocolo` associado
- Editor de protocolo: passos agora podem ser reordenados também por arrastar e soltar (drag-and-drop)
- `Dashboard`: tarefas em destaque agora priorizam itens mais recentes e prioritários; pendências muito antigas vão para a seção de revisão
- `Dashboard`: duplo clique e ação principal agora podem abrir a `Agenda` já na data exata da tarefa, reduzindo busca manual por itens antigos
- `Dashboard`: pendências antigas prioritárias permanecem disponíveis na revisão sem alternar imprevisivelmente o foco principal
- `Dashboard`: o bloco principal foi consolidado como `Agora`, mantendo uma única tarefa acionável
- `DatabaseService.listDeadlineAlerts()`: alertas de tarefas atrasadas agora priorizam prioridade e recência, evitando que itens muito antigos dominem a lista principal
- `Agenda e Prioridades`: itens da lista (Dia/Semana/Mês) agora exibem ação direta `🔗` para abrir o protocolo associado da tarefa, além da opção no menu de contexto
- `Protocolos Operacionais`: nova ação `⏰ Protocolos por horário` cria/abre templates de rotina temporal (`Remédio 08:00`, `Remédio 20:00`, `Preparar saída 30 min antes`, `Reunião 1h antes`)
- `Dashboard`: o card `⏰ Protocolos de agora` agora considera gatilhos de horário apenas para protocolos da categoria `Horários` (horário fixo e antecedência "antes")
- Categorias padrão de `Protocolos` expandidas com foco em rotina real: `Horários`, `Rotina diária`, `Saídas e reuniões` e `Medicamentos`
- `Protocolos Operacionais`: formulário ganhou configuração explícita de gatilho por horário (modo, `HH:mm` fixo ou minutos de antecedência), exibida apenas para categoria `Horários`
- `Estudos e Atividades`: novos atalhos `⏸ Pausar` e `▶ Retomar` no painel da lista para mudar status do plano sem entrar em edição completa
- `Dashboard`: novo card `🧠 Captura rápida de ideias` para despejar anotações no impulso e revisar depois sem interromper a atividade atual
- `Ideias e Projetos`: novas capturas podem ir para a categoria `Caixa de entrada`, com revisão posterior por prioridade e duplo clique direto a partir da dashboard
- `Ideias e Projetos`: suporte a hierarquia leve entre anotações/ideias via vínculo `Pertence a`, permitindo relacionar notas filhas a uma ideia-mãe durante a organização
- `Ideias e Projetos`: nova janela `🗂 Revisão da caixa de entrada` com ações rápidas para priorizar, vincular, transformar em projeto, abrir checklist, virar tarefa de hoje e arquivar capturas
- Categorias padrão de `Ideias` agora incluem `Arquivo`, facilitando tirar itens já triados do fluxo ativo sem apagá-los
- `Estudos e Atividades`: frequência agora consciente de pausas — dias em que o plano estava `PAUSADO` aparecem como ⏸ no calendário e não entram no cálculo de faltas
- `Estudos e Atividades`: frequência calculada a partir da data em que o plano ficou ativo pela primeira vez (`EM_ANDAMENTO`), não da data de criação; planos apenas planejados não acumulam faltas
- `Estudos e Atividades`: novo mecanismo de abono de faltas (`ABONADO`) — botão `🎟 Abonar` por falta individual e `🎟 Abonar todas` para justificar em lote sem necessidade de reposição
- `Dashboard`: novo card `📚 Estudos do dia` — exibe estudos com frequência programada para o dia de hoje e permite abrir o Diário Científico com um clique
- `Dashboard`: botão `▶ Abrir Diário` por item do card de estudos e duplo clique abre o diário diretamente da dashboard

### Corrigido
- Empacotamento: backup `app.css.bak` removido do JAR e quality gate executado com build limpo
- Documentação: fases, horário silencioso, decisões e hipóteses de uso real sincronizados com o estado concluído
- Agenda: parar o timer embutido volta a localizar a tarefa e salva a sessão no mesmo banco configurado
- Execução de protocolos: etapas abertas não mantêm mais fundo branco no tema escuro e ícones de histórico usam classes temáticas válidas
- Textos auxiliares do Dashboard agora usam classe CSS temática, evitando tokens não resolvidos durante a montagem da cena
- Diálogos preparados por `WindowManager` agora recebem o tema diretamente, evitando cabeçalhos com texto escuro quando abertos no modo escuro
- Sincronização não aplica mais um plano que mudou enquanto a prévia estava aberta
- Fechar e reabrir a janela Google não permite iniciar uma segunda operação remota em paralelo
- Conflitos e exclusões preservados agora possuem resolução explícita sem edição manual do banco
- OAuth agora valida status HTTP antes de interpretar tokens e remove autorização revogada sem apagar tokens em falhas transitórias
- Respostas Google truncadas, itens sem identidade e paginação cíclica deixam de produzir decisões de sincronização
- Logs e diálogos Google não exibem mensagens brutas que possam conter token, corpo da API ou detalhes sensíveis
- Google Tasks não limita mais a leitura aos primeiros 100 itens nem sobrescreve alterações concorrentes silenciosamente
- Exclusões locais ou remotas preservam o outro lado e o resumo da sincronização informa quantos itens exigem revisão
- Importação manual e automática do Google Tasks agora grava mapeamento sem redescobrir tarefas por título ou duplicar na repetição
- Sincronização Google só atualiza texto/data quando há diferença e contabiliza essas atualizações no resumo
- Falha ao mapear uma exportação remove a criação remota sem vínculo, permitindo nova tentativa limpa
- Indicador de pendências não anima mais indefinidamente e sempre retorna à opacidade integral após três ciclos ou interrupção
- Áudio de lembretes não reinicia nem se sobrepõe e agora respeita pausa e horário silencioso também em disparos manuais, testes e fallback
- Tema escuro não exibe mais a marca de seleção em checkboxes desmarcados após a troca de tema
- Controle geral de lembretes agora cancela o timer, interrompe áudio e animação, bloqueia disparos manuais e fallback de beep e mantém as contagens estáticas visíveis
- Plano diário remove apoios concluídos e invalida com segurança um plano cuja tarefa essencial foi concluída após o salvamento
- Build Maven agora compila com `--release 21`; testes SQLite recebem acesso nativo restrito ao módulo necessário
- Opções de acesso nativo da execução JavaFX agora usam o parâmetro `options` suportado pelo plugin 0.0.8
- Uso genérico inseguro na configuração das colunas do histórico de sessões foi removido
- Geometria das janelas foi extraída para cálculo puro com testes de limites, centralização, mínimos e posição preservada
- Perfil Maven `javafx-ui-tests` adicionado para validar owner, modalidade, CSS e posição usando o toolkit JavaFX real
- Janelas operacionais de protocolo, checklists e Google Tasks agora quebram barras de ações e textos extensos sem ocultar comandos em larguras menores
- Troca de tema em janelas abertas voltou a ser confiável; o registro fraco não depende mais do `hashCode` mutável da lista de stylesheets
- Troca claro/escuro agora invalida explicitamente o CSS de cada raiz, evitando resíduos visuais em abas já construídas
- Controles JavaFX sem classe específica agora herdam texto, fundo, borda e seleção dos tokens do tema, em vez das cores pretas do Modena
- Checklists, badges de Ideias/Vendas e o editor HTML do Diário deixaram de forçar fundos claros ou texto escuro no modo escuro
- Janelas de conhecimento agora mantêm títulos, KPIs, metadados e ações acessíveis no tamanho mínimo, com rolagem vertical onde o conteúdo é extenso
- Histórico de sessões, timer normal/compacto e pré-visualização de impressão agora mantêm filtros, controles e ações acessíveis nos tamanhos mínimos e nas escalas de saída suportadas pelo JavaFX
- Barra da pré-visualização agora acompanha a troca de tema sem alterar o fundo de papel do relatório HTML
- Texto auxiliar do tema escuro recebeu contraste maior para permanecer legível em rodapés, dicas e estados vazios
- Janelas secundárias agora possuem owner e modalidade consistentes e não devem retirar a maximização da janela principal ao abrir formulários
- No KDE/KWin Wayland, fechar um diálogo não reduz mais a superfície da janela principal enquanto a flag de maximização permanece ativa; os limites restaurados também são preservados
- Janelas secundárias são centralizadas e limitadas à área útil do monitor; o timer compacto preserva a posição escolhida
- Posições solicitadas antes de `show()` são preservadas mesmo quando o compositor GTK reposiciona a janela durante a abertura
- Timer de tarefa ampliado e responsivo, sem truncar título, vencimento ou linhas longas do histórico
- Histórico de sessões agora usa `task_id` real, mantém fallback para registros legados e combina filtros de tarefa e período
- Histórico de sessões informa erros de carregamento, trata tarefa removida e exporta CSV em UTF-8 com escaping correto
- Seletor manual de foco corrigido para não quebrar o rótulo verticalmente e para apresentar botões em português
- Lembretes não tocam durante a inicialização; som e movimento deixaram de ser obrigatórios
- `Ctrl/Cmd+S` deixou de disparar lembretes acidentalmente
- `AgendaTabController.submitForm()`: adicionado `refreshCurrentView()` e `triggerDashboardRefresh()` após salvar tarefa — lista agora atualiza imediatamente sem trocar de aba
- `theme-dark.css`: cobertura completa do popup do `DatePicker` em modo escuro (botões de navegação de mês, labels, células de dias, dias adjacentes, hoje e selecionado)
- `app.css` / `theme-dark.css`: seleção de linhas em `ListView` e `TableView` agora visível em linhas pares e ímpares (`:filled:selected:odd` / `:filled:selected:even`)
- Badge da barra de status agora usa a mesma base visível da dashboard (Atrasos + Hoje + Protocolos) e mostra breakdown `A/H/P`
- Tooltip no badge de status detalha o significado de `A/H/P` e mostra os totais atuais
- `Agenda e Prioridades`: painel de formulário agora é preview-first; seleção da lista carrega pré-visualização read-only, edição só via botão `Editar selecionada`, `Esc` cancela edição/criação, e ação primária alterna entre `Nova tarefa` e `+ Adicionar tarefa`
- Badge da barra de status agora mostra `PENDÊNCIAS` (não confunde com "atrasos") e usa cor de aviso quando `A=0` (apenas hoje/protocolos)
- Dashboard: painéis/listas de "Alertas de atraso" e "Próximos prazos" ganharam alturas mínimas e VGrow para não ficarem invisíveis quando a área superior cresce
- Dashboard: conteúdo da aba agora fica dentro de `ScrollPane` vertical para permitir rolagem quando os painéis ultrapassam a altura da janela

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
