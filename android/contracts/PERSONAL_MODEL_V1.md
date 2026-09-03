# Modelo pessoal v1

## Escopo

O contrato v1 cobre apenas ranking de presets de adiamento. O dataset contém
features categóricas minimizadas e o preset escolhido; o manifesto identifica o
artefato, runtime, integridade, estado e avaliação. Recomendação não executa
ação e qualquer opção inválida é removida pelas regras de domínio.

## Estados do artefato

| Estado | Efeito |
|---|---|
| `SHADOW` | pontua e mede localmente, mas `rules-v1` controla a interface |
| `ACTIVE` | pode ordenar opções após opt-in e gates aprovados |
| `ROLLED_BACK` | permanece apenas para auditoria; `rules-v1` controla a interface |

Um artefato precisa de SHA-256 válido, versão de features compatível e no mínimo
60 amostras de treino. Ativação também exige 12 amostras no contexto consultado,
avaliação contra o baseline e confirmação explícita. Falha de leitura,
integridade ou compatibilidade sempre retorna ao `rules-v1`.

## Limites

- somente `SNOOZE_PRESET` existe nesta versão;
- treino, avaliação e inferência são locais;
- o dataset não contém instante preciso nem identificador por observação;
- saúde e os demais campos proibidos em `RECOMMENDATION_V1.md` são proibidos;
- o manifesto não contém caminho externo, URL, token ou credencial;
- retenção, inspeção e limpeza continuam sob controle do usuário.

