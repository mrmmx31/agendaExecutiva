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
