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

**Estado:** Encerrado em 2026-08-31, sem evidências.

**Preparação técnica:** versão do piloto enviada ao GitHub; nenhuma alteração adicional de produto será feita enquanto esta hipótese não tiver evidência.

**Decisão:** `SEM EVIDÊNCIA`. Manter o limite atual; reabrir somente se uso futuro produzir duas ocorrências semelhantes de excesso ou insuficiência.

### H2 — Capacidade reduzida

**Pergunta:** o termo `Capacidade reduzida` é compreensível e a opção diminui pressão?

Registrar confusão com o nome, sensação de culpa ou situação em que a redução ajudou a começar.

**Estado:** Encerrado em 2026-08-31, sem evidências.

**Decisão:** `SEM EVIDÊNCIA`. Manter o texto e o comportamento; reabrir com uma ocorrência clara de incompreensão ou padrão recorrente de pressão.

### H3 — Forma da captura

**Pergunta:** a pequena janela de captura preserva o contexto melhor que um painel na tela atual?

Registrar perda de foco, dificuldade para encontrar a janela, bloqueio de conteúdo ou benefício de ela fechar rapidamente.

**Estado:** Encerrado em 2026-08-31, sem evidências.

**Decisão:** `SEM EVIDÊNCIA`. Manter a janela; reabrir se surgirem duas ocorrências semelhantes de interrupção ou ocultação.

### H4 — Retomada livre ou vinculada

**Pergunta:** toda pista útil cabe em uma tarefa existente ou às vezes precisa existir sem vínculo?

Registrar somente quando houver uma pista real que não possa ser associada honestamente a uma tarefa.

**Estado:** Encerrado em 2026-08-31, sem evidências.

**Decisão:** `SEM EVIDÊNCIA`. Manter o vínculo obrigatório; reabrir quando existirem pelo menos duas necessidades concretas de contexto livre.

### H5 — Encerramento do dia

**Pergunta:** a ação manual é encontrada no momento certo ou um lembrete de horário seria útil?

Registrar esquecimento recorrente, interrupção causada por convite indesejado ou uso espontâneo bem-sucedido.

**Estado:** Encerrado em 2026-08-31, sem evidências.

**Decisão:** `SEM EVIDÊNCIA`. Manter acionamento manual; qualquer convite futuro dependerá de evidência e deverá ser opcional, silencioso e nunca modal.

## 4. Critério de decisão

Uma hipótese pode ser encerrada quando ocorrer uma destas condições:

- duas observações independentes apontam o mesmo atrito concreto;
- duas observações mostram que o comportamento atual ajuda e nenhuma contradiz;
- uma falha causa perda de dados, bloqueio ou pressão sensorial relevante, caso em que não é necessário esperar repetição.

Resultados possíveis: `MANTER`, `AJUSTAR`, `TESTAR ALTERNATIVA` ou `SEM EVIDÊNCIA`. `SEM EVIDÊNCIA` mantém o comportamento atual.

## 5. Resumo das decisões

| Hipótese | Estado | Evidências | Decisão | Próxima ação |
|---|---|---:|---|---|
| H1 — Quantidade do plano | Encerrado | 0 | SEM EVIDÊNCIA | Manter limite; reabrir por evidência futura |
| H2 — Capacidade reduzida | Encerrado | 0 | SEM EVIDÊNCIA | Manter nome e comportamento |
| H3 — Forma da captura | Encerrado | 0 | SEM EVIDÊNCIA | Manter janela pequena |
| H4 — Retomada vinculada | Encerrado | 0 | SEM EVIDÊNCIA | Manter vínculo obrigatório |
| H5 — Encerramento manual | Encerrado | 0 | SEM EVIDÊNCIA | Manter acionamento manual |

## 6. Encerramento

O piloto foi encerrado em 2026-08-31 por decisão explícita do usuário para liberar o planejamento do Projeto 2. A inspeção agregada do SQLite encontrou zero planos diários e nenhum evento utilizável para as cinco hipóteses; nenhum conteúdo pessoal foi consultado. Assim, `100%` significa que o protocolo foi encerrado e documentado, não que os comportamentos foram comprovados por uso real.

As cinco decisões `SEM EVIDÊNCIA` preservam os padrões atuais. Qualquer mudança futura deve citar o evento que reabriu a hipótese e não pode ser justificada apenas pelo encerramento administrativo deste piloto.
