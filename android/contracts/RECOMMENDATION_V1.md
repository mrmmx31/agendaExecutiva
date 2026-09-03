# Recomendação local v1

## Finalidade

Registrar eventos categóricos mínimos para comparar regras determinísticas e,
somente depois do volume mínimo, permitir avaliação offline de um ranking local.
Uma recomendação ordena opções; nunca executa ação.

## Razões explicáveis

| Código | Significado exibível |
|---|---|
| `CAUTIOUS_DEFAULT` | padrão cauteloso por falta de histórico suficiente |
| `MANUAL_PREFERENCE` | preferência escolhida explicitamente pelo usuário |
| `ENOUGH_LOCAL_HISTORY` | histórico local atingiu o mínimo do contexto |
| `QUIET_HOURS_GUARD` | horário silencioso limitou os canais |
| `DEVICE_AVAILABLE` | dispositivo necessário está disponível |
| `ACTIVE_PROTOCOL` | protocolo explicitamente ativo favorece seu atalho |
| `DOMAIN_LIMIT_APPLIED` | regra de domínio removeu uma saída inválida |

## Dados proibidos

- texto, título, nota, conteúdo de tela ou nome de tarefa/protocolo;
- UUID de tarefa, alerta, execução, pessoa ou dispositivo pareado;
- localização, aplicativo em primeiro plano ou histórico de navegação;
- frequência cardíaca, sono, atividade, medicação, substância ou sintoma;
- inferência de distração por simultaneidade, troca de janela ou outro aparelho;
- URL, token, credencial, identificador publicitário ou analytics externo.

`capacity_context` só muda por escolha explícita. Ausência de evento não significa
falha, distração ou recusa. Eventos ficam locais em P2-08 e não entram no sync.
