# Runbook de teste fisico P2-10

Versao: 1.0, 2026-09-03. Este procedimento so pode ser executado apos
autorizacao explicita do proprietario dos dispositivos. Nao versionar numero de
serie, conta, pareamento, relatorio, captura de tela com dado pessoal ou saida
bruta que contenha identificadores.

## Variante isolada

Telefone e Wear usam o mesmo `applicationId` de teste:
`com.pessoal.agenda.mobile.fieldtest`. Ele e diferente do pacote normal,
`com.pessoal.agenda.mobile`, portanto possui banco, preferencias, Keystore,
permissoes e Data Layer separados. O rotulo visivel e `Agenda Sensorial - Teste`
e a versao e `0.1.0-fieldtest`.

O build `fieldTest` usa assinatura debug e nunca e artefato publicavel. Nao
instalar APK release sem uma estrategia de assinatura fora do repositorio.

## Trava e pre-flight

Sem as tres variaveis abaixo, o script termina antes da primeira chamada ADB:

```bash
AGENDA_ALLOW_PHYSICAL_TESTS=I_HAVE_EXPLICIT_USER_AUTHORIZATION \
AGENDA_PHYSICAL_SERIAL='<serial autorizado>' \
AGENDA_PHYSICAL_STAGE=preflight \
./android/scripts/p2_10_physical_gate.sh
```

`AGENDA_PHYSICAL_WEAR_SERIAL` e opcional e so deve ser informado se o relogio
fisico estiver com depuracao autorizada. O pre-flight le fabricante/modelo,
versao Android, SDK, bateria e presenca anterior da variante, sem instalar,
limpar, conceder permissao ou abrir a Agenda pessoal. A saida fica em `/tmp`.

## Instalacao isolada

Trocar o stage para `install`. O script monta e instala somente a variante
`.fieldtest`, sem `pm clear`, sem `grant` e sem alterar configuracoes de rede,
bateria, Bluetooth, audio ou Health Connect. Se houver serial Wear autorizado,
instala o APK correspondente com o mesmo pacote/assinatura.

### Conexao do Samsung sem ocupar USB

Em Android 11 ou superior, computador e telefone podem usar a mesma rede Wi-Fi.
Ativar `Opcoes do desenvolvedor > Depuracao sem fio` e parear por QR code ou
codigo no Device Manager do Android Studio. Depois do pareamento, o cabo USB nao
e necessario. Em Android 10 ou inferior, ADB por TCP/IP exige conexao USB
inicial. A smartband nao usa ADB nem precisa ocupar a porta USB; deve ser
carregada separadamente e continuar pareada ao aplicativo companheiro.

## Gate 6 - telefone, permissao e relatorio

Pre-condicoes: backup normal do telefone concluido; bateria acima de 40%; app
normal nao selecionado; fixtures identificadas como ficticias.

1. abrir `Agenda Sensorial - Teste` e confirmar ausencia de prompt no startup;
2. negar notificacao ao tentar ativar alertas e confirmar que tarefas,
   protocolos e captura continuam funcionando;
3. conceder notificacao em nova tentativa e confirmar estado visivel;
4. ativar uma categoria Health Connect ficticia, negar e confirmar zero leitura;
5. conceder somente uma categoria com dado de teste, importar em foreground e
   conferir origem, cobertura e lacuna sem zero inventado;
6. revogar a categoria e confirmar bloqueio de nova importacao;
7. criar entradas manuais ficticias, corrigir e excluir uma delas;
8. gerar previa de sete dias, excluir uma linha e exportar JSON, CSV e PDF para
   pasta temporaria escolhida pelo usuario;
9. abrir os tres arquivos, conferir o mesmo snapshot e apaga-los ao terminar;
10. confirmar que nenhum dado de saude entrou na fila de sync desktop.

Aprovar somente sem crash, prompt espontaneo, permissao excedente, vazamento em
log ou divergencia entre previa e arquivos.

## Gate 7 - audio e politica do sistema

Usar apenas o botao de teste cancelavel, em volume baixo e fora de chamada real.
Registrar para cada linha apenas `aprovado`, `falhou` ou `nao suportado`:

| Cenario | Resultado |
|---|---|
| automatico sem fone | pendente |
| preferir telefone | pendente |
| fone Bluetooth conectado | pendente |
| fone com fio/USB, se suportado | pendente |
| fone removido durante o teste | pendente |
| DND/silencioso | pendente |
| audio de midia concorrente | pendente |
| chamada simulada/ambiente de teste | pendente |

A Agenda pode preferir um `AudioDeviceInfo` para seu proprio `AudioTrack`, mas
nao controla a rota de WhatsApp ou outro app. Aprovar se a rota efetiva/fallback
for informada, DND e foco forem respeitados, o teste cancelar e nenhuma escolha
global permanecer alterada.

## Gate 8 - wearable fisico

O wearable disponível é a Mertto ZL02D Sport, identificada em `Sobre` como
família ZL02CPro, firmware 2.0.9. Ela não executa Wear OS: não informar serial
Wear, não instalar APK Wear e não tentar ADB. Confirmar qual aplicativo
companheiro está pareado e conceder acesso somente às notificações fictícias da
`Agenda Sensorial - Teste`.

Depois da permissão visual, o ADB autorizado pode criar um único alerta visual
fictício sem desktop. O receptor existe apenas no build `fieldTest`, exige a
permissão de sistema `android.permission.DUMP` do processo shell e não toca em
dados pessoais:

```bash
adb -s '<serial autorizado>' shell am broadcast \
  -a com.pessoal.agenda.mobile.fieldtest.PUBLISH_FIXTURE_ALERT \
  -n com.pessoal.agenda.mobile.fieldtest/com.pessoal.agenda.mobile.fieldtest.FieldTestAlertReceiver
```

1. publicar alerta ficticio e verificar texto e vibracao espelhados;
2. observar se a pulseira mostra ações; registrar `suportado` ou `indisponível`,
   sem presumir que botões Android sejam encaminhados pelo app companheiro;
3. usar `Concluir` e `Adiar` no telefone e verificar que a notificação refletida
   desaparece ou muda sem duplicação;
4. desligar Bluetooth, publicar um alerta e confirmar que o telefone continua
   funcional sem repetição agressiva na pulseira;
5. reconectar e verificar a política real do companheiro para alertas perdidos;
6. executar um passo fictício de `Vou sair` com a pulseira apagada/acesa;
7. desligar alertas no telefone e confirmar ausência de novo estímulo.

Não capturar tráfego BLE, não fazer engenharia reversa do protocolo proprietário
e não importar dados de saúde do aplicativo companheiro sem API oficial e novo
consentimento. Dados de saúde continuam vindo do Health Connect no telefone.

## Gate 9 - bateria, memoria e temperatura

Coletar baseline antes da instalacao e apos o gate funcional. Executar uma janela
passiva de 24 horas com alertas cautelosos, sem sensor continuo e sem treino
periodico. Registrar agregados, nunca dump bruto versionado:

| Medida | Inicio | Fim | Criterio inicial |
|---|---:|---:|---|
| bateria telefone (%) | pendente | pendente | sem drenagem anormal atribuida ao app |
| bateria Wear (%) | pendente | pendente | sem drenagem anormal atribuida ao app |
| temperatura maxima | pendente | pendente | sem aquecimento percebido/alerta termico |
| PSS telefone em repouso | pendente | pendente | sem crescimento monotono apos reabrir 5x |
| PSS Wear em repouso | pendente | pendente | sem crescimento monotono apos reabrir 5x |
| jobs/wakelocks | pendente | pendente | nenhum loop ou trabalho periodico do modelo |

Interromper em alerta repetido, aquecimento, perda rapida de bateria, audio que
nao cancela ou comportamento que afete chamada/midia fora da Agenda.

## Encerramento

Desinstalar apenas `com.pessoal.agenda.mobile.fieldtest` quando solicitado pelo
proprietario. Nao desinstalar nem limpar `com.pessoal.agenda.mobile`. Resumir na
matriz somente aparelho/modelo sem serial, versoes, resultado e desvios sem dado
pessoal. O gate 10 continua exigindo decisao separada entre uso pessoal e
distribuicao publica.
