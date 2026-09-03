# Preparação do Google Drive

Objetivo: permitir backup cifrado da chave de assinatura na pasta privada de
dados da Agenda. Esta preparação não concede acesso aos arquivos normais do
Drive e não altera sozinha o token atual do Google Tasks.

## Ação manual no Google Cloud

1. Acesse a [biblioteca da Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com).
2. No seletor superior, escolha o mesmo projeto que contém o cliente OAuth
   usado pela Agenda. Não crie outro cliente e não substitua
   `~/.agenda/google-credentials.json`.
3. Na página `Google Drive API`, clique em `Ativar` (`Enable`). Se aparecer
   `Gerenciar`, a API já está ativa.
4. Abra [Google Auth Platform - Data Access](https://console.cloud.google.com/auth/scopes).
5. Clique em `Add or remove scopes` e adicione somente
   `https://www.googleapis.com/auth/drive.appdata`.
6. Salve. Não adicione `drive`, `drive.file` nem escopos de leitura geral.
7. Em [Audience](https://console.cloud.google.com/auth/audience), confirme que a
   conta usada pela Agenda está cadastrada como usuário de teste quando o app
   estiver em modo `Testing`.

Depois desses passos, abra `Configurações > Integrações > Backup da chave de
assinatura` e use `Autorizar Google Drive`. A autorização é incremental e pede
Tasks junto com `drive.appdata`, preservando a sincronização existente. O link
pode ser aberto e copiado ou somente copiado para outro perfil do navegador.

Em seguida, use `Criar ou atualizar backup`. A senha atual do PKCS#12 apenas
valida a chave local. A senha de recuperação, com no mínimo 16 caracteres,
deriva a chave AES e não é salva. Por fim, `Testar restauração` baixa, decifra e
valida o PKCS#12 em memória sem substituir o arquivo local.

## Implementação

- `GoogleAuthService`: escopos persistidos e migração de tokens antigos como
  Tasks-only, consentimento incremental e preservação do refresh token;
- `GoogleDriveAppDataService`: lista, cria, atualiza e baixa um único arquivo
  fixo em `appDataFolder`;
- `SigningKeyBackupCrypto`: PBKDF2-HMAC-SHA256 (600.000 iterações), AES-256-GCM,
  salt e nonce aleatórios, cabeçalho autenticado e hash SHA-256 do conteúdo;
- `SigningKeyDriveBackupService`: valida chave privada/certificado no PKCS#12,
  zera buffers temporários e executa restauração não destrutiva.

## Limites

- o arquivo é cifrado localmente antes do upload;
- a senha de recuperação não é a senha da conta Google e não vai ao Drive;
- token OAuth, keystore aberto e credenciais do cliente não entram no backup;
- a restauração deve verificar hash e certificado antes de substituir qualquer
  arquivo local;
- publicação para terceiros exige nova revisão OAuth, privacidade e suporte.
- o estado só pode ser marcado como concluído depois de upload e restauração
  reais com a chave definitiva; testes unitários não substituem essa prova.

Referências: [pasta de dados da aplicação](https://developers.google.com/drive/api/guides/appdata) e
[autorização incremental](https://developers.google.com/identity/protocols/oauth2/resources/best-practices#incremental-authorization).
