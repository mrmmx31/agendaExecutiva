# Inventário de dados do modelo pessoal

P2-09 não autoriza coleta adicional. O dataset de treino é uma projeção local
dos eventos já permitidos em P2-08 e existe somente quando a personalização está
ligada. Desligar a personalização impede nova coleta e uso do modelo; apagar o
histórico invalida os artefatos derivados e retorna ao `rules-v1`.

| Grupo | Features permitidas | Origem |
|---|---|---|
| tempo discreto | período do dia e grupo semana/fim de semana | hora e dia já registrados |
| contexto explícito | contexto ativo e capacidade escolhida | seleção explícita existente |
| alerta | tipo e faixa de prazo | códigos categóricos existentes |
| superfície | telefone ou relógio | origem categórica existente |
| rótulo | preset de adiamento escolhido | ação explícita existente |

São proibidos: frequência cardíaca, sono, passos, medicação, substância, sintoma,
qualquer outro dado de saúde, texto livre, nome de tarefa/protocolo, UUID
operacional, localização, aplicativo ou janela em uso, navegação, contato,
credencial, token e identificador publicitário.

O artefato guarda somente pesos numéricos, vocabulário fechado, versão,
contagens agregadas e métricas. Eventos e artefatos não entram no sync nem em
analytics. Dados sintéticos versionados no repositório não representam pessoa,
conta ou dispositivo real.

