# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit: `096d3270`

| Campo | Valor |
|---|---|
| Hash completo | `096d327097154570d42c3998bc273659b2bc50c1` |
| Mensagem | feat(dashboard): suporte TDAH — tarefas de hoje, protocolos vencendo e notificações periódicas - DashboardController: cards 'Tarefas de HOJE' e 'Protocolos Vencendo' - PendencyNotificationService: alertas via beep a cada 5 minutos - SharedContext: novos ObservableLists todayTaskItems e expiringProtocolItems - AgendaTabController: refresh imediato da lista ao salvar tarefa - theme-dark.css: cobertura completa do popup DatePicker em modo escuro - app.css: seleção em ListView/TableView visível em linhas pares e ímpares - docs: CHANGELOG.md e DEVELOPMENT.md criados com documentação técnica |
| Data | 2026-06-23 12:07:55 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
DEVELOPMENT.md
LAST_CHANGES.md
src/main/java/com/pessoal/agenda/app/SharedContext.java
src/main/java/com/pessoal/agenda/service/PendencyNotificationService.java
src/main/java/com/pessoal/agenda/ui/controller/AgendaTabController.java
src/main/java/com/pessoal/agenda/ui/controller/DashboardController.java
src/main/resources/com/pessoal/agenda/app.css
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |  47 +++++
 DEVELOPMENT.md                                     | 227 +++++++++++++++++++++
 LAST_CHANGES.md                                    |  44 ++++
 .../java/com/pessoal/agenda/app/SharedContext.java |   6 +
 .../service/PendencyNotificationService.java       |  92 +++++++++
 .../agenda/ui/controller/AgendaTabController.java  |   3 +
 .../agenda/ui/controller/DashboardController.java  |  99 +++++++++
 src/main/resources/com/pessoal/agenda/app.css      |  74 ++++++-
 8 files changed, 590 insertions(+), 2 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
