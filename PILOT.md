# Piloto de Uso Real

Este documento valida as hipóteses da seção 21 da [SPEC.md](SPEC.md). Ele não mede produtividade, não exige uso diário e não substitui o [baseline técnico e humano](USABILITY_BASELINE.md).

## 1. Regra de baixa carga

Registrar somente quando ocorrer um destes eventos:

- algo da Agenda ajudou a começar, lembrar ou retomar;
- uma escolha, janela ou alerta causou atrito perceptível;
- uma opção importante pareceu ausente ou desnecessária.

O registro deve levar menos de 30 segundos. Não incluir título, notas ou conteúdo pessoal da tarefa.

## 2. Registro de eventos

| Data | Contexto | Área | Ajudou/atrapalhou | Observação curta |
|---|---|---|---|---|
| | Normal ou capacidade reduzida | Plano, captura, retomada ou encerramento | Ajudou / Atrapalhou | |

Adicionar linhas somente quando houver algo relevante. Ausência de registro não conta como falha nem quebra uma sequência.

## 3. Hipóteses

### H1 — Quantidade do plano

**Pergunta:** uma tarefa essencial e até duas de apoio são suficientes na maioria dos dias usados?

Registrar quando faltar espaço útil, quando itens de apoio sobrarem repetidamente ou quando o limite reduzir decisões de forma clara.

**Estado:** Em andamento desde 2026-08-30, ainda sem evidências.

**Decisão:** manter o limite até existirem pelo menos duas ocorrências semelhantes de excesso ou insuficiência. Não aumentar capacidade por uma exceção isolada.

### H2 — Capacidade reduzida

**Pergunta:** o termo `Capacidade reduzida` é compreensível e a opção diminui pressão?

Registrar confusão com o nome, sensação de culpa ou situação em que a redução ajudou a começar.

**Estado:** Aguardando uso real.

**Decisão:** texto pode mudar com uma ocorrência clara de incompreensão; comportamento só muda com padrão recorrente.

### H3 — Forma da captura

**Pergunta:** a pequena janela de captura preserva o contexto melhor que um painel na tela atual?

Registrar perda de foco, dificuldade para encontrar a janela, bloqueio de conteúdo ou benefício de ela fechar rapidamente.

**Estado:** Aguardando uso real.

**Decisão:** manter a janela enquanto não houver duas ocorrências semelhantes de interrupção ou ocultação.

### H4 — Retomada livre ou vinculada

**Pergunta:** toda pista útil cabe em uma tarefa existente ou às vezes precisa existir sem vínculo?

Registrar somente quando houver uma pista real que não possa ser associada honestamente a uma tarefa.

**Estado:** Aguardando uso real.

**Decisão:** não criar contexto livre sem pelo menos duas necessidades concretas; vínculo atual preserva clareza e integridade.

### H5 — Encerramento do dia

**Pergunta:** a ação manual é encontrada no momento certo ou um lembrete de horário seria útil?

Registrar esquecimento recorrente, interrupção causada por convite indesejado ou uso espontâneo bem-sucedido.

**Estado:** Aguardando uso real.

**Decisão:** lembrete continua ausente por padrão. Qualquer convite futuro deve ser opcional, silencioso e nunca modal.

## 4. Critério de decisão

Uma hipótese pode ser encerrada quando ocorrer uma destas condições:

- duas observações independentes apontam o mesmo atrito concreto;
- duas observações mostram que o comportamento atual ajuda e nenhuma contradiz;
- uma falha causa perda de dados, bloqueio ou pressão sensorial relevante, caso em que não é necessário esperar repetição.

Resultados possíveis: `MANTER`, `AJUSTAR`, `TESTAR ALTERNATIVA` ou `SEM EVIDÊNCIA`. `SEM EVIDÊNCIA` mantém o comportamento atual.

## 5. Resumo das decisões

| Hipótese | Estado | Evidências | Decisão | Próxima ação |
|---|---|---:|---|---|
| H1 — Quantidade do plano | Em andamento | 0 | Sem evidência | Usar normalmente |
| H2 — Capacidade reduzida | Aguardando | 0 | Sem evidência | Usar quando necessário |
| H3 — Forma da captura | Aguardando | 0 | Sem evidência | Capturar normalmente |
| H4 — Retomada vinculada | Aguardando | 0 | Sem evidência | Registrar interrupções reais |
| H5 — Encerramento manual | Aguardando | 0 | Sem evidência | Encerrar quando fizer sentido |
