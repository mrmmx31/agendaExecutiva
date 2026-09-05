# Aceite e distribuicao do Projeto 2

Atualizado em 03/09/2026. Este documento separa o aceite tecnico do uso pessoal
de qualquer decisao futura de venda ou publicacao em loja.

## Decisao da versao 0.1

- canal: instalacao pessoal direta (`sideload`);
- publico: somente o proprietario do projeto;
- venda, Play Store e distribuicao a terceiros: nao autorizadas nesta versao;
- dados: permanecem locais conforme os consentimentos e limites documentados;
- alegacao clinica, diagnostico, tratamento e emergencia: proibidos.

Essa decisao permite concluir o piloto pessoal sem preencher declaracoes de uma
loja que ainda nao sera usada. Antes de venda, beta externo ou publicacao, devem
ser reabertos escopo regulatorio, politica de privacidade hospedada, Data Safety,
Health apps, suporte, controlador dos dados e estrategia de Play App Signing.

## Estado do aceite

| Item | Estado |
|---|---|
| gates funcionais 1 a 8 | aprovados, com limites fisicos catalogados |
| janela passiva de 24 horas e medicao final | aprovada após 32h55 no gate 9 |
| APK/AAB e verificacao de certificado comum | candidato final assinado e verificado em 05/09/2026 |
| chave pessoal definitiva | criada fora do repositorio, protegida por senha e modo `600` |
| backup cifrado separado da chave | aprovado no Google Drive `appDataFolder`; upload e restauração não destrutiva validados em 03/09/2026 |
| instalacao do APK release pessoal | aprovada no Moto Edge 60; pareamento, snapshot e fila 0 confirmados |
| publicacao ou venda | fora do escopo da versao 0.1 |

## Cerimonia minima da chave pessoal

1. criar o keystore fora do repositorio;
2. usar senha forte que nao apareca em terminal, Git ou documento;
3. manter uma copia cifrada separada e testar sua restauracao;
4. fornecer as quatro variaveis `AGENDA_RELEASE_*` somente ao processo local;
5. executar `android/scripts/p2_10_release_candidate.sh`;
6. guardar certificado e checksums, nunca as senhas, junto ao registro da versao;
7. instalar apenas o APK do telefone no Moto; o APK Wear fica reservado a
   dispositivos Wear OS e nao deve ser instalado na ZL02CPRO.

Perder a chave impede atualizacoes assinadas sobre a mesma instalacao. A chave
definitiva foi criada em 03/09/2026 após autorização explícita, fora do
repositorio e sem expor a senha. Ela não deve ser usada para instalação até
existir uma cópia cifrada separada e sua restauração ter sido testada.

## Fechamento

O Projeto 2 chegou a 100% em 05/09/2026. O sync local persistente foi validado,
os artefatos foram regenerados com a chave pessoal, o APK release foi instalado
e o percurso final confirmou startup sem prompt, câmera negada com fallback por
colagem, pareamento, snapshot e fila 0. A tela foi mantida ligada por solicitação
do usuário durante a depuração; essa configuração não pertence ao aplicativo.
