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
5 minutos. Uma coleta havia sido agendada para 04/09/2026 às 16:14, mas esse
baseline foi posteriormente invalidado pela correção de navegação abaixo.

O timeout temporário de tela do Moto foi restaurado de 30 para 5 minutos. A
instalação da correção de navegação em 03/09 invalidou o baseline anterior; o
timer das 16:14 de 04/09 foi cancelado porque o telefone sairia da rede. Uma
coleta válida foi realizada em 05/09, 32h55 após instalar esse APK: bateria em
73%, fora de carga, temperatura de 28,9 °C e cinco amostras PSS de 162.791,
162.584, 162.714, 162.932 e 163.073 KiB. Não houve crescimento monotônico nem
referência ao pacote no buffer de crash. O gate 9 foi aprovado. A smartband
fechada não expõe bateria por ADB; essa limitação permanece registrada.

## Navegação e sync móvel

O `fieldTest` mais recente foi instalado com preservação dos dados. As seis
operações locais continuaram na fila. No Moto, abrir `Capturar` e pressionar
Voltar retornou a `Hoje`; um segundo Voltar encerrou a Activity, que foi
reaberta ao final. Dois testes Compose equivalentes passaram no AVD.

O aceite também revelou que o telefone não estava pareado e que o servidor
desktop atual só vive durante a sessão efêmera de pareamento. A fila offline é
durável, mas sync recorrente após fechar/expirar a janela ainda não é um recurso
operacional. `P2-03` foi reaberta para corrigir o ciclo de vida do endpoint e da
identidade TLS.

Em 05/09, a correção de `P2-03` foi instalada preservando os dados. O Moto
permaneceu pareado depois de reiniciar o desktop, recebeu o snapshot e reduziu a
fila de 8 para 0 sem duplicar a captura aplicada. A tela inicial passou a
mostrar `Pareado ao desktop`; o desktop passou a mostrar o nome do aparelho e a
atualizar o estado após aprovação, revogação ou comando manual.

O mesmo aceite revelou que pendentes e concluídas usavam o mesmo ícone na lista
Android. A variante corrigida usa círculo vazio para pendente, marca e título
riscado/atenuado para concluída, e mantém as concluídas depois das tarefas
abertas sem alterar a ordem relativa de cada grupo. O APK `fieldTest` foi
reinstalado com preservação de dados; inspeção física em tema escuro confirmou
as pendências no início da tela. Testes unitários, montagem do APK e 20 testes
Compose direcionados passaram no AVD.

## Próxima interação mínima

Nenhuma para o release pessoal `0.1.0`. O candidato final foi assinado,
instalado, pareado e sincronizado em 05/09/2026. O backup cifrado da chave no
Google Drive e a restauração não destrutiva foram validados em 03/09/2026.

## Seleção individual de áudio

A variante atual lista separadamente as saídas externas que o `AudioManager`
expõe e permite manter uma preferência local por nome e tipo, sem endereço
Bluetooth. No Moto, com fone e smartband conectados, somente `MOTO XT220`
apareceu como rota de áudio; a ZL02CPRO permaneceu conectada ao Da Fit, mas não
foi exposta simultaneamente como saída. A interface selecionou o fone sem salvar
o rascunho nem reproduzir som. Se o Android expuser os dois perfis de áudio, os
dois serão listados; a Agenda não consegue ativar por API pública um perfil
A2DP que o sistema não ofereceu.
