# Projeto 2 — Prótese Executiva e Sensorial Distribuída

| Campo | Valor |
|---|---|
| Status | Implementação iniciada; 8% do Projeto 2 |
| Versão da spec | 1.0 |
| Data | 2026-08-31 |
| Plataformas | Desktop JavaFX, Android e Wear OS |
| Estratégia | Local-first, offline-first, consentimento granular |
| Fonte de verdade | Este documento para o Projeto 2; `SPEC.md` para o produto desktop atual |

## 1. Visão

O Projeto 2 estende a Agenda para uma prótese executiva e sensorial distribuída entre notebook, smartphone e smartwatch. O sistema deve reduzir o custo de lembrar, iniciar, confirmar, adiar, sair de casa, capturar algo fora do notebook e preparar um relato organizado para acompanhamento médico.

O smartphone é o nó móvel e offline. O smartwatch é uma superfície curta de percepção e resposta. O desktop continua sendo a superfície principal de organização. Nenhum dispositivo depende de o outro estar ligado para executar o que já foi sincronizado.

## 2. Limites de segurança e finalidade

O produto auxilia rotina, registro e comunicação. Na primeira versão ele não:

- diagnostica TDAH, intoxicação, abstinência, arritmia ou qualquer condição;
- recomenda iniciar, suspender ou alterar dose de medicamento;
- classifica uso de substância como seguro;
- interpreta batimento cardíaco como causa de comportamento;
- substitui médico, psicólogo, emergência ou orientação farmacêutica;
- envia relatório automaticamente a terceiros;
- promete detectar emergência ou garantir entrega de alerta.

Dados de saúde, medicamentos e substâncias são dados sensíveis. A coleta será opcional por categoria, revogável e visível. Qualquer futura função clínica, diagnóstica ou terapêutica exige análise regulatória própria antes da implementação. A Anvisa trata software com finalidade médica sob as regras de SaMD, e dados de saúde são sensíveis na LGPD: [orientação SaMD](https://www.gov.br/anvisa/pt-br/assuntos/noticias-anvisa/2022/software-como-dispositivo-medico-perguntas-e-respostas/) e [conceito de dado sensível](https://www.gov.br/anpd/pt-br/canais_atendimento/cidadao-titular-de-dados/denuncia-peticao-de-titular-referente-lgpd).

## 3. Objetivos

1. Entregar alertas da Agenda ao smartphone e, quando possível, ao smartwatch.
2. Permitir no relógio somente as ações essenciais `Concluir` e `Adiar`.
3. Permitir adiamento manual detalhado no smartphone e sugestões limitadas no relógio.
4. Manter captura, protocolos e respostas disponíveis quando o notebook estiver desligado.
5. Oferecer um fluxo `Vou sair` que abra imediatamente o protocolo adequado.
6. Sincronizar posteriormente operações feitas offline, sem duplicação ou perda silenciosa.
7. Permitir configurar os canais visual, vibração e áudio da própria aplicação.
8. Priorizar fone conectado para o áudio da Agenda quando suportado e configurado.
9. Reunir registros voluntários de rotina, medicamentos, substâncias, sintomas e dados autorizados de saúde.
10. Gerar relatório básico, revisável e exportável pelo usuário para seu médico.
11. Evoluir de regras explicáveis para personalização local calibrada pelo comportamento observado.
12. Catalogar contratos, modelos, permissões e componentes para manutenção previsível.

## 4. Não objetivos da primeira entrega

- Integração com WhatsApp.
- Sincronização obrigatória em nuvem.
- Suporte inicial a iPhone ou Apple Watch.
- Controle da saída de áudio de outros aplicativos.
- Coleta contínua própria de sinais vitais em segundo plano.
- IA generativa tomando decisões de saúde ou prioridade sem confirmação.
- Treinamento de modelo com poucos eventos ou sem baseline comparável.
- Publicação imediata na Play Store.
- Migração do desktop JavaFX para outra tecnologia.

## 5. Plataformas e compatibilidade

### 5.1 Smartphone

- Aplicativo Android nativo em Kotlin e Jetpack Compose.
- Banco local Room e fila durável de operações.
- `minSdk` inicial a confirmar após inventário do aparelho; referência de desenvolvimento API 34.
- Trabalho persistente por WorkManager, que é a API recomendada para tarefas que sobrevivem ao fechamento e reinício do aplicativo: [documentação oficial](https://developer.android.com/develop/background-work/background-tasks/persistent).

### 5.2 Smartwatch

- Baseline funcional: Wear OS pareado a Android.
- Fallback inicial: notificação Android criada no smartphone e espelhada automaticamente no relógio.
- Módulo Wear OS dedicado quando forem necessários estado offline próprio, Tile, resposta mais confiável ou interface específica.
- A Data Layer API será usada somente entre Android e Wear OS; não serão criados sockets Bluetooth próprios. A API pode transmitir diretamente quando há conexão e manter itens para sincronização posterior: [visão geral](https://developer.android.com/training/wearables/data/overview) e [DataItem](https://developer.android.com/training/wearables/data/data-items).
- Se o relógio não executar Wear OS, a fase de descoberta deve registrar fabricante, sistema e capacidades. Nesse caso, o MVP fica limitado ao espelhamento oferecido pelo sistema até existir SDK oficial compatível.

### 5.3 Ambiente inicial

- SDK localizado em `/home/lsi/Android/Sdk`.
- AVD de telefone próprio: `Agenda_Phone_API_34`, Android 14/API 34 com Play Store.
- AVD de relógio próprio: `Agenda_Wear_API_34`, Wear OS 5/API 34.
- `adb` e emulador disponíveis.
- Telefone físico Samsung detectado por USB, mas fica fora dos testes até o gate de dispositivo real.
- Os dois AVDs iniciam e respondem via `adb`; o pareamento pelo assistente do Android Studio ainda está pendente.

## 6. Arquitetura de referência

### 6.1 Componentes

| Componente | Responsabilidade |
|---|---|
| Agenda Desktop | Organização completa, edição, relatórios, servidor local de pareamento e sincronização |
| Agenda Android | Réplica operacional, captura offline, protocolos, alertas, áudio, Health Connect e fila de sync |
| Agenda Wear | Exibir alerta curto, concluir, adiar e mostrar o passo atual do protocolo |
| Contrato Sync | Versão, schemas, comandos idempotentes, cursores, conflitos e capacidades |
| Motor Sensorial | Seleção de canal, cooldown, horário silencioso e prevenção de sobreposição |
| Motor de Recomendação | Regras e ranking de sugestões, sempre separado da execução |
| Registro de Saúde | Consentimentos, importações Health Connect e entradas manuais sensíveis |
| Gerador de Relatório | Agregação local, proveniência, prévia, redação neutra e exportação explícita |

### 6.2 Topologia

```text
Desktop JavaFX <-- HTTPS local pareado --> Android
                                           |
                                           +-- notificações espelhadas --> relógio
                                           |
                                           +-- Wear Data Layer <-------> Wear OS app
                                           |
                                           +-- Health Connect (permissões selecionadas)
```

Não haverá banco compartilhado em rede. Cada dispositivo mantém seu próprio estado e troca comandos/eventos versionados.

### 6.3 Reaproveitamento da base do Motoclube

O projeto `/home/lsi/IdeaProjects/tesourariamc/android` serve como referência mecânica para:

- Kotlin, Compose e Gradle Kotlin DSL;
- Room com schemas exportados e migrações explícitas;
- fila offline de operações pendentes;
- pareamento por convite e código de uso único;
- TLS local com fingerprint do certificado temporário;
- credencial cifrada com Android Keystore;
- IDs de operação, sequência, hash, limites de payload e mensagens sanitizadas;
- testes unitários, Robolectric, Room e instrumentados.

Não serão copiados domínio financeiro, package name, banco, chaves, credenciais ou contratos. Código só poderá ser extraído depois que um componente genérico tiver API, testes e licença/ownership claros.

## 7. Conectividade e sincronização

### 7.1 Pareamento

**SYN-01:** o desktop gera convite QR de curta duração contendo versão, endpoint local, ID do desktop, nonce e fingerprint TLS.

**SYN-02:** o usuário confirma no desktop o nome do aparelho e os papéis solicitados.

**SYN-03:** o Android cria chaves no Android Keystore e recebe credencial específica do dispositivo.

**SYN-04:** dispositivos podem ser listados e revogados no desktop. Revogar um aparelho não apaga dados locais automaticamente.

**SYN-05:** segredos, URLs completas e payloads sensíveis não entram em logs.

### 7.2 Transporte

**SYN-06:** a primeira versão sincroniza por HTTPS na rede local ou hotspot, após convite explícito.

**SYN-07:** ausência do notebook não bloqueia captura, protocolo, alerta já recebido ou resposta no relógio.

**SYN-08:** WorkManager tenta sincronização quando as condições configuradas forem atendidas, com backoff e comando manual `Sincronizar agora`.

**SYN-09:** transporte pode repetir; efeitos devem ser idempotentes.

### 7.3 Contrato

Cada operação possui:

- `operation_id` UUID;
- `device_id`;
- `sequence` monotônica por dispositivo;
- `contract_version`;
- `entity_type` e `entity_id` UUID;
- `command_type`;
- `occurred_at` e fuso;
- `payload` validado por schema;
- `payload_hash`;
- `base_revision` quando houver edição concorrente.

Estados: `PENDING`, `SENT`, `APPLIED`, `CONFLICT`, `REJECTED` e `RETRYABLE`.

**SYN-10:** confirmação e adiamento são comandos idempotentes; repetir o mesmo `operation_id` retorna o resultado anterior.

**SYN-11:** exclusões são tombstones com retenção definida, nunca ausência ambígua.

**SYN-12:** conflitos de texto ou estrutura não usam “última escrita vence” silenciosamente. O usuário compara versões no dispositivo adequado.

**SYN-13:** ações temporais compatíveis podem convergir por regra: concluir prevalece sobre adiamento anterior, mas um adiamento posterior a uma reabertura permanece válido.

**SYN-14:** entidades desktop que hoje usam IDs inteiros recebem `sync_uuid` estável por migração aditiva; IDs locais nunca são enviados como identidade global.

## 8. Alertas sensoriais

### 8.1 Modelo

Um alerta contém `alert_id`, origem, referência opcional, texto curto, instante previsto, janela válida, criticidade funcional, canais permitidos, política de repetição e ações disponíveis.

**ALT2-01:** canais são configuráveis separadamente: visual, vibração do telefone, vibração do relógio e áudio.

**ALT2-02:** o controle geral desativa imediatamente novos estímulos sem ocultar tarefas.

**ALT2-03:** horário silencioso, pausa temporária e prevenção de sobreposição continuam obrigatórios em todos os dispositivos.

**ALT2-04:** um alerta não escala automaticamente de canal sem política visível e consentida.

**ALT2-05:** alertas mostram por que apareceram e qual dispositivo os originou.

**ALT2-06:** falha de entrega é registrada como estado técnico, não como falha do usuário.

### 8.2 Ações

**ALT2-07:** smartwatch exibe somente `Concluir` e `Adiar` como ações primárias.

**ALT2-08:** `Concluir` registra uma operação idempotente e apresenta confirmação curta, sem exigir abrir o telefone.

**ALT2-09:** `Adiar` no relógio usa um conjunto pequeno de sugestões calculadas previamente no telefone. Não há teclado nem seletor complexo no relógio.

**ALT2-10:** o smartphone sempre oferece definição manual de data/hora e presets configuráveis.

**ALT2-11:** sugestão de adiamento informa a duração escolhida e permite correção no telefone.

**ALT2-12:** limites mínimos, máximos, horário silencioso, prazo e quantidade de adiamentos impedem ciclos agressivos ou infinitos.

### 8.3 Wear OS

Notificações no Wear OS usam a mesma estrutura básica das notificações Android e podem ser espelhadas pelo sistema: [documentação oficial](https://developer.android.com/training/wearables/notifications).

Fases:

1. notificação do telefone com duas ações, validada no emulador pareado;
2. módulo Wear com Compose, `WearableListenerService` restrito aos eventos necessários e Data Layer;
3. Tile opcional `Agora` com próximo item e ações curtas;
4. funcionamento desconectado limitado aos alertas já materializados no relógio.

O Data Layer não será a fonte de verdade. Android e Wear mantêm cópias locais mínimas.

## 9. Áudio e prioridade de saída

**AUD-01:** a configuração controla somente sons produzidos pela Agenda. O aplicativo não promete redirecionar áudio de WhatsApp ou outros aplicativos.

**AUD-02:** política padrão sugerida: fone Bluetooth ou com fio conectado, depois dispositivo configurado, depois alto-falante do telefone, respeitando silencioso/DND e disponibilidade.

**AUD-03:** opções iniciais:

- `Automático do sistema`;
- `Preferir fone`;
- `Preferir telefone`;
- `Somente vibração`;
- `Sem saída sensorial`.

**AUD-04:** a tela lista apenas destinos realmente disponíveis e oferece teste curto, cancelável e sem sobreposição.

**AUD-05:** quando a rota preferida desaparecer, aplicar fallback configurado e registrar a razão.

**AUD-06:** a escolha não deve alterar permanentemente a rota global do sistema.

Android restringe seleção direta de dispositivo a casos e APIs específicos; `setCommunicationDevice` é destinado a comunicação, enquanto outras rotas dependem do sistema/MediaRouter. A implementação deve testar capacidades e não alegar controle universal: [AudioManager](https://developer.android.com/reference/android/media/AudioManager).

## 10. Fluxo “Vou sair”

**OUT-01:** smartphone e desktop apresentam uma ação direta `Vou sair`.

**OUT-02:** a ação abre imediatamente o protocolo de saída padrão ou uma lista curta de protocolos frequentes; não abre o editor.

**OUT-03:** protocolo pode conter carteira, chaves, celular, fone, documentos, medicamentos, carregadores e itens condicionais definidos pelo usuário.

**OUT-04:** cada passo oferece confirmação rápida; o passo atual pode ser enviado ao relógio.

**OUT-05:** execução funciona offline e sincroniza depois.

**OUT-06:** alterações estruturais feitas no Android entram em caixa de revisão antes de substituir template do desktop.

**OUT-07:** contexto opcional pode escolher protocolo por horário, local ou compromisso, mas a primeira versão usa seleção explícita e regras determinísticas.

**OUT-08:** a IA não adiciona silenciosamente itens ao protocolo; ela apenas sugere, com origem e opção de aceitar uma vez ou incorporar ao template.

## 11. Captura móvel e organização posterior

**CAP2-01:** captura abre em uma ação e aceita texto livre offline.

**CAP2-02:** classificação não é obrigatória no momento da captura.

**CAP2-03:** capturas sincronizam como eventos imutáveis e recebem confirmação do desktop.

**CAP2-04:** deduplicação usa `capture_id`, não texto.

**CAP2-05:** organizar em tarefa, ideia, protocolo ou nota ocorre no telefone ou desktop, com resultado sincronizável.

**CAP2-06:** falha preserva texto e oferece nova tentativa; nunca limpa antes de persistir localmente.

## 12. Saúde, medicamentos e substâncias

### 12.1 Consentimento

**HLT-01:** cada fonte é opt-in: frequência cardíaca, frequência em repouso, sono, atividade, medicação, substância, sintoma e nota de rotina.

**HLT-02:** a tela mostra finalidade, período lido, última leitura e como revogar.

**HLT-03:** negar uma permissão não reduz funções de agenda, protocolos ou alertas.

**HLT-04:** dados importados mantêm origem, dispositivo, instante e cobertura; ausência de amostra não vira valor zero.

### 12.2 Health Connect e relógio

Health Connect será a integração preferida no telefone para dados já coletados por relógio/aplicativos. Ele possui tipos de frequência cardíaca e permissões granulares: [tipos de dados](https://developer.android.com/health-and-fitness/health-connect/data-types) e [escrita de séries](https://developer.android.com/health-and-fitness/health-connect/write-data).

Health Services no Wear OS só será usado se houver necessidade validada de medição própria; ele abstrai sensores e métricas, mas implica permissões e custo de bateria: [Health Services](https://developer.android.com/health-and-fitness/health-services).

**HLT-05:** inicialmente ler resumos autorizados do Health Connect; não manter sensor cardíaco próprio continuamente.

**HLT-06:** leituras de alta frequência não são copiadas integralmente ao desktop por padrão. Sincronizar cobertura e agregados necessários ao relatório.

**HLT-07:** acesso histórico ou em segundo plano exige consentimento separado e justificativa visível.

### 12.3 Registro manual

**HLT-08:** medicação registra nome definido pelo usuário, quantidade/unidade opcional, horário planejado, horário informado, origem e nota opcional.

**HLT-09:** substância registra categoria/nome livre, quantidade/unidade opcional, horário, contexto e efeito percebido opcional, com linguagem neutra.

**HLT-10:** sintomas ou eventos relevantes registram horário, intensidade subjetiva opcional e nota.

**HLT-11:** campos desconhecidos podem ficar vazios; o sistema não inventa dose, pureza, interação ou causalidade.

**HLT-12:** correção mantém trilha local de alteração; exclusão é explícita e sincronizável.

Registros clínicos formais no Health Connect usam recursos FHIR e permissões específicas, inclusive para medicamentos: [formato de Medical Records](https://developer.android.com/health-and-fitness/health-connect/medical-records/data-format). Entradas pessoais deste produto permanecerão em modelo próprio até existir necessidade de interoperabilidade clínica validada.

## 13. Relatório para acompanhamento médico

**RPT-01:** usuário escolhe período e categorias antes da geração.

**RPT-02:** relatório separa fato registrado, dado de sensor, agregado e observação do usuário.

**RPT-03:** conteúdo possível:

- cobertura temporal e lacunas de dados;
- horários registrados de sono e rotina;
- frequência cardíaca por faixa/resumo e origem;
- medicações e substâncias registradas;
- sintomas/eventos informados;
- alertas concluídos, adiados e ignorados, em agregado;
- sessões de foco e protocolos, em agregado;
- notas escolhidas explicitamente pelo usuário.

**RPT-04:** não declarar correlação como causalidade nem produzir diagnóstico.

**RPT-05:** prévia permite excluir linhas, ocultar categorias e corrigir identificação.

**RPT-06:** exportação inicial PDF e CSV/JSON estruturado; FHIR somente em fase posterior com validação de interoperabilidade.

**RPT-07:** nenhum envio automático. O usuário escolhe arquivo e canal fora da aplicação.

**RPT-08:** cada relatório inclui versão do schema, período, fuso, fontes, permissões e limitações.

## 14. Estratégia de inteligência artificial

### 14.1 Princípio

A IA recomenda; o domínio valida; o usuário decide. O motor de agenda nunca depende de texto livre produzido por modelo para executar uma mutação.

### 14.2 Evolução por níveis

| Nível | Técnica | Uso permitido | Gate |
|---|---|---|---|
| IA-0 | Regras determinísticas | presets de adiamento, canal e protocolo sugerido | primeiro MVP |
| IA-1 | Estatística local | horários de resposta, frequência de adiamento, eficácia por contexto | volume mínimo e baseline |
| IA-2 | Ranking on-device | ordenar presets e alertas não clínicos | avaliação offline e rollback |
| IA-3 | Modelo local treinável | personalização com dados no aparelho | bateria, privacidade e calibração aprovadas |
| IA-4 | LLM opcional | resumir texto escolhido e estruturar relatório revisável | consentimento por envio e redação |

### 14.3 Escolha recomendada

1. Implementar `RecommendationEngine` independente do runtime de ML.
2. Começar com regras versionadas e explicáveis.
3. Usar LiteRT como primeira opção de inferência Android quando existir um modelo pequeno estável; o runtime em Play services processa entradas no dispositivo: [LiteRT Android](https://ai.google.dev/edge/litert/android/play_services).
4. Manter ONNX Runtime Mobile como alternativa se a portabilidade de modelos ou treinamento local justificar o custo: [ORT Mobile](https://onnxruntime.ai/docs/get-started/with-mobile.html) e [treinamento no dispositivo](https://onnxruntime.ai/docs/get-started/training-on-device.html).
5. Não escolher agora um provedor de LLM em nuvem. Criar `AiTextProvider` e avaliar por privacidade, retenção, região, custo, Structured Outputs, disponibilidade e possibilidade de troca. A Responses API da OpenAI é uma candidata para saídas estruturadas, não uma dependência decidida: [documentação oficial](https://developers.openai.com/api/docs/guides/latest-model).

### 14.4 Entradas permitidas para personalização

- horário e dia da semana;
- tipo do alerta e prazo;
- foco/protocolo ativo;
- dispositivo disponível;
- canal usado;
- tempo até confirmar;
- adiamentos e duração;
- preferência manual do usuário;
- contexto de capacidade escolhido explicitamente.

Dados cardíacos, medicamentos, substâncias e sintomas ficam fora do ranking automático inicial. Só poderão entrar em pesquisa posterior com hipótese explícita, consentimento separado, avaliação de risco e sem inferência clínica.

### 14.5 Guardrails

**AI-01:** toda sugestão traz código de razão legível.

**AI-02:** nenhuma sugestão executa conclusão, adiamento, medicação ou envio sem ação do usuário.

**AI-03:** regras de domínio limitam a saída mesmo que o modelo retorne valor inválido.

**AI-04:** modelo nunca escolhe dose, substância ou conduta médica.

**AI-05:** usuário pode desativar personalização, limpar histórico e voltar às regras padrão.

**AI-06:** aprendizado requer quantidade mínima por contexto; sem amostra, usar fallback determinístico.

**AI-07:** métricas de qualidade incluem taxa de correção manual, latência, bateria, adiamentos repetidos e alertas perdidos; “mais engajamento” não é objetivo suficiente.

**AI-08:** versões de modelo e regra possuem rollback imediato.

### 14.6 Catálogo de modelos

Cada versão deve registrar:

- `model_id`, versão e hash;
- runtime e versão;
- finalidade e usos proibidos;
- features e unidades;
- origem e janela dos dados;
- quantidade mínima de amostras;
- métricas offline e baseline;
- calibração e limites conhecidos;
- consumo de bateria, memória e latência;
- data de ativação e motivo;
- estratégia de rollback;
- model card versionado, sem dados pessoais.

## 15. Segurança e privacidade

**SEC-01:** banco Android usa Room; campos de saúde e credenciais recebem proteção adicional com chaves do Android Keystore.

**SEC-02:** tráfego desktop/telefone usa TLS pareado, autenticação por dispositivo, nonce e limites de corpo.

**SEC-03:** logs não contêm texto de saúde, medicamento, substância, nota, token ou credencial.

**SEC-04:** backup Android permanece desativado até existir política cifrada e testada.

**SEC-05:** exportação sensível alerta sobre destino e oferece arquivo protegido quando tecnicamente adequado.

**SEC-06:** permissões seguem minimização e são solicitadas no contexto da função, não na primeira abertura.

**SEC-07:** a tela `Dados e privacidade` mostra dados locais, fontes, retenção, exportação e exclusão.

**SEC-08:** excluir dados sensíveis não exige excluir tarefas e protocolos.

**SEC-09:** toda dependência que acessa rede, saúde, áudio ou sensores entra no catálogo de recursos e na revisão de permissões.

**SEC-10:** a aplicação segue APIs de segurança da plataforma e Android Keystore: [Android Security](https://developer.android.com/security).

## 16. Modelo de dados inicial

Entidades previstas, ainda sem compromisso de schema físico:

| Entidade | Finalidade |
|---|---|
| `Device` | aparelho pareado, capacidades, revogação |
| `SyncOperation` | comando durável e idempotente |
| `SyncCursor` | progresso por dispositivo/contrato |
| `TaskReplica` | subconjunto operacional da tarefa |
| `ProtocolReplica` | template disponível offline |
| `ProtocolRun` / `ProtocolStepRun` | execução e confirmações |
| `AlertDefinition` | regra materializada de alerta |
| `AlertDelivery` | tentativa por dispositivo/canal |
| `AlertAction` | concluir, adiar, abrir, dispensar |
| `SnoozePolicy` | presets, limites e fallback |
| `SensoryProfile` | canais, rota de áudio e silêncio |
| `MobileCapture` | captura livre offline |
| `ConsentGrant` | finalidade, fonte e revogação |
| `HealthImportCursor` | origem e janela já processada |
| `HealthSummary` | agregado com cobertura/proveniência |
| `IntakeLog` | medicação ou substância informada |
| `SymptomLog` | evento subjetivo informado |
| `RecommendationEvent` | entrada, opções, escolha e razão |
| `ModelRegistry` | versão, hash, métricas e rollback |
| `ReportManifest` | período, fontes, schema e redações |

## 17. UX por dispositivo

### 17.1 Smartphone

Primeira tela operacional, não landing page:

- `Agora`;
- próxima ação e alerta ativo;
- `Vou sair`;
- `Capturar`;
- protocolo em execução;
- sincronização discreta e acessível;
- navegação para saúde, histórico e configurações sem disputar atenção.

### 17.2 Relógio

- uma mensagem por vez;
- texto curto, legível e sem parágrafo;
- dois comandos primários estáveis: `Concluir` e `Adiar`;
- confirmação háptica curta;
- estado offline ou falha sem culpar o usuário;
- sem formulário de saúde, configuração complexa ou relatório.

### 17.3 Configurações móveis

Grupos separados:

1. Dispositivos e sincronização.
2. Alertas e canais sensoriais.
3. Áudio e fallback.
4. Relógio.
5. Saúde e permissões.
6. Personalização e IA.
7. Dados, exportação e exclusão.

## 18. Requisitos não funcionais

- Ação no relógio: no máximo dois toques a partir do alerta expandido.
- Captura móvel: persistência local antes de qualquer rede.
- Mutação sincronizada: idempotente e auditável.
- Interface operacional utilizável em 320 dp sem texto truncado.
- Tema claro/escuro e escala de fonte do sistema.
- Nenhum alerta sonoro inesperado na instalação.
- Nenhuma coleta de saúde antes de consentimento específico.
- Nenhuma operação de rede na thread principal.
- Trabalho de fundo compatível com Doze e limites de bateria.
- Migrações Room com schema exportado e teste de todas as versões suportadas.
- Contratos desktop/Android testados por fixtures compartilhadas.
- Relatório reproduzível a partir do mesmo snapshot e versão.

## 19. Testes e ambientes

### 19.1 Pirâmide

- Unidade: regras, adiamento, conflitos, serialização, redação e guardrails.
- Persistência: Room temporário, migrações e fila offline.
- Contrato: fixtures JSON válidas/inválidas em Java e Kotlin.
- Integração: servidor desktop temporário e Android emulado.
- UI Android: Compose tests e screenshots em larguras/temas.
- Wear: emulador pareado, notificações, Data Layer e ações.
- Sistema: WorkManager, reboot, Doze, rede ausente e reconexão.
- Dispositivo real: somente depois dos gates de emulador e backup.

### 19.2 Matriz mínima

| Cenário | Telefone | Relógio | Notebook | Resultado esperado |
|---|---|---|---|---|
| Todos conectados | online | conectado | online | alerta e resposta convergem |
| Notebook desligado | offline da Agenda | conectado | desligado | alerta materializado funciona e enfileira ação |
| Relógio desconectado | online | desconectado | online/offline | telefone assume canal e relógio sincroniza depois |
| Sem internet | Wi-Fi local | conectado | mesma rede | sync local funciona |
| Fora de casa | offline do desktop | conectado | inacessível | captura/protocolo funcionam e sincronizam depois |
| Fone removido | online | conectado | qualquer | fallback de áudio explícito |

## 20. Fases de entrega

### P2-00 — Especificação e arquitetura

**Status:** Concluído em 2026-08-31.

Entregas: esta spec, fechamento honesto do piloto, inventário Android, estratégia de IA e mapa de manutenção.

### P2-01 — Scaffold Android e emuladores

Criar `android/`, módulos `app` e futuramente `wear`, Compose, Room, lint, testes e CI local. Validar AVD API 34 e criar AVD Wear OS pareado. Nenhum dado real.

**Status:** Em andamento em 2026-08-31, 83% (5 de 6 itens concluídos).

**Checklist de avanço:**

- [x] criar o projeto Gradle isolado e o módulo `app` em Kotlin/Compose;
- [x] criar Room somente para metadados técnicos, exportar o schema e testar a persistência;
- [x] aprovar testes locais, lint e montagem do APK;
- [x] criar o AVD `Agenda_Phone_API_34` com Play Store e validar o app em temas claro e escuro;
- [x] criar e inicializar o AVD `Agenda_Wear_API_34` com Wear OS 5;
- [ ] parear os dois AVDs pelo assistente Wear do Android Studio e registrar a conexão.

**Evidência:** `./gradlew test lint assembleDebug` e o teste instrumentado Compose passaram. O APK foi instalado somente em emuladores API 34; a interface foi inspecionada em tema claro e escuro sem cortes, sobreposição ou texto escuro residual. Os dois AVDs concluíram o primeiro boot e responderam via `adb`. Nenhuma permissão sensível, dado real, Health Connect, rede da Agenda ou IA foi adicionada.

**Percentual geral:** a implementação tem dez fases de `P2-01` a `P2-10`, cada uma valendo 10 pontos percentuais. Como 5/6 da `P2-01` estão concluídos, o avanço geral arredondado é 8%. `P2-00` é especificação e não entra nesse percentual.

### P2-02 — Núcleo móvel offline

Captura, réplica mínima, protocolos, fila de operações, schemas e dados fictícios. Sem rede e sem saúde.

### P2-03 — Pareamento e sync local

Servidor desktop, QR, TLS fixado, Keystore, cursores, snapshots, idempotência, conflitos e revogação.

### P2-04 — Alertas e áudio no smartphone

Notificações, concluir/adiar, horário silencioso, cooldown, WorkManager, perfis sensoriais, teste de rota e fallback.

### P2-05 — Wear OS

Espelhamento validado, módulo Wear, Data Layer, dois botões, estado offline mínimo e matriz emulador pareado.

### P2-06 — “Vou sair” e protocolos móveis

Execução offline, passo atual, relógio, sugestões determinísticas e revisão de alterações estruturais.

### P2-07 — Saúde e relatório básico

Consentimentos, Health Connect, entradas manuais, retenção, relatório com proveniência e exportação revisável. Sem recomendação clínica.

### P2-08 — Telemetria local de recomendação

Eventos sem texto sensível, regras explicáveis, catálogo, baseline e painel de correção/limpeza.

### P2-09 — Personalização por modelo

Somente após volume mínimo: benchmark LiteRT/ONNX, avaliação offline, shadow mode, ativação opt-in e rollback.

### P2-10 — Dispositivos reais e revisão regulatória

Telefone e relógio físicos, bateria, permissões, áudio, perda de conexão, exportação para médico e revisão do enquadramento antes de qualquer alegação clínica.

## 21. Gates obrigatórios

1. Não iniciar saúde antes de sync e exclusão estarem testados.
2. Não iniciar ML antes de regras, eventos e baseline estáveis.
3. Não usar dados cardíacos no ranking antes de revisão de risco específica.
4. Não testar no banco desktop real sem backup e prévia.
5. Não testar no telefone físico antes do emulador passar.
6. Não publicar sem revisão de permissões, Data Safety, privacidade e enquadramento regulatório.
7. Não enviar dados sensíveis para IA em nuvem por padrão.
8. Não marcar fase concluída sem evidência e atualização percentual.

## 22. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Alertas virarem pressão | cooldown, silêncio, limites, opt-in e feedback rápido |
| Relógio não ser Wear OS | descoberta e fallback por espelhamento |
| Android matar trabalho de fundo | WorkManager, materialização antecipada e testes Doze |
| Duplicação no sync | UUID, hash, sequência e idempotência |
| Conflito entre dispositivos | revisão explícita e tombstones |
| Áudio sair no local errado | capacidades reais, teste, fallback e escopo só da Agenda |
| IA aprender padrão ruim | baseline, shadow mode, correção, limite e rollback |
| Dado cardíaco gerar falsa inferência | separado de recomendação e sem alegação clínica |
| Exposição de uso de substância | cifragem, logs sanitizados, exportação explícita |
| Bateria excessiva | agregados, sync oportunista e sem sensor contínuo inicial |
| Dependência de fornecedor | interfaces de runtime/provedor e formatos versionados |
| Documentação ficar obsoleta | mapa de manutenção e DoD documental |

## 23. Critério de sucesso do Projeto 2

O projeto será considerado operacional quando, em dispositivo real e sem depender do notebook:

1. um alerta previamente sincronizado chegar ao telefone e relógio;
2. `Concluir` e `Adiar` convergirem sem duplicação;
3. `Vou sair` abrir e executar um protocolo offline;
4. uma captura móvel sincronizar posteriormente;
5. rota de áudio e fallback respeitarem configuração da Agenda;
6. permissões de saúde puderem ser negadas ou revogadas sem quebrar o produto;
7. um relatório de período puder ser revisado e exportado sem inferência clínica;
8. personalização puder ser desligada e revertida às regras padrão.

## 24. Próxima ação

Concluir `P2-01` pareando `Agenda_Phone_API_34` e `Agenda_Wear_API_34` pelo assistente Wear do Android Studio. Não conectar o telefone físico, não tocar no banco pessoal e não implementar saúde ou IA. Após a evidência de pareamento, iniciar `P2-02` pelo modelo offline e dados exclusivamente fictícios.
