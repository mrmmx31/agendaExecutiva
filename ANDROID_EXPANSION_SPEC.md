# Projeto 3 - Expansao Funcional Android

| Campo | Valor |
|---|---|
| Status | Em implementacao; A3-00 concluida |
| Progresso geral | 10% (1 de 10 fases) |
| Versao da spec | 1.0 |
| Data | 2026-09-05 |
| Plataformas | Android, desktop JavaFX e smartband via Da Fit |
| Predecessor | `PROJECT2_SPEC.md`, concluido em 100% |

## 1. Objetivo

Transformar o Android de replica operacional minima em uma superficie diaria completa da Agenda. O telefone deve permitir consultar, iniciar e registrar trabalho fora do notebook, sem copiar para uma tela pequena toda a complexidade administrativa do desktop.

O desktop continua sendo a superficie para organizacao em massa, relatorios extensos, conciliacao e configuracao estrutural. O Android prioriza `Agora`, acao rapida, execucao offline, retomada e sincronizacao posterior.

### Correcoes preparatorias entregues

- [x] convite de pareamento consumido uma unica vez, sem reabrir o dialogo ao girar a tela;
- [x] protocolo ativo pode ser encerrado com confirmacao, preservacao dos passos feitos, estado terminal no relogio e evento `PROTOCOL_RUN_CANCELLED` na fila.

Essas correcoes estabilizam a base existente e nao contam como conclusao de uma fase do Projeto 3.

## 2. Principios obrigatorios

1. A tela inicial responde primeiro a `o que faco agora?`.
2. Operacoes frequentes funcionam offline e entram em fila duravel.
3. Destruicao, encerramento ou descarte exigem confirmacao e, quando viavel, desfazer.
4. Rotacao, morte do processo e mudanca de tema preservam estado sem repetir dialogos ou comandos.
5. A navegacao principal usa no maximo cinco destinos; recursos adicionais ficam em `Mais`.
6. Listas distinguem graficamente pendente, em andamento, concluido, atrasado e conflito, sem depender apenas de cor.
7. A smartband Da Fit recebe notificacoes sensoriais em melhor esforco; ela nao e tratada como Wear OS.
8. Saude e recomendacao permanecem locais, consentidas, explicaveis e nao clinicas.
9. Nenhuma fase e marcada como concluida sem teste automatizado e evidencia no aparelho ou emulador aplicavel.
10. O progresso desta spec deve ser atualizado no mesmo commit da implementacao.

## 3. Inventario do desktop e destino movel

Legenda: **Completo** = criar/editar/executar no telefone; **Operacional** = consulta e acoes curtas; **Desktop** = manter administracao somente no computador.

| Dominio desktop | Recursos existentes | Destino Android | Prioridade |
|---|---|---|---|
| Dashboard | Agora, foco automatico/manual, plano diario, captura, estudos do dia, protocolos urgentes, atrasos, revisao e encerramento do dia | Completo para Agora, captura, plano e retomada; operacional para revisao | P0 |
| Agenda e Prioridades | tarefas recorrentes, datas/horarios, categoria, prioridade, status, notas, protocolo ligado, filtros, checklist, timer, historico, Google Tasks, Calendar/iCal, duplicatas e impressao | Completo para tarefa, checklist, foco e timer; operacional para historico; exportacao/duplicatas no desktop | P0 |
| Protocolos Operacionais | modelos, passos ordenados, tipos saida/reuniao/horario, validade, execucao e impressao | Completo para escolher e executar; edicao curta/proposta no telefone; modelagem em massa no desktop | P0 |
| Estudos e Atividades | planos, tipo, categoria, status, paginas/progresso, prazo, descricao, grade semanal, horas minimas, diario cientifico, timer, frequencia e compensacoes | Completo para consultar plano, registrar sessao/diario, timer, paginas e presenca; grade estrutural no desktop | P1 |
| Ideias e Projetos | captura, inbox, prioridade, tipo, impacto, viabilidade, prazo, metodologia, referencias, relacoes, proximas acoes, checklist/lista/Kanban, conversao em tarefa e arquivo | Completo para captura, consulta, proxima acao e checklist; operacional para triagem; edicao cientifica e Kanban amplo no desktop | P1 |
| Financeiro e Pendencias | receitas/despesas, vencimento, pago, filtros, totais, urgentes, editar/excluir e impressao | Operacional: consultar, registrar e marcar pago; relatorios e exclusao em massa no desktop | P2 |
| Vendas e Estoque | vendas, recebimento, cliente/notas, catalogo de material/servico, preco, estoque, entrada/saida e alerta baixo | Operacional: consulta, venda rapida, recebido e ajuste de estoque; cadastro amplo/relatorio no desktop | P2 |
| Caixa de entrada | captura global e triagem para tarefa, ideia/projeto, nota ou descarte | Completo, offline-first | P0 |
| Plano e revisao diaria | capacidade normal/reduzida, essencial/apoio, decisoes sobre sobras, nota de fechamento e reabertura | Completo com fluxo curto | P1 |
| Foco e sessoes | timer, pausar, interrupcao, pista de retomada, janela compacta, salvar/editar sessao e historico CSV | Completo para timer/interrupcao/salvar; consulta de historico; CSV no desktop | P0 |
| Alertas | liga/desliga, intervalo, silencio, pausa, som, animacao e teste | Completo e por canal no telefone | ja entregue; expandir |
| Google Tasks | OAuth, conta, previa, sync bidirecional, conflitos e revisao | Exibir estado e acionar sync desktop inicialmente; integracao Google direta no Android requer ADR e credencial propria | P2 |
| Google Drive | backup cifrado da chave de assinatura | Somente estado/diagnostico; autorizacao e restauracao permanecem no desktop | Desktop |
| Configuracoes | tema, categorias, atalhos, alertas, Google, dispositivos, metricas e backup | Completo para tema/sensorial/privacidade/sync; categorias e backup no desktop | P1 |
| Metricas locais | uso, foco, revisao e limpeza | Resumo opcional e limpeza local | P2 |
| Impressao/relatorios | pre-visualizacao, impressao e CSV/iCal | Compartilhar/exportar arquivo quando util; composicao avancada no desktop | Desktop |

## 4. Arquitetura de informacao Android

### 4.1 Navegacao principal

1. **Hoje:** Agora, protocolo ativo, timer ativo, plano e proximos alertas.
2. **Tarefas:** pendentes/concluidas, busca, filtros curtos e checklist.
3. **Capturar:** entrada universal com destino opcional.
4. **Rotinas:** protocolos e estudos.
5. **Mais:** projetos, financeiro, vendas/estoque, saude, recomendacoes, fila e configuracoes.

O botao Voltar fecha detalhe ou dialogo, depois retorna a `Hoje`; somente um novo Voltar em `Hoje` permite sair.

### 4.2 Estado e restauracao

- Destino atual, filtros, rascunhos, formulario e dialogo relevante usam `rememberSaveable` ou `SavedStateHandle`.
- Deep links sao eventos de consumo unico; recriar a Activity nao pode reapresenta-los.
- Comando submetido nao pode ser repetido por rotacao.
- Timer, protocolo e fila vivem no repositorio, nao apenas no Composable.

## 5. Requisitos funcionais

### 5.1 Hoje, foco e tarefas

- `A3-NOW-01`: mostrar um foco principal e ate duas acoes de apoio.
- `A3-NOW-02`: iniciar, pausar, interromper, retomar e salvar sessao no telefone.
- `A3-NOW-03`: criar/editar tarefa, concluir/reabrir e operar checklist offline.
- `A3-NOW-04`: montar e editar plano diario normal ou reduzido.
- `A3-NOW-05`: encerrar/reabrir dia com decisoes sobre tarefas restantes.
- `A3-NOW-06`: toda acao destrutiva ou terminal tem confirmacao; conclusao simples oferece desfazer.

### 5.2 Protocolos e saida de casa

- `A3-PRO-01`: `Vou sair` inicia um protocolo compativel ou apresenta escolha curta.
- `A3-PRO-02`: execucao mostra progresso, passo atual, concluidos e pendentes.
- `A3-PRO-03`: `Encerrar protocolo` pede confirmacao, preserva passos feitos, remove estado ativo e sincroniza `PROTOCOL_RUN_CANCELLED`.
- `A3-PRO-04`: reiniciar cria nova execucao sem apagar a anterior.
- `A3-PRO-05`: telefone permite sugerir alteracao estrutural; desktop revisa antes de modificar o modelo.

### 5.3 Estudos e cursos

- `A3-STU-01`: listar planos por hoje, em andamento, pausado e concluido.
- `A3-STU-02`: detalhe mostra meta, prazo, paginas/unidades, progresso e agenda semanal.
- `A3-STU-03`: registrar sessao com duracao, paginas/unidades, tipo, notas e data.
- `A3-STU-04`: diario aceita texto simples offline; formato rico e anexos ficam para fase posterior.
- `A3-STU-05`: timer de estudo sobrevive a background e processo morto.
- `A3-STU-06`: registrar presenca, falta justificada e compensacao sem inferencia automatica.

### 5.4 Ideias e projetos

- `A3-IDE-01`: captura rapida cria item de inbox sem classificacao obrigatoria.
- `A3-IDE-02`: triagem permite promover, arquivar, vincular, converter em tarefa ou enviar ao checklist.
- `A3-IDE-03`: projeto mostra objetivo, proxima acao e checklist em lista compacta.
- `A3-IDE-04`: Kanban completo, metodologia e referencias extensas continuam no desktop inicialmente.

### 5.5 Financeiro, vendas e estoque

- `A3-OPS-01`: valores pessoais ficam ocultaveis e nao aparecem em notificacoes.
- `A3-OPS-02`: registrar lancamento e marcar pago funcionam offline.
- `A3-OPS-03`: venda rapida, marcar recebido e ajuste de estoque exigem confirmacao.
- `A3-OPS-04`: exclusao, conciliacao, relatorios e cadastro em massa permanecem no desktop.

### 5.6 Sincronizacao

- `A3-SYN-01`: ampliar snapshots e comandos por dominio, com UUID, sequencia, hash e revisao.
- `A3-SYN-02`: baixar alteracoes incrementalmente; nao retransmitir o banco inteiro.
- `A3-SYN-03`: conflito de texto/estrutura/estado e revisado explicitamente.
- `A3-SYN-04`: status mostra ultima tentativa, ultimo sucesso, fila, conflito e desktop alcancavel.
- `A3-SYN-05`: pareamento e deep link nao reaparecem por rotacao.
- `A3-SYN-06`: schema novo exige fixture, migracao e compatibilidade com a versao anterior.

## 6. Alertas sensoriais e Da Fit

### 6.1 Contrato comum

Cada alerta tem titulo curto, motivo, urgencia, horario elegivel, validade, repeticao limitada e acoes `Concluir`/`Adiar` no telefone. Perfil global, horario silencioso, pausa e canais continuam soberanos.

Alertas podem vir de tarefa, foco, estudo, protocolo, pagamento ou compromisso. Dados financeiros, notas de saude e substancias nunca entram no texto exibido na tela bloqueada ou na smartband.

### 6.2 Mertto ZL02D/ZL02CPro com Da Fit

- A Agenda publica uma notificacao Android no canal autorizado; o Da Fit decide se espelha texto, som ou vibracao.
- A aplicacao nao consegue selecionar diretamente vibracao, som, fila ou botoes da smartband.
- `Concluir` e `Adiar` permanecem disponiveis na notificacao do telefone.
- O teste real atual comprova texto e som intermitente, sem vibracao e sem acoes no pulso.
- Alerta emitido desconectado nao e considerado entregue ao relogio e pode nao ser reproduzido ao reconectar.
- A tela de diagnostico deve mostrar permissao Android, canal, estado Bluetooth observavel, instrucao Da Fit e hora da ultima notificacao publicada, sem afirmar entrega no pulso.
- Wear OS continua com Data Layer e acoes completas, separado do perfil Da Fit.

### 6.3 Criterios sensoriais

- Nenhum beep ou vibracao sem opt-in.
- Cooldown impede sobreposicao e repeticao agressiva.
- Adiar oferece presets contextuais e horario manual no telefone.
- Tarefa concluida, protocolo encerrado ou alerta expirado cancela notificacoes relacionadas.
- Teste sensorial informa qual rota foi tentada; nao declara sucesso apenas porque a API aceitou a publicacao.

## 7. Dados e contratos a acrescentar

Entidades replicadas previstas: `daily_plan`, `daily_plan_item`, `task_checklist_item`, `focus_context`, `task_session`, `study_plan`, `study_schedule`, `study_entry`, `study_compensation`, `project_idea`, `idea_checklist_item`, `finance_entry`, `sale_entry` e `inventory_item`.

Comandos iniciais previstos: `TASK_CREATED`, `TASK_UPDATED`, `TASK_STATUS_CHANGED`, `CHECKLIST_ITEM_CHANGED`, `FOCUS_CHANGED`, `SESSION_RECORDED`, `DAILY_PLAN_CHANGED`, `DAY_CLOSED`, `STUDY_SESSION_RECORDED`, `STUDY_PROGRESS_CHANGED`, `IDEA_CAPTURED`, `IDEA_TRIAGED`, `FINANCE_PAYMENT_CHANGED`, `SALE_STATUS_CHANGED`, `INVENTORY_ADJUSTED` e `PROTOCOL_RUN_CANCELLED`.

Cada dominio deve definir ownership, campos editaveis no Android, tombstone, regra de conflito, limite de payload, dados proibidos em log/notificacao e estrategia de downgrade antes do codigo.

## 8. Fases e progresso

Cada fase vale 10 pontos percentuais. Parcialidade pode ser anotada dentro da fase, mas o progresso geral so soma fase com gate integralmente aprovado.

### A3-00 - Baseline e contratos (100%)
- [x] validar inventario com fixtures compartilhados;
- [x] definir navegacao e componentes adaptativos;
- [x] versionar contratos por dominio e estrategia de migracao;
- [x] registrar baseline de tempo, memoria, bateria e acessibilidade.

**Evidencia:** `contracts/EXPANSION_V1.md` define ownership, ativacao e
migracao; o catalogo fechado cobre dez dominios e e validado pelos testes Java
e Kotlin. `P3_00_NAVIGATION.md` fixa cinco destinos, breakpoints, Voltar,
restauracao e acessibilidade. `P3_00_BASELINE.md` registra cinco inicializacoes
no Moto (mediana 337 ms), 122001 KiB PSS, estado termico inicial e protocolo de
bateria reproduzivel. Schemas e fixtures passaram em `jq`; as suites de contrato
desktop e Android passaram sem falhas.

### A3-01 - Hoje, plano e foco (0%)
- [ ] Dashboard movel e plano diario;
- [ ] foco manual/automatico explicavel;
- [ ] encerramento e reabertura do dia;
- [ ] testes de rotacao, processo morto e offline.

### A3-02 - Tarefas, checklist e timer (0%)
- [ ] CRUD operacional e estados visuais;
- [ ] checklist e desfazer;
- [ ] timer, interrupcao, retomada e sessoes;
- [ ] sync/conflitos e testes ponta a ponta.

### A3-03 - Protocolos robustos (0%)
- [ ] iniciar, concluir, encerrar e reiniciar;
- [ ] historico e reconciliacao desktop;
- [ ] alertas associados e cancelamento terminal;
- [ ] teste telefone, Wear OS e fallback Da Fit.

### A3-04 - Estudos e cursos (0%)
- [ ] lista/detalhe de planos;
- [ ] diario e registro de sessao;
- [ ] progresso, frequencia e compensacao;
- [ ] timer e sync offline.

### A3-05 - Ideias e projetos (0%)
- [ ] inbox e triagem;
- [ ] detalhe compacto e proxima acao;
- [ ] checklist de projeto;
- [ ] conversao em tarefa e sync.

### A3-06 - Financeiro e operacoes (0%)
- [ ] financeiro operacional com privacidade;
- [ ] vendas e recebimento;
- [ ] estoque e alertas de nivel baixo;
- [ ] confirmacoes, conflitos e sync.

### A3-07 - Sensorial e smartband (0%)
- [ ] fontes novas no motor de alertas;
- [ ] diagnostico Da Fit e texto privado;
- [ ] cancelamento/expiracao entre dominios;
- [ ] matriz fisica atualizada.

### A3-08 - Configuracoes, fila e observabilidade (0%)
- [ ] status dinamico de conexao e sync;
- [ ] conflitos compreensiveis;
- [ ] preferencias moveis organizadas;
- [ ] exportacao diagnostica sanitizada.

### A3-09 - Release e aceite (0%)
- [ ] migracoes historicas e downgrade documentado;
- [ ] testes unitarios, instrumentados e ponta a ponta;
- [ ] desempenho, bateria, temas e acessibilidade;
- [ ] APK/AAB assinado, backup e aceite fisico.

## 9. Ordem de implementacao

O primeiro incremento deve fechar A3-00 e A3-01. Em seguida A3-02, A3-03 e A3-04 entregam o nucleo diario e os cursos. Ideias entram antes dos modulos financeiros. A3-07 acompanha todas as fases, mas so fecha quando cada nova fonte de alerta cancelar e expirar corretamente.

## 10. Definicao de pronto

Uma capacidade esta pronta somente quando possui contrato versionado, persistencia e migracao, regra offline, UI clara/escura adaptativa, estado vazio/erro/ocupado, acessibilidade, testes automatizados, evidencia no ambiente aplicavel e atualizacao desta checklist e do `MAINTENANCE_MAP.md`.
