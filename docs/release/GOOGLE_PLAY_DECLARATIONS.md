# Declaracoes Google Play - rascunho P2-10

Data da revisao: 2026-09-03. Este arquivo e uma entrada para o Play Console, nao
uma declaracao submetida. A resposta final deve ser confrontada com o AAB
assinado, a lista de SDKs e o comportamento da versao publicada.

## Health apps

- o aplicativo oferece registro e visualizacao de dados de saude e fitness;
- categorias aplicaveis incluem atividade/fitness, sono e gerenciamento de
  saude, conforme as opcoes vigentes do formulario;
- o app acessa Health Connect somente para leitura em foreground das categorias
  ativadas;
- nao oferece diagnostico, tratamento, prescricao, ajuste de medicacao ou
  monitoramento de emergencia;
- o aviso de privacidade deve estar no app e em URL publica antes da submissao.

## Data Safety - comportamento observado

| Tipo | Origem/uso | Sai do aparelho | Compartilhado/vendido |
|---|---|---|---|
| atividade do app | tarefas, protocolos, alertas e acoes | apenas sync local pareado escolhido pelo usuario | nao |
| informacao de saude | Health Connect e entrada voluntaria | nao no produto atual; somente arquivo exportado pelo usuario | nao |
| identificador do dispositivo | UUID e credencial de pareamento | somente desktop local pareado | nao |
| diagnostico tecnico | classe de erro sanitizada | nao ha backend de analytics/crash | nao |
| modelo pessoal | artefato e metricas agregadas | nao | nao |

O conceito de "coleta" do formulario Google deve ser respondido conforme sua
definicao vigente, inclusive excecoes para processamento local, transferencia
iniciada pelo usuario e provedores de servico. O Google Play services for Wear
OS deve ser revisto no SDK Index antes de cada release.

## Permissoes do APK atual

Declaradas diretamente pelo telefone:

- `INTERNET`: HTTPS somente com o desktop local pareado;
- `POST_NOTIFICATIONS`: alerta visual, pedido contextual;
- `VIBRATE`: estimulo habilitado pelo usuario;
- `health.READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_SLEEP` e
  `READ_STEPS`: leitura Health Connect granular e em foreground.

Adicionadas por bibliotecas AndroidX/Google nos APKs:

- `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `WAKE_LOCK` e
  `RECEIVE_BOOT_COMPLETED`: WorkManager/Data Layer;
- permissao dinamica `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: protecao de
  receivers internos AndroidX.

O APK Wear nao solicita sensor corporal, localizacao, microfone, camera, contatos
ou armazenamento. Nenhum APK solicita escrita Health Connect, historico
ampliado ou leitura em background.

## Checklist de submissao

- [ ] preencher controlador e contato no aviso de privacidade;
- [ ] publicar aviso em URL HTTPS publica, nao geobloqueada e fora de PDF;
- [ ] adicionar a mesma URL no app e no Play Console;
- [ ] gerar e analisar AAB assinado de release;
- [ ] revisar SDK Index e manifest final do AAB;
- [ ] preencher Data Safety conforme definicoes vigentes;
- [ ] preencher Health apps declaration;
- [ ] fornecer conta/instrucoes de revisao sem dados pessoais;
- [ ] arquivar capturas e respostas submetidas com a versao do release.

Referencias oficiais:

- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/14738291
- https://support.google.com/googleplay/android-developer/answer/16679511

