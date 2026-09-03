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

Depois desses passos, informe que a configuração Cloud foi concluída. A Agenda
ainda precisará implementar consentimento incremental, upload, download e teste
de restauração. Na primeira utilização do backup, o Google voltará a mostrar a
tela de consentimento para o novo escopo; o acesso já concedido ao Tasks deve
ser preservado.

## Limites

- o arquivo é cifrado localmente antes do upload;
- a senha de recuperação não é a senha da conta Google e não vai ao Drive;
- token OAuth, keystore aberto e credenciais do cliente não entram no backup;
- a restauração deve verificar hash e certificado antes de substituir qualquer
  arquivo local;
- publicação para terceiros exige nova revisão OAuth, privacidade e suporte.

Referências: [pasta de dados da aplicação](https://developers.google.com/drive/api/guides/appdata) e
[autorização incremental](https://developers.google.com/identity/protocols/oauth2/resources/best-practices#incremental-authorization).
