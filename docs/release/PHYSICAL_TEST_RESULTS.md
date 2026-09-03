# Resultados físicos P2-10

Sessão iniciada em 03/09/2026. Este documento contém apenas resultados
agregados e fixtures; serial ADB, endereço Bluetooth e saídas brutas permanecem
fora do Git.

## Ambiente autorizado

- telefone: Motorola Edge 60, Android 16/API 36;
- variante: `com.pessoal.agenda.mobile.fieldtest`, versão 0.1.0-fieldtest;
- wearable: Mertto ZL02D Sport, família ZL02CPro, firmware 2.0.9;
- companheiro confirmado: Da Fit, com listener de notificações habilitado;
- conexão ADB: TLS por Wi-Fi validado no mesmo aparelho; USB pode ser removido.

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
`Adiar`; Da Fit está autorizado a observar notificações. Dois formatos foram
observados no telefone e na pulseira.

| Verificação | Estado |
|---|---|
| fixture publicada sem desktop e sem áudio | aprovado |
| ações Android presentes e não `localOnly` | aprovado |
| texto recebido na ZL02CPRO | aprovado após habilitar `Outras notificações` no Da Fit |
| vibração do telefone no canal vibratório | aprovado; dois pulsos percebidos |
| alerta sonoro da ZL02CPRO | aprovado apenas no canal vibratório, embora a Agenda não tenha solicitado áudio |
| vibração da ZL02CPRO | não suportada no ensaio; não ocorreu nem com o canal vibratório e a pulseira em `tocar e vibrar` |
| ações exibidas pela pulseira | indisponíveis neste hardware; a pulseira exibiu somente o texto |
| `Concluir` no telefone | aprovado; receptor real removeu a notificação e persistiu `COMPLETED`/`COMPLETE` |
| `Adiar` no telefone | aprovado; receptor real removeu a notificação, persistiu `SNOOZE` e criou agendamento futuro |
| desconexão/reconexão Bluetooth | aprovado; transporte e Da Fit retomaram, sem repetição agressiva |
| alerta publicado durante desconexão | não reproduzido no pulso após reconectar; a pulseira mostrou somente `não conectado` |

O fixture usado era exclusivamente visual: a Agenda não solicitou áudio nem
vibração do telefone. No momento da observação, o Android estava com Não
Perturbe desligado e sem fluxos relevantes silenciados. Assim, ausência de áudio
nesse ensaio é esperada; ausência de vibração no pulso pertence à configuração
ou capacidade do Da Fit/ZL02CPRO. Um segundo fixture exclusivo do APK
`fieldTest` declarou vibração pelo canal atual e pelo metadado Android legado,
sempre com `sound=null`. O telefone vibrou e a pulseira emitiu áudio, mas a
pulseira não vibrou. Isso indica que o Da Fit trata a notificação vibratória como
alerta sensorial e escolhe o efeito no wearable. A Agenda não consegue selecionar
separadamente esse efeito na smartband fechada. As ações continuam disponíveis
na notificação do telefone; o espelhamento genérico do Da Fit não as transportou.
No ensaio desconectado, o alerta permaneceu funcional no telefone, mas não foi
enfileirado pelo Da Fit para o pulso. A aplicação deve considerar esse
espelhamento como entrega oportunista, nunca como sincronização confiável.
As ações do telefone foram disparadas por um receptor protegido da variante
`fieldTest`, que encaminha um `Intent` interno ao receptor de produção. Assim, o
ensaio percorreu a mesma persistência e o mesmo cancelamento usados pelos botões,
sem depender de coordenadas da interface nem capturar outras notificações do
telefone.

## Gate 9

Baseline inicial às 11:20 UTC: telefone em carga, bateria 48%, temperatura
34,0 °C e PSS aproximado de 198 MB após uso funcional. Não houve `FATAL
EXCEPTION`; mensagens de vendor/HWUI sem crash foram observadas. A janela de 24
horas, bateria da pulseira e medição final continuam pendentes.

## Próxima interação mínima

1. testar `Concluir` e `Adiar` pelo telefone com a pulseira conectada;
2. executar um passo fictício de `Vou sair` com a pulseira apagada e acesa;
3. desligar os alertas no telefone e confirmar ausência de novo estímulo;
4. confirmar teste do áudio próprio da Agenda em volume baixo para o gate 7;
5. retirar o cabo USB quando conveniente e manter telefone/computador na mesma
   rede durante o restante da sessão.
