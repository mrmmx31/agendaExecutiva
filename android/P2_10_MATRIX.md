# Matriz P2-10 - Dispositivos reais e revisao de release

Inicio: 2026-09-03. Cada um dos dez gates vale 10% da fase P2-10 e 1 ponto
percentual do Projeto 2. Dados de teste em host/emulador sao ficticios. Nenhum
comando pode ser enviado a telefone ou relogio fisico sem autorizacao explicita.

## Progresso

| # | Gate | Estado | Evidencia/pendencia |
|---:|---|---|---|
| 1 | uso pretendido e enquadramento regulatorio | aprovado | `docs/release/REGULATORY_SCOPE.md`; limites nao clinicos e gatilhos de nova revisao |
| 2 | privacidade, Data Safety e Health apps | aprovado para pre-release | aviso dentro do app, `PRIVACY_NOTICE.md` e rascunho `GOOGLE_PLAY_DECLARATIONS.md`; URL/controlador continuam gate de publicacao |
| 3 | APK release, permissoes, backup, logs, SDKs e segredos | aprovado | APKs nao depuraveis e `scripts/p2_10_static_gate.sh` verde; release ainda nao assinado |
| 4 | regressao funcional ponta a ponta em telefone/Wear virtuais | pendente | executar todos os instrumentados e percurso pareado com fixtures novas |
| 5 | falhas virtuais: processo, rede, Data Layer, Doze e reinicio | pendente | provar persistencia, reconciliacao e ausencia de estimulo indevido |
| 6 | telefone fisico: instalacao, permissoes, Health Connect e exportacao | bloqueado por autorizacao | usar perfil/dados de teste separados; nao acessar a Agenda pessoal |
| 7 | audio fisico: fone com/sem fio, remocao, DND, chamada e midia | bloqueado por autorizacao | validar rota efetiva e fallback, sem prometer controle de outros apps |
| 8 | relogio fisico: entrega, concluir, adiar, desconexao e reconciliacao | bloqueado por autorizacao | confirmar modelo Wear OS e pareamento antes de instalar |
| 9 | bateria, memoria e temperatura em uso prolongado | bloqueado por autorizacao/tempo | baseline e janela definida abaixo; medir telefone e relogio reais |
| 10 | aceite final, artefato assinado e decisao de distribuicao | bloqueado pelos gates 4-9 | preencher lacunas legais/publicacao ou declarar release apenas pessoal |

**Estado atual:** 30% do P2-10 (3 de 10); Projeto 2 em 93%.

## Gate 1 - decisao regulatoria

Uso v1 permanece produtividade, acessibilidade pessoal, autorregistro e
relatorio revisado pelo usuario. Diagnostico, triagem, predicao clinica,
tratamento, dose, causalidade e emergencia sao proibidos. A hipotese tecnica de
nao enquadramento como SaMD depende desses limites e deve ser reavaliada antes
de venda ou mudanca de alegacao.

## Gate 2 - declaracoes

O app mostra antes dos consentimentos que saude e opcional, processada localmente
e nao enviada automaticamente, alem do limite nao medico. A tela foi inspecionada
em 360 dp nos temas claro/escuro e o teste Compose percorreu previa/exportacao.
Publicacao continua bloqueada ate preencher controlador/contato, hospedar a
politica em URL HTTPS e submeter Data Safety e Health apps no Play Console.

## Gate 3 - release estatico

Comandos:

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:assembleRelease :wear:assembleRelease
cd ..
./android/scripts/p2_10_static_gate.sh
```

Resultado: 101 tarefas Gradle aprovadas. `apkanalyzer` confirmou os dois APKs
como nao depuraveis. O script valida XML, bloqueio de backup/cleartext,
permissoes finais, ausencia de analytics/runtime nao aprovado, logs no limite de
saude/modelo e padroes de segredo. O telefone tem apenas rede/WorkManager,
notificacao, vibracao e quatro leituras Health Connect esperadas; o Wear tem
somente permissoes normais trazidas por WorkManager/Data Layer. Camera,
microfone, localizacao, armazenamento, contatos e sensores corporais Wear nao
aparecem.

## Plano dos gates virtuais

Gate 4 deve executar as suites instrumentadas completas em ambos os AVDs,
reinstalar os APKs e percorrer alerta, `Concluir`, `Adiar`, `Vou sair`, captura,
fila, Health Connect negado, previa/exportacao e personalizacao desligada.

Gate 5 deve exercitar, sempre por serial explicito de emulador:

1. encerrar processos e confirmar restauracao sem prompt/estimulo espontaneo;
2. desconectar/reconectar rede durante operacao pendente;
3. interromper Data Layer, registrar acao Wear offline e reconciliar uma vez;
4. entrar/sair de idle/Doze com alerta materializado ficticio;
5. reiniciar AVDs e verificar Room, WorkManager e outboxes;
6. coletar `dumpsys batterystats`, memoria e jobs apenas como baseline virtual.

## Plano dos gates fisicos

- criar backup e perfil de teste antes da instalacao;
- registrar modelo/Android/Wear OS e versoes, sem versionar numero de serie;
- usar somente tarefas e relatorios ficticios no primeiro percurso;
- capturar estado inicial/final de bateria, memoria, temperatura e rotas;
- testar alerta visual, vibracao e audio em volume cauteloso e cancelavel;
- testar fone Bluetooth, fone com fio quando suportado, alto-falante, DND,
  remocao durante playback, chamada e midia concorrente;
- testar relogio conectado/desconectado, tela apagada, concluir/adiar e
  reconciliacao idempotente;
- executar uma janela passiva de 24 horas antes de concluir bateria;
- interromper imediatamente em aquecimento, drenagem anormal ou estimulo
  repetido.

O gate fisico nao autoriza coleta continua de sensor, uso de dados pessoais da
Agenda desktop nem alteracao da rota global de outros aplicativos.

