# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit: `5acbc9f5`

| Campo | Valor |
|---|---|
| Hash completo | `5acbc9f5d678b7954b15743787d224c229d6f5cf` |
| Mensagem | feat(ui): badge popover + atalho alternativo de lembrete - AgendaApp: click no badge abre popover com breakdown A/H/P e acao 'Lembrar agora' - AgendaApp: adiciona atalho Ctrl/Cmd+Shift+S alem de Ctrl/Cmd+S - AgendaTabController: fluxo preview-first com ESC para cancelar e edicao explicita - docs: CHANGELOG atualizado com popover e atalho alternativo |
| Data | 2026-06-23 15:33:32 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
DEVELOPMENT.md
LAST_CHANGES.md
src/main/java/com/pessoal/agenda/AgendaApp.java
src/main/java/com/pessoal/agenda/service/PendencyNotificationService.java
src/main/java/com/pessoal/agenda/ui/controller/AgendaTabController.java
src/main/java/com/pessoal/agenda/ui/controller/DashboardController.java
src/main/resources/com/pessoal/agenda/app.css
src/main/resources/sounds/reminder.wav
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |  14 ++-
 DEVELOPMENT.md                                     |  27 +++-
 LAST_CHANGES.md                                    |  30 ++---
 src/main/java/com/pessoal/agenda/AgendaApp.java    | 140 ++++++++++++++++++++-
 .../service/PendencyNotificationService.java       |  26 +++-
 .../agenda/ui/controller/AgendaTabController.java  | 123 ++++++++++++++++--
 .../agenda/ui/controller/DashboardController.java  |  12 +-
 src/main/resources/com/pessoal/agenda/app.css      |  18 +++
 src/main/resources/sounds/reminder.wav             | Bin 0 -> 26504 bytes
 9 files changed, 354 insertions(+), 36 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
