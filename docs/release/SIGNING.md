# Assinatura de release

Telefone e Wear precisam usar o mesmo certificado para o Data Layer. Nenhum
keystore, alias, senha ou arquivo de propriedades com segredo pode entrar no
repositorio.

Os módulos aceitam exclusivamente estas variáveis de ambiente:

- `AGENDA_RELEASE_STORE_FILE`;
- `AGENDA_RELEASE_STORE_PASSWORD`;
- `AGENDA_RELEASE_KEY_ALIAS`;
- `AGENDA_RELEASE_KEY_PASSWORD`.

Todas devem estar presentes ou todas ausentes. Sem elas, `assembleRelease`
continua gerando APK não assinado para auditoria local. Com elas, o mesmo bloco
de assinatura e aplicado a telefone e Wear.

`android/scripts/p2_10_release_candidate.sh` exige um keystore fora do
repositorio, limpa apenas outputs Gradle, gera APK/AAB dos dois módulos, verifica
assinaturas, exige o mesmo SHA-256 de certificado, repete o gate estático e
grava somente certificado/checksums em `/tmp`. O script não cria chave, não
instala, não publica e não imprime senhas.

O ensaio automatizado deste fluxo usa uma chave sintética de curta validade em
`/tmp` e a remove ao terminar. Ela serve apenas para provar o empacotamento e
jamais pode ser usada para distribuição.

Antes de distribuição pública, decidir quem controla a chave, política de
backup/rotação, Play App Signing e recuperação. Para uso pessoal, manter o
keystore em armazenamento privado com cópia cifrada separada.

## Cerimonia da versao 0.1

Em 03/09/2026, com autorização explícita do proprietário, foi criada uma chave
definitiva RSA de 4096 bits em armazenamento privado fora do repositório. O
arquivo tem modo `600`; a senha não foi impressa nem registrada. APK e AAB de
telefone e Wear foram gerados, tiveram assinatura verificada e apresentaram o
mesmo certificado. O registro público está em `RELEASE_0.1.md`.

Em 03/09/2026, a cópia cifrada foi criada no `appDataFolder` do Google Drive
com escopo mínimo. O ensaio de restauração baixou, autenticou, decifrou e abriu
o PKCS#12, comparando seu certificado com a chave local sem substituir o
arquivo. Conta, token, senha e identificador remoto não foram registrados.
Não mover o keystore aberto para o repositório, para o telefone ou para
diretório sincronizado sem proteção explícita.
