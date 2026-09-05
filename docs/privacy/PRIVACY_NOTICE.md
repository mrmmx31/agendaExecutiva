# Aviso de privacidade - Agenda Sensorial

Versao de trabalho: 1.0, 2026-09-03.

Este aviso descreve o comportamento da versao `0.1.0` para Android e Wear OS.
Antes de distribuicao publica, preencher a identificacao e o contato do
controlador, publicar este conteudo em URL HTTPS acessivel e imutavel para o
usuario, e revisar novamente o aplicativo efetivamente assinado.

## Finalidade

A Agenda Sensorial auxilia organizacao pessoal, lembretes, protocolos, registro
voluntario e preparacao de um relatorio revisavel. Ela nao diagnostica, nao
prescreve, nao recomenda dose ou substancia e nao substitui profissional de
saude.

## Dados tratados

- tarefas, capturas, protocolos, alertas e respectivas acoes;
- preferencias sensoriais e historico categorico de recomendacoes;
- credencial tecnica para pareamento com o desktop escolhido pelo usuario;
- mediante consentimento separado, resumos de frequencia cardiaca, sono e
  passos lidos do Health Connect;
- mediante registro voluntario, medicacao, substancia, sintoma, evento e nota
  de rotina;
- artefato e metricas agregadas do modelo pessoal local.

O inventario detalhado e as retencoes ficam em `HEALTH_DATA_INVENTORY.md`,
`RECOMMENDATION_DATA_INVENTORY.md` e `PERSONAL_MODEL_DATA_INVENTORY.md`.

## Processamento, transferencia e compartilhamento

Os dados permanecem nos dispositivos do usuario. A sincronizacao com o desktop
ocorre somente por canal local pareado e autenticado; dados de saude ainda nao
fazem parte desse canal. Telefone e relogio trocam apenas estados e comandos
minimos pelo Google Play services for Wear OS. Nao ha analytics, publicidade,
venda de dados, IA em nuvem ou envio automatico de relatorio.

Uma exportacao JSON, CSV ou PDF so e criada depois de previa, selecao de formato
e destino pelo usuario. O arquivo exportado pode conter dados sensiveis e deixa
as protecoes internas da Agenda no destino escolhido.

## Protecao e retencao

Backup e transferencia Android estao desativados. Credenciais e conteudo de
saude usam chaves nao exportaveis do Android Keystore; saude e cifrada com
AES-256-GCM. Transporte local usa TLS com certificado fixado. Retencao de saude
e configuravel por categoria; historico de recomendacao tambem possui retencao
e exclusao local. Logs tecnicos nao devem conter texto sensivel, token ou
credencial.

## Escolhas do usuario

Cada categoria de saude inicia desligada e pode ser revogada separadamente.
Permissoes Health Connect sao solicitadas somente ao importar. Alertas,
personalizacao, audio e vibracao podem ser desligados. O usuario pode corrigir
ou excluir registros locais e revisar cada linha antes da exportacao. Negar ou
revogar saude nao bloqueia tarefas, protocolos ou alertas.

A camera e solicitada somente ao escolher `Ler QR code` no pareamento. A imagem
e interpretada no aparelho, nao e armazenada nem transmitida; negar a permissao
mantem disponivel a colagem manual do convite e nao bloqueia outras funcoes.

## Pendencias anteriores a publicacao

- controlador: `A DEFINIR`;
- contato de privacidade: `A DEFINIR`;
- URL publica HTTPS: `A DEFINIR`;
- data de vigencia publica: `A DEFINIR`.
