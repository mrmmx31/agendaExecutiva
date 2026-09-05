# P3-00 - Navegacao Android

## Destinos

| Destino | Conteudo principal | Estado restaurado |
|---|---|---|
| Hoje | foco, timer, protocolo ativo, plano e alertas proximos | secao, foco expandido e rolagem util |
| Tarefas | busca, filtros, lista e detalhe | consulta, filtros e tarefa aberta |
| Capturar | entrada universal | rascunho ate salvar ou descartar |
| Rotinas | protocolos e estudos | aba interna e execucao ativa |
| Mais | projetos, operacoes, saude, fila e configuracoes | ultima subarea nao sensivel |

## Adaptacao

- Ate 599 dp: barra inferior com cinco destinos.
- De 600 a 839 dp: rail lateral e conteudo unico.
- A partir de 840 dp: rail e lista/detalhe em duas colunas quando o dominio suportar.
- Rotacao nao altera destino, nao reenvia comando e nao reabre deep link consumido.
- Dialogo aberto pelo usuario sobrevive a rotacao; dialogo ja dispensado nao reaparece.

## Voltar

Prioridade: fechar dialogo, fechar detalhe, voltar da subarea de `Mais`, retornar a `Hoje` e somente entao sair. Execucao de timer ou protocolo permanece persistida ao sair da tela.

## Estado

Estado operacional fica no repositorio/Room. `SavedStateHandle` guarda identidade de detalhe e filtros. `rememberSaveable` guarda apenas estado visual pequeno e rascunho limitado. Segredo, payload de saude e objetos de banco nao entram em `Bundle`.

## Acessibilidade

- Alvo de toque minimo de 48 dp.
- Estado nao depende apenas de cor.
- Icone sem texto possui descricao; icone decorativo nao e anunciado.
- Ordem de foco acompanha leitura visual.
- Fonte aumentada nao trunca acao terminal nem sobrepoe conteudo.
- Temas claro/escuro preservam contraste e componentes do sistema.

