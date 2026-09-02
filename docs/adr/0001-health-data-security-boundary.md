# ADR 0001 - Fronteira local de dados de saude

## Contexto

`P2-07` introduz consentimentos e registros manuais sensiveis. O dominio precisa
ser testavel sem permissao Android e sem misturar plaintext com transporte,
logs ou recomendacao.

## Decisao

O Room guarda metadados operacionais em colunas normais e campos sensiveis como
envelopes AES-256-GCM. A chave fica no Android Keystore e nao e exportavel. O
dominio depende de uma interface de cifra para permitir testes deterministas sem
chave real. Consentimento de produto e permissao Health Connect sao estados
distintos; ambos sao necessarios para importar uma categoria.

Health Connect entra por uma interface isolada e declara apenas permissoes de
leitura para frequencia cardiaca, frequencia em repouso, sono e passos. A
declaracao no manifesto nao dispara o pedido: a UI solicita somente as categorias
ativadas, ao tocar em importar. Leituras ocorrem em foreground e cobrem sete dias.
Historico ampliado, background e escrita permanecem fora do escopo.

Referencias oficiais consultadas em 2026-09-02:

- [permissoes e controle de sincronizacao](https://developer.android.com/health-and-fitness/health-connect/ui/permissions);
- [restricoes de leitura e historico](https://developer.android.com/health-and-fitness/health-connect/read-data);
- [criptografia Android](https://developer.android.com/privacy-and-security/cryptography).

## Alternativas rejeitadas

- plaintext confiando apenas no sandbox: nao atende a protecao adicional da spec;
- cifrar o banco inteiro sem separar metadados: dificulta migracao e consultas;
- solicitar todas as permissoes juntas: viola minimizacao e revogacao granular;
- copiar amostras cardiacas completas: excede a finalidade inicial de relatorio.

## Consequencias e rollback

Perda da chave torna o conteudo sensivel ilegivel; a UI deve oferecer exclusao
dos registros afetados sem quebrar a Agenda. Uma cifra nova exige versao de
envelope e migracao. Desativar P2-07 remove acesso aos dados de saude, mas
preserva tarefas, protocolos, capturas e alertas.

## Dados afetados

Consentimentos, resumos Health Connect, ingestao manual, sintomas, notas de
rotina e manifestos de relatorio. O inventario normativo esta em
`docs/privacy/HEALTH_DATA_INVENTORY.md`.
