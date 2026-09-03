# Model card - regras-v1

| Campo | Valor |
|---|---|
| model_id | `deterministic-rules` |
| versão/hash | `rules-v1`; SHA-256 do fonte registrado abaixo |
| runtime | Kotlin puro, sem runtime de ML |
| finalidade | ordenar até três presets de adiamento, canal ou protocolo |
| usos proibidos | ação automática, saúde, clínica, dose, substância, diagnóstico |
| features | hora/dia, prazo, contexto explícito, dispositivo, canal e respostas categóricas |
| origem/janela | eventos locais minimizados, no máximo 90 dias |
| mínimo | 12 eventos do mesmo propósito/contexto; abaixo disso usar fallback |
| baseline | ordem cautelosa fixa da instalação |
| ativação | não ativa personalização; somente baseline em P2-08 |
| rollback | desligar personalização e retornar imediatamente à ordem fixa |

Qualidade será comparada por correção manual, latência, adiamentos repetidos e
alertas perdidos. Bateria, memória e latência serão medidas no gate de P2-09.

## Algoritmo reproduzível

- contexto mínimo: finalidade, período do dia, dia útil/fim de semana, contexto
  ativo, capacidade explícita e tipo categórico do alerta;
- período do dia: 05-11, 12-17, 18-22 e 23-04;
- eventos elegíveis: adiamento, aceitação ou correção explícita com opção do
  mesmo propósito e contexto;
- mínimo: 12 eventos elegíveis; abaixo disso a saída permanece no baseline;
- ranking local: frequência decrescente, seguida da ordem baseline e do código
  da opção para desempate estável;
- limites anteriores ao ranking: disponibilidade do canal, horário silencioso,
  finalidade e máximo de três opções;
- preferência manual válida ocupa a primeira posição mesmo sem histórico;
- ausência de protocolo ou de canal válido produz ausência de recomendação.

O artefato de referência é
`android/app/src/main/java/com/pessoal/agenda/mobile/recommendation/RecommendationEngine.kt`.
SHA-256: `cbc3b7ec5dbc4443b4d1201ca19baad2af975fc270e81a44abe45422267a75b8`.
Seu hash deve ser atualizado no mesmo commit de qualquer alteração de regra.
