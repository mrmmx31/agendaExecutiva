# Contrato de pareamento local v1

## Princípios

- o pareamento exige presença diante do desktop e confirmação explícita nele;
- o QR identifica uma sessão curta, mas não concede acesso sozinho;
- nenhuma descoberta automática, porta permanente ou fallback para HTTP é permitido;
- falhas externas usam mensagem genérica e logs não contêm convite, código, nonce, URL completa, chave ou credencial.

## Convite

```text
agenda://pair?v=1&session_id=<uuid>&desktop_id=<uuid>&endpoint=<url>&expires_at=<utc>&nonce=<base64url>&fingerprint=<sha256-hex>
```

O endpoint deve ser exatamente `https://<host>:<porta>/api/v1/pair/requests`. A sessão dura no máximo cinco minutos. `nonce` contém entre 32 e 96 bytes aleatórios em Base64 URL-safe sem padding; `fingerprint` é o SHA-256 hexadecimal minúsculo do certificado X.509 efêmero. O código decimal de seis dígitos aparece somente no desktop.

## Fluxo em duas etapas

1. O Android valida integralmente o convite, fixa o certificado pelo fingerprint e cria uma chave RSA 2048 no Android Keystore.
2. `POST /api/v1/pair/requests` envia a solicitação limitada a 32 KiB.
3. O desktop valida sessão, nonce e código em tempo constante e mostra nome, ID e papéis solicitados.
4. Antes da decisão, responde `202` com `request_id`, token efêmero de conclusão e intervalo de consulta. Nenhuma credencial permanente existe ainda.
5. O usuário aprova ou recusa no desktop. O Android consulta `POST /api/v1/pair/requests/{request_id}/complete` usando o token efêmero.
6. Somente após aprovação o desktop cria uma credencial aleatória de 32 bytes, persiste apenas seu hash e devolve a credencial cifrada para a chave pública do Android.
7. A chave privada não sai do Keystore. A credencial é recifrada por AES-GCM com chave não exportável antes de qualquer persistência Android.

Solicitação, aprovação, expiração, cancelamento e conclusão são de uso único. Repetir a consulta de conclusão pode devolver a mesma resposta cifrada durante a sessão, sem emitir outra credencial.

## Papéis v1

- `TASKS_READ`: receber réplica operacional de tarefas;
- `CAPTURES_WRITE`: enviar capturas móveis;
- `PROTOCOLS_EXECUTE`: receber protocolos e enviar execuções.

O desktop concede um subconjunto visível dos papéis solicitados. Ausência de papel recusa somente a operação correspondente.

## Revogação

O desktop mantém ID, nome normalizado, hash da credencial, papéis, contrato, criação, último uso e instante de revogação. Revogar bloqueia novas sessões imediatamente, mas não apaga o banco do telefone. O Android mostra estado revogado e oferece remover seus dados locais separadamente.
