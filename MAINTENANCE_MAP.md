# Mapa de Manutenção

Este documento é o ponto de entrada para manutenção. Ele reduz a necessidade de reconstruir contexto pelo histórico de conversas.

## 1. Leitura mínima por tipo de trabalho

| Trabalho | Ler nesta ordem |
|---|---|
| Correção desktop existente | `MAINTENANCE_MAP.md` → seção relevante de `DEVELOPMENT.md` → código/testes locais |
| Regra ou UX do produto atual | `SPEC.md` → `ARCHITECTURE.md` → código/testes |
| Projeto Android/Wear | `PROJECT2_SPEC.md` → `android/contracts/README.md` → `android/README.md` |
| IA ou personalização | `PROJECT2_SPEC.md` seções 14, 15, 20 e 21 → model card correspondente |
| Saúde ou relatório médico | `PROJECT2_SPEC.md` seções 2, 12, 13, 15 e 21 |
| Google Tasks | `SPEC.md` seções 9.9 e 26 → classes `Google*` → testes correspondentes |
| Tema/janelas | `DEVELOPMENT.md` → `ThemeManager`, `WindowManager`, CSS e testes JavaFX |
| Ver estado recente | `LAST_CHANGES.md` → `git log` → `git status` |

Não usar `LAST_CHANGES.md` como especificação; ele é gerado pelo hook após commits.

## 2. Fontes de verdade

| Documento | Autoridade | Não deve conter |
|---|---|---|
| `SPEC.md` | produto desktop atual, requisitos e percentuais | detalhes extensos do Projeto 2 |
| `PROJECT2_SPEC.md` | Android, Wear, sync móvel, saúde e IA | código implementado fictício |
| `ARCHITECTURE.md` | limites técnicos do desktop | decisões de produto não aprovadas |
| `DEVELOPMENT.md` | mecânica e convenções do desktop | backlog ou hipóteses |
| `PILOT.md` | evidência e decisões do piloto encerrado | conteúdo pessoal |
| `UI_VALIDATION.md` | matriz manual reproduzível | dados reais |
| `CHANGELOG.md` | mudanças entregues | planos ainda não implementados como fatos |
| `LAST_CHANGES.md` | resumo automático do último commit | edição manual |

## 3. Estado atual resumido

- Desktop JavaFX: fases 0 a 5 concluídas.
- Estabilização: concluída.
- Google Tasks ao vivo: 100%.
- Piloto: encerrado em 100% com cinco decisões `SEM EVIDÊNCIA`.
- Projeto 2: implementação em 23,3%, restando 76,7%; `P2-01` e `P2-02` concluídas, `P2-03` em 33,3% (2 de 6 itens).
- Aplicação desktop pessoal pode estar aberta durante manutenção; confirmar processo antes de limpar `target/` ou reiniciar.
- Banco pessoal: `~/.agenda-pessoal/agenda.db`.
- Tokens Google: `~/.agenda/google-tokens.json`, permissão esperada `600`.

## 4. Mapa do desktop

| Área | Modelo/estado | Persistência | Regra | UI | Testes principais |
|---|---|---|---|---|---|
| Tarefas | `Task` | `TaskRepository` | `TaskService` | `AgendaTabController` | `DatabaseServiceTest`, services |
| Plano diário | `DailyPlan*` | `DailyPlanRepository` | `DailyPlanService` | `DailyPlanPanel` | `DailyPlanServiceTest`, `DailyPlanPanelFxTest` |
| Encerramento | review records | `DayReviewRepository` | `DayReviewService` | `DayReviewWindow` | `DayReviewServiceTest`, FX |
| Captura universal | `InboxCapture` | `InboxCaptureRepository` | `InboxCaptureService` | `QuickCaptureWindow`, `InboxTriageWindow` | services e FX |
| Foco/retomada | `FocusContext` | repository próprio | `FocusSelectionService`, `FocusContextService` | Dashboard/timer | services e FX |
| Alertas | preferências/contagens | Preferences | `PendencyNotificationService` | Config/Dashboard/status | service e FX |
| Protocolos | `Protocol*` | `ProtocolRepository` | serviços/controllers atuais | Checklist e execução | repository/FX |
| Google Tasks | mappings/snapshots | repositories Google | auth, gateway e sync services | Config e sync window | transport, sync e FX |
| Janelas | estado JavaFX | Preferences quando aplicável | `WindowManager` | views | `WindowManagerFxTest`, responsive tests |
| Tema | tokens CSS | preferência visual | `ThemeManager` | todas as scenes | testes FX de contraste/tema |

Regra: UI não executa SQL; repository não contém regra de negócio; service não depende de JavaFX.

## 5. Estrutura do Projeto 2

O scaffold `android/` foi criado em `P2-01`. Não criar arquivos vazios para fases futuras; a estrutura alvo continua:

```text
agenda/
├── android/
│   ├── settings.gradle.kts
│   ├── app/                  # smartphone: Compose, Room, repositório e fila offline
│   ├── contracts/            # schemas móveis versionados; compartilhados a partir da P2-03
│   ├── wear/                 # criado apenas em P2-05 ou antes se necessário ao teste
│   └── README.md
├── docs/
│   ├── adr/                  # decisões arquiteturais numeradas
│   ├── models/               # model cards
│   └── privacy/              # inventário de dados/permissões
├── src/main/java/...         # desktop existente
├── PROJECT2_SPEC.md
└── MAINTENANCE_MAP.md
```

Não criar um módulo Java/Kotlin compartilhado entre Maven desktop e Android antes de existir duplicação real. O contrato compartilhado inicial é schema + fixtures.

## 6. Componentes móveis planejados

Implementado em `P2-02`:

| Área | Local |
|---|---|
| Room v2, entidades, DAO e migração | `android/app/src/main/java/com/pessoal/agenda/mobile/data/local/` |
| Transações, fixtures e fila | `android/app/src/main/java/com/pessoal/agenda/mobile/data/OfflineRepository.kt` |
| Estado e ações da UI | `android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileViewModel.kt` |
| Telas Compose | `android/app/src/main/java/com/pessoal/agenda/mobile/ui/AgendaMobileApp.kt` |
| Contrato v1 | `android/contracts/README.md` e `android/contracts/v1/` |
| Pareamento v1 e ameaça | `android/contracts/PAIRING_V1.md` e `android/contracts/THREAT_MODEL_P2_03.md` |
| Parser de convite | `android/app/src/main/java/com/pessoal/agenda/mobile/pairing/PairingInvitation.kt` |
| Testes | `android/app/src/test/` e `android/app/src/androidTest/` |

| Domínio | Android | Wear | Desktop |
|---|---|---|---|
| Pareamento | `pairing/` + Keystore | nenhum segredo mestre | servidor temporário + aprovação |
| Sync | Room queue + WorkManager | Data Layer mínimo | endpoint local + journal |
| Alertas | scheduler, notification, sensory | notification/action listener | definição e materialização |
| Áudio | capability + route policy | háptico/áudio do Wear se permitido | configuração sincronizável |
| Protocolos | réplica e execução offline | passo atual e confirmar | editor e reconciliação |
| Captura | persistência local imediata | fora do MVP | triagem completa |
| Saúde | Health Connect + entrada manual | Health Services só se validado | agregados e relatório |
| IA | engine, features, runtime adapter | opções pré-calculadas | análise/model registry opcional |

## 7. Catálogo de recursos externos

Atualizar esta tabela quando uma dependência for adicionada ou removida.

| Recurso | Uso previsto | Dados acessados | Obrigatório? | Alternativa/saída |
|---|---|---|---|---|
| Android SDK / Compose | app móvel | UI local | sim | nenhuma no Android nativo |
| Room | banco offline | dados locais do app | sim | SQLite direto, não recomendado |
| WorkManager | sync e tarefas duráveis | IDs/estado de fila | sim | agendamento manual limitado |
| Android Keystore | chaves do aparelho | chaves não exportáveis | sim | nenhuma aceitável |
| Wear notification bridge | alertas iniciais | texto/ações do alerta | MVP | app Wear dedicado |
| Wear Data Layer | telefone ↔ relógio | payload mínimo do app | P2-05 | notificação espelhada |
| Health Connect | saúde autorizada | tipos escolhidos | opcional | entrada manual/nenhuma coleta |
| Health Services | sensor Wear próprio | sinal autorizado | não inicial | Health Connect |
| LiteRT | inferência local | features minimizadas | futuro | regras ou ONNX Runtime |
| ONNX Runtime Mobile | inferência/treino portátil | features minimizadas | alternativa | LiteRT ou regras |
| Provedor LLM | resumo textual revisável | somente seleção consentida | não | processamento local/manual |
| Google Tasks | sync de tarefas | tarefas autorizadas | opcional atual | uso só local |
| Gson 2.10.1 | leitura das fixtures no teste Java | somente JSON fictício versionado | teste P2-03 | remover após adotar codec de produção |

Para cada SDK novo registrar: versão fixa, licença, origem, permissões, rede, telemetria, tamanho, política de atualização, CVEs e teste de remoção.

## 8. Catálogo de permissões Android

Nenhuma permissão entra “para uso futuro”. Cada uma exige requisito, tela e teste de negação.

| Permissão/capacidade | Fase | Justificativa | Comportamento negado |
|---|---|---|---|
| `INTERNET` | P2-03 | sync HTTPS local | app continua offline |
| notificações | P2-04 | alertas opt-in | alertas ficam dentro do app |
| Bluetooth/Wear APIs | P2-05 | comunicação oficial com Wear | telefone continua funcional |
| Health Connect por tipo | P2-07 | relatório autorizado | categoria fica vazia |
| histórico/background health | posterior | somente hipótese aprovada | leitura limitada ao permitido |
| sensores Wear | posterior | medição própria validada | usar dados já disponíveis |

Localização não é requisito inicial. “Vou sair” não justifica pedir localização até existir caso concreto aprovado.

## 9. Contratos que não podem mudar silenciosamente

- semântica de `Concluir`, `Adiar`, `Reabrir` e tombstone;
- identidade UUID e idempotência de `operation_id`;
- versão mínima/máxima do protocolo de sync;
- unidade e fuso de todo instante e medida;
- categorias de consentimento;
- códigos de razão da recomendação;
- schema e proveniência de relatório;
- usos proibidos de dados de saúde;
- fallback sensorial e horário silencioso.

Mudança incompatível exige nova versão de contrato, fixture de compatibilidade, migração e ADR.

## 10. Registro de decisões

Criar um ADR em `docs/adr/NNNN-titulo.md` quando houver:

- novo transporte ou backend;
- mudança de topologia local/cloud;
- novo runtime ou provedor de IA;
- uso novo de dado de saúde;
- alteração de política de conflito;
- nova permissão sensível;
- mudança regulatória ou de finalidade;
- compartilhamento de código com outro projeto.

Formato mínimo: contexto, decisão, alternativas, consequências, dados afetados, rollback e links para requisitos.

## 11. Model cards

Todo modelo não determinístico usa `docs/models/<model_id>/<version>/MODEL_CARD.md` com:

- finalidade e proibições;
- features, unidades e minimização;
- dados de treino e janela;
- baseline determinístico;
- métricas e subgrupos relevantes;
- calibração;
- latência, memória e bateria;
- versão/hash do artefato;
- shadow mode;
- critério de ativação/desativação;
- rollback;
- incidentes conhecidos.

Sem model card e teste de fallback, o modelo não entra em release.

## 12. Receitas de manutenção

### Corrigir alerta

1. Identificar `alert_id`, dispositivo, canal e estado, sem copiar conteúdo sensível.
2. Reproduzir com relógio/tempo fake.
3. Testar silêncio, pausa, sobreposição, concluir e adiar.
4. Verificar idempotência e sync offline.
5. Atualizar requisito e regressão.

### Alterar sync

1. Ler contrato e matriz de compatibilidade.
2. Adicionar fixture antes do código.
3. Testar repetição, reorder, timeout, corpo truncado e conflito.
4. Validar versão anterior.
5. Nunca testar primeiro no banco pessoal.

### Alterar saúde

1. Citar finalidade e requisito.
2. Confirmar tipo e permissão oficial.
3. Definir minimização, retenção e exclusão.
4. Testar negação/revogação.
5. Revisar relatório e alegação clínica.
6. Atualizar inventário de dados e análise regulatória.

### Alterar IA

1. Comparar com regra baseline.
2. Atualizar features e model card.
3. Rodar avaliação offline e shadow mode.
4. Verificar domínio/guardrails.
5. Medir bateria/latência.
6. Demonstrar rollback para regras.

### Alterar áudio

1. Limitar escopo à Agenda.
2. Inventariar destinos disponíveis pela API.
3. Testar fone Bluetooth, com fio, speaker e remoção durante playback.
4. Testar silencioso, DND, chamada e mídia concorrente.
5. Não alterar rota global permanentemente.

## 13. Quality gates

### Desktop

```bash
./mvnw test
./mvnw -Pjavafx-ui-tests test
git diff --check
```

### Android

```bash
cd android
./gradlew test lint assembleDebug
ANDROID_SERIAL=emulator-5556 ./gradlew connectedDebugAndroidTest
```

Definir sempre `ANDROID_SERIAL` quando houver dispositivo físico conectado. Em `P2-01`, os AVDs reservados são `Agenda_Phone_API_34` e `Agenda_Wear_API_34`; o telefone físico permanece fora do gate.

### Gate documental

- requisito e aceite atualizados;
- percentual coerente;
- CHANGELOG descreve somente o entregue;
- mapa de manutenção aponta o componente novo;
- dependência/permissão catalogada;
- ADR/model card quando aplicável;
- nenhum segredo, dado pessoal, token, URL OAuth ou relatório real versionado.

## 14. Checklist para retomar uma sessão de trabalho

1. Ler `git status --short --branch` e não reverter mudanças desconhecidas.
2. Ler `LAST_CHANGES.md` e os três commits mais recentes.
3. Localizar a fase ativa na spec correspondente.
4. Confirmar se a aplicação pessoal está aberta.
5. Confirmar banco, backup e dispositivo alvo antes de teste mutável.
6. Ler apenas os módulos do mapa da área.
7. Atualizar checklist durante o avanço.
8. Executar gates proporcionais ao risco.
9. Atualizar documentos no mesmo commit.
10. Conferir `HEAD`, `origin/master` e worktree após o push.

## 15. Informações proibidas no repositório

- banco pessoal e backups;
- credenciais/tokens Google;
- chaves Android ou certificados privados;
- relatórios médicos reais;
- nomes/doses/substâncias do usuário;
- dados brutos de sensores;
- URLs de pareamento ativas;
- fingerprints vinculadas a sessões reais;
- API keys de IA;
- dumps de log com conteúdo de tarefas.

Fixtures usam dados fictícios inequívocos e IDs reservados para teste.
