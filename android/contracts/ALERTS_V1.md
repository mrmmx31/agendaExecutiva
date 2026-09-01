# Contratos de alertas v1

## Estado inicial

Uma instalação começa com o controle geral desligado. O perfil inicial mantém
apenas o canal visual selecionado, mas nenhum estímulo é emitido antes do opt-in
explícito. Áudio e vibração exigem seleção própria.

## Limites

- texto e motivo: até 160 caracteres cada;
- janela válida: no máximo sete dias;
- entregas por alerta: de uma a cinco;
- intervalo entre repetições: de cinco minutos a 24 horas;
- cooldown global: de um a 60 minutos, com padrão de cinco;
- adiamento: mínimo de cinco minutos, máximo de 24 horas e até cinco vezes;
- padrão cauteloso: presets de 10, 30 e 60 minutos, máximo de quatro horas e três adiamentos.

Horário silencioso, pausa, cooldown, sobreposição, limite de entregas e
interseção dos canais autorizados são barreiras independentes. Supressão é um
estado técnico; não representa falha do usuário.

## Ações

`COMPLETE` não aceita novo horário. `SNOOZE` exige `snooze_until` futuro dentro
da política. Toda ação tem `operation_id` para entrar posteriormente na fila
idempotente. O telefone continuará oferecendo horário manual; o relógio usará
somente presets calculados no telefone.

## Áudio

A política descreve preferência apenas para sons da Agenda. Ela não altera a
rota global nem promete controlar áudio de outros aplicativos. A resolução de
capacidade, teste curto e fallback entram nas entregas posteriores de `P2-04`.
