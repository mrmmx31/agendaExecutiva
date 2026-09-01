# Agenda Mobile

Aplicativo Android do Projeto 2 com núcleo offline concluído em `P2-02` e transporte local implementado em `P2-03`. Existem captura livre, réplica de tarefas, execução de protocolo, fila durável, Keystore, HTTPS fixado, snapshot e conflitos. O pareamento Android visual e a matriz ponta a ponta permanecem para o último item de `P2-03`; alertas, saúde e IA ainda não existem.

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
- O pareamento foi feito pelo comando `Pair Wearable` do Device Manager do Android Studio.
- Nunca omitir `ANDROID_SERIAL` enquanto um telefone físico estiver visível no `adb`.

## Evidência atual

- `test`, `lint` e `assembleDebug`: aprovados.
- Testes Room e do repositório offline com Robolectric: aprovados.
- Oito testes instrumentados no AVD API 34: duas migrações Room, três fluxos Compose e três testes do Keystore aprovados.
- Renderização clara e escura no telefone virtual: inspecionada sem cortes ou resíduos de tema.
- Fluxo fictício validado: uma captura mais início e quatro passos de protocolo geraram seis operações sequenciais na fila.
- Parser do convite de pareamento aprovado em cinco testes de validade, expiração, campos fechados e bloqueio de downgrade HTTP.
- Schemas e fixtures de pareamento/sync carregados da mesma pasta e aprovados nos testes Kotlin e Java.
- Credencial de pareamento recifrada com AES-GCM e chaves RSA/AES não exportáveis do Android Keystore; nenhum segredo em texto aberto nas preferências.
- Room v3 com estados completos da fila, cursor confirmado, conflitos revisáveis e migração `2 -> 3` validada.
- Transporte HTTPS local com certificado fixado, lote limitado, snapshot paginado e ação de sync visível quando pareado.
- Primeiro boot dos AVDs de telefone e Wear OS: aprovado.
- Pareamento telefone-relógio: aprovado; o assistente confirmou `Successful pairing` entre os dois AVDs.
- O Google Pixel Watch do AVD precisou das permissões de notificações e dispositivos próximos; isso não altera as permissões do aplicativo Agenda.

## Limites

- Package: `com.pessoal.agenda.mobile`.
- Banco: `agenda-mobile.db`, separado do SQLite desktop.
- Banco atual: Room v3, com schema exportado em `app/schemas/` e migrações explícitas `1 -> 2 -> 3`.
- Contrato atual: v1, catalogado em `contracts/README.md`.
- Backup e transferência de dados Android estão desativados.
- Nenhuma permissão sensível é solicitada.
- `INTERNET` serve exclusivamente ao HTTPS local fixado; nenhuma API externa ou telemetria foi adicionada.
- Os dados demonstrativos são determinísticos e não representam dados pessoais.
- Não usar telefone físico antes do gate previsto em `PROJECT2_SPEC.md`.
