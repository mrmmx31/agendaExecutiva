# Model card - regras-v1

| Campo | Valor |
|---|---|
| model_id | `deterministic-rules` |
| versão/hash | `rules-v1`; hash será fixado junto da implementação |
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
