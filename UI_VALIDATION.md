# Checklist Manual de Interface

Este roteiro complementa as seções 14.4 e 15.4 da [SPEC.md](SPEC.md). Ele valida comportamento nativo, responsividade e tema sem acessar o banco, as preferências ou as credenciais pessoais. Resultados das métricas principais são consolidados em [USABILITY_BASELINE.md](USABILITY_BASELINE.md).

## 1. Registro da execução

Preencher antes de iniciar:

| Campo | Valor |
|---|---|
| Data e hora | |
| Commit ou descrição do diff | |
| Sistema operacional | |
| Ambiente gráfico/gerenciador de janelas | |
| Java (`java -version`) | |
| Resolução e escala | |
| Tema inicial | |
| Diretório isolado | |
| Executor | |

Resultados permitidos: `APROVADO`, `REPROVADO`, `BLOQUEADO` ou `N/A`. Todo resultado diferente de `APROVADO` deve ter uma observação.

## 2. Preparação isolada

1. Execute as suítes automatizadas:

   ```bash
   ./mvnw test
   ./mvnw -Pjavafx-ui-tests test
   ```

2. Crie um perfil descartável e inicie a aplicação:

   ```bash
   VALIDATION_HOME="$(mktemp -d)"
   ./mvnw -Pmanual-ui-validation \
     -Dagenda.validation.home="$VALIDATION_HOME" \
     javafx:run
   ```

3. Registre o caminho exibido por `printf '%s\n' "$VALIDATION_HOME"` na tabela da seção 1.
4. Não conecte uma conta Google. A janela Google Tasks pode ser validada até o estado vazio/desconectado; operações ao vivo seguem o protocolo condicionado da seção 26 da spec.
5. Ao terminar, confirme que os dados de teste estão somente no diretório registrado. A remoção desse diretório é uma ação manual deliberada.

O perfil Maven altera `user.home` e `java.util.prefs.userRoot` apenas no processo JavaFX. Assim, SQLite, tema, alertas, métricas, tokens e atalhos da execução ficam separados do perfil pessoal.

## 3. Dados fictícios mínimos

Criar pela própria interface, usando somente conteúdo artificial:

- uma tarefa de hoje com título de pelo menos 120 caracteres e notas longas;
- uma tarefa vencida há menos de 7 dias e outra há mais de 30 dias;
- uma tarefa com checklist curto e um item de checklist longo;
- um protocolo com checklist e uma execução iniciada;
- uma sessão de foco associada à tarefa de hoje;
- uma ideia com descrição longa, checklist e uma captura ainda não classificada;
- um plano/registro de estudo com texto longo, quando exigido para abrir Diário e Monitor.

Também validar estados vazios antes de criar os dados quando a janela os oferecer.

## 4. Matriz de ambiente

Executar pelo menos estas combinações. Quando o sistema não oferecer uma escala, usar `N/A` e registrar a limitação.

| ID | Área útil mínima | Escala | Tema | Principal | Resultado | Evidência/observação |
|---|---:|---:|---|---|---|---|
| A | 1280x720 | 100% | Claro | Normal | | |
| B | 1280x720 | 100% | Escuro | Maximizada | | |
| C | 1366x768 | 125% | Claro e escuro | Maximizada | | |
| D | 1920x1080 | 150% | Claro e escuro | Normal e maximizada | | |

Uma resolução física maior não substitui a área útil de 1280x720. Painéis, barras do sistema e decoração da janela devem ser descontados.

## 5. Contrato comum por janela

Para cada janela da seção 6 e em cada combinação aplicável:

1. Abrir com a principal normal e depois maximizada.
2. Confirmar owner correto e modalidade esperada.
3. Abrir uma secundária a partir dela quando o fluxo permitir.
4. Alternar claro/escuro enquanto permanece aberta.
5. Redimensionar até o mínimo permitido.
6. Conferir estado vazio, título/texto longo, rolagem e ação essencial.
7. Fechar pela ação da interface e pelo botão do sistema.
8. Confirmar que nenhuma parte ficou fora da área útil.
9. Confirmar que a principal manteve maximização, posição e tamanho restaurado.

Critérios objetivos de reprovação:

- texto essencial cortado sem tooltip ou quebra de linha;
- texto escuro ilegível no tema escuro, ou resíduo claro fora do conteúdo deliberadamente tratado como papel;
- botão essencial inacessível por layout, rolagem ou teclado;
- janela fora da área útil ou maior que ela após `WindowManager.show()`;
- secundária sem owner, atrás da principal ou bloqueando janela não relacionada;
- principal desmaximizada, redimensionada ou deslocada ao abrir/fechar formulário;
- exceção, travamento, perda de texto digitado ou fechamento silencioso após falha.

## 6. Matriz de janelas

Marcar o resultado agregado depois de executar o contrato comum. Usar a observação para identificar combinação e passo que falharam.

| Janela/fluxo | Forma de abertura | Modalidade esperada | Resultado | Evidência/observação |
|---|---|---|---|---|
| Timer normal | Dashboard ou tarefa | Modeless | | |
| Timer compacto | Timer normal | Modeless, posição preservada | | |
| Histórico de sessões | Timer/Dashboard | Modeless | | |
| Execução de protocolo | Protocolos | Modal | | |
| Checklist de tarefa | Agenda | Modal | | |
| Checklist de projeto | Ideias | Modal | | |
| Diário de estudos | Estudos | Modal | | |
| Monitor de estudos | Estudos | Modeless | | |
| Detalhe de ideia | Ideias | Modal | | |
| Revisão de ideias | Ideias | Modal | | |
| Google Tasks desconectado | Agenda/Configurações | Modal | | |
| Pré-visualização de impressão | Ação de impressão | Modal | | |

Limitações aceitas, sem mascarar outros defeitos:

- o relatório HTML da pré-visualização mantém aparência de papel branco no tema escuro;
- seleção de impressora e outros diálogos nativos podem seguir o tema/escala do sistema;
- sincronização Google ao vivo não faz parte desta checklist sem confirmação explícita.

## 7. Cenários ponta a ponta

Executar os cenários A a F da seção 16 da spec e registrar:

| Cenário | Resultado | Evidência/observação |
|---|---|---|
| A - Começar sem plano | | |
| B - Capturar durante o foco | | |
| C - Interromper e retomar | | |
| D - Pausar estímulos | | |
| E - Encerrar o dia | | |
| F - Tela pequena | | |

## 8. Evidências e encerramento

- Nome sugerido: `AAAA-MM-DD_<ambiente>_<matriz>_<janela>_<passo>.png`.
- Capturas devem usar apenas os dados fictícios do perfil isolado.
- Para maximização, registrar também ambiente gráfico, área útil, posição/tamanho antes e depois e estado reportado pelo sistema quando essa informação estiver disponível.
- Um defeito deve conter passos, esperado, observado, combinação da matriz e evidência.
- A execução só é aprovada quando não houver `REPROVADO` e todo `BLOQUEADO` estiver documentado como limitação externa aceita pela spec.

Resumo final:

| Campo | Valor |
|---|---|
| Suíte padrão | |
| Perfil JavaFX | |
| Combinações aprovadas | |
| Janelas aprovadas | |
| Cenários aprovados | |
| Defeitos encontrados | |
| Limitações externas | |
| Resultado geral | |
