# Modelo de ameaça P2-03

## Ativos

Credencial do dispositivo, chave privada Android, identidade do desktop, operações pendentes, réplicas, decisões de conflito e dados pessoais que passarão a existir após pareamento real.

## Adversários considerados

| Ameaça | Controle obrigatório |
|---|---|
| QR adulterado | parser estrito, HTTPS obrigatório, fingerprint e confirmação no desktop |
| observador ou intermediário na rede local | certificado efêmero fixado, credencial por dispositivo e autenticação de cada sessão |
| repetição do convite ou código | nonce aleatório, expiração de cinco minutos, código fora do QR e consumo único |
| dispositivo não confirmado | estado pendente e aprovação explícita no desktop antes da emissão da credencial |
| credencial copiada do armazenamento | chave AES não exportável no Keystore e backup desativado |
| aparelho revogado | consulta de revogação em toda sessão; nenhum acesso apenas por posse de convite antigo |
| lote repetido | `operation_id`, hash, sequência e resultado idempotente persistido |
| UUID reutilizado com conteúdo diferente | rejeição terminal `ID_REUSED`, sem alterar o resultado original |
| payload excessivo ou malformado | limite antes de parse, schema fechado e mensagem externa genérica |
| vazamento por logs | logger sanitizado sem payload, texto, URL completa, nonce, token ou credencial |
| conflito concorrente | `base_revision`, estado `CONFLICT` e revisão explícita; sem último escritor silencioso |

## Limites assumidos

Comprometimento com root do telefone, invasão completa do desktop ou acesso físico desbloqueado não podem ser neutralizados pelo protocolo. A fase reduz exposição e permite revogação, mas não promete confidencialidade nesses cenários. O relógio não recebe credencial mestre do desktop.

## Gates antes de rede real

1. schemas e fixtures válidas/inválidas em Java e Kotlin;
2. parser de convite rejeitando campos extras, duplicados, expiração e downgrade;
3. testes de uso único, aprovação, recusa, expiração e comparação constante;
4. teste de pin TLS com certificado incorreto;
5. teste de idempotência e cursor com banco isolado;
6. revisão de logs e limites de corpo;
7. teste exclusivamente em AVD e banco temporário.
