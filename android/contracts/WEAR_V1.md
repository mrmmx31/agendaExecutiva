# Contrato Wear v1

## Responsabilidades

O telefone materializa no relógio apenas o estado mínimo necessário para exibir
um alerta já autorizado. O relógio devolve comandos idempotentes. O Data Layer
é transporte entre os dois APKs, não banco principal nem substituto do sync da
Agenda.

## Compatibilidade e caminhos

- pacote e assinatura dos APKs Android e Wear devem ser iguais;
- estado durável: `DataItem` em `/agenda/v1/alerts/{alert_id}`;
- ação durável: `DataItem` em `/agenda/v1/actions/{operation_id}`, sempre
  precedido por persistência local no relógio;
- capacidades: `agenda_phone_v1` e `agenda_wear_v1`;
- payload JSON UTF-8 fechado e `contract_version = 1`.

O transporte não inclui credencial de pareamento desktop, token Google, dados de
saúde, medicamentos, substâncias, texto livre adicional ou histórico completo.

## Estado materializado

`wear-alert-state` contém UUID, revisão monotônica, texto e motivo curtos, janela
temporal, criticidade funcional, estado, confirmação opcional da última operação
e até três sugestões de adiamento entre 5 e 240 minutos. As únicas ações, nesta
ordem, são `COMPLETE` e `SNOOZE`.

Estados terminais e revisões antigas não podem reativar o alerta. A cópia local
do relógio será limitada aos alertas ainda válidos e operações não confirmadas.

## Ações

A ação reutiliza a forma fechada de `alert-action`: `operation_id` garante
idempotência; `COMPLETE` exige `snooze_until = null`; `SNOOZE` exige instante
entre 5 e 240 minutos após `occurred_at`. O relógio não oferece teclado nem
seletor temporal complexo. A escolha manual detalhada permanece no telefone.

## Falha e reconexão

Uma ação é persistida no relógio antes de criar seu `DataItem`. O telefone aplica
a operação, publica estado com `acknowledged_operation_id` e só então remove o
item da ação. Ausência do telefone nunca transforma entrega técnica em falha do
usuário e não autoriza repetição sensorial fora da política recebida.
