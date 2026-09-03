# Inventario de dados de saude

Este inventario implementa o limite inicial de `P2-07`. Ele descreve dados que o
produto podera armazenar, mas nao autoriza coleta. Toda categoria inicia
desligada, pode ser revogada isoladamente e permanece fora de recomendacoes.

## Categorias e finalidade

| Categoria | Origem inicial | Finalidade permitida | Retencao padrao | Conteudo sensivel cifrado |
|---|---|---|---|---|
| Frequencia cardiaca | Health Connect, foreground | resumo revisavel de periodo | 365 dias | agregados e origem |
| Frequencia em repouso | Health Connect, foreground | resumo revisavel de periodo | 365 dias | agregados e origem |
| Sono | Health Connect, foreground | cobertura e horarios no relatorio | 365 dias | agregados e origem |
| Atividade | Health Connect, foreground | cobertura e agregado no relatorio | 365 dias | agregados e origem |
| Medicacao | entrada manual | fato informado pelo usuario | ate exclusao ou 3650 dias | nome, quantidade, unidade e nota |
| Substancia | entrada manual | fato informado pelo usuario | ate exclusao ou 3650 dias | nome, quantidade, contexto, efeito e nota |
| Sintoma | entrada manual | fato informado pelo usuario | ate exclusao ou 3650 dias | rotulo, intensidade e nota |
| Nota de rotina | entrada manual | observacao escolhida para relatorio | ate exclusao ou 3650 dias | texto |

Retencao e uma preferencia por categoria, entre 1 e 3650 dias. Expirar ou excluir
gera tombstone sincronizavel; nao apaga tarefas, protocolos ou alertas.

No Android atual, a retenção é aplicada no startup. Ingestões e observações
vencidas têm ciphertext e IV limpos e mantêm tombstone/auditoria. Resumos do
Health Connect vencidos são removidos e deixam somente auditoria técnica.

## Metadados minimos

- UUID, versao, instante e fuso;
- origem `MANUAL` ou `HEALTH_CONNECT`;
- categoria e consentimento que autorizou a operacao;
- proveniencia e janela de cobertura para agregados;
- revisao, instante de alteracao e tombstone para correcao/exclusao.

Ausencia de amostra permanece ausencia. O sistema nao preenche quantidade,
unidade, intensidade, pureza, interacao, causalidade ou diagnostico.

## Protecao e fluxo

- chaves AES-GCM nao exportaveis ficam no Android Keystore;
- colunas sensiveis guardam somente ciphertext autenticado e nonce unico;
- banco, backup e logs nunca recebem plaintext sensivel fora do limite de uso;
- sync usa o canal TLS pareado e somente categorias consentidas;
- exportacao exige previa e escolha explicita de destino;
- JSON, CSV e PDF derivam do mesmo snapshot revisado e nao sao enviados automaticamente;
- CSV neutraliza formulas e PDF pagina texto longo; o destino escolhido recebe arquivo sem senha;
- Health Connect pede somente leitura das categorias ativas, no botao de importacao;
- a janela inicial e de sete dias; historico ampliado e background nao sao solicitados;
- revogar interrompe novas leituras sem afetar a Agenda operacional.

## Proibicoes

- recomendar dose, interrupcao, substancia ou conduta medica;
- inferir que uma substancia e segura;
- converter lacuna em zero ou inventar relacao causal;
- usar saude no ranking automatico inicial;
- enviar dado sensivel para IA ou terceiro por padrao;
- versionar fixtures, relatorios ou logs com dados pessoais.
