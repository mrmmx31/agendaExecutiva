# Agenda pessoal (JavaFX + SQLite)
Aplicacao desktop para organizacao pessoal com persistencia local em SQLite.
## Modulos entregues no MVP
- Agenda com tarefas do dia, filtro por mes e marcacao de concluidas
- Alertas de atrasos (tarefas vencidas e pagamentos vencidos)
- Dashboard executivo com indicadores de carga, riscos e prazos
- Checklists de protocolos e acoes
- Financeiro (orcamentos, pendencias, lancamentos)
- Vendas pessoais e controle basico de estoque
- Frequencia de estudos e atividades
- Banco de ideias para projetos pessoais

## Estrutura visual profissional
- Cabecalho executivo com acoes rapidas de atualizacao
- Dashboard com KPIs operacionais (tarefas, atrasos, financeiro, estudos, estoque e ideias)
- Secoes em formato de cards para reduzir ruido visual e facilitar foco
- Barra de status para feedback de validacao e confirmacao de acoes
## Banco de dados
- Arquivo local: `~/.agenda-pessoal/agenda.db`
- Tabelas criadas automaticamente na primeira execucao
## Requisitos
- Java 21+
- Maven (ou usar `./mvnw`)
## Executar
```bash
cd /home/lsi/IdeaProjects/agenda
./mvnw javafx:run
```

## Executar no IntelliJ

- Abra o projeto e aguarde o Maven sync.
- No seletor de configuracoes, escolha `Run Agenda (JavaFX Maven)`.
- Clique em Run para iniciar a aplicacao.
## Build
```bash
cd /home/lsi/IdeaProjects/agenda
./mvnw -DskipTests compile
```
## Proximos passos sugeridos
- Edicao e exclusao de registros
- Dashboard com indicadores mensais (totais, atrasos e horas estudadas)
- Notificacao ativa por timer (ex.: checagem a cada 5 minutos)
- Relatorios CSV para financeiro e vendas
