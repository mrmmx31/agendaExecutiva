# Agenda Mobile

Aplicativo Android do Projeto 2 com núcleo offline e `P2-03` concluídos. Existem captura livre, réplica de tarefas, execução de protocolo, fila durável, pareamento por QR/deep link ou colagem, Keystore, HTTPS fixado, snapshot e conflitos. `P2-04` possui contratos, Room v4, WorkManager, notificações visuais opt-in e saída sensorial configurável; saúde e IA ainda não estão ativas.

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
- Dezenove testes instrumentados no AVD API 34: migrações Room, WorkManager, seis fluxos Compose, notificações, Keystore e matriz HTTPS aprovados.
- Renderização clara e escura no telefone virtual: inspecionada sem cortes ou resíduos de tema.
- Fluxo fictício validado: uma captura mais início e quatro passos de protocolo geraram seis operações sequenciais na fila.
- Parser do convite de pareamento aprovado em cinco testes de validade, expiração, campos fechados e bloqueio de downgrade HTTP.
- Schemas e fixtures de pareamento/sync carregados da mesma pasta e aprovados nos testes Kotlin e Java.
- Credencial de pareamento recifrada com AES-GCM e chaves RSA/AES não exportáveis do Android Keystore; nenhum segredo em texto aberto nas preferências.
- Room v3 com estados completos da fila, cursor confirmado, conflitos revisáveis e migração `2 -> 3` validada.
- Transporte HTTPS local com certificado fixado, lote limitado, snapshot paginado e ação de sync visível quando pareado.
- Pareamento Android cancelável por `agenda://pair` ou colagem, com reconexão e criptografia fora da thread principal.
- Gate real desktop + AVD aprovado com captura móvel entregue em banco temporário (`PAIRING_GATE_SYNCED`).
- Contratos de alertas, perfil sensorial e ações validados em Kotlin/Java; 26 testes locais Android passam sem produzir estímulo.
- Room v4 persiste definições, materializações, entregas, ações e perfil; 31 testes locais e 13 instrumentados passam, incluindo migrações desde v1.
- WorkManager 2.9.1 mantém trabalho único por alerta, reconcilia no startup e cancela em Room/sistema; 36 testes locais e 15 instrumentados passam sem entrega sensorial.
- Notificação visual exige switch e permissão contextual; canal privado/silencioso e ações offline idempotentes foram aprovados com 42 testes locais e 19 instrumentados.
- Perfis `Visual`, `Discreto` e `Fone`, pausa, silêncio, cooldown, áudio por faixa, vibração e teste cancelável foram aprovados com 44 testes locais e 23 instrumentados; nenhuma suíte toca áudio automaticamente.
- Primeiro boot dos AVDs de telefone e Wear OS: aprovado.
- Pareamento telefone-relógio: aprovado; o assistente confirmou `Successful pairing` entre os dois AVDs.
- O Google Pixel Watch do AVD precisou das permissões de notificações e dispositivos próximos; isso não altera as permissões do aplicativo Agenda.

## Limites

- Package: `com.pessoal.agenda.mobile`.
- Banco: `agenda-mobile.db`, separado do SQLite desktop.
- Banco atual: Room v4, com schema exportado em `app/schemas/` e migrações explícitas `1 -> 2 -> 3 -> 4`.
- Contrato atual: v1, catalogado em `contracts/README.md`.
- Backup e transferência de dados Android estão desativados.
- Nenhuma permissão é solicitada durante instalação ou startup.
- WorkManager declara permissões normais de boot, wake lock, rede e serviço interno; `POST_NOTIFICATIONS` só é solicitado após o switch de alertas visuais.
- `INTERNET` serve exclusivamente ao HTTPS local fixado; nenhuma API externa ou telemetria foi adicionada.
- Os dados demonstrativos são determinísticos e não representam dados pessoais.
- Não usar telefone físico antes do gate previsto em `PROJECT2_SPEC.md`.
