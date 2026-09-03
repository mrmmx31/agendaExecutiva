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
| 4 | regressao funcional ponta a ponta em telefone/Wear virtuais | aprovado | suites comuns verdes e 14 passos pareados orquestrados por `p2_10_emulator_gate.sh` |
| 5 | falhas virtuais: processo, rede, Data Layer, Doze e reinicio | aprovado | fixture duravel sobreviveu a rede desligada, processo, idle e reboot; API Wear possui retry limitado |
| 6 | telefone fisico: instalacao, permissoes, Health Connect e exportacao | em execução | instalação isolada, permissão negada/concedida, captura e protocolo aprovados; Health Connect sintético e exportações SAF pendentes |
| 7 | audio fisico: fone com/sem fio, remocao, DND, chamada e midia | em execução | rota automática inspecionada sem reprodução; ensaios sensoriais permanecem pendentes |
| 8 | wearable fisico: entrega, acoes disponiveis e desconexao | aprovado com limites do hardware | texto/som e ações no telefone aprovados; ações/vibração/fila no pulso indisponíveis; alertas desligados não entregam |
| 9 | bateria, memoria e temperatura em uso prolongado | em execução | baseline inicial coletado; janela de 24 horas e medição final pendentes |
| 10 | aceite final, artefato assinado e decisao de distribuicao | bloqueado pelos gates 4-9 | preencher lacunas legais/publicacao ou declarar release apenas pessoal |

**Estado atual:** 60% do P2-10 (6 de 10); Projeto 2 em 96%.

**Sessão física em andamento:** o detalhamento incremental, sem identificadores,
fica em `docs/release/PHYSICAL_TEST_RESULTS.md`. Evidência parcial não altera o
percentual de um gate até todos os critérios obrigatórios dele passarem.

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

## Resultado dos gates virtuais

`p2_10_emulator_gate.sh` protege ambos os seriais recusando qualquer valor que
nao seja AVD/QEMU. A suite comum do telefone executou 50 casos, com os nove
passos pareados corretamente ignorados; a do Wear executou 12, com cinco passos
pareados ignorados. Em seguida, 14 instrumentacoes coordenadas validaram nos
dois sentidos o pareamento, adiamento, conclusao, outbox offline e protocolo.
Todos passaram. Os testes pareados agora exigem o argumento `pairedGate=true`,
portanto nao aguardam a outra ponta dentro de uma suite comum.

`p2_10_resilience_gate.sh` criou, sem rede, uma captura pendente e um alerta
visual quatro horas no futuro. Room e WorkManager mantiveram ambos apos morte do
processo, idle e reboot do telefone. O Wear tambem reiniciou e os dois lados
voltaram a encontrar o no pareado. O primeiro ensaio mostrou que
`sys.boot_completed=1` pode preceder a disponibilidade da API Wear; o gate agora
faz retry limitado, e a repeticao completa passou. O script restaura rede,
bateria simulada e idle por `trap`, inclusive em falha, e coleta somente baseline
virtual de memoria/jobs/bateria. Nenhum canal sensorial foi acionado.

## Plano dos gates fisicos

O procedimento detalhado esta em `docs/release/PHYSICAL_TEST_RUNBOOK.md`. A
variante `fieldTest` usa pacote e armazenamento separados, e
`p2_10_physical_gate.sh` encerra antes de qualquer ADB sem autorizacao textual,
serial fisico explicito e stage valido. Telefone e Wear podem ser preparados
separadamente; nada e instalado durante o pre-flight.

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

**Perfil do wearable real:** a Mertto ZL02D Sport informa família ZL02CPro e
firmware 2.0.9. As especificações disponíveis e a dependência de aplicativo
companheiro a classificam como smartband Bluetooth sem Wear OS, Wi-Fi, ADB ou
Data Layer. O APK Wear não será instalado nela. O gate 8 passa a medir
espelhamento sensorial e a registrar ações como disponíveis ou indisponíveis.
O ensaio físico confirmou texto e alerta sonoro, mas não vibração nem ações;
`Concluir` e `Adiar` no pulso continuam cobertos pelo módulo Wear
OS em emulador e exigiriam Wear OS ou SDK oficial para validação física. O
identificador potencialmente único mostrado em `Sobre` foi omitido do Git.

**Preparacao validada sem dispositivo:** os APKs `fieldTest` de telefone e Wear
foram montados em 77 tarefas e inspecionados com `apkanalyzer`: ambos usam
`com.pessoal.agenda.mobile.fieldtest`, versao `0.1.0-fieldtest`, rotulo
`Agenda Sensorial - Teste` e assinatura/debug apenas de laboratorio. O gate
completo das variantes normal, release e `fieldTest` passou em 325 tarefas; os
APKs release foram regenerados e o gate estatico continuou verde. Tres ensaios
de recusa confirmaram que o script fisico termina antes de ADB sem autorizacao,
serial ou com serial de emulador. Essa preparacao reduz risco, mas nao conta como
evidencia dos gates 6 a 9.

**Assinatura preparada:** os módulos aceitam as mesmas quatro variáveis
`AGENDA_RELEASE_*`, todas ou nenhuma, sem segredo no repositório.
`p2_10_release_candidate.sh` recusa configuração ausente, keystore dentro do
projeto e certificados divergentes; quando houver credenciais autorizadas, gera
e verifica APK/AAB dos dois módulos e checksums sem instalar ou publicar. A
politica e o procedimento ficam em `docs/release/SIGNING.md`. Nenhuma chave foi
de produção criada nesta etapa; a validação automatizada usou somente uma chave
sintética de um dia fora do projeto e a removeu ao terminar. APK e AAB dos dois
módulos passaram na verificação de assinatura e certificado comum. Em seguida,
o gate integral sem credenciais aprovou 377 tarefas Gradle e 365 casos unitários,
e os APKs release voltaram a ser não assinados. O gate 10 permanece pendente.
