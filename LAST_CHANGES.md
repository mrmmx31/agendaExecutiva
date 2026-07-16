# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit: `59a440a8`

| Campo | Valor |
|---|---|
| Hash completo | `59a440a8a9031889ba78f555b32fd13d7b54a439` |
| Mensagem | Acréscimos de várias features e correções |
| Data | 2026-07-16 00:10:54 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
CHANGELOG.md
LAST_CHANGES.md
src/main/java/com/pessoal/agenda/AgendaApp.java
src/main/java/com/pessoal/agenda/DatabaseService.java
src/main/java/com/pessoal/agenda/app/AppContext.java
src/main/java/com/pessoal/agenda/infra/Database.java
src/main/java/com/pessoal/agenda/model/AttendanceDay.java
src/main/java/com/pessoal/agenda/model/ProjectIdea.java
src/main/java/com/pessoal/agenda/model/Protocol.java
src/main/java/com/pessoal/agenda/model/StudyCompensation.java
src/main/java/com/pessoal/agenda/model/Task.java
src/main/java/com/pessoal/agenda/repository/ProjectIdeaRepository.java
src/main/java/com/pessoal/agenda/repository/ProtocolRepository.java
src/main/java/com/pessoal/agenda/repository/StudyCompensationRepository.java
src/main/java/com/pessoal/agenda/repository/StudyPlanRepository.java
src/main/java/com/pessoal/agenda/repository/StudyStatusLogRepository.java
src/main/java/com/pessoal/agenda/repository/TaskRepository.java
src/main/java/com/pessoal/agenda/repository/TaskSessionRepository.java
src/main/java/com/pessoal/agenda/service/CategoryService.java
src/main/java/com/pessoal/agenda/service/StudyAttendanceService.java
src/main/java/com/pessoal/agenda/service/TaskService.java
src/main/java/com/pessoal/agenda/ui/controller/AgendaTabController.java
src/main/java/com/pessoal/agenda/ui/controller/ChecklistController.java
src/main/java/com/pessoal/agenda/ui/controller/DashboardController.java
src/main/java/com/pessoal/agenda/ui/controller/IdeasController.java
src/main/java/com/pessoal/agenda/ui/controller/StudyController.java
src/main/java/com/pessoal/agenda/ui/view/IdeaInboxReviewWindow.java
src/main/java/com/pessoal/agenda/ui/view/ProjectIdeaDetailWindow.java
src/main/java/com/pessoal/agenda/ui/view/StudyMonitorWindow.java
src/main/java/com/pessoal/agenda/ui/view/TaskTimerWindow.java
src/main/resources/com/pessoal/agenda/app.css
```

## Diff Resumido

```diff
 CHANGELOG.md                                       |   34 +
 LAST_CHANGES.md                                    |   78 +-
 src/main/java/com/pessoal/agenda/AgendaApp.java    |   13 +-
 .../java/com/pessoal/agenda/DatabaseService.java   |   17 +-
 .../java/com/pessoal/agenda/app/AppContext.java    |    9 +-
 .../java/com/pessoal/agenda/infra/Database.java    |   16 +
 .../com/pessoal/agenda/model/AttendanceDay.java    |    1 +
 .../java/com/pessoal/agenda/model/ProjectIdea.java |   11 +-
 .../java/com/pessoal/agenda/model/Protocol.java    |    5 +
 .../pessoal/agenda/model/StudyCompensation.java    |    1 +
 src/main/java/com/pessoal/agenda/model/Task.java   |    9 +-
 .../agenda/repository/ProjectIdeaRepository.java   |   37 +-
 .../agenda/repository/ProtocolRepository.java      |  209 +++-
 .../repository/StudyCompensationRepository.java    |   19 +
 .../agenda/repository/StudyPlanRepository.java     |   24 +
 .../repository/StudyStatusLogRepository.java       |   95 ++
 .../pessoal/agenda/repository/TaskRepository.java  |   27 +-
 .../agenda/repository/TaskSessionRepository.java   |    5 +
 .../pessoal/agenda/service/CategoryService.java    |   19 +-
 .../agenda/service/StudyAttendanceService.java     |   94 +-
 .../com/pessoal/agenda/service/TaskService.java    |   24 +-
 .../agenda/ui/controller/AgendaTabController.java  |  389 ++++++-
 .../agenda/ui/controller/ChecklistController.java  |  234 ++++-
 .../agenda/ui/controller/DashboardController.java  | 1097 +++++++++++++++++++-
 .../agenda/ui/controller/IdeasController.java      |   17 +-
 .../agenda/ui/controller/StudyController.java      |   58 +-
 .../agenda/ui/view/IdeaInboxReviewWindow.java      |  670 ++++++++++++
 .../agenda/ui/view/ProjectIdeaDetailWindow.java    |   37 +-
 .../pessoal/agenda/ui/view/StudyMonitorWindow.java |   77 +-
 .../pessoal/agenda/ui/view/TaskTimerWindow.java    |   80 +-
 src/main/resources/com/pessoal/agenda/app.css      |   66 +-
 31 files changed, 3317 insertions(+), 155 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
