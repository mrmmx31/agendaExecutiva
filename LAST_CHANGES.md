# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | Organiza configuração da integração Google Tasks |
| Data | 2026-08-30 06:03:46 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
SPEC.md
src/main/java/com/pessoal/agenda/repository/GoogleTasksMappingRepository.java
src/main/java/com/pessoal/agenda/ui/controller/ConfigController.java
src/main/java/com/pessoal/agenda/ui/view/GoogleAccountConnectionFlow.java
src/main/java/com/pessoal/agenda/ui/view/GoogleOperationGuard.java
src/main/java/com/pessoal/agenda/ui/view/GoogleTasksSyncWindow.java
src/main/resources/com/pessoal/agenda/app.css
src/test/java/com/pessoal/agenda/repository/GoogleTasksMappingRepositoryTest.java
src/test/java/com/pessoal/agenda/ui/controller/ConfigGoogleTasksSettingsFxTest.java
```

## Diff Resumido

```diff
 SPEC.md                                            |   8 +
 .../repository/GoogleTasksMappingRepository.java   |  16 ++
 .../agenda/ui/controller/ConfigController.java     | 214 ++++++++++++++++++++-
 .../ui/view/GoogleAccountConnectionFlow.java       |  94 +++++++++
 .../agenda/ui/view/GoogleOperationGuard.java       |   6 +
 .../agenda/ui/view/GoogleTasksSyncWindow.java      |  55 ++----
 src/main/resources/com/pessoal/agenda/app.css      |   1 +
 .../GoogleTasksMappingRepositoryTest.java          |  31 +++
 .../ConfigGoogleTasksSettingsFxTest.java           | 149 ++++++++++++++
 9 files changed, 525 insertions(+), 49 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
