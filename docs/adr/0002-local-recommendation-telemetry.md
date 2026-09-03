# ADR 0002 - Telemetria local minimizada

## Decisão

Eventos de recomendação são estruturas fechadas, categóricas, locais e sem
referência a entidades operacionais. P2-08 não usa runtime de ML: estabelece
baseline e regras explicáveis antes de qualquer benchmark. Saúde permanece em
fronteira independente e não pode virar feature do ranking.

Personalização, retenção e limpeza pertencem ao domínio, não ao runtime futuro.
Uma sugestão nunca chama a operação sugerida; a ação continua no fluxo explícito
existente. Eventos não entram no sync e não usam SDK de analytics.

## Consequências

Não será possível reconstruir texto ou tarefa a partir da telemetria. Análises
mais detalhadas exigirão nova versão de contrato e revisão de privacidade. O
fallback determinístico continua funcional com zero eventos e é o rollback.
