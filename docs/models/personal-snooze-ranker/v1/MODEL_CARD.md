# Model card - personal-snooze-ranker v1

| Campo | Valor inicial |
|---|---|
| Finalidade | ordenar presets de adiamento |
| Tipo | classificador linear multiclasse auditável |
| Runtime inicial | Kotlin local, sem dependência nativa |
| Entrada | sete features categóricas do contrato v1 |
| Saída | pontuação para cinco presets permitidos |
| Estado inicial | `SHADOW` |
| Fallback | `rules-v1` |

## Treino e avaliação

O treino ocorre no aparelho, com personalização ligada, a partir de escolhas
explícitas. São exigidas pelo menos 60 observações elegíveis para produzir um
artefato e pelo menos 12 no contexto para consultá-lo. A avaliação usa partição
temporal: exemplos mais recentes não participam do treino. O relatório compara
acurácia top-1 com o mesmo conjunto avaliado pelo baseline.

O dataset no repositório é sintético e serve somente para contrato e avaliação
reproduzível. Não expressa qualidade do modelo nem autoriza promoção.

## Promoção e rollback

O modelo só pode sair de `SHADOW` após superar o baseline no conjunto reservado,
respeitar orçamento de latência/memória/bateria, passar verificação de SHA-256 e
receber opt-in explícito. Empate, regressão, artefato incompatível, hash inválido
ou limpeza do histórico mantêm/restauram `rules-v1`.

## Limites e usos proibidos

O modelo não diagnostica TDAH, saúde, distração, adesão ou capacidade. Não decide
medicação, urgência clínica, canal sensorial nem passo de protocolo. Não usa
saúde, texto, identificadores operacionais ou sinais implícitos de tela. Uma
pontuação nunca executa a ação correspondente.
