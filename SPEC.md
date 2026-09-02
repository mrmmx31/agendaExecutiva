# Especificação do Produto — Agenda como Prótese Executiva Pessoal

| Campo | Valor |
|---|---|
| Status | Implementação, baseline inicial e estabilização concluídos |
| Versão | 1.1 |
| Data | 2026-08-30 |
| Plataforma | Desktop JavaFX, Java 21, SQLite local |
| Fonte de verdade | Este documento para produto e UX; `ARCHITECTURE.md` para limites técnicos |

## 1. Resumo

A Agenda Científica Pessoal deve funcionar como uma prótese executiva: um sistema externo que ajuda o usuário a lembrar, escolher, iniciar, sustentar, interromper e retomar atividades com menos esforço mental.

O produto não deve ser apenas um repositório de tarefas. A tela inicial deve responder, de forma calma e controlável, a quatro perguntas:

1. O que merece minha atenção agora?
2. Qual é a menor ação que posso iniciar?
3. Onde registro algo antes de esquecer?
4. Como retomo o que estava fazendo após uma interrupção?

O termo “prótese executiva” é uma metáfora de produto. A aplicação não diagnostica, trata ou substitui acompanhamento médico ou terapêutico.

## 2. Problema

### 2.1 Problemas observados

- A aplicação cresceu por módulos e expõe muitas decisões simultâneas.
- Janelas secundárias historicamente possuíam tamanhos, posicionamento e hierarquia visual diferentes.
- Abrir formulários podia interferir no estado maximizado da janela principal.
- Alertas repetitivos, som e animação contínua geravam pressão sensorial.
- KPIs, atrasos antigos e próximas ações disputavam a mesma atenção.
- Registrar, revisar e executar são atividades diferentes, mas nem sempre estavam visualmente separadas.
- Uma lista completa de pendências não informa necessariamente qual ação iniciar.
- Interrupções não possuem um fluxo explícito de anotação e retomada.

### 2.2 Hipótese do produto

Se o sistema mostrar uma ação principal, permitir captura sem classificação imediata, preservar o contexto de trabalho e oferecer lembretes controláveis, o custo de iniciar e retomar tarefas será menor e a agenda será consultada com mais frequência.

## 3. Visão e princípios

### 3.1 Visão

Ao abrir a agenda, o usuário deve conseguir começar algo útil em até 30 segundos, sem precisar percorrer todas as abas ou reorganizar todo o acervo.

### 3.2 Princípios obrigatórios

1. **Agora antes do acervo:** a ação presente tem precedência visual sobre indicadores e listas extensas.
2. **Uma decisão por vez:** fluxos guiados não devem pedir várias classificações simultâneas.
3. **Capturar primeiro, organizar depois:** data, categoria e prioridade não são obrigatórias na captura bruta.
4. **Reconhecimento antes de memória:** o sistema deve oferecer opções visíveis e contexto, sem depender de o usuário lembrar onde algo está.
5. **Calma sem invisibilidade:** pendências continuam visíveis, mas som e movimento são opt-in.
6. **Retomada é parte do fluxo:** interromper não deve significar perder o contexto.
7. **Ações reversíveis:** adiar, arquivar, trocar foco e reorganizar devem evitar perda de dados.
8. **Explicabilidade:** sugestões automáticas devem informar por que foram escolhidas.
9. **Foco como âncora, não exclusividade:** a tarefa principal organiza a retomada, mas atividades paralelas não significam distração nem encerram a sessão por inferência.
10. **Local e privado:** dados pessoais permanecem no SQLite local, salvo sincronização explicitamente acionada.
11. **Consistência:** janelas, botões, termos, estados vazios e atalhos seguem contratos comuns.

## 4. Objetivos e não objetivos

### 4.1 Objetivos da versão-alvo

- Tornar o Dashboard o ponto operacional diário.
- Oferecer um ciclo de início, execução, interrupção, retomada e encerramento do dia.
- Manter um único foco principal explícito, com até duas tarefas de apoio.
- Criar uma caixa de entrada universal para capturas não classificadas.
- Reduzir fadiga de alertas e impedir estímulos inesperados.
- Uniformizar e testar todas as janelas secundárias.
- Preservar os módulos atuais sem obrigar o usuário a navegar neles para ações comuns.
- Aumentar cobertura automatizada de regras de negócio, persistência e contratos de UI.

### 4.2 Não objetivos

- Diagnosticar TDAH ou recomendar tratamento.
- Substituir calendário, e-mail ou gerenciadores corporativos completos.
- Adicionar colaboração multiusuário nesta fase.
- Criar gamificação baseada em punição, sequência diária ou culpa.
- Usar IA ou serviços externos para decidir prioridades na primeira versão.
- Reescrever toda a interface em FXML antes de validar os fluxos.
- Remover Financeiro, Vendas, Estudos, Ideias ou Protocolos.

## 5. Usuário e trabalhos principais

### 5.1 Perfil primário

Pessoa que administra atividades profissionais, estudos, rotina, finanças e projetos pessoais em um computador, com dificuldade recorrente para escolher, iniciar ou retomar ações quando há muitas pendências.

### 5.2 Trabalhos que o produto deve resolver

- “Quando eu abrir a agenda, quero saber por onde começar.”
- “Quando uma ideia surgir, quero registrá-la sem abandonar o que faço.”
- “Quando eu for interrompido, quero deixar uma pista clara para retomar.”
- “Quando houver pendências antigas, quero revisá-las sem serem tratadas como emergência permanente.”
- “Quando meu dia terminar, quero registrar o que ficou e preparar uma entrada simples para amanhã.”
- “Quando abrir uma janela, quero que ela caiba na tela e não altere a janela principal.”

## 6. Métricas de sucesso

As métricas devem ser calculadas localmente e só serão exibidas ao usuário se ele habilitar essa opção. Não haverá telemetria remota implícita.

| Métrica | Meta inicial |
|---|---|
| Tempo entre abrir o app e iniciar/abrir o foco | mediana menor que 30 segundos |
| Captura rápida | no máximo 2 ações além da digitação |
| Retomada após interrupção registrada | no máximo 2 ações |
| Janelas secundárias fora da área útil | zero nos ambientes suportados |
| Perda de maximização causada por formulário | zero |
| Alertas sonoros sem opção explícita | zero |
| Animações contínuas por padrão | zero |
| Tarefas escolhidas automaticamente sem justificativa visível | zero |
| Erros silenciosos de carregamento ou persistência | zero |

Antes de medir evolução, a Fase 0 deve registrar uma linha de base manual para os quatro primeiros fluxos.

## 7. Arquitetura de informação

### 7.1 Navegação principal

As abas atuais permanecem:

1. Dashboard
2. Agenda e Prioridades
3. Protocolos Operacionais
4. Financeiro e Pendências
5. Vendas e Estoque
6. Estudos e Atividades
7. Ideias e Projetos
8. Configurações

O Dashboard é a entrada padrão e possui três vistas internas:

- **Hoje:** foco atual, plano do dia, tarefas e estudos programados.
- **Organizar:** caixa de entrada, protocolos acionáveis e triagem curta.
- **Revisar:** atrasos, itens antigos e indicadores recolhidos.

### 7.2 Hierarquia visual do Dashboard

Ordem obrigatória:

1. Bloco “Agora”.
2. Ação de captura global.
3. Conteúdo da vista interna selecionada.
4. Indicadores gerais recolhidos por padrão.

Nenhum KPI pode ocupar mais destaque que o foco atual.

## 8. Estado atual

Legenda: **Concluído**, **Parcial**, **Pendente**.

| Capacidade | Estado | Observação |
|---|---|---|
| Propriedade e modalidade de janelas | Concluído | Centralizado em `WindowManager` |
| Preservação da maximização principal | Concluído | Validado no KDE/KWin Wayland com estado EWMH e geometria restaurada |
| Limite e centralização de janelas na tela | Concluído | Aplicado a `Stage` secundário |
| Tema comum em janelas e diálogos | Concluído | Aplicação global de CSS |
| Auditoria interna de todos os formulários | Concluído | Grupos operacional, conhecimento e utilitário validados nos tamanhos mínimos |
| Dashboard “Agora” | Concluído | Foco automático ou manual persistente |
| Iniciar timer pelo foco | Concluído | Abre `TaskTimerWindow` |
| Alertas configuráveis | Concluído | Controle geral, áudio cauteloso, horário silencioso e movimento limitado validados |
| Pausa de lembretes | Concluído | Pausa/retomada bloqueia áudio periódico, manual e de teste sem esconder contagens |
| Sincronização Google Tasks | Concluído | Núcleo, conflitos, rede, preview, revisão assistida e validação automatizada concluídos |
| Captura rápida de tarefa | Concluído | Dentro da aba Agenda |
| Captura e revisão de ideias | Concluído | Fluxo específico de Ideias |
| Caixa de entrada universal | Concluído | Captura, atalho, triagem e compatibilidade com Ideias validados |
| Plano diário guiado | Concluído | Fluxo persistente, editável, reduzido e integrado ao Agora |
| Encerramento e revisão do dia | Concluído | Ciclo diário, revisão neutra e métricas locais opt-in validados |
| Pista de interrupção e retomada | Concluído | Captura, Dashboard e recuperação controlada do timer validados |
| Testes automatizados de UI | Concluído | Perfil JavaFX cobre janela, modalidade, tema, responsividade e maximização |

## 9. Requisitos funcionais

### 9.1 Dashboard e foco atual

**NOW-01 — Foco único:** o Dashboard deve exibir no máximo uma tarefa como foco principal.

**NOW-02 — Origem explícita:** o foco deve indicar um dos modos: `Escolhido por você`, `Plano de hoje`, `Timer em andamento` ou `Sugestão automática`.

**NOW-03 — Ações diretas:** o foco deve oferecer `Iniciar/Continuar`, `Abrir`, `Escolher outro` e `Usar automático` quando aplicáveis.

**NOW-04 — Persistência:** a escolha manual deve sobreviver a refresh e reinício do aplicativo.

**NOW-05 — Invalidação segura:** se a tarefa escolhida for concluída ou removida, o foco manual deve ser limpo e uma nova sugestão deve ser apresentada sem erro.

**NOW-06 — Justificativa:** a sugestão automática deve exibir uma razão curta, por exemplo `vence hoje`, `prioridade crítica` ou `em andamento`.

**NOW-07 — Estabilidade:** refreshes com os mesmos dados devem manter a mesma sugestão.

**NOW-08 — Pendências antigas:** tarefas muito antigas não devem dominar continuamente o foco. Elas entram na revisão e só retornam ao “Agora” quando escolhidas, críticas ou explicitamente reativadas.

**NOW-09 — Âncora não exclusiva:** “foco principal” identifica o contexto que deve ser preservado e retomado. Ele não afirma que o usuário executa somente uma atividade, nem deve gerar alerta ou correção apenas porque outras janelas, dispositivos ou tarefas estão em uso.

#### Ordem determinística de escolha automática

1. Timer ativo.
2. Foco manual válido.
3. Tarefa principal do plano diário.
4. Tarefa em andamento com vencimento atual.
5. Tarefa de hoje por prioridade.
6. Tarefa atrasada recente por prioridade.
7. Próxima tarefa aberta por vencimento.

Empates são resolvidos por prioridade, data de vencimento e ID crescente. A ordem não pode depender da ordem incidental de uma consulta SQL.

### 9.2 Início do dia

**DAY-01 — Entrada guiada:** quando não houver plano para a data, a vista Hoje deve oferecer `Começar meu dia`.

**DAY-02 — Fluxo curto:** o início do dia deve ter no máximo três etapas:

1. Revisar compromissos e tarefas de hoje.
2. Escolher uma tarefa essencial e até duas de apoio.
3. Confirmar a primeira ação.

**DAY-03 — Baixa capacidade:** o usuário pode marcar o dia como `capacidade reduzida`; nesse estado, apenas uma tarefa essencial é solicitada e alertas não críticos perdem destaque.

**DAY-04 — Plano editável:** tarefas do plano podem ser trocadas ou reordenadas sem editar a tarefa original.

**DAY-05 — Continuidade:** se um plano de hoje já existir, abrir o aplicativo deve restaurá-lo.

**DAY-06 — Sem bloqueio:** o usuário pode ignorar o ritual e usar os módulos normalmente.

### 9.3 Captura universal

**CAP-01 — Acesso global:** todas as abas devem oferecer a mesma ação de captura, inclusive por atalho configurável. Atalho inicial proposto: `Ctrl/Cmd+Shift+Espaço`.

**CAP-02 — Campo único:** a captura inicial exige apenas texto.

**CAP-03 — Salvamento rápido:** `Enter` salva; `Shift+Enter` cria nova linha; `Esc` fecha sem salvar após confirmação somente se houver texto.

**CAP-04 — Destino tardio:** uma captura nova entra como `Não classificada`. O usuário pode depois convertê-la em tarefa, ideia, nota de interrupção ou descartá-la.

**CAP-05 — Preservação de contexto:** abrir e fechar a captura não troca a aba ativa nem altera a janela principal.

**CAP-06 — Confirmação discreta:** após salvar, mostrar uma confirmação textual breve, sem modal e sem som.

**CAP-07 — Sem perda:** falha de persistência mantém o texto no campo e apresenta ação para tentar novamente.

**CAP-08 — Compatibilidade:** capturas de ideias existentes permanecem acessíveis. Uma migração para a caixa universal deve preservar conteúdo e vínculos.

### 9.4 Foco, timer e interrupções

**FOC-01 — Início imediato:** `Iniciar foco` começa ou abre o timer da tarefa escolhida.

**FOC-02 — Uma sessão ativa:** apenas uma tarefa pode possuir timer ativo. Trocar exige pausar ou encerrar a sessão atual.

**FOC-03 — Estado visível:** a aplicação deve mostrar tarefa ativa, tempo e estado `rodando/pausado` sem depender da janela do timer estar aberta.

**FOC-04 — Interromper:** o timer deve oferecer `Fui interrompido`.

**FOC-05 — Pista de retomada:** ao interromper, o usuário pode registrar em um único campo “onde parei / próximo passo”.

**FOC-06 — Retomar:** na próxima abertura, o Dashboard deve mostrar a pista e a ação `Retomar`.

**FOC-07 — Encerramento:** parar uma sessão deve permitir salvar duração e observação sem perder o vínculo real com `task_id`.

**FOC-08 — Recuperação:** se o aplicativo fechar com timer ativo, a próxima inicialização deve oferecer recuperar ou descartar o intervalo, sem contabilizá-lo silenciosamente.

**FOC-09 — Continuidade explícita:** manutenção da Agenda, sincronização, troca de janela ou uso paralelo de outro aplicativo/dispositivo não pausa, encerra ou troca a sessão ativa. Somente ação explícita do usuário ou recuperação após encerramento do processo altera esse estado.

### 9.5 Encerramento e revisão

**REV-01 — Encerrar dia:** a vista Hoje deve oferecer `Encerrar meu dia` quando houver plano diário.

**REV-02 — Revisão curta:** o encerramento deve mostrar tarefas concluídas, sessões registradas e itens ainda abertos.

**REV-03 — Decisão limitada:** para cada item aberto, as opções iniciais são `Amanhã`, `Manter data`, `Voltar à caixa de entrada` ou `Concluir`.

**REV-04 — Preparação:** o usuário pode escolher uma tarefa inicial para amanhã, sem criar obrigatoriedade ou sequência.

**REV-05 — Registro:** o plano recebe `closed_at`; pode ser reaberto no mesmo dia.

**REV-06 — Revisão de antigos:** itens antigos são agrupados por faixa (`até 7 dias`, `8–30`, `mais de 30`) e não por linguagem culpabilizante.

### 9.6 Lembretes e estímulos

**NTF-01 — Configuração persistente:** ativação, som, animação e intervalo devem sobreviver a reinícios.

**NTF-02 — Padrões calmos:** padrão inicial de 15 minutos, sem som e sem animação contínua.

**NTF-03 — Intervalos:** oferecer 5, 15, 30 e 60 minutos.

**NTF-04 — Pausa:** permitir pausar por 30 minutos e retomar antes do prazo.

**NTF-05 — Visibilidade:** pausar lembretes não esconde contagens nem pendências.

**NTF-06 — Sem repetição imediata:** iniciar o aplicativo não deve tocar alerta; a primeira checagem periódica ocorre após o intervalo configurado.

**NTF-07 — Atalho seguro:** nenhum lembrete pode usar `Ctrl/Cmd+S`.

**NTF-08 — Movimento limitado:** quando ativada, uma animação de atenção deve executar por no máximo três ciclos por evento, nunca indefinidamente.

**NTF-09 — Horário silencioso:** permitir uma faixa diária configurável sem som, mantendo indicação visual.

**NTF-10 — Controle geral imediato:** desligar lembretes deve cancelar a checagem periódica e interromper imediatamente som e movimento, sem esconder contagens ou pendências. Nenhum beep pode ocorrer enquanto o controle geral estiver desligado, inclusive por atalho ou fallback de áudio.

**NTF-11 — Som cauteloso:** quando ativado, o som executa no máximo uma vez por evento de lembrete, nunca se sobrepõe e respeita controle geral, pausa e horário silencioso. Falha do arquivo de áudio pode usar beep somente sob as mesmas condições.

**NTF-12 — Controle independente:** som e animação permanecem opt-in e independentes. Desligar o som não desliga a indicação visual; desligar lembretes preserva as preferências escolhidas para quando forem reativados.

### 9.7 Janelas, diálogos e navegação

**WIN-01 — Propriedade:** todo `Stage` secundário deve possuir owner válido quando a principal estiver aberta.

**WIN-02 — Modalidade:** edição que bloqueia o fluxo usa `WINDOW_MODAL`; monitores, histórico e timer podem ser modeless.

**WIN-03 — Maximização:** abrir ou fechar um formulário não pode restaurar, minimizar ou redimensionar a janela principal.

**WIN-04 — Área útil:** nenhuma janela deve abrir fora da área útil do monitor ou maior que ela, considerando decorações.

**WIN-05 — Centralização:** janelas novas abrem centralizadas sobre o owner; janelas compactas arrastáveis preservam a posição escolhida.

**WIN-06 — Dimensões responsivas:** conteúdo deve continuar utilizável em 1280×720 e escalas de 100%, 125% e 150%.

**WIN-07 — Cabeçalho:** janelas secundárias usam fundo, cabeçalho, espaçamento e título comuns.

**WIN-08 — Ações:** ação primária fica à esquerda ou no fim lógico do formulário de maneira consistente; cancelar/fechar nunca recebe estilo primário.

**WIN-09 — Texto:** rótulos e conteúdo não podem truncar informação essencial. Textos longos usam wrap; tabelas só usam rolagem horizontal quando o dado for genuinamente tabular.

**WIN-10 — Tema:** toda cena e todo diálogo respondem imediatamente à troca de tema.

**WIN-11 — Fechamento global:** sair da aplicação encerra timers de UI, notificadores e janelas registradas sem deixar processo ativo.

### 9.8 Configurações

**CFG-01 — Categorias claras:** configurações devem separar Aparência, Lembretes, Captura, Atalhos e Categorias.

**CFG-02 — Aplicação imediata:** mudanças visuais e sensoriais entram em vigor sem reiniciar.

**CFG-03 — Restaurar padrão:** cada grupo deve permitir restaurar somente suas próprias preferências.

**CFG-04 — Persistência correta:** preferências de interface usam `java.util.prefs.Preferences`; estado de domínio e histórico usam SQLite.

### 9.9 Sincronização Google Tasks

**GSY-01 — Idempotência:** repetir uma sincronização sem alterações não pode criar, concluir ou atualizar tarefas novamente.

**GSY-02 — Mapeamento atômico:** criação local/remota e gravação do respectivo mapeamento devem ocorrer como uma única operação recuperável; importação e exportação manuais também criam mapeamento.

**GSY-03 — Paginação completa:** todas as listas e tarefas disponíveis devem ser percorridas por `nextPageToken`, sem limite silencioso de 100 itens.

**GSY-04 — Conflitos e exclusões:** alteração concorrente, reabertura e exclusão em qualquer lado exigem regra explícita e prévia, sem sobrescrever ou recriar silenciosamente.

**GSY-05 — Falha segura:** timeout, falta de rede, token expirado/revogado, resposta parcial e limite da API não podem corromper mapeamentos nem bloquear nova tentativa.

**GSY-06 — Observabilidade:** a interface informa lista, início, fim, quantidade processada, conflitos, falhas e ação de recuperação sem expor token ou corpo sensível da API.

**GSY-07 — Testabilidade:** transporte HTTP, relógio e autenticação devem ser injetáveis; testes usam servidor simulado e banco temporário, nunca a conta pessoal.

## 10. Requisitos de experiência e conteúdo

### 10.1 Linguagem

- Usar linguagem descritiva: `pendente há 12 dias`.
- Evitar culpa ou ameaça: `esquecida`, `falhou`, `não deixe escapar`, `sufocando`.
- Informar consequência antes de ação destrutiva.
- Preferir verbos concretos: `Iniciar`, `Retomar`, `Adiar`, `Arquivar`.
- Não usar “urgente” quando a urgência não vier de prazo ou prioridade explícita.

### 10.2 Densidade

- Bloco “Agora”: uma tarefa e até quatro ações.
- Plano diário: uma tarefa essencial e até duas de apoio.
- Estado vazio deve oferecer no máximo uma ação principal.
- KPIs permanecem recolhidos por padrão.
- Cards são usados apenas para itens ou ferramentas realmente delimitados, sem cards aninhados.

### 10.3 Controles

- Ícones para comandos conhecidos, com tooltip quando não forem autoexplicativos.
- Checkboxes/toggles para preferências binárias.
- Segmented controls para modos mutuamente exclusivos.
- Combos/menus para conjuntos de opções.
- Botões de texto somente para comandos claros.
- Tamanho mínimo de alvo interativo: 32×32 px; recomendado 36×36 px.

### 10.4 Acessibilidade

- Todas as ações devem ser alcançáveis por teclado.
- Ordem de foco segue a ordem visual.
- `Esc` fecha diálogos ou cancela edição sem salvar silenciosamente.
- Cor nunca é o único indicador de estado.
- Temas claro e escuro devem manter contraste legível.
- Movimento deve respeitar a preferência de animação desativada.

## 11. Modelo de dados proposto

### 11.1 Plano diário

```sql
CREATE TABLE daily_plans (
    plan_date TEXT PRIMARY KEY,
    capacity TEXT NOT NULL DEFAULT 'NORMAL',
    created_at TEXT NOT NULL,
    closed_at TEXT,
    closing_note TEXT
);

CREATE TABLE daily_plan_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_date TEXT NOT NULL,
    task_id INTEGER NOT NULL,
    role TEXT NOT NULL,
    position INTEGER NOT NULL,
    FOREIGN KEY (plan_date) REFERENCES daily_plans(plan_date),
    FOREIGN KEY (task_id) REFERENCES tasks(id)
);
```

Valores aceitos:

- `capacity`: `REDUCED`, `NORMAL`.
- `role`: `ESSENTIAL`, `SUPPORT`.
- Uma data possui no máximo um item `ESSENTIAL` e dois `SUPPORT`.

### 11.2 Caixa de entrada universal

```sql
CREATE TABLE inbox_captures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    raw_text TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
    created_at TEXT NOT NULL,
    triaged_at TEXT,
    target_id INTEGER
);
```

Valores de `kind`: `UNCLASSIFIED`, `TASK`, `IDEA`, `INTERRUPTION_NOTE`, `ARCHIVED`.

`target_id` referencia logicamente o registro criado após triagem. Como pode apontar para agregados diferentes, a integridade dessa relação é responsabilidade do serviço de aplicação.

### 11.3 Contexto de foco

```sql
CREATE TABLE focus_context (
    task_id INTEGER PRIMARY KEY,
    resume_note TEXT,
    interrupted_at TEXT,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id)
);
```

Somente um contexto pode ser marcado como retomada corrente pela camada de serviço. O timer ativo continua sendo administrado por `TaskTimerService`, com recuperação de falha adicionada em fase própria.

O checkpoint recuperável do timer usa uma linha única separada do contexto de foco:

```sql
CREATE TABLE timer_recovery (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    task_id INTEGER NOT NULL,
    elapsed_seconds INTEGER NOT NULL,
    was_running INTEGER NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
```

O checkpoint registra somente segundos efetivamente contados. Tempo transcorrido com a aplicação fechada nunca é inferido nem acrescentado.

### 11.4 Migrações

- Migrações são aditivas e idempotentes em `Database.runMigrations()`.
- Nenhuma coluna ou tabela atual será removida nesta versão.
- Migração de capturas de Ideias deve ser opcional e transacional.
- Falha de migração interrompe a inicialização com mensagem acionável; não abre o app em estado parcialmente migrado.

## 12. Arquitetura técnica

### 12.1 Limites

- `model`: records/enums sem JavaFX.
- `repository`: SQL e mapeamento, sem regra de produto.
- `service`: validação e casos de uso, sem JavaFX.
- `ui/controller`: estado e ações da tela, sem SQL direto.
- `ui/view`: janelas e componentes especializados.
- `app`: composição e coordenação entre controllers.

### 12.2 Componentes novos previstos

- `DailyPlan`, `DailyPlanItem`, `CaptureInboxItem`, `FocusContext` em `model`.
- `DailyPlanRepository`, `CaptureInboxRepository`, `FocusContextRepository`.
- `DailyPlanningService`, `CaptureService`, `FocusContextService`.
- `QuickCaptureWindow`, modeless e registrada no `WindowManager`.
- `DailyPlanningWindow` ou painel guiado dentro do Dashboard.
- `DayReviewWindow` ou painel guiado dentro do Dashboard.

### 12.3 Decisões de implementação

- Não criar um segundo barramento global; ampliar `SharedContext` apenas para estado realmente compartilhado.
- Não guardar estado de domínio em `Preferences`.
- Consultas lentas devem sair da JavaFX Application Thread e retornar via `Platform.runLater`.
- Exceções de persistência nunca viram listas vazias silenciosas.
- `WindowManager` é a única entrada para criação, preparação e exibição de janelas secundárias.
- `Dialogs` é a única factory de alertas comuns.
- Cores novas devem usar tokens CSS; valores hardcoded existentes serão migrados quando o componente for tocado.

## 13. Estados e tratamento de erros

Toda área com dados deve prever:

- **Carregando:** indicador estável, sem alterar dimensões.
- **Vazio:** mensagem curta e uma ação útil quando aplicável.
- **Erro:** causa compreensível, ação `Tentar novamente` e detalhe técnico expansível.
- **Sucesso:** confirmação não modal e temporária.
- **Sem conexão externa:** módulos locais continuam funcionando.

Regras adicionais:

- Texto digitado não é limpo antes da confirmação de persistência.
- Ações destrutivas exigem confirmação quando não forem reversíveis.
- Operações idempotentes podem ser repetidas após erro.
- Logs técnicos não devem incluir tokens OAuth nem conteúdo pessoal desnecessário.

## 14. Requisitos não funcionais

### 14.1 Desempenho

- Primeira tela operacional em até 2 segundos com banco de até 10 mil tarefas em hardware de referência local.
- Atualização do Dashboard em até 300 ms para consultas locais típicas.
- Captura persistida em até 150 ms no percentil 95.
- Nenhuma consulta de rede bloqueia a thread JavaFX.

### 14.2 Confiabilidade

- SQLite usa transações para operações que alteram mais de uma tabela.
- Fechamento inesperado não pode corromper sessões já salvas.
- Toda sessão deve armazenar `task_id`; leitura legada por assunto é apenas fallback.
- Serviços periódicos são interrompidos ao fechar a aplicação.

### 14.3 Privacidade e segurança

- Banco local em `~/.agenda-pessoal/agenda.db`.
- Nenhuma telemetria remota por padrão.
- Sincronização Google permanece opt-in.
- Tokens e segredos não são exibidos em mensagens ou exportações.
- Exportações exigem destino escolhido pelo usuário.

### 14.4 Compatibilidade visual

Matriz mínima:

- 1280×720, 1366×768, 1920×1080.
- Escala 100%, 125% e 150% quando suportada.
- Tema claro e escuro.
- Principal normal e maximizada.
- Uma e múltiplas janelas secundárias.

## 15. Estratégia de testes

### 15.1 Testes unitários

- Ordenação determinística do foco.
- Regras de plano diário e limites de itens.
- Conversão de captura para tarefa/ideia.
- Validação de intervalo e pausa de notificações.
- Formatação CSV e datas.

### 15.2 Testes de repositório

- Migrações em banco vazio e banco legado.
- CRUD e integridade de `daily_plans`.
- Triagem transacional de capturas.
- Persistência e recuperação de contexto de foco.
- Vínculo real de sessões por `task_id`.

Todos usam banco temporário isolado.

### 15.3 Testes JavaFX

Adicionar uma base de testes JavaFX/TestFX, preferencialmente com execução headless quando estável, cobrindo:

- Abertura de cada `Stage` pelo `WindowManager`.
- Owner e modalidade.
- Troca de tema em janela já aberta.
- Fluxo de teclado da captura.
- Seleção manual de foco.
- Ativação/desativação das configurações de lembrete.
- Ausência de texto cortado em dimensões mínimas conhecidas.

### 15.4 Testes manuais obrigatórios

Checklist por janela:

1. Abrir com principal normal.
2. Abrir com principal maximizada.
3. Abrir uma secundária a partir de outra.
4. Alternar tema com a janela aberta.
5. Redimensionar até o mínimo.
6. Conferir textos longos e estados vazios.
7. Fechar pelo botão do sistema e pela ação da UI.

Janelas da primeira auditoria:

- Timer e timer compacto.
- Histórico de sessões.
- Execução de protocolo.
- Checklist de tarefa e checklist de projeto.
- Diário e monitor de estudos.
- Detalhe e revisão de ideias.
- Google Tasks.
- Pré-visualização de impressão.

## 16. Cenários de aceite ponta a ponta

### Cenário A — Começar sem plano

1. Usuário abre o app sem plano para hoje.
2. Dashboard mostra `Começar meu dia`, sem modal automático.
3. Usuário escolhe uma tarefa essencial.
4. Dashboard passa a mostrá-la em “Agora”.
5. `Iniciar foco` abre o timer sem alterar a maximização da principal.

### Cenário B — Capturar durante o foco

1. Timer está em andamento.
2. Usuário aciona captura global.
3. Digita texto e pressiona Enter.
4. Captura fecha e confirma o salvamento sem trocar a aba nem parar o timer.
5. Item aparece em Organizar como `Não classificado`.

### Cenário C — Interromper e retomar

1. Usuário marca `Fui interrompido`.
2. Registra “validar retorno da API no caso sem CNPJ”.
3. Fecha a aplicação.
4. Na próxima abertura, “Agora” mostra a tarefa, a pista e `Retomar`.

### Cenário D — Pausar estímulos

1. Existem pendências.
2. Usuário pausa lembretes por 30 minutos.
3. Badge continua mostrando a contagem, permanece estático e não toca som.
4. Menu passa a oferecer `Retomar lembretes`.

### Cenário E — Encerrar o dia

1. Usuário abre `Encerrar meu dia`.
2. Visualiza concluídos e até três itens do plano.
3. Move um item para amanhã e mantém outro na data original.
4. Confirma.
5. Plano recebe horário de encerramento e amanhã recebe somente o item escolhido.

### Cenário F — Tela pequena

1. Aplicação roda em 1280×720 com principal maximizada.
2. Usuário abre cada janela da matriz.
3. Nenhuma abre fora da área útil.
4. Ações essenciais permanecem alcançáveis por rolagem ou layout responsivo.
5. Fechar a secundária mantém a principal maximizada.

## 17. Plano de entrega

### Fase 0 — Baseline e documentação

**Status:** Concluída em 2026-08-30.

**Progresso:** 100% (3 de 3 etapas concluídas). A implementação funcional das Fases 1 a 5 permanece em 100%.

- Adotar esta spec como fonte de verdade.
- Atualizar `README`, `DEVELOPMENT` e `CHANGELOG` para o comportamento atual.
- Registrar tempos manuais dos fluxos principais.
- Criar checklist reproduzível de visualização.

**Saída:** documentação coerente e baseline registrado.

### Fase 1 — Estabilidade e uniformidade

**Status:** Concluída em 2026-08-27.

- Finalizar auditoria das janelas secundárias.
- Validar maximização nos sistemas-alvo.
- Corrigir truncamentos, mínimos e rolagens indevidas.
- Criar testes básicos de contratos do `WindowManager`.
- Corrigir configuração Maven para `--release 21` e warnings sob controle do projeto.

**Saída:** todos os formulários abrem e fecham de forma previsível.

**Resultado:** saída atingida; nenhuma limitação visual bloqueante permanece para iniciar a Fase 2.

### Fase 2 — Plano diário

**Status:** Concluída em 2026-08-27.

**Progresso:** 100% (5 de 5 etapas concluídas).

- Implementar modelo, migração, repository e service.
- Construir início do dia em até três etapas.
- Integrar plano com o bloco “Agora”.
- Implementar capacidade reduzida.

**Saída:** usuário escolhe e inicia a tarefa essencial a partir do Dashboard.

**Resultado:** saída atingida; o plano diário é criado em três etapas, restaurado e editado sem alterar tarefas, aceita capacidade reduzida e posiciona sua essencial no bloco `Agora`, de onde o timer pode ser iniciado diretamente.

### Fase 3 — Captura universal

**Status:** Concluída em 2026-08-27.

**Progresso:** 100% (5 de 5 etapas concluídas).

- Implementar caixa de entrada e `QuickCaptureWindow`.
- Adicionar atalho global configurável.
- Implementar triagem para tarefa, ideia, interrupção ou arquivo.
- Integrar capturas de Ideias sem perda de dados.

**Saída:** qualquer pensamento pode ser salvo sem classificação imediata.

**Resultado:** saída atingida; a captura universal pode ser aberta pelo cabeçalho ou atalho configurável, preserva texto e contexto, informa falhas sem perda e oferece triagem transacional para tarefa, ideia, nota de interrupção ou arquivo. Ideias classificadas entram no fluxo de revisão existente e dados anteriores permanecem intactos.

### Fase 4 — Interrupção e retomada

**Status:** Concluída em 2026-08-28.

**Progresso:** 100% (4 de 4 etapas concluídas).

- Persistir contexto de foco.
- Adicionar `Fui interrompido` e pista de retomada.
- Exibir retomada no Dashboard.
- Implementar recuperação de timer após fechamento inesperado.

**Saída:** contexto de trabalho sobrevive a interrupções e reinícios.

**Resultado:** saída atingida; pistas e checkpoints sobrevivem a reinícios, e qualquer tempo recuperado depende de decisão explícita do usuário.

### Fase 5 — Encerramento e revisão

**Status:** Concluída em 2026-08-30.

**Progresso:** 100% (4 de 4 etapas concluídas).

- Implementar encerramento curto do dia.
- Integrar adiamento e preparação de amanhã.
- Reorganizar revisão de pendências antigas por faixas.
- Adicionar métricas locais opcionais.

**Saída:** ciclo diário completo sem depender de manutenção extensa do backlog.

**Resultado:** saída atingida; encerramento, decisões sobre itens abertos, preparação de amanhã, revisão neutra de pendências e métricas locais opcionais completam o ciclo diário.

## 18. Definition of Done

Uma história só está concluída quando:

- Requisitos e critérios de aceite correspondentes estão atendidos.
- Regra de negócio está fora da UI quando aplicável.
- Migração é idempotente e testada quando houver persistência.
- Estado vazio, carregamento e erro foram tratados.
- Tema claro e escuro foram verificados.
- Navegação por teclado foi verificada.
- Testes automatizados proporcionais ao risco foram adicionados e passam.
- `./mvnw test` e `git diff --check` passam.
- Não há alteração acidental em arquivos do IntelliJ ou dados pessoais.
- Documentação afetada foi atualizada.
- A aplicação foi executada e o fluxo foi validado visualmente.

## 19. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Dashboard voltar a ficar sobrecarregado | Limites explícitos de itens e KPIs recolhidos |
| Automação escolher foco inadequado | Escolha manual persistente e justificativa visível |
| Caixa de entrada virar novo backlog infinito | Revisão curta no início/encerramento e contagem sem alarme |
| Beep útil virar estímulo excessivo | Controle geral imediato, som opt-in, sem sobreposição e intervalos explícitos |
| Muitas preferências aumentarem complexidade | Padrões calmos e grupos pequenos com restauração local |
| Regressão em dados existentes | Migrações aditivas, banco temporário e backup antes de migração ampla |
| UI JavaFX difícil de testar em headless | Separar lógica em services e manter matriz manual automatizável |
| `DatabaseService` legado ampliar acoplamento | Novos recursos usam repository/service; migração gradual das leituras |
| Escopo crescer por todos os módulos | Entregar fases verticais começando pelo ciclo diário |

## 20. Decisões adotadas

- Dashboard continuará como primeira aba.
- Haverá uma única tarefa essencial por dia.
- Captura universal começa sem tipo obrigatório.
- Som e animação permanecem desativados por padrão.
- A escolha automática é local, determinística e explicável.
- Não haverá sequência diária, pontuação ou punição por dias não usados.
- Estado de produto ficará no SQLite; preferências visuais no sistema de preferências.
- As Fases 0 a 5 estão concluídas; novos recursos dependem de evidência do uso real, enquanto correções objetivas seguem o pacote de estabilização.

## 21. Questões para validar durante uso real

Estas questões não bloquearam a implementação e permanecem como hipóteses do piloto de uso real:

- Uma tarefa essencial e duas de apoio são suficientes na maioria dos dias?
- `Capacidade reduzida` é o termo mais claro ou deve ser substituído por outro?
- A captura universal deve permanecer como pequena janela ou painel sobreposto à tela atual?
- A pista de retomada deve ser vinculada sempre a uma tarefa ou também aceitar contexto livre?
- O encerramento do dia deve aparecer por horário configurável ou apenas por ação manual?

As respostas devem vir de uso real da aplicação, não de adicionar opções antecipadamente.

## 22. Primeiro pacote de trabalho

Este pacote executa a Fase 1. A ordem abaixo é a ordem recomendada de implementação.

### F1-01 — Sincronizar documentação

**Status:** Concluído em 2026-08-27.

**Escopo:** atualizar `README.md`, `DEVELOPMENT.md` e `CHANGELOG.md` para remover instruções antigas sobre modalidade de diálogos, intervalo fixo, animação contínua e atalhos `Ctrl/Cmd+S`.

**Aceite:** os três documentos descrevem o comportamento atual e apontam para esta spec; nenhum código é alterado.

### F1-02 — Corrigir toolchain Java 21

**Status:** Concluído em 2026-08-27.

**Escopo:** substituir `source/target` por `release 21` no compilador Maven e classificar warnings restantes entre corrigíveis e provenientes de dependências.

**Aceite:** `./mvnw clean test` passa; o warning de módulos causado por `-source/-target` desaparece; warnings externos são documentados, não mascarados indiscriminadamente.

### F1-03 — Tornar geometria testável

**Status:** Concluído em 2026-08-27.

**Escopo:** extrair do `WindowManager` o cálculo puro de limite e centralização, mantendo interação com `Stage` em uma camada fina.

**Aceite:** testes unitários cobrem janela menor, maior que a tela, owner parcialmente fora da tela e limites mínimos; resultado nunca ultrapassa `visualBounds`.

### F1-04 — Contratos JavaFX de janela

**Status:** Concluído em 2026-08-27.

**Escopo:** criar infraestrutura mínima de teste JavaFX para owner, modalidade, classe CSS comum e preservação de posição.

**Aceite:** testes executam de forma repetível pelo Maven ou, se headless não for confiável no ambiente, existe perfil separado documentado e a lógica pura continua coberta no teste padrão.

### F1-05 — Auditoria visual do grupo operacional

**Status:** Concluído em 2026-08-27.

**Janelas:** execução de protocolo, checklist de tarefa, checklist de projeto e Google Tasks.

**Aceite por janela:** cabe na matriz mínima, não corta ação essencial, textos longos quebram corretamente, tema alterna em tempo real e fechamento não altera a principal.

**Resultado:** barras de ações passam a quebrar linha nos checklists, na execução ativa de protocolo e no Google Tasks; títulos e itens extensos cedem espaço e quebram corretamente. Testes JavaFX cobrem responsividade, troca de tema durante a exibição e preservação da geometria da principal. A validação do estado nativo maximizado permanece na F1-08.

### F1-06 — Auditoria visual do grupo de conhecimento

**Status:** Concluído em 2026-08-27.

**Janelas:** diário de estudos, monitor de frequência, detalhe de ideia e revisão da caixa de entrada.

**Aceite:** mesmos critérios de F1-05, incluindo estados vazios e listas extensas.

**Resultado:** Diário com cabeçalho e índice flexíveis; Monitor com KPIs, legenda e ações de compensação responsivos; Detalhe de Ideia com metadados em grade e linhas sem fundo claro fixo; Revisão da Caixa de Entrada com rolagem vertical e ações que quebram linha. Estados vazios do Diário e Monitor e lista preenchida da revisão foram verificados em tema escuro e nos tamanhos mínimos.

### F1-07 — Auditoria visual do grupo utilitário

**Status:** Concluído em 2026-08-27.

**Janelas:** histórico de sessões, timer normal/compacto e pré-visualização de impressão.

**Aceite:** confirmar as correções existentes, testar escala de interface e registrar qualquer limitação nativa de impressão ou WebView.

**Resultado:** filtros e rodapé do Histórico quebram linha e o estado vazio está em português; Timer normal mantém metadados, controles, notas e histórico acessíveis no tamanho mínimo e o modo compacto limita títulos extensos com tooltip; Pré-visualização usa barra responsiva e acompanha o tema em tempo real, preservando o relatório como folha branca. As três janelas foram verificadas nos mínimos lógicos, que permanecem estáveis nas escalas de saída de 100%, 125% e 150% do JavaFX.

**Limitações conhecidas:** o conteúdo HTML do WebView representa o papel e não acompanha o fundo escuro; o diálogo de seleção de impressora pertence ao sistema operacional e pode ignorar o tema e a escala visual da aplicação. A impressão física depende de driver/impressora disponível e não é automatizável pela suíte JavaFX.

### F1-08 — Validação real de maximização

**Status:** Concluído em 2026-08-27.

**Escopo:** executar a matriz manual no gerenciador de janelas usado pelo usuário, pois o ambiente automatizado atual não consegue garantir o estado nativo de maximização.

**Aceite:** abrir e fechar pelo menos uma janela modeless, uma modal e uma secundária encadeada sem alterar `isMaximized` nem a geometria restaurada da principal.

**Resultado:** matriz executada no KDE/KWin Wayland com estado EWMH nativo. Histórico modeless, opções de impressão modal e timer normal→compacto→normal foram abertos e fechados com a principal maximizada em `1920×977`. Foi reproduzida e corrigida a inconsistência em que o KWin mantinha os átomos de maximização, mas restaurava a superfície para `1272×826` ao fechar um diálogo. Após o reparo central, a principal permaneceu maximizada em todos os fluxos e voltou exatamente aos limites restaurados iniciais `(336,122)`, `1260×820`.

### F1-09 — Fechamento da fase

**Status:** Concluído em 2026-08-27.

**Escopo:** executar testes, revisar diff, atualizar a matriz de estado desta spec e registrar limitações conhecidas.

**Aceite:** todos os itens F1 anteriores concluídos; `./mvnw test` e `git diff --check` passam; nenhum processo permanece ativo; Fase 2 pode começar sem débito visual bloqueante.

**Resultado:** F1-01 a F1-08 revisados e concluídos; matriz de estado sincronizada; suíte padrão com 10 testes e perfil JavaFX com 20 testes aprovados; `git diff --check` aprovado; nenhum processo da aplicação ativo e nenhuma alteração em banco ou dado pessoal incluída. A mudança da configuração do IntelliJ limita-se à permissão `javafx.web` documentada.

**Limitações não bloqueantes:** impressão física e aparência do diálogo nativo dependem do sistema/driver; o documento WebView permanece com aparência de papel; a matriz nativa de maximização foi executada no KDE/KWin Wayland; warnings restantes de `Unsafe` pertencem ao Maven/OpenJFX e estão documentados em `DEVELOPMENT.md`.

## 23. Segundo pacote de trabalho

Este pacote executa a Fase 2. O percentual da fase considera as cinco etapas abaixo.

### P2-01 — Núcleo persistente do plano diário

**Status:** Concluído em 2026-08-27.

**Escopo:** implementar modelo, migração idempotente, repository transacional, service e composição no `AppContext`.

**Aceite:** uma data aceita uma tarefa essencial e até duas de apoio; capacidade reduzida não aceita apoios; tarefas duplicadas, removidas ou concluídas são tratadas com segurança; edição não altera `created_at`; falha durante substituição preserva o plano anterior.

**Resultado:** tabelas `daily_plans` e `daily_plan_items` adicionadas com checks, unicidade e chaves estrangeiras; `DailyPlanService` concentra os limites e invalida plano sem essencial; sete testes cobrem migração, persistência, edição, regras, cascata e atomicidade.

### P2-02 — Entrada da vista Hoje

**Status:** Concluído em 2026-08-27.

**Escopo:** exibir `Começar meu dia` quando não houver plano e resumir o plano existente sem abrir modal automaticamente.

**Aceite:** DAY-01, DAY-05 e DAY-06; estados vazio, carregamento e erro tratados.

**Resultado:** a vista Hoje passa a abrir com um bloco `Meu dia`; sem plano, oferece `Começar meu dia` sem bloquear os demais módulos; com plano, restaura capacidade, tarefa essencial e apoios. Carga e falha possuem estados próprios, com nova tentativa, e nenhum modal é aberto automaticamente. O componente foi validado em largura estreita, tema escuro e navegação nativa de botão.

### P2-03 — Fluxo curto de planejamento

**Status:** Concluído em 2026-08-27.

**Escopo:** construir o fluxo de até três etapas para revisar, escolher essencial/apoios e confirmar a primeira ação.

**Aceite:** DAY-02 e DAY-04; seleção e reordenação não editam a tarefa original; todo o fluxo funciona por teclado.

**Resultado:** `Começar meu dia` e `Editar plano` abrem no próprio Dashboard as etapas de revisão, seleção e confirmação. A seleção aceita uma essencial e até dois apoios opcionais, permite limpar ou reordenar apoios e restaura escolhas existentes durante edição. A última etapa escolhe entre abrir a essencial na Agenda ou permanecer no Dashboard; salvar usa `DailyPlanService`, enquanto cancelar ou pressionar `Esc` descarta a edição. Testes JavaFX percorrem o fluxo e testes de serviço comprovam que reordenar o plano não modifica as tarefas originais.

### P2-04 — Integração com Agora

**Status:** Concluído em 2026-08-27.

**Escopo:** inserir a tarefa essencial do plano na ordem determinística do foco e oferecer início direto do timer.

**Aceite:** NOW-02, ordem de foco item 3 e cenário A completo sem alterar a maximização.

**Resultado:** a seleção do bloco `Agora` segue a precedência timer ativo, escolha manual, essencial do plano e sugestão automática, com origem textual explícita. `Iniciar foco` inicia o contador antes de abrir o timer; para um timer existente, a ação retoma quando pausado ou traz a janela em andamento. Alterações do timer atualizam o Dashboard por listener global. Planos com essencial concluída são invalidados e apoios concluídos são removidos com segurança. A precedência foi extraída para `FocusSelectionService` e coberta por testes determinísticos.

### P2-05 — Capacidade reduzida e fechamento

**Status:** Concluído em 2026-08-27.

**Escopo:** aplicar o modo reduzido na interface, reduzir destaque de alertas não críticos e fechar a matriz/testes da fase.

**Aceite:** DAY-03, testes padrão/JavaFX aprovados e saída da Fase 2 atingida.

**Resultado:** a etapa de revisão oferece controle segmentado entre `Ritmo normal` e `Capacidade reduzida`. No modo reduzido, os apoios são removidos e a seleção solicita somente a essencial; estudos, pendências antigas e indicadores recolhidos perdem peso decorativo, enquanto foco, prazos críticos e texto mantêm contraste. O fluxo foi validado no tema escuro com banco temporário. A suíte padrão aprovou 22 testes e o perfil JavaFX aprovou 39; `git diff --check` passou e nenhum processo permaneceu ativo.

## 24. Terceiro pacote de trabalho

Este pacote executa a Fase 3. O percentual da fase considera as cinco etapas abaixo.

### P3-01 — Núcleo persistente da caixa de entrada

**Status:** Concluído em 2026-08-27.

**Escopo:** implementar modelo, migração idempotente, repository, service e composição no `AppContext` para capturas inicialmente não classificadas.

**Aceite:** texto não vazio é preservado integralmente; toda captura nasce como `UNCLASSIFIED`, sem destino nem data de triagem; consultas retornam as mais recentes primeiro; schema rejeita estados incoerentes; testes usam banco temporário.

**Resultado:** `inbox_captures` e seu índice de consulta foram adicionados com tipos e consistência protegidos por `CHECK`; `InboxCaptureService` registra texto multilinha sem normalizá-lo e oferece listagem e contagem da caixa de entrada. Cinco testes cobrem migração idempotente, persistência, ordenação, limites e integridade, sem acessar o banco pessoal.

### P3-02 — Janela de captura rápida

**Status:** Concluído em 2026-08-27.

**Escopo:** construir `QuickCaptureWindow` com um único campo de texto, confirmação breve e tratamento de falha sem perder o conteúdo.

**Aceite:** CAP-02, CAP-03, CAP-06 e CAP-07; `Enter` salva, `Shift+Enter` cria linha, `Esc` fecha com confirmação apenas quando houver texto; tema, tamanho mínimo e teclado são testados.

**Resultado:** `QuickCaptureWindow` oferece somente o campo livre e os comandos essenciais em uma janela modeless gerenciada pelo `WindowManager`. `Enter` salva sem alterar o texto, `Shift+Enter` permanece com o comportamento multilinha do `TextArea` e `Esc` fecha diretamente quando vazio ou apresenta confirmação inline quando há conteúdo não salvo. O sucesso aparece brevemente sem modal ou som; falhas preservam o texto e oferecem nova tentativa. Seis testes JavaFX cobrem teclado, descarte, recuperação, tamanho mínimo e legibilidade no tema escuro.

### P3-03 — Acesso universal e atalho

**Status:** Concluído em 2026-08-27.

**Escopo:** disponibilizar captura em todas as abas e adicionar atalho configurável com padrão `Ctrl/Cmd+Shift+Space`.

**Aceite:** CAP-01 e CAP-05; abrir, salvar ou cancelar preserva aba, foco lógico, maximização e estado das demais janelas.

**Resultado:** o cabeçalho comum a todas as abas recebeu a ação `Capturar`, que abre uma única `QuickCaptureWindow` sem navegar ou modificar o conteúdo selecionado. O atalho persistente usa `Ctrl/Cmd+Shift+Espaço` por padrão e pode ser trocado nas Configurações por duas combinações alternativas, desativado ou restaurado; rebinding remove a combinação anterior imediatamente. Preferências inválidas retornam ao padrão. Três testes de preferência, três testes JavaFX de binding e um novo contrato de contexto cobrem persistência, desativação, troca de acelerador, aba ativa e geometria. O fluxo foi executado visualmente com banco e preferências temporários.

### P3-04 — Triagem da caixa de entrada

**Status:** Concluído em 2026-08-27.

**Escopo:** listar capturas pendentes e convertê-las de forma transacional em tarefa, ideia, nota de interrupção ou arquivo.

**Aceite:** CAP-04; conversão define tipo, data de triagem e destino quando aplicável, sem duplicar nem apagar a captura em caso de falha.

**Resultado:** o cabeçalho mostra a contagem de capturas pendentes e abre `InboxTriageWindow`, com lista cronológica, conteúdo integral, data explícita para tarefa e ações para tarefa, ideia, nota de interrupção ou arquivo. Tarefa e ideia são criadas na mesma transação que marca a captura, armazenam `target_id` e preservam o texto original; interrupção e arquivo mantêm o registro sem destino. Repetição não duplica, falha no destino faz rollback e falha posterior de refresh não altera nem reporta incorretamente o resultado persistido. Cinco testes de serviço e quatro contratos JavaFX cobrem destinos, atomicidade, erro, vazio, responsividade e tema escuro. O percurso captura, contagem e triagem foi validado visualmente com dados temporários.

### P3-05 — Compatibilidade com Ideias e fechamento

**Status:** Concluído em 2026-08-27.

**Escopo:** integrar a caixa universal ao fluxo existente de Ideias, validar dados anteriores e fechar a matriz visual e automatizada da fase.

**Aceite:** CAP-08; ideias existentes permanecem acessíveis sem perda de conteúdo ou vínculos; testes padrão/JavaFX e matriz claro/escuro são aprovados; saída da Fase 3 é atingida.

**Resultado:** ideias criadas pela triagem universal entram como `nova` na categoria técnica `Caixa de entrada`, possuem vínculo reversível pelo `target_id` e aparecem na revisão especializada existente. A interface distingue `Caixa de entrada` universal de `Revisão de ideias`, reduzindo ambiguidade entre as filas. A migração em massa das ideias anteriores foi descartada por ser desnecessária e criar risco de duplicação: o schema é aditivo e os registros continuam no `project_ideas`. Teste dedicado comprova preservação de todos os campos avançados, descrição e referência, vínculo entre ideia-mãe e filha e checklist após nova migração e nova captura universal. A suíte padrão aprovou 36 testes e o perfil JavaFX aprovou 68; tema escuro, tamanhos mínimos e fluxo visual com dados temporários permanecem aprovados; `git diff --check` passou e nenhum processo ficou aberto.

## 25. Auditoria programada de alertas

Este pacote deve ser executado após a Fase 3 e antes de ampliar notificações na Fase 4. O beep é um apoio de foco útil, mas deve permanecer previsível e integralmente controlável.

**Status do pacote:** Concluído em 2026-08-27, 100% (3 de 3 itens concluídos).

### ALT-01 — Controle geral confiável

**Status:** Concluído em 2026-08-27.

**Escopo:** fazer o controle `Lembretes periódicos` interromper timer, som e animação imediatamente, mantendo apenas contagens estáticas visíveis.

**Aceite:** NTF-01, NTF-05 e NTF-10; desligar durante uma animação a encerra; atalho e fallback não emitem beep com o controle desligado; reativar restaura as preferências anteriores.

**Resultado:** `Lembretes periódicos` tornou-se uma barreira geral: ao desligar, cancela o timer, interrompe a saída sonora ativa e, pelo callback imediato da configuração, encerra a animação do indicador. Verificações periódicas e manuais consultam o controle antes de áudio ou callback visual; o fallback também revalida as preferências antes do beep. As contagens `A/H/P` continuam visíveis e estáticas, enquanto as ações de lembrar e pausar ficam indisponíveis. Som, animação e intervalo são preservados para a reativação. Cinco testes usam preferências e saída sonora isoladas, sem tocar beep nem alterar preferências pessoais. A suíte padrão aprovou 41 testes e o perfil JavaFX aprovou 73; a aba Configurações foi validada visualmente com banco e preferências temporários, incluindo desligamento e restauração das opções.

### ALT-02 — Estímulo sonoro cauteloso

**Status:** Concluído em 2026-08-27.

**Escopo:** impedir sobreposição e repetição desnecessária do áudio, mantendo o som opcional e adicionando uma forma deliberada de testá-lo nas configurações.

**Aceite:** NTF-02, NTF-06, NTF-09 e NTF-11; nenhum som na inicialização; um único som por evento; pausa e horário silencioso são respeitados; teste de som só ocorre por ação explícita.

**Resultado:** a saída sonora recusa nova reprodução enquanto um clip está ativo, em vez de interromper e reiniciar o áudio. Inicialização e agendamento não emitem som imediato. Pausa, controle geral e horário silencioso são consultados antes do arquivo e novamente antes do beep de fallback; iniciar uma pausa ou uma faixa silenciosa vigente interrompe som ativo. A faixa silenciosa é opt-in, persiste início e fim em intervalos de 30 minutos e trata corretamente períodos que atravessam meia-noite, mantendo callback visual e contagens. A aba Configurações ganhou controles próprios e `Testar som`, cuja única reprodução ocorre por ação explícita e informa quando pausa, faixa silenciosa ou áudio em curso impedem o teste. Onze testes isolados usam relógio e saída simulados. A suíte padrão aprovou 47 testes e o perfil JavaFX aprovou 79. A validação visual clara/escura com dados temporários também corrigiu um resíduo em que checkboxes desmarcados podiam exibir a marca no tema escuro.

### ALT-03 — Movimento limitado e testes

**Status:** Concluído em 2026-08-27.

**Escopo:** limitar a animação a três ciclos e adicionar testes isolados para preferências, timer, pausa, atalho, áudio e callback visual.

**Aceite:** NTF-03, NTF-04, NTF-08 e NTF-12; preferências de teste não alteram as preferências pessoais; ativar/desativar pela interface é coberto por teste JavaFX.

**Resultado:** o badge usa `StatusAlertAnimator`, que executa exatamente três ciclos de 1,2 segundo, ignora tentativas de reinício enquanto está ativo e sempre retorna à opacidade integral ao terminar ou ser interrompido. O atalho foi isolado em `ReminderShortcutBinding` e o callback visual passou a usar dispatcher injetável, mantendo `Platform.runLater` em produção. Treze testes do serviço cobrem preferências, timer, controle geral, pausa, faixa silenciosa, áudio e callback; contratos JavaFX cobrem três ciclos, interrupção, atalho `Ctrl/Cmd+Shift+R` sem `Ctrl/Cmd+S` e ativação/desativação independente dos controles com preferências temporárias. A suíte padrão aprovou 49 testes e o perfil JavaFX aprovou 86; `git diff --check` passou e nenhum processo ficou aberto.

**Fechamento:** todos os achados iniciais foram corrigidos. A ampliação futura de notificações permanece condicionada aos requisitos NTF e aos testes isolados deste pacote.

## 26. Auditoria programada da sincronização Google Tasks

Este pacote sucede a auditoria de alertas. A validação ao vivo deve ocorrer somente depois dos testes com transporte simulado e com confirmação explícita, pois a implementação atual pode criar, atualizar, concluir ou excluir dados na conta conectada.

**Status do pacote:** Concluído em 2026-08-27, 100% (4 de 4 itens concluídos).

**Checklist de avanço:**

- [x] GSYNC-01 — Núcleo determinístico e idempotente
- [x] GSYNC-02 — Paginação, exclusões e conflitos
- [x] GSYNC-03 — Rede, autenticação e recuperação
- [x] GSYNC-04 — Interface e validação controlada (4 de 4 verificações)

### GSYNC-01 — Núcleo determinístico e idempotente

**Status:** Concluído em 2026-08-27.

**Escopo:** separar transporte Google, planejamento do sync e aplicação de mudanças; fazer criação retornar o ID local diretamente e persistir tarefa/mapeamento de forma atômica.

**Aceite:** GSY-01, GSY-02 e GSY-07; repetir o mesmo cenário não duplica; falha entre criação e mapeamento pode ser retomada; títulos iguais nunca são usados para descobrir identidade.

**Resultado:** o transporte HTTP implementa `GoogleTasksGateway` e a orquestração saiu de `GoogleTasksService` para `GoogleTasksSyncService`, que calcula um `SyncPlan` antes de aplicar mudanças. `TaskRepository.saveReturningId()` devolve a identidade gerada diretamente. Importações automáticas e manuais usam `GoogleTasksSyncRepository` para inserir tarefa e mapeamento na mesma transação; repetição retorna o ID já mapeado. Exportações consultam o mapeamento antes de criar e, se sua gravação falhar após a criação remota, removem a tarefa Google sem identidade local para permitir nova tentativa limpa. Atualizações remotas só são enviadas quando título, notas ou data diferem e agora entram no resumo do sync. Sete testes com gateway falso e SQLite temporário cobrem títulos iguais com IDs distintos, repetição sem escritas, importação manual, atualização única, rollback e compensação. A suíte padrão aprovou 56 testes e o perfil JavaFX aprovou 93, sem acessar conta, tokens ou rede Google.

### GSYNC-02 — Paginação, exclusões e conflitos

**Status:** Concluído em 2026-08-27.

**Escopo:** percorrer todas as páginas, tratar mapeamentos órfãos e definir decisões explícitas para texto, conclusão, reabertura e exclusão em ambos os lados.

**Aceite:** GSY-03 e GSY-04; mais de 100 itens são processados; exclusões não bloqueiam sincronizações futuras; alterações concorrentes não são sobrescritas silenciosamente.

**Resultado:** listas e tarefas percorrem todas as páginas por `nextPageToken`, com tombstones solicitados explicitamente apenas pelo motor de sincronização. O mapeamento persiste a fotografia de título, notas, data e status da última sincronização. Alterações unilaterais são propagadas na direção correta; conclusão e reabertura funcionam nos dois sentidos. Divergências simultâneas recebem `CONFLICT`, enquanto exclusões recebem `LOCAL_DELETED` ou `REMOTE_DELETED`; em ambos os casos o outro lado é preservado e o resumo informa a necessidade de revisão. Oito testes novos cobrem paginação HTTP, processamento de 150 tarefas, edição remota, conflito concorrente, convergência sem falso conflito, exclusões nos dois lados e reabertura bidirecional. A suíte padrão aprovou 64 testes e o perfil JavaFX aprovou 101, sem acessar conta, tokens ou rede Google.

### GSYNC-03 — Rede, autenticação e recuperação

**Status:** Concluído em 2026-08-27.

**Escopo:** adicionar timeouts, validação de status OAuth/API, classificação de erros, nova tentativa segura e proteção adequada do arquivo de tokens.

**Aceite:** GSY-05 e GSY-06; falhas de rede/token têm mensagem acionável, não registram segredos e deixam o estado recuperável.

**Resultado:** o transporte injetável aplica timeout de conexão e requisição, valida todo status Tasks/OAuth e classifica autenticação, limite, timeout, rede, servidor, resposta inválida e requisição. Leituras e mutações idempotentes repetem no máximo uma vez em falhas transitórias; criação não repete após resposta ambígua, e `401` invalida o access token para uma única renovação. Token revogado remove a autorização local, enquanto falha transitória preserva a nova tentativa. Respostas truncadas, IDs ausentes e paginação cíclica abortam antes do plano. Corpos da API e mensagens brutas não chegam a logs ou diálogos. Arquivos de credenciais/tokens são criados ou migrados para permissão POSIX `600`. A interface informa início/fim por lista, itens Google/local processados, revisões, erros e ação de recuperação. Treze testes novos cobrem status, timeout, limite, repetição segura, renovação, resposta parcial sem mutação, sanitização, permissão e resumo. A suíte padrão aprovou 77 testes e o perfil JavaFX aprovou 114, sem acessar conta, tokens ou rede Google.

### GSYNC-03.1 — Escolha segura da conta no OAuth

**Status:** Concluído em 2026-08-30.

**Problema:** a conexão abria imediatamente o navegador padrão com `prompt=consent`. Isso podia reutilizar uma sessão Google de outra conta e não oferecia a URL para abertura no navegador ou perfil correto.

**Resultado:** antes da conexão, a interface oferece `Abrir e copiar link` e `Somente copiar link`. A URL solicita ao Google `select_account` junto do consentimento, permanece disponível na área de transferência e não contém o segredo do cliente. O servidor local reserva a porta durante todo o fluxo e o callback passa a usar e validar um `state` aleatório antes de confirmar o sucesso no navegador. As permissões continuam limitadas ao Google Tasks; nenhuma permissão de perfil foi adicionada. A suíte padrão aprovou 121 testes e o perfil JavaFX aprovou 181, sem acessar a conta ou a rede Google.

### GSYNC-03.2 — Configuração central da integração

**Status:** Concluído em 2026-08-30.

**Problema:** a integração só podia ser administrada dentro da janela de sincronização, enquanto a aba Configurações concentrava aparência, alertas, métricas e categorias numa única rolagem extensa. O estado das credenciais, a quantidade de vínculos e as consequências de desconectar não estavam visíveis num local central.

**Resultado:** Configurações foi dividida em `Geral`, `Integrações` e `Categorias`. O painel Google Tasks informa conexão, credenciais OAuth, permissão concedida, modo manual com prévia e quantidade de vínculos locais. Ele permite conectar com escolha de conta/cópia do link, desconectar, atualizar o estado, abrir a sincronização e limpar somente os vínculos locais mediante confirmação. Desconectar preserva vínculos; limpá-los não exclui tarefas e alerta sobre possíveis duplicatas numa nova sincronização. Conexão, desconexão e limpeza ficam bloqueadas durante outra operação Google. O fluxo OAuth é compartilhado entre as duas telas e a barra de ações quebra linha em larguras menores. A suíte padrão aprovou 122 testes e o perfil JavaFX aprovou 185, sem acessar a conta ou a rede Google.

### GSYNC-03.3 — Cancelamento da autorização OAuth

**Status:** Concluído em 2026-08-30.

**Problema:** fechar a aba de autorização no navegador deixava a aplicação aguardando o callback local por até dois minutos. Durante esse período, os controles Google permaneciam bloqueados e não havia uma forma visível de cancelar ou tentar novamente.

**Resultado:** cada tentativa OAuth possui uma sessão cancelável que fecha imediatamente o servidor local de callback. `Cancelar conexão` aparece durante a espera tanto em Configurações quanto na janela Google Tasks; fechar a janela de sincronização também cancela a tentativa. O cancelamento é tratado como decisão do usuário, sem diálogo de erro, libera o bloqueio global e reativa `Conectar conta` para uma nova tentativa. A suíte padrão aprovou 123 testes e o perfil JavaFX aprovou 187, sem acessar a conta ou a rede Google.

### GSYNC-03.4 — Títulos íntegros nos comandos de sincronização

**Status:** Concluído em 2026-08-30.

**Problema:** após uma sincronização, o resumo extenso do rodapé disputava largura com os comandos inferiores. JavaFX comprimia os botões e apresentava títulos incompletos ou com reticências.

**Resultado:** somente o texto de status passa a ceder largura e exibir reticências, mantendo o conteúdo completo em tooltip. Botões do cabeçalho, sincronização, ações manuais, log e rodapé preservam sua largura preferida; as ações manuais continuam quebrando linha. Teste JavaFX em rodapé de 310 px confirma que `Atualizar` e `Fechar` permanecem integrais e sem sobreposição. A suíte padrão aprovou 123 testes e o perfil JavaFX aprovou 188.

### GSYNC-03.5 — Título correto no log bidirecional

**Status:** Concluído em 2026-08-30.

**Problema:** na atualização Google → local, a operação era aplicada corretamente, mas o log exibia o título local anterior porque a mensagem reutilizava o objeto capturado antes da mutação.

**Resultado:** mensagens de texto e status agora usam explicitamente o título do lado que originou a alteração: Google nas atualizações locais e local nas atualizações remotas. Um teste de regressão exige que uma edição remota registre o novo título recebido. A suíte padrão aprovou 123 testes e o perfil JavaFX aprovou 188, sem acessar a conta ou a rede Google.

### GSYNC-03.6 — Comparação informada de conflitos

**Status:** Concluído em 2026-08-30.

**Problema:** a revisão identificava a pendência e explicava a consequência da escolha, mas não mostrava os valores atuais dos dois lados. Resolver conflitos reais exigia escolher entre local e Google sem comparar título, notas, data e status.

**Resultado:** `Revisar pendências` carrega as versões atuais em segundo plano e apresenta `Versão local` e `Versão Google` lado a lado antes das decisões. Cada lado mostra título, status, data e notas; tarefas excluídas ou ausentes aparecem explicitamente como indisponíveis. Trocar o item limpa a escolha anterior, e `Aplicar decisão` continua desabilitado até uma nova escolha explícita. O comparador usa controles somente leitura compatíveis com tema escuro e mantém colunas sem sobreposição em 620 px. Testes cobrem conflitos, ambos os sentidos de exclusão, conteúdo comparativo e contraste. A suíte padrão aprovou 125 testes e o perfil JavaFX aprovou 191, sem acessar a conta ou a rede Google.

### GSYNC-03.7 — Visibilidade das tarefas relacionadas

**Status:** Concluído em 2026-08-30.

**Problema:** o painel local mostrava apenas tarefas abertas, ocultando tarefas concluídas que ainda possuíam vínculo ou conflito Google. A revisão apresentava uma pendência por vez em um seletor fechado, o que dava a impressão de que itens como `Ler Capítulo 7 do Web Application Hacker's Handbook` não estavam mapeados.

**Resultado:** ao selecionar uma lista Google, o painel `Tarefas Locais Relacionadas` reúne tarefas abertas e todas as tarefas vinculadas àquela lista, inclusive concluídas. A ação foi renomeada para `Revisar conflitos/exclusões`, deixando claro que ela não representa todas as tarefas Google. O diálogo mostra a contagem e até cinco conflitos/exclusões simultaneamente em uma lista visível; selecionar outra linha atualiza o comparador e limpa a decisão anterior. Teste JavaFX confirma as cinco linhas, incluindo o título extenso do capítulo, sem depender da abertura de um `ComboBox`. A suíte padrão aprovou 125 testes e o perfil JavaFX aprovou 192.

### GSYNC-03.8 — Confirmação forte de substituição

**Status:** Concluído em 2026-08-30.

**Problema:** mesmo com comparação e escolha explícita, `Aplicar decisão` executava imediatamente. Durante o piloto, duas resoluções foram aplicadas sem intenção clara e o usuário não conseguiu recordar o segundo item nem o lado escolhido.

**Resultado:** o primeiro diálogo agora usa `Continuar...` e nunca executa diretamente. Uma segunda confirmação repete item, lado escolhido, consequência e ausência de desfazer automático; seu botão nomeia `Aplicar versão local` ou `Aplicar versão Google`. `Cancelar` é o botão padrão, inclusive para acionamento por teclado, e abortar informa que nenhuma versão foi substituída. Testes verificam o resumo irreversível e os estados JavaFX dos botões. A suíte padrão aprovou 126 testes e o perfil JavaFX aprovou 194.

### GSYNC-03.9 — Auditoria Google somente leitura

**Status:** Concluído em 2026-08-31.

**Problema:** a validação ao vivo exigia que o usuário abrisse e transcrevesse cada comparação, tornando a investigação lenta e sujeita a cliques acidentais. O comparador também reduzia todo `done = 1` a `Concluída`, ocultando estados locais mais ricos como `Cancelada`.

**Resultado:** `GoogleTasksReadOnlyAudit` reutiliza a autorização persistida para consultar listas/tarefas e cruza os resultados com o SQLite sem sincronizar, resolver ou excluir. O relatório omite tokens, IDs remotos e conteúdo de notas; mostra contagens, conflitos, campos divergentes e listas vinculadas inacessíveis na conta atual. `ReviewVersion` agora separa o booleano compatível com Google do rótulo local real, preservando `Pendente`, `Em andamento`, `Concluída`, `Bloqueada` e `Cancelada` na interface e auditoria. Testes cobrem diferenças seletivas, indisponibilidade, cancelamento local e lista ausente. A suíte padrão aprovou 130 testes e o perfil JavaFX aprovou 198. A execução real encontrou 18 tarefas e 18 vínculos em `Minhas tarefas`, três conflitos somente de status e cinco vínculos de uma lista não retornada pela conta atual.

### GSYNC-03.10 — Resolução automatizada com escopo explícito

**Status:** Concluído em 2026-08-31.

**Problema:** resolver vários conflitos repetindo o fluxo visual era lento e aumentava a exposição a escolhas acidentais, embora a decisão semântica continuasse dependendo do usuário.

**Resultado:** `GoogleTasksConflictResolver` recebe somente IDs explicitamente autorizados e pré-valida o lote inteiro antes da primeira escrita. Cada vínculo deve existir, permanecer em `CONFLICT`, ter os dois lados disponíveis e possuir exclusivamente diferença de status; qualquer divergência adicional aborta o lote. A ferramenta não escolhe o lado pelo usuário. Três testes cobrem IDs explícitos, aprovação restrita a status e rejeição de campo não autorizado. A suíte padrão aprovou 133 testes e o perfil JavaFX aprovou 201.

### GSYNC-04 — Interface e validação controlada

**Status:** Concluído em 2026-08-27.

**Checklist interna:**

- [x] impedir operações Google concorrentes;
- [x] exibir preview antes de aplicar mudanças;
- [x] permitir resolução assistida de conflitos e exclusões;
- [x] validar ida, volta, repetição e falhas com infraestrutura simulada.

**Escopo:** impedir operações concorrentes, exibir preview/resumo de mudanças e validar com servidor simulado antes de uma lista Google descartável.

**Aceite:** todos os requisitos GSY; testes automatizados cobrem ida, volta, repetição, conflito, exclusão, paginação e falhas; teste ao vivo só ocorre após confirmação e backup.

**Resultado:** a janela usa um guardião global para impedir conexão, carga, sincronização, importação, exportação, remoção e resolução concorrentes, inclusive após fechar e reabrir. `prepareSync()` gera preview sem mutação; o usuário confirma antes da aplicação, e `applyPrepared()` revalida os dois lados, aplica uma única vez ou exige nova prévia se o estado mudou. `Revisar pendências` lista conflitos e exclusões e oferece decisões concretas para usar a versão local/Google, recriar, restaurar ou aceitar a exclusão, sempre informando a consequência. Nove testes novos cobrem exclusão mútua, preview sem mutação, aplicação única, expiração, apresentação e resoluções de conflito/exclusão. A suíte padrão aprovou 86 testes e o perfil JavaFX aprovou 123. A validação automatizada usou transporte, gateway e SQLite simulados; a validação ao vivo posterior é acompanhada separadamente em `GSYNC-LIVE`.

**Fechamento:** todos os achados automatizáveis do pacote foram corrigidos. A checklist acima é a fonte de acompanhamento do percentual automatizado; a validação operacional com conta real é controlada pela checklist `GSYNC-LIVE` abaixo.

### GSYNC-LIVE — Validação ao vivo controlada

**Status operacional:** Concluído em 2026-08-31, 100% (8 de 8 verificações concluídas). Este percentual não reduz os 100% do pacote automatizado.

**Evidência segura registrada:** após a conexão e a primeira sincronização executadas pelo usuário, `PRAGMA quick_check` permaneceu `ok`. O banco contém 22 vínculos em duas listas, sem IDs Google duplicados, tarefas locais duplicadamente vinculadas ou referências locais órfãs. Em comparação ao backup pré-teste, cinco vínculos são novos: três pertencem a tarefas locais preexistentes, evidência compatível com exportação, e dois pertencem a tarefas locais novas, evidência compatível com importação. Nenhuma tarefa local preexistente foi removida. Existem 17 vínculos ativos e cinco vínculos preexistentes agora marcados como conflito, que ainda exigem revisão explícita. A repetição na lista `Minhas tarefas` apresentou zero criações, atualizações, mudanças de status e revisões; terminou com `nenhuma alteração detectada`, mantendo contagens e integridade locais inalteradas. A primeira etapa do `LIVE-06` importou o item descartável com exatamente `1 criar local` e zero nas demais ações. O banco permaneceu íntegro e confirmou uma única tarefa local, um único vínculo `ACTIVE` e nenhuma duplicação para o ID Google. O título retornado pelo Google continha um espaço inicial, preservado igualmente na tarefa e no snapshot. Em seguida, a edição local para `TESTE Agenda 2026-08-30 02 LOCAL` produziu exatamente `1 atualizar Google`. Nova inspeção confirmou `quick_check = ok`, unicidade do item e snapshot `ACTIVE` idêntico ao título local, sem criação adicional. A edição inversa para `TESTE Agenda 2026-08-30 03 GOOGLE` produziu exatamente `1 atualizar local`; tarefa e snapshot assumiram o novo título, permaneceram únicos e `ACTIVE`. O log exibiu indevidamente o título anterior, originando a correção `GSYNC-03.5`, sem perda ou divergência de dados. A conclusão local produziu exatamente `1 status Google`; o banco permaneceu íntegro, com a tarefa única em `CONCLUIDA`, `done = 1`, snapshot `synced_done = 1` e vínculo `ACTIVE`. Por fim, a reabertura no Google produziu exatamente `1 status local`; tarefa e snapshot retornaram a `done = 0`, a tarefa ficou `PENDENTE`, o vínculo continuou único e `ACTIVE`, não surgiram órfãos e `quick_check` permaneceu `ok`. Na preparação do `LIVE-07`, a versão `GSYNC-03.7` exibiu corretamente os cinco conflitos simultâneos e incluiu no painel local as tarefas concluídas vinculadas, inclusive `Ler Capítulo 7 do Web Application Hacker's Handbook`. Durante a inspeção, resoluções foram aplicadas acidentalmente ao capítulo e a `Pegar a Royal no Motorock`; ambas passaram a `ACTIVE`. Comparação campo a campo confirmou que título, notas, data, conclusão e status locais das duas permanecem idênticos ao backup pré-teste. Os outros três vínculos continuam `CONFLICT`, nenhuma exclusão ocorreu, não existem órfãos e `quick_check = ok`. O incidente originou a barreira `GSYNC-03.8`. Após reiniciar com a proteção, o usuário selecionou a versão local de `Consulta Rosy`, avançou até a segunda confirmação e cancelou. A interface manteve a contagem em três; inspeção confirmou os três estados e timestamps inalterados, sem snapshot novo, e `quick_check = ok`.

**Evidência complementar do LIVE-07:** antes da resolução foi criado o backup íntegro `agenda-before-google-conflicts-local-20260831-053120.db`, contendo 20 vínculos `ACTIVE` e os três `CONFLICT` esperados. Após autorização explícita para preservar o lado local, a ferramenta restrita resolveu somente os vínculos 14, 61 e 91. A auditoria posterior encontrou 18 tarefas, 18 vínculos e zero revisões em `Minhas tarefas`; o SQLite ficou com 23 vínculos `ACTIVE`, nenhum órfão e `quick_check = ok`. Os estados locais permaneceram `CONCLUIDA`, `CONCLUIDA` e `CANCELADA`; por limitação semântica do Google Tasks, os três estados `done = 1` foram representados remotamente como concluídos. Os cinco vínculos da lista inacessível permaneceram intocados.

**Exclusão controlada do LIVE-07:** a inspeção somente leitura identificou os cinco vínculos inacessíveis como tarefas locais concluídas de uma única lista antiga, sem órfãos; eles foram excluídos do teste. Após o backup íntegro `agenda-before-google-controlled-deletion-20260831-054216.db`, somente o item descartável `TESTE Agenda 2026-08-30 03 GOOGLE` foi excluído no Google. A prévia apresentou exatamente uma revisão `REMOTE_DELETED`, com a versão local disponível e a Google indisponível. Preservar o lado local liberou apenas o vínculo antigo e a prévia seguinte apresentou exatamente uma recriação Google. O novo vínculo 133 ficou `ACTIVE`, a tarefa local permaneceu `PENDENTE`, `done = 0`, e título e snapshot convergiram. A auditoria final encontrou 18 tarefas, 18 vínculos e zero revisões na lista acessível; o banco permaneceu com 23 vínculos `ACTIVE`, nenhum órfão, cinco vínculos antigos intocados e `quick_check = ok`.

**Evidência do LIVE-08:** antes do teste foram criados backups íntegros do banco e do arquivo de tokens, ambos sem exposição de credenciais. Uma sessão OAuth real foi iniciada em modo de cópia de link, chegou ao estado de espera pelo callback e foi cancelada programaticamente; a espera terminou em 1 ms com `CancellationException`, sem bloquear a aplicação. Após o cancelamento, o arquivo de tokens permaneceu byte a byte idêntico, em modo `600`, a autorização anterior continuou válida e o banco permaneceu com 23 vínculos `ACTIVE`. Para validar a retomada, o access token em memória foi invalidado e o serviço obteve outro pelo refresh token, consultando uma lista e 18 tarefas. O refresh token permaneceu idêntico ao backup; somente access token e expiração foram renovados. A auditoria final encontrou zero revisões, nenhum órfão e `quick_check = ok`; os cinco vínculos da lista antiga permaneceram intocados.

**Checklist ao vivo:**

- [x] LIVE-01 — Conectar a conta escolhida pelo fluxo OAuth
- [x] LIVE-02 — Executar a primeira sincronização mantendo a integridade local
- [x] LIVE-03 — Observar importação Google → local sem órfãos ou duplicação de vínculo
- [x] LIVE-04 — Observar exportação local → Google com vínculo persistido
- [x] LIVE-05 — Repetir sem alterações e confirmar zero criações/atualizações
- [x] LIVE-06 — Validar edição, conclusão e reabertura nos dois sentidos com item descartável
- [x] LIVE-07 — Revisar os cinco conflitos existentes e validar uma exclusão controlada
- [x] LIVE-08 — Cancelar uma autorização pendente, tentar novamente e validar reconexão

**Fechamento:** o protocolo ao vivo foi concluído sem perda de dados. A sincronização permanece manual e opt-in; os cinco vínculos de uma lista antiga inacessível estão preservados para decisão futura e não bloqueiam a lista atual.

## 27. Implementação da interrupção e retomada

**Status do pacote:** Concluído em 2026-08-28, 100% (4 de 4 itens concluídos).

**Checklist de avanço:**

- [x] P4-01 — Persistir uma única pista de retomada vinculada à tarefa
- [x] P4-02 — Adicionar `Fui interrompido` ao timer
- [x] P4-03 — Mostrar a pista e `Retomar` no Dashboard
- [x] P4-04 — Recuperar ou descartar timer após encerramento inesperado

### P4-01 — Contexto persistente de foco

**Status:** Concluído em 2026-08-28.

**Escopo:** criar migração, modelo, repositório e serviço para uma única pista corrente, vinculada a uma tarefa existente e aberta.

**Aceite:** FOC-05 e estrutura 11.3; salvar uma nova pista substitui a anterior atomicamente; reiniciar o serviço preserva texto, tarefa e horário; tarefa concluída ou inexistente não recebe pista nova.

**Resultado:** `focus_context` guarda uma única pista vinculada por chave estrangeira a uma tarefa. `FocusContextService` valida texto e tarefa aberta, substitui a pista em transação, restaura o contexto após recriação da camada de serviço e remove a pista ao concluir a retomada. Exclusão da tarefa remove o contexto por cascata; falhas durante a substituição preservam a pista anterior. Sete testes novos cobrem migração idempotente, reinício, substituição única, rollback, validações, retomada e exclusão. A suíte padrão aprovou 93 testes e o perfil JavaFX aprovou 130; `git diff --check` passou.

### P4-02 — Interromper pelo timer

**Status:** Concluído em 2026-08-28.

**Escopo:** adicionar `Fui interrompido` ao timer normal e ao modo compacto, com um único campo para registrar onde parou ou o próximo passo.

**Aceite:** FOC-04 e FOC-05; acionar a interrupção pausa o contador durante a captura; salvar mantém a tarefa ativa e pausada com pista persistida; cancelar retoma somente um timer que estava rodando; campo vazio e falha de persistência não fecham o diálogo nem perdem o texto.

**Resultado:** o timer principal oferece o comando textual `Fui interrompido` e o modo compacto expõe a mesma ação com tooltip. O diálogo reutiliza a pista existente da tarefa, orienta a registrar apenas o próximo passo concreto e permite nova tentativa sem apagar o conteúdo. Ao salvar, o timer permanece associado à tarefa e pausado; ao cancelar, volta a rodar somente se esse era o estado anterior. A auditoria encontrou que diálogos novos ainda podiam nascer sem CSS escuro antes do hook global; `WindowManager.prepare()` agora aplica o tema diretamente ao `DialogPane`, eliminando o cabeçalho escuro residual. Cinco contratos JavaFX cobrem integração, cancelamento, retry, validação, modo compacto, limites e contraste. A suíte padrão aprovou 93 testes e o perfil JavaFX aprovou 135; `git diff --check` passou.

### P4-03 — Retomar pelo Dashboard

**Status:** Concluído em 2026-08-28.

**Escopo:** restaurar a pista persistida no bloco `Agora`, identificar a tarefa e permitir retomar em uma ação direta.

**Aceite:** FOC-06 e cenário C; uma pista válida aparece após reconstruir o Dashboard com o rótulo `Retomada pendente`, título da tarefa e texto `Onde você parou`; o estado oferece somente `Retomar` e `Abrir tarefa`; retomar inicia o timer, abre sua janela e só então remove a pista.

**Resultado:** uma pista válida assume temporariamente o bloco `Agora` acima da seleção automática, manual ou do plano, sem alterar a precedência normal quando não existe retomada. O card usa destaque de aviso moderado, mantém títulos e pistas longas com quebra de linha e reduz as ações imediatas a duas. `Retomar` inicia ou continua o timer, abre a tarefa e remove o contexto somente após essas etapas; em seguida, o bloco volta ao estado `Timer em andamento`. Preferências e abertura de janela foram injetadas no controller para testes sem tocar dados pessoais. Textos auxiliares do Dashboard deixaram de aplicar tokens por estilo inline, eliminando avisos e resíduos durante a montagem do tema. Três contratos JavaFX cobrem reconstrução, fluxo completo, quantidade de ações, pista longa, largura reduzida e contraste escuro. A suíte padrão aprovou 93 testes e o perfil JavaFX aprovou 138; `git diff --check` passou.

### P4-04 — Recuperação controlada do timer

**Status:** Concluído em 2026-08-28.

**Escopo:** persistir checkpoints do timer ativo e, na inicialização seguinte, exigir decisão entre recuperar e descartar o intervalo anterior.

**Aceite:** FOC-08; checkpoint contém tarefa, segundos efetivamente contados, estado e horário; tarefa removida ou concluída invalida o registro; a aplicação não inicia o rastreamento novo antes da decisão; recuperar restaura os segundos exatos com timer pausado; descartar limpa o registro e não cria sessão; fechar o diálogo ou usar `Esc` não toma decisão implícita.

**Resultado:** `timer_recovery` mantém uma única fotografia vinculada à tarefa. `TaskTimerRecoveryService` grava no primeiro segundo, em intervalos de cinco segundos, em mudanças de estado e no fechamento normal; parar o timer remove o checkpoint. Na próxima abertura, `TimerRecoveryDialog` informa tarefa, duração, estado e horário do último checkpoint, explica que o período fora da aplicação não será somado e exige `Recuperar pausado` ou `Descartar intervalo`. A recuperação restaura o contador e abre sua janela sem salvar sessão nem voltar a contar automaticamente. Sete testes de domínio cobrem migração, persistência entre instâncias, checkpoint durante execução, restauração exata, descarte, parada e invalidação; três contratos JavaFX cobrem conteúdo, decisões obrigatórias, limites e tema escuro. A suíte padrão aprovou 100 testes e o perfil JavaFX aprovou 148; `git diff --check` passou.

**Fechamento:** a Fase 4 está concluída. Pista de interrupção, retomada pelo Dashboard e recuperação do timer agora formam um fluxo persistente, explícito e sem contabilização silenciosa.

## 28. Implementação do encerramento e revisão

**Status do pacote:** Concluído em 30/08/2026, 100% (4 de 4 itens concluídos).

**Checklist de avanço:**

- [x] P5-01 — Resumir e encerrar/reabrir o plano do dia
- [x] P5-02 — Decidir itens abertos e preparar a primeira tarefa de amanhã
- [x] P5-03 — Agrupar pendências antigas por faixas neutras
- [x] P5-04 — Adicionar métricas locais opcionais e concluir a fase

### P5-01 — Resumo persistente do dia

**Status:** Concluído em 28/08/2026.

**Escopo:** preservar a fotografia original do plano, reunir tarefas concluídas, sessões registradas e itens ainda abertos, e permitir encerrar ou reabrir o mesmo dia.

**Aceite:** REV-01, REV-02 e REV-05; `Encerrar meu dia` aparece quando existe plano; concluir uma tarefa não apaga sua participação histórica; fechamento grava `closed_at` e nota opcional; reabertura limpa o fechamento sem modificar tarefas ou sessões.

**Resultado:** `DayReviewService` monta a fotografia do dia a partir do plano original, tarefas e sessões, sem modificar os dados históricos ao filtrar o plano ativo. O Dashboard oferece `Encerrar meu dia`, abre uma revisão com concluídas, sessões e itens abertos e, depois do fechamento, mantém acesso por `Revisar encerramento`. Encerrar grava horário e nota opcional de forma idempotente; reabrir limpa somente esses metadados. Se houver erro de persistência, a janela preserva a nota e permite nova tentativa. Seis testes de domínio e cinco contratos JavaFX cobrem resumo, fechamento, repetição segura, reabertura, falha, responsividade e tema escuro. A suíte padrão aprovou 106 testes e o perfil JavaFX aprovou 159; `git diff --check` passou.

### P5-02 — Decisões e preparação de amanhã

**Status:** Concluído em 28/08/2026.

**Escopo:** limitar cada item aberto às decisões `Amanhã`, `Manter data`, `Voltar à caixa de entrada` e `Concluir`, além de permitir uma única tarefa inicial opcional para amanhã.

**Aceite:** REV-03 e REV-04; todos os itens abertos recebem uma decisão explícita; `Amanhã` reagenda sem apagar os demais campos; retornar preserva título e notas como captura não classificada e cancela a tarefa estruturada; concluir atualiza tarefa e status; a tarefa inicial só pode ser escolhida entre os itens enviados para amanhã; ausência de escolha não bloqueia o encerramento; um plano diferente já existente para amanhã não é sobrescrito.

**Resultado:** a janela de revisão apresenta um seletor curto por item e uma escolha inicial opcional derivada somente dos itens adiados. `DayReviewRepository` aplica reagendamento, conclusão, retorno à caixa de entrada, preparação do plano reduzido de amanhã e fechamento do dia em uma única transação SQLite. Conflitos preservam nota e escolhas para nova tentativa, e o contador global da caixa de entrada é atualizado imediatamente. Quatro novos testes de domínio cobrem decisões, preparação opcional, validações e rollback integral; um novo contrato JavaFX cobre a confirmação combinada. A suíte padrão aprovou 110 testes e o perfil JavaFX aprovou 164; `git diff --check` passou.

### P5-03 — Revisão neutra de pendências

**Status:** Concluído em 28/08/2026.

**Escopo:** retirar tarefas vencidas da lista única de destaque, agrupá-las por tempo pendente e impedir que itens antigos normais retomem o foco automático.

**Aceite:** REV-06 e NOW-08; as faixas são `Até 7 dias`, `8–30 dias` e `Mais de 30 dias`, com limites inclusivos e contagem visível; os itens usam `pendente há N dias`, sem linguagem culpabilizante; todas as tarefas vencidas permanecem acessíveis por duplo clique; tarefas antigas normais só retornam ao `Agora` por escolha manual ou plano diário, enquanto prioridade crítica continua elegível; o painel geral repete somente tarefas recentes ou críticas.

**Resultado:** a vista `Revisar` apresenta três abas estáveis com contagens e listas completas, mantendo a seção em atenção reduzida. `OverdueAgeBand` centraliza os limites de classificação. O algoritmo de foco exclui tarefas vencidas normais da seleção automática e preserva a exceção crítica. `DatabaseService.listDeadlineAlerts()` usa a data local explicitamente e deixa tarefas antigas normais apenas na revisão, evitando duplicação contínua. Três novos testes de domínio/persistência cobrem limites e filtragem; dois contratos JavaFX cobrem agrupamento, foco, largura reduzida e tema escuro. A suíte padrão aprovou 113 testes e o perfil JavaFX aprovou 169; `git diff --check` passou.

### P5-04 — Métricas locais opcionais

**Status:** Concluído em 30/08/2026.

**Escopo:** medir localmente os três fluxos interativos da seção 6 sem registrar conteúdo pessoal e sem exibir dados antes de consentimento explícito.

**Aceite:** métricas desativadas por padrão; enquanto desativadas, nenhum evento é criado e o painel não ocupa espaço; a configuração persiste; somente tipo, valor numérico e horário são armazenados; foco registra apenas a primeira ação bem-sucedida da sessão; captura e retomada contam tentativas até o sucesso; o painel mostra medianas dos 30 registros mais recentes; retenção máxima de 200 eventos por tipo; o usuário pode apagar o histórico; não há rede, identificador de tarefa, texto ou telemetria remota.

**Resultado:** `LocalMetricsService` controla consentimento, sessão e medianas; `LocalMetricsRepository` persiste eventos mínimos em `local_metric_events` e limita a retenção. `Configurações` ganhou ativação e limpeza confirmada. A vista `Revisar` usa `LocalMetricsPanel`, completamente invisível quando desligado e sem metas coloridas ou ranking. A captura rápida informa a quantidade real de tentativas, e a retomada só é registrada depois de timer, janela e remoção da pista concluírem com sucesso. Cinco testes de domínio cobrem opt-in, ausência de coleta, mediana, limpeza, retenção e isolamento de falhas; três contratos JavaFX cobrem configuração, ocultação, conteúdo, largura reduzida e tema escuro; os contratos existentes validam instrumentação de captura e retomada. A suíte padrão aprovou 118 testes e o perfil JavaFX aprovou 177; `git diff --check` passou.

**Fechamento:** a Fase 5 está concluída. O ciclo diário agora cobre planejamento, foco, interrupção, retomada, encerramento, preparação de amanhã e revisão de pendências, com métricas locais inteiramente opcionais.

## 29. Fechamento residual da Fase 0

**Status do pacote:** Concluído em 30/08/2026, 100% (3 de 3 itens concluídos).

Este pacote fecha a rastreabilidade que ficou aberta quando as fases funcionais avançaram. Ele não reabre nem reduz o progresso das Fases 1 a 5.

**Checklist de avanço:**

- [x] P0-01 — Sincronizar o estado da documentação
- [x] P0-02 — Criar checklist visual reproduzível
- [x] P0-03 — Registrar a referência atual dos quatro fluxos

### P0-01 — Estado documental coerente

**Status:** Concluído em 30/08/2026.

**Escopo:** corrigir o estado geral da spec, explicitar o progresso separado da Fase 0 e confirmar que `README.md`, `DEVELOPMENT.md` e `CHANGELOG.md` apontam para a fonte de verdade e para os comandos de validação atuais.

**Aceite:** a spec não se apresenta mais como proposta inicial; o percentual funcional concluído não é reduzido pela pendência documental; nenhum resultado histórico ou número de baseline é inventado retroativamente.

**Resultado:** cabeçalho e plano de entrega foram sincronizados com o estado real. A implementação funcional permanece em 100%, enquanto o fechamento residual da Fase 0 passa a ter checklist e percentual próprios. A auditoria confirmou que os três documentos auxiliares já descrevem a spec, a suíte padrão, o perfil JavaFX e a matriz manual vigente.

### P0-02 — Checklist visual reproduzível

**Status:** Concluído em 30/08/2026.

**Escopo:** criar um roteiro operacional versionado com ambiente, dados temporários, matriz de janelas, passos, evidências e critérios objetivos de aprovação.

**Aceite:** outra execução consegue repetir a validação sem acessar dados pessoais e registrar resultado por combinação relevante de resolução, escala, tema e estado da principal.

**Resultado:** `UI_VALIDATION.md` define metadados, preparação, dados fictícios, quatro combinações mínimas de ambiente, contrato objetivo por janela, matriz das doze janelas/fluxos, cenários ponta a ponta, evidências e critério de encerramento. O perfil Maven `manual-ui-validation` isola SQLite, preferências e credenciais por meio de `user.home` e `java.util.prefs.userRoot`; o diretório pode ser escolhido em cada execução sem alterar o perfil pessoal. O `effective-pom` confirmou que o perfil preserva todas as opções nativas existentes, a suíte padrão aprovou 118 testes e `git diff --check` passou. A execução da matriz e o registro de seus resultados pertencem ao P0-03.

### P0-03 — Referência atual dos fluxos

**Status:** Concluído em 30/08/2026, 100% (4 de 4 fluxos validados).

**Checklist de medição:**

- [x] Tempo até a primeira ação de foco — 88 segundos em amostra guiada
- [x] Ações da captura rápida — 2 ações e 1 tentativa de salvar
- [x] Ações da retomada — 1 ação e 1 tentativa
- [x] Janelas fora da área útil — referência técnica atual registrada

**Escopo:** medir a referência atual dos quatro primeiros fluxos da seção 6 e registrar separadamente que a linha anterior às mudanças não foi coletada.

**Aceite:** foco, captura, retomada e janelas possuem data, ambiente, amostra e resultado observável; ausência de baseline histórico é apresentada como limitação, não como valor estimado.

**Resultado:** `USABILITY_BASELINE.md` registra explicitamente a ausência da linha histórica, fixa as unidades das quatro métricas e documenta ambiente, amostra e referência técnica atual. Em KDE/KWin Wayland, com dados isolados, o Dashboard abriu dentro da área útil; os cinco casos geométricos e oito contratos reais de janela passaram, e o perfil JavaFX completo aprovou 177 testes. Uma rodada humana guiada registrou 88 segundos até a retomada/foco, duas ações para captura com uma tentativa de salvar e uma ação/tentativa para retomar. A pista foi removida e a captura persistida. O tempo inclui leitura das instruções e, isoladamente, não permite atribuir o valor acima da meta ao produto. A aplicação foi encerrada e nenhuma informação pessoal foi usada.

**Fechamento:** a Fase 0 está concluída. Documentação, checklist reproduzível e referência inicial dos quatro fluxos estão versionadas; novas amostras podem refinar as medianas sem reabrir o pacote.

## 30. Estabilização pós-conclusão

**Status do pacote:** Concluído em 30/08/2026, 100% (3 de 3 itens concluídos).

Este pacote corrige achados objetivos da auditoria final sem reabrir as Fases 0 a 5, que permanecem em 100%.

**Checklist de avanço:**

- [x] S-01 — Remover resíduos e erro de cor na execução de protocolos
- [x] S-02 — Corrigir a busca legada de tarefa que retorna sempre `null`
- [x] S-03 — Sincronizar requisitos históricos e executar o quality gate final

### S-01 — Cores temáticas na execução de protocolos

**Status:** Concluído em 30/08/2026.

**Escopo:** retirar fundo branco inline das etapas abertas e impedir que tokens CSS sejam interpretados como valores por `Color.web()` no histórico.

**Aceite:** etapas abertas e concluídas usam tokens de superfície/borda nos dois temas; ícones de execução concluída, cancelada e ativa usam classes semânticas; renderizar histórico concluído não lança erro; contrato JavaFX comprova fundos escuros e textos legíveis.

**Resultado:** linhas de etapa agora usam `protocol-step-row-open` ou `protocol-step-row-done`, e os ícones usam `t-success`, `t-danger` ou `t-warn`. Nenhuma cor literal permanece nesses dois caminhos; o novo contrato JavaFX cobre todos os estados no tema escuro. O teste direcionado e os 118 testes da suíte padrão passaram; `git diff --check` foi aprovado.

### S-02 — Busca legada de tarefa

**Status:** Concluído em 30/08/2026.

**Escopo:** substituir `DatabaseService.findTaskById()`, atualmente um stub que retorna sempre `null`, por leitura compatível com o modelo vigente e cobrir o chamador da agenda.

**Aceite:** tarefa existente retorna todos os campos atuais; ID ausente ou nulo retorna `null` para compatibilidade do chamador; erro de banco não é convertido em ausência; busca e repositório de sessão usam o mesmo arquivo configurado no `DatabaseService`.

**Resultado:** `DatabaseService` conserva uma única instância de `Database`, delega a busca ao `TaskRepository` e entrega essa mesma dependência ao `TaskSessionRepository`. O timer embutido na lista volta a encontrar a tarefa e pode salvar sua sessão no SQLite correto. Teste temporário cobre todos os campos, ausência e persistência vinculada. O teste direcionado e os 119 testes da suíte padrão passaram; `git diff --check` foi aprovado.

### S-03 — Coerência e quality gate

**Status:** Concluído em 30/08/2026.

**Escopo:** atualizar referências históricas contraditórias, executar suítes padrão/JavaFX, compilar o pacote e revisar o diff final.

**Aceite:** Fases 0 a 5 apresentam estado coerente; requisitos implementados não são descritos como futuros; hipóteses remanescentes ficam vinculadas ao uso real; build limpo gera o JAR sem backups; suítes padrão e JavaFX, `git diff --check` e inspeção de processos passam.

**Resultado:** Fases 4 e 5 receberam status/progresso no plano resumido, NTF-09 passou a descrever o horário silencioso já existente e decisões/perguntas foram atualizadas para o piloto real. `app.css.bak`, sem referência, foi retirado dos recursos. `./mvnw clean package` gerou `target/agenda-1.0-SNAPSHOT.jar` com 119 testes aprovados; o perfil JavaFX aprovou 179 testes. O JAR contém classes/CSS esperados e nenhum `.bak`; `git diff --check` passou e nenhuma instância da Agenda permaneceu ativa. A alteração da configuração IntelliJ continua limitada ao módulo Maven e ao acesso `javafx.web` já documentado.

**Fechamento:** a estabilização pós-conclusão está concluída. Os dois defeitos funcionais encontrados pela auditoria foram corrigidos, a documentação está coerente e o artefato foi produzido por build limpo.

## 31. Piloto de uso real

**Status do pacote:** Concluído em 31/08/2026, 100% (6 de 6 itens encerrados). Fases 0 a 5 e estabilização permanecem em 100%.

O piloto valida as hipóteses da seção 21 sem criar opções antecipadamente e sem transformar uso da Agenda em nova obrigação.

**Resultado operacional:** o protocolo Google ao vivo foi concluído em `GSYNC-LIVE`. As cinco hipóteses de uso foram encerradas como `SEM EVIDÊNCIA`, preservando os comportamentos atuais e sem autorizar mudanças de produto.

**Estado operacional em 31/08/2026:**

- `PIL-01` a `PIL-05` encerrados sem evidências de uso normal;
- inspeção agregada do SQLite encontrou zero planos diários registrados; conteúdo pessoal não foi consultado;
- implementação e documentação enviadas para `origin/master` até `95be01dfd`;
- worktree local e remoto conferidos sem divergência;
- credenciais e tokens Google restritos a permissão `600`;
- banco validado por `PRAGMA quick_check` e backups operacionais criados com permissão `600`;
- teste Google ao vivo concluído em 100%, incluindo idempotência, ciclo de estado, conflitos, exclusão controlada, cancelamento OAuth e reconexão.
- encerramento administrativo solicitado pelo usuário para liberar a especificação do Projeto 2; nenhuma conclusão comportamental foi inferida.

**Checklist de avanço:**

- [x] PIL-00 — Definir protocolo de observação de baixa carga
- [x] PIL-01 — Encerrar quantidade do plano diário como `SEM EVIDÊNCIA`
- [x] PIL-02 — Encerrar nome e efeito da capacidade reduzida como `SEM EVIDÊNCIA`
- [x] PIL-03 — Encerrar formato da captura universal como `SEM EVIDÊNCIA`
- [x] PIL-04 — Encerrar vínculo obrigatório da pista de retomada como `SEM EVIDÊNCIA`
- [x] PIL-05 — Encerrar acionamento manual do dia como `SEM EVIDÊNCIA`

### PIL-00 — Protocolo de observação

**Status:** Concluído em 30/08/2026.

**Escopo:** transformar as cinco perguntas abertas em observações acionáveis sem registrar conteúdo pessoal, impor sequência diária ou incentivar mudanças por exceções isoladas.

**Aceite:** cada hipótese define evento observável e regra de decisão; um registro leva menos de 30 segundos; ausência de uso não gera cobrança; falhas de dados, bloqueio ou pressão sensorial podem interromper o piloto imediatamente.

**Resultado:** `PILOT.md` usa registro por evento, cinco hipóteses separadas e decisões `MANTER`, `AJUSTAR`, `TESTAR ALTERNATIVA` ou `SEM EVIDÊNCIA`. Novos recursos continuam bloqueados até existir evidência suficiente.

### PIL-01 — Quantidade do plano diário

**Status:** Encerrado em 31/08/2026 como `SEM EVIDÊNCIA`.

**Escopo:** observar em uso normal se uma tarefa essencial e até duas de apoio reduzem decisões ou se deixam trabalho relevante sem lugar no plano.

**Aceite:** duas observações independentes apontam benefício ou o mesmo atrito; exceção isolada não muda o limite; nenhum título ou conteúdo pessoal é registrado.

**Resultado:** a inspeção agregada encontrou zero planos diários. O aceite observacional não foi atingido e nenhuma validação comportamental é alegada. O limite atual permanece; uma evidência futura pode reabrir a hipótese sem reabrir as fases concluídas.

### PIL-02 a PIL-05 — Encerramento sem evidência

**Status:** Encerrados em 31/08/2026 como `SEM EVIDÊNCIA`.

**Resultado:** `Capacidade reduzida`, janela de captura universal, vínculo obrigatório da pista e encerramento manual permanecem inalterados. `PILOT.md` registra separadamente a regra de reabertura de cada hipótese. O percentual de 100% representa encerramento rastreável do protocolo, não confirmação por uso real.

## 32. Projeto 2 — Extensão móvel e sensorial

**Status:** Especificado em 31/08/2026; implementação 63,3%, com 36,7% restantes. `P2-01` a `P2-06` estão concluídas; `P2-07` está em 33,3% (2 de 6 itens).

**Visão:** estender a Agenda para Android e Wear OS, mantendo o desktop como superfície de organização, o smartphone como nó móvel offline e o smartwatch como superfície curta de percepção e resposta. Alertas devem oferecer `Concluir` e `Adiar`; capturas e protocolos devem funcionar fora do notebook e convergir depois.

**Escopo adicional:** áudio configurável da própria Agenda, fluxo `Vou sair`, Health Connect opt-in, registros voluntários de medicamentos/substâncias/sintomas, relatório revisável para acompanhamento médico e personalização evolutiva local-first.

**Limite:** nenhuma função clínica, diagnóstica, terapêutica ou de ajuste de medicamento está autorizada. Dados de saúde ficam fora da recomendação automática inicial. IA em nuvem não é dependência e nunca recebe dado sensível por padrão.

**Documentos:** requisitos, arquitetura, fases e gates estão em `PROJECT2_SPEC.md`. O ponto de entrada para manutenção, catálogos, contratos e retomada de contexto está em `MAINTENANCE_MAP.md`.

**Próxima ação:** implementar `Saúde e privacidade` com opt-in granular e gestão dos registros locais cifrados; Health Connect permanece desligado.
