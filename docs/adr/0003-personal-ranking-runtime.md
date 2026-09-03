# ADR 0003 - Runtime do ranking pessoal

## Contexto

P2-08 estabilizou eventos categóricos, regras explicáveis e o fallback
`rules-v1`. P2-09 precisa avaliar um modelo aprendido sem transformar uma
sugestão em ação, sem criar coleta nova e sem acoplar o produto a um runtime
pesado antes de medir benefício.

O primeiro problema é pequeno e tabular: ordenar os presets de adiamento
`SNOOZE_5`, `SNOOZE_10`, `SNOOZE_15`, `SNOOZE_30` e `SNOOZE_60` a partir de
contextos explícitos e categóricos.

## Decisão

O primeiro candidato será um classificador linear multiclasse implementado em
Kotlin, atrás de uma interface de runtime. Pesos e metadados ficarão em um
artefato local versionado, com SHA-256, e cada pontuação poderá ser decomposta
por feature. O modelo começará exclusivamente em `SHADOW` e o `rules-v1`
continuará determinando a interface até os gates de promoção.

O treino e a avaliação usarão somente os campos permitidos pelo contrato
`personal-ranking-dataset` v1. Saúde, texto, IDs operacionais, localização e
inferência de atividade de tela não entram nas features. O modelo não recebe
acesso a repositórios operacionais e sua saída ainda passa pelos limites do
domínio.

LiteRT via Google Play services e ONNX Runtime permanecem candidatos de adapters
futuros. LiteRT evita embutir o runtime no APK e executa a entrada no aparelho;
ONNX oferece maior portabilidade e opção de runtime customizado. A adoção de
qualquer um exige benchmark reproduzível de tamanho, latência, memória, bateria,
disponibilidade offline e manutenção. Nenhuma dependência é adicionada nesta
decisão.

## Consequências

- o primeiro modelo é pequeno, auditável e reversível sem download ou serviço;
- a aplicação continua funcional quando o artefato falta, é inválido ou regride;
- trocar o runtime não altera o contrato de features nem os limites de domínio;
- modelos mais complexos só entram após demonstrarem ganho material no mesmo
  conjunto de avaliação;
- o rollback imediato é remover o artefato ativo e voltar ao `rules-v1`.

