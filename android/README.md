# Agenda Mobile

Aplicativos Android e Wear OS do Projeto 2 com `P2-01` a `P2-09` concluídas e `P2-10` em 3 de 10 gates. Existem captura livre, réplica de tarefas, execução de protocolo, fila durável, pareamento por QR/deep link ou colagem, Keystore, HTTPS fixado, snapshot, conflitos, alertas sensoriais configuráveis e ações Wear offline. Saúde possui contratos, persistência cifrada, coleta Health Connect opt-in e relatório revisável; o ranking aprendido tem treino, shadow, ativação confirmada, inspeção e rollback locais.

## Requisitos

- Java 17 ou superior.
- Android SDK em `ANDROID_SDK_ROOT`.
- Platform 34 para o telefone e 35 para compilar Compose Wear 1.5.6; os dois AVDs continuam API 34.

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
- Vinte e oito testes instrumentados no AVD API 34: migrações Room, WorkManager, Compose, notificações, saída sensorial, Keystore e matriz HTTPS aprovados.
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
- Matriz final de `P2-04` aprovada com 28 testes instrumentados e gates externos de permissão/reinício; detalhes e limites em [`P2_04_MATRIX.md`](P2_04_MATRIX.md).
- Contrato Wear v1 aprovado em cinco testes: payload fechado, duas ações, até três adiamentos, caminhos canônicos e recusa de campo sensível inesperado.
- Módulo Wear com o mesmo package/assinatura do telefone, Compose Wear Material 3 e Data Layer oficial; APK aberto no AVD Wear sem prompt, notificação ou estímulo.
- Primeiro boot dos AVDs de telefone e Wear OS: aprovado.
- Pareamento telefone-relógio: aprovado; o assistente confirmou `Successful pairing` entre os dois AVDs.
- O Google Pixel Watch do AVD precisou das permissões de notificações e dispositivos próximos; isso não altera as permissões do aplicativo Agenda.
- Data Layer pareado aprovado para estado, `Concluir`, `Adiar` e reconciliação depois de o app do telefone ser reaberto; matriz em [`P2_05_MATRIX.md`](P2_05_MATRIX.md).
- Room Android v5 mantém revisão Wear; Room próprio do relógio persiste alerta e outbox antes do feedback.
- P2-06 entrega `Vou sair`, escolha determinística, passo atual e confirmação offline no Wear e sugestões estruturais que exigem revisão; o percurso pareado está aprovado em [`P2_06_MATRIX.md`](P2_06_MATRIX.md).
- P2-07 possui contratos, inventário de privacidade, Room v8 e payloads AES-GCM protegidos pelo Keystore; permissões Health Connect são granulares e pedidas somente no fluxo explícito de importação.
- `Saúde e privacidade` oferece oito opt-ins e formulários locais para medicação, substância, sintoma/evento e nota de rotina, com correção e exclusão explícita.
- P2-08 possui contratos minimizados e Room v9 para eventos, decisões e configurações locais; personalização começa desligada e o histórico não entra no sync.
- `rules-v1` oferece baseline e ranking local após 12 amostras do mesmo contexto, com razões e limites de domínio antes da saída.
- Alertas e protocolos emitem eventos categóricos locais após sucesso; repetição idempotente, opt-out e falha de telemetria não alteram o fluxo operacional.
- `Recomendações locais` controla opt-in, contexto explícito, preferências e retenção; mostra razões, métricas e histórico categórico corrigível e apagável, com retorno imediato ao baseline.
- O tema controla explicitamente os ícones das barras do sistema; a tela de recomendações foi inspecionada em claro e escuro no Pixel virtual.
- A matriz final de recomendação local está em [`P2_08_MATRIX.md`](P2_08_MATRIX.md); `rules-v1` processou 10 mil observações fictícias em 0,338 s no teste do gate.
- P2-09 usa inicialmente um classificador linear auditável em Kotlin; contrato, inventário, model card e dataset sintético fecham as features e mantêm saúde fora do modelo.
- O avaliador P2-09 usa partição temporal 80/20, compara top-1 com `rules-v1` e não permite promoção com menos de 30 exemplos reservados ou ganho inferior a 5 pontos percentuais.
- O wrapper de shadow mode retorna a decisão `rules-v1` intacta, limita o treino às 2.000 amostras recentes e mantém somente contagem/concordância em memória nesta etapa.
- Room v10 persiste artefatos canônicos com SHA-256 e métricas shadow agregadas; ativação é transacional e hash/contrato inválido volta ao `rules-v1`.
- A tela separa coleta, treino, ativação e rollback; mostra qualidade e custo local. Modelo ativo só reordena presets autorizados e essa ordem chega ao Wear.
- A matriz final [`P2_09_MATRIX.md`](P2_09_MATRIX.md) fecha o modelo local no escopo de emulador; o benchmark de candidatos e os custos medidos estão em [`P2_09_RUNTIME_BENCHMARK.md`](P2_09_RUNTIME_BENCHMARK.md).
- P2-10 cataloga dez gates em [`P2_10_MATRIX.md`](P2_10_MATRIX.md); escopo regulatório, aviso/declarações de privacidade e auditoria estática dos APKs release estão aprovados.

## Limites

- Package: `com.pessoal.agenda.mobile`.
- Banco: `agenda-mobile.db`, separado do SQLite desktop.
- Banco atual: Room v10 no telefone e v2 no relógio, com schemas exportados e migrações explícitas.
- Contrato atual: v1, catalogado em `contracts/README.md`.
- O Data Layer transporta somente estados e ações mínimas v1 de alertas e do passo atual do protocolo; dados sensíveis não fazem parte desses payloads.
- Backup e transferência de dados Android estão desativados.
- Nenhuma permissão é solicitada durante instalação ou startup.
- WorkManager declara permissões normais de boot, wake lock, rede e serviço interno; `POST_NOTIFICATIONS` só é solicitado ao ativar um perfil que inclua o canal visual.
- `INTERNET` serve exclusivamente ao HTTPS local fixado; nenhuma API externa ou telemetria foi adicionada.
- Os dados demonstrativos são determinísticos e não representam dados pessoais.
- Não usar telefone físico antes do gate previsto em `PROJECT2_SPEC.md`.
