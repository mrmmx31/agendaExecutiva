# Últimas Mudanças

> Gerado automaticamente pelo git hook `post-commit`.
> Não editar manualmente — será sobrescrito no próximo commit.

## Commit mais recente

| Campo | Valor |
|---|---|
| Mensagem | feat(desktop): proteger chave no Google Drive |
| Data | 2026-09-03 19:05:12 -0400 |
| Autor | mrmmx31 |

## Arquivos Alterados

```
MAINTENANCE_MAP.md
PROJECT2_SPEC.md
android/P2_10_MATRIX.md
docs/integrations/GOOGLE_DRIVE_SETUP.md
docs/release/ACCEPTANCE.md
docs/release/PHYSICAL_TEST_RESULTS.md
docs/release/RELEASE_0.1.md
docs/release/SIGNING.md
src/main/java/com/pessoal/agenda/service/GoogleAuthService.java
src/main/java/com/pessoal/agenda/service/GoogleDriveAppDataService.java
src/main/java/com/pessoal/agenda/service/SigningKeyBackupCrypto.java
src/main/java/com/pessoal/agenda/service/SigningKeyDriveBackupService.java
src/main/java/com/pessoal/agenda/ui/controller/ConfigController.java
src/main/java/com/pessoal/agenda/ui/view/GoogleAccountConnectionFlow.java
src/test/java/com/pessoal/agenda/service/GoogleDriveAppDataServiceTest.java
src/test/java/com/pessoal/agenda/service/GoogleTasksTransportTest.java
src/test/java/com/pessoal/agenda/service/SigningKeyBackupCryptoTest.java
```

## Diff Resumido

```diff
 MAINTENANCE_MAP.md                                 |   2 +-
 PROJECT2_SPEC.md                                   |  12 ++
 android/P2_10_MATRIX.md                            |  11 +-
 docs/integrations/GOOGLE_DRIVE_SETUP.md            |  27 ++-
 docs/release/ACCEPTANCE.md                         |   4 +-
 docs/release/PHYSICAL_TEST_RESULTS.md              |   3 +-
 docs/release/RELEASE_0.1.md                        |   7 +-
 docs/release/SIGNING.md                            |   9 +-
 .../pessoal/agenda/service/GoogleAuthService.java  |  79 +++++--
 .../agenda/service/GoogleDriveAppDataService.java  | 173 +++++++++++++++
 .../agenda/service/SigningKeyBackupCrypto.java     | 115 ++++++++++
 .../service/SigningKeyDriveBackupService.java      | 106 +++++++++
 .../agenda/ui/controller/ConfigController.java     | 236 ++++++++++++++++++++-
 .../ui/view/GoogleAccountConnectionFlow.java       |  26 ++-
 .../service/GoogleDriveAppDataServiceTest.java     |  73 +++++++
 .../agenda/service/GoogleTasksTransportTest.java   |  16 ++
 .../agenda/service/SigningKeyBackupCryptoTest.java |  47 ++++
 17 files changed, 910 insertions(+), 36 deletions(-)
```

---

Para ver o histórico completo de mudanças, consulte [CHANGELOG.md](CHANGELOG.md).
