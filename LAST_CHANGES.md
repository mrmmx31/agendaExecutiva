# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | Permite cancelar autorização do Google |
| Data | 2026-08-30 07:02:25 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
SPEC.md
src/main/java/com/pessoal/agenda/service/GoogleAuthService.java
src/main/java/com/pessoal/agenda/ui/controller/ConfigController.java
src/main/java/com/pessoal/agenda/ui/view/GoogleAccountConnectionFlow.java
src/main/java/com/pessoal/agenda/ui/view/GoogleTasksSyncWindow.java
src/test/java/com/pessoal/agenda/service/GoogleTasksTransportTest.java
src/test/java/com/pessoal/agenda/ui/controller/ConfigGoogleTasksSettingsFxTest.java
```

## Diff Resumido

```diff
 SPEC.md                                            |  8 +++
 .../pessoal/agenda/service/GoogleAuthService.java  | 82 +++++++++++++++++++++-
 .../agenda/ui/controller/ConfigController.java     | 35 +++++++--
 .../ui/view/GoogleAccountConnectionFlow.java       | 39 +++++++---
 .../agenda/ui/view/GoogleTasksSyncWindow.java      | 51 +++++++++++---
 .../agenda/service/GoogleTasksTransportTest.java   | 23 ++++++
 .../ConfigGoogleTasksSettingsFxTest.java           | 39 +++++++++-
 7 files changed, 251 insertions(+), 26 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
