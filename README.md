# Agenda Científica Pessoal

Aplicação desktop JavaFX para planejamento pessoal com persistência local em SQLite. O produto funciona como uma prótese executiva pessoal: menos decisões simultâneas, um foco explícito e fluxos curtos para capturar, iniciar e retomar atividades.

## Funcionalidades atuais

- Dashboard operacional com bloco `Agora`, foco do plano, automático ou escolhido manualmente e acesso direto ao timer.
- Plano diário guiado em três etapas, com uma tarefa essencial, até dois apoios e capacidade reduzida.
- Revisão do dia com decisões limitadas por item, preparação opcional de amanhã, encerramento persistente e reabertura.
- Revisão de tarefas vencidas por faixas neutras, sem recolocá-las continuamente no foco automático.
- Métricas locais opcionais de foco, captura e retomada, desativadas por padrão e sem telemetria remota.
- Agenda diária, semanal, mensal e anual, com recorrência, prioridades, status e protocolos vinculados.
- Captura rápida de tarefas com apenas os campos essenciais.
- Protocolos operacionais executáveis, templates de rotina e gatilhos de horário.
- Timer por tarefa, interrupção com pista de retomada e histórico de sessões vinculado por `task_id`.
- Financeiro, vendas, estoque, estudos, frequência e projetos pessoais.
- Captura e revisão de ideias em caixa de entrada.
- Integração Google Tasks e exportação iCalendar.
- Temas claro e escuro aplicados também às janelas secundárias.
- Lembretes configuráveis por intervalo, som e animação, com pausa temporária.
- Gerenciamento central de janelas para owner, modalidade, posicionamento e limites da tela.

## Direção do produto

A visão, os requisitos, os critérios de aceite e o plano de entrega do desktop estão em [SPEC.md](SPEC.md). As Fases 0 a 5, estabilização, auditorias, Google Tasks ao vivo e piloto foram encerrados. O piloto terminou com cinco decisões `SEM EVIDÊNCIA`, sem alegar validação comportamental. O Projeto 2 móvel/sensorial está em 65%, com `P2-01` a `P2-06` concluídas e `P2-07` em 3 de 6 itens; o estado detalhado fica em [PROJECT2_SPEC.md](PROJECT2_SPEC.md).

Documentação complementar:

- [ARCHITECTURE.md](ARCHITECTURE.md): camadas e decisões arquiteturais.
- [DEVELOPMENT.md](DEVELOPMENT.md): convenções e mecânica de desenvolvimento.
- [PROJECT2_SPEC.md](PROJECT2_SPEC.md): Android, Wear OS, sincronização offline, saúde, relatórios e IA.
- [MAINTENANCE_MAP.md](MAINTENANCE_MAP.md): rota curta para manutenção, contratos, catálogos e quality gates.
- [CHANGELOG.md](CHANGELOG.md): mudanças por versão.
- [PILOT.md](PILOT.md): encerramento e regras de reabertura das hipóteses de uso real.

## Dados

- Banco local: `~/.agenda-pessoal/agenda.db`.
- Tabelas e migrações são aplicadas automaticamente na inicialização.
- A aplicação funciona localmente; sincronizações externas são acionadas explicitamente.

Antes de testar migrações amplas com dados reais, faça backup do arquivo `agenda.db`.

## Requisitos

- Java 21 ou superior.
- Maven, ou o Maven Wrapper incluído no projeto.

## Executar

```bash
cd /home/lsi/IdeaProjects/agenda
./mvnw javafx:run
```

## Executar no IntelliJ

1. Abra o projeto e aguarde a sincronização Maven.
2. Selecione a configuração `Agenda Científica` ou execute `javafx:run` pela janela Maven.
3. Inicie a aplicação.

## Testar

```bash
./mvnw test
```

Os testes usam bancos SQLite temporários quando exercitam persistência. A matriz manual está em [UI_VALIDATION.md](UI_VALIDATION.md), a referência de uso em [USABILITY_BASELINE.md](USABILITY_BASELINE.md), e o fechamento rastreável nas seções 15 e 29 da [SPEC.md](SPEC.md).

### Auditar Google Tasks sem alterar dados

Com uma conta já conectada pela aplicação, o comando abaixo executa somente leituras das listas Google e do SQLite. Ele compara vínculos e conflitos, não sincroniza, não resolve decisões e não imprime tokens, IDs remotos ou conteúdo das notas.

```bash
./mvnw -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=com.pessoal.agenda.tools.GoogleTasksReadOnlyAudit
```

A autenticação pode renovar o token OAuth local se ele estiver expirado; nenhuma tarefa Google ou local é modificada.

## Compilar

```bash
./mvnw -DskipTests compile
```

## Estado do desenvolvimento

O projeto mantém `DatabaseService` para leituras legadas enquanto novos recursos usam `repository` e `service`. Não introduza SQL em controllers JavaFX. Consulte a Definition of Done da spec antes de considerar uma história concluída.
