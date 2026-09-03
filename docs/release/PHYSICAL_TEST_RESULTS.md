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
| isolamento Health sintético | aprovado | `fieldTest` leu exclusivamente a origem oficial Health Connect Toolbox; produção mantém origens consentidas |
| concessão Health com dado sintético | aprovado | Toolbox recebeu somente escrita cardíaca; a Agenda importou em foreground uma série sintética de 10 amostras e preservou origem/cobertura |
| prévia e exportação JSON/CSV/PDF | aprovado | mesmo snapshot de quatro linhas; JSON e CSV preservaram categoria/origem e PDF A4 abriu com uma página |
| revogação Health Connect | aprovado após correção | ensaio encontrou permissão Android remanescente; desligar um sensor agora revoga todo o Health Connect, desliga os quatro consentimentos de sensor e preserva categorias manuais |
| limpeza do ensaio | aprovado | registro sintético, arquivos temporários e Toolbox removidos; Agenda terminou sem permissão Health concedida |

## Gate 7

| Verificação | Estado | Evidência |
|---|---|---|
| tela e rota sem reprodução | aprovado | rota efetiva exibida como automática do sistema |
| prévia isolada | aprovado após correção | tom de 700 ms e volume interno 0,35 agora pode ser testado sem ativar alertas; rascunho sobrevive a recriação da Activity |
| automática do sistema | aprovado com ressalva | beep ouvido na ZL02CPRO; a escolha automática do Android não priorizou o telefone |
| priorizar telefone | aprovado | `setPreferredDevice` aceito, saída efetiva exibida como Moto Edge 60 e beep ouvido no telefone |
| priorizar fone com ZL02CPRO | falhou/intermitente | Android aceitou e exibiu a smartband como saída; houve reprodução em tentativas automáticas anteriores, mas não na tentativa observada desta rota |
| Não Perturbe | aprovado | tentativa bloqueada antes de pedir foco de áudio; estado normal restaurado imediatamente |
| fone Bluetooth real | aprovado | MOTO XT220 foi identificado como saída efetiva e reproduziu o beep |
| remoção do fone | aprovado com ressalva | após remover o MOTO XT220, a próxima saída elegível foi a ZL02CPRO; fallback para telefone não ocorre enquanto outro endpoint Bluetooth existe |
| mídia concorrente | aprovado | foco `MAY_DUCK`: beep no MOTO XT220, música reduziu e retornou ao volume normal |
| fone com fio/USB | não suportado no ambiente | acessório não disponível na sessão |
| chamada controlada | não suportado no ambiente | nenhuma chamada real foi provocada; bloqueio por política permanece coberto por teste automatizado |

## Gate 8 - aprovado com limites do hardware

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
| alerta geral desligado | aprovado; notificações existentes removidas e nova fixture ficou com zero entregas |
| protocolo `Vou sair` | aprovado no telefone; controles no pulso são indisponíveis na smartband e aprovados no AVD Wear OS |

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
O desligamento percorreu perfil, WorkManager, notificações visuais e limpeza do
estado Wear. Com o perfil `globalEnabled=false`, uma nova definição permaneceu
agendada no banco com zero entregas e não gerou estímulo no telefone ou no Da
Fit.

## Gate 9

Baseline inicial às 11:20 UTC: telefone em carga, bateria 48%, temperatura
34,0 °C e PSS aproximado de 198 MB após uso funcional. Medição intermediária
às 16:20 UTC, depois dos gates 6 e 7: bateria 52%, temperatura 33,9 °C, PSS
aproximado de 190 MB e nenhum `FATAL EXCEPTION` retido. A variação de bateria
não representa consumo porque houve carga durante a sessão. A janela de 24
horas, bateria da pulseira e medição final continuam pendentes. Esse baseline
foi encerrado sem aceite porque uma nova variante `fieldTest` foi instalada
durante a janela.

Novo baseline do binário com seleção individual de áudio, às 20:13 UTC:
telefone fora de carga, bateria 28%, temperatura 31,9 °C, PSS aproximado de
129 MB e zero referências ao pacote no buffer de crash. O timeout permaneceu em
5 minutos. A coleta final foi reagendada para 04/09/2026 às 16:14
(`America/Manaus`), após completar 24 horas desse mesmo binário.

O timeout temporário de tela do Moto foi restaurado de 30 para 5 minutos. A
coleta final protegida foi reagendada localmente para 04/09/2026 às 16:14
(`America/Manaus`), após completar 24 horas. Ela recusa execução
antecipada, emulador e modelo diferente do telefone autorizado; sucesso ou
falha será mostrado em diálogo. A smartband fechada não expõe bateria por ADB,
portanto esse valor continua dependendo de leitura manual no aplicativo
companheiro ou será registrado como indisponível.

## Próxima interação mínima

1. manter telefone/computador na mesma rede até a coleta agendada; o telefone
   pode ficar fora do cabo;
2. informar a bateria da pulseira pelo aplicativo companheiro, se disponível;
3. escolher armazenamento separado para o backup cifrado da chave definitiva.

## Seleção individual de áudio

A variante atual lista separadamente as saídas externas que o `AudioManager`
expõe e permite manter uma preferência local por nome e tipo, sem endereço
Bluetooth. No Moto, com fone e smartband conectados, somente `MOTO XT220`
apareceu como rota de áudio; a ZL02CPRO permaneceu conectada ao Da Fit, mas não
foi exposta simultaneamente como saída. A interface selecionou o fone sem salvar
o rascunho nem reproduzir som. Se o Android expuser os dois perfis de áudio, os
dois serão listados; a Agenda não consegue ativar por API pública um perfil
A2DP que o sistema não ofereceu.
