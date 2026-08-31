# Agenda Mobile

Scaffold Android do Projeto 2. Nesta fase há somente a composição visual inicial e o banco Room de metadados técnicos. Não existem sincronização, captura, alertas, saúde ou IA implementados.

## Requisitos

- Java 17 ou superior.
- Android SDK em `ANDROID_SDK_ROOT`.
- Platform e Build Tools 34.

## Verificar

```bash
cd android
./gradlew test lint assembleDebug
```

Com emulador autorizado:

```bash
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

## Emuladores P2-01

- Telefone: `Agenda_Phone_API_34`, Android 14/API 34, imagem Google Play.
- Relógio: `Agenda_Wear_API_34`, Wear OS 5/API 34.
- O pareamento deve ser feito pelo comando `Pair Wearable` do Device Manager do Android Studio.
- Nunca omitir `ANDROID_SERIAL` enquanto um telefone físico estiver visível no `adb`.

## Evidência atual

- `test`, `lint` e `assembleDebug`: aprovados.
- Teste Room com Robolectric: aprovado.
- Teste instrumentado Compose no AVD API 34: aprovado.
- Renderização clara e escura no telefone virtual: inspecionada sem cortes ou resíduos de tema.
- Primeiro boot dos AVDs de telefone e Wear OS: aprovado.
- Pareamento telefone-relógio: pendente; não considerar `P2-01` concluída antes dessa evidência.

## Limites

- Package: `com.pessoal.agenda.mobile`.
- Banco: `agenda-mobile.db`, separado do SQLite desktop.
- Backup e transferência de dados Android estão desativados.
- Nenhuma permissão sensível é solicitada.
- Não usar telefone físico antes do gate previsto em `PROJECT2_SPEC.md`.
