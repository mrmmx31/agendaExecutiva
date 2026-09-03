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
| `PERSONAL_MODEL` | modelo pessoal ativo reordenou opções já permitidas pelas regras |

## Dados proibidos

- texto, título, nota, conteúdo de tela ou nome de tarefa/protocolo;
- UUID de tarefa, alerta, execução, pessoa ou dispositivo pareado;
- localização, aplicativo em primeiro plano ou histórico de navegação;
- frequência cardíaca, sono, atividade, medicação, substância ou sintoma;
- inferência de distração por simultaneidade, troca de janela ou outro aparelho;
- URL, token, credencial, identificador publicitário ou analytics externo.

`capacity_context` só muda por escolha explícita. Ausência de evento não significa
falha, distração ou recusa. Eventos ficam locais em P2-08 e não entram no sync.

## Instrumentação v1

- eventos são tentados somente após a mutação operacional durável e falha de
  telemetria não desfaz alerta, ação ou protocolo;
- repetição idempotente de uma entrega/ação/passo não cria novo evento;
- `deadline_bucket` usa o fim da janela válida do alerta, nunca texto da tarefa;
- entrega multicanal registra um canal primário na ordem relógio, áudio,
  vibração do telefone e visual;
- `active_context` é `PROTOCOL` somente quando existe execução explicitamente
  ativa; não há inferência de foco por tela, janela ou uso de dispositivo;
- ação recebida pelo Data Layer usa `WATCH`; as demais operações locais usam
  `PHONE`;
- alertas suprimidos ou com falha técnica não contam como apresentados.

## Indicadores locais

Os indicadores são descritivos e não diagnosticam distração, adesão ou estado
clínico. `Sequências estimadas` conta apenas pares consecutivos de adiamentos no
mesmo contexto categórico explícito, separados por no máximo uma hora. Como IDs
operacionais são proibidos, o valor não afirma que os dois eventos pertencem ao
mesmo alerta e é exibido como estimativa.
