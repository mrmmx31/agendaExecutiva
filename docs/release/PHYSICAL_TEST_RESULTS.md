# Resultados físicos P2-10

Sessão iniciada em 03/09/2026. Este documento contém apenas resultados
agregados e fixtures; serial ADB, endereço Bluetooth e saídas brutas permanecem
fora do Git.

## Ambiente autorizado

- telefone: Motorola Edge 60, Android 16/API 36;
- variante: `com.pessoal.agenda.mobile.fieldtest`, versão 0.1.0-fieldtest;
- wearable: Mertto ZL02D Sport, família ZL02CPro, firmware 2.0.9;
- companheiro confirmado: Da Fit, com listener de notificações habilitado;
- conexão ADB atual: USB; depuração Wi-Fi ativada, pareamento TLS pendente.

## Gate 6

| Verificação | Estado | Evidência |
|---|---|---|
| pre-flight | aprovado | bateria 47%, 35,9 °C e variante ausente antes do ensaio |
| instalação isolada | aprovado | APK `fieldTest` instalado sem limpar dados ou conceder permissões |
| startup sem prompt | aprovado | tela inicial abriu sem solicitação espontânea |
| notificação negada | aprovado | AppOps `ignore`, mensagem explícita e restante do app funcional |
| captura após negação | aprovado | captura fictícia salva e enfileirada offline |
| notificação concedida | aprovado | AppOps `allow` e estado visual ativo |
| protocolo offline | aprovado | quatro passos fictícios concluídos; fila avançou sem crash |
| Health Connect negado | aprovado | leitura cardíaca permaneceu `granted=false` |
| registro sensível local | aprovado | fixture criada, corrigida, confirmada e excluída |
| concessão Health com dado sintético | pendente | não ler dados pessoais; exige fonte sintética isolada |
| prévia e exportação JSON/CSV/PDF | pendente | exige seleção visual de destino temporário |

## Gate 7

| Verificação | Estado | Evidência |
|---|---|---|
| tela e rota sem reprodução | aprovado | rota efetiva exibida como automática do sistema |
| reprodução cautelosa | pendente | depende de confirmação do proprietário |
| fone, remoção, DND, chamada e mídia | pendente | observação física ainda necessária |

## Gate 8

O receptor ADB protegido e exclusivo de `fieldTest` materializou um alerta
visual fictício. O Android registrou título, motivo e as ações `Concluir` e
`Adiar`; Da Fit está autorizado a observar notificações. O alerta permanece
ativo para confirmação visual no pulso.

| Verificação | Estado |
|---|---|
| fixture publicada sem desktop e sem áudio | aprovado |
| ações Android presentes e não `localOnly` | aprovado |
| texto/vibração recebidos na ZL02CPRO | aguardando observação |
| ações exibidas pela pulseira | aguardando observação |
| desconexão/reconexão Bluetooth | pendente |

## Gate 9

Baseline inicial às 11:20 UTC: telefone em carga, bateria 48%, temperatura
34,0 °C e PSS aproximado de 198 MB após uso funcional. Não houve `FATAL
EXCEPTION`; mensagens de vendor/HWUI sem crash foram observadas. A janela de 24
horas, bateria da pulseira e medição final continuam pendentes.

## Próxima interação mínima

1. informar se o alerta fictício apareceu e vibrou na pulseira;
2. informar se a pulseira mostrou algum botão/ação ou somente texto;
3. confirmar teste de áudio em volume baixo;
4. fornecer o código efêmero de pareamento Wi-Fi ou concluir por QR no Android
   Studio para liberar o cabo USB.
