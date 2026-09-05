# Registro do candidato 0.1

Gerado em 03/09/2026 às 16:36:41 UTC para uso pessoal por `sideload`. Nenhum
artefato foi publicado ou enviado a terceiro.

## Assinatura

- algoritmo da chave: RSA, 4096 bits;
- certificado SHA-256: `2339d2c87cea8b686aecbfca621de7d0f0816f4c7c7c14fe8431b8249cfe4905`;
- mesmo certificado confirmado nos módulos telefone e Wear;
- chave privada e senha: fora do repositório;
- backup cifrado separado: criado no `appDataFolder` do Google Drive e
  restauração não destrutiva validada em 03/09/2026.

## Artefatos

| Artefato | SHA-256 |
|---|---|
| `app-release.apk` | `22affa7db9510580f408f6cf22eab4b87b07794d3f5da6ec678fb286809cf9f2` |
| `wear-release.apk` | `cf3c5e510e525032e40db3b060843f79a00edb100c8bc36a982362d490a40516` |
| `app-release.aab` | `640d06dc4df4c17236d70359ca2896b1191904d3a05da87c00cd5b0ab78cd670` |
| `wear-release.aab` | `6033b0286afbacf03666598dc77aef92bf96997f1845903dfdbf2dce39f1d837` |

`android/scripts/p2_10_release_candidate.sh` verificou as quatro assinaturas,
o certificado comum e o gate estático. O gate 9 foi concluído; a instalação
final aguarda a regeneração do candidato com a seleção de áudio atual e a
correção do sync local reaberto.

Este candidato foi sucedido por uma alteração ainda não empacotada na seleção
individual de saída de áudio. Os hashes acima continuam identificando o
candidato auditado, mas não devem ser instalados como fechamento final. Após o
novo código passar pelos gates, os quatro artefatos serão regenerados com a
mesma chave e este registro receberá os novos hashes.
