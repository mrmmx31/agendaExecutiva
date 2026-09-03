# Inventário de telemetria de recomendação

P2-08 usa somente códigos e faixas discretas locais. A instalação começa com
personalização desligada. O histórico pode ser inspecionado, corrigido por evento
e apagado sem alterar tarefas, protocolos, alertas ou saúde.

| Grupo | Campos permitidos | Retenção inicial |
|---|---|---|
| tempo | instante, hora local, dia da semana e faixa de prazo | 90 dias |
| contexto explícito | nenhum, foco, protocolo, capacidade reduzida ou paralela | 90 dias |
| resposta | concluir/adiar/expirar, latência limitada e duração do adiamento | 90 dias |
| superfície | telefone/relógio e canal categórico | 90 dias |
| recomendação | versão da regra, opção, posição e código de razão | 90 dias |

Não são coletados texto livre, identificadores de entidades operacionais, saúde,
localização, conteúdo de outros aplicativos ou sinais de uso da tela. Não existe
SDK de analytics, envio em nuvem ou integração com o sync desktop nesta fase.

Métricas permitidas: quantidade por contexto, latência mediana, taxa de correção,
adiamentos repetidos e alertas sem resposta. Engajamento isolado não é objetivo.
