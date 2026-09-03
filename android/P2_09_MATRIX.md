# Matriz P2-09 - Personalização por modelo

Data: 2026-09-03. Escopo: dados sintéticos e emulador
`Agenda_Phone_API_34`. Nenhum banco pessoal, conta, sensor ou telefone físico foi
usado.

## Resultado

| Gate | Evidência | Resultado |
|---|---|---|
| fronteira de dados | schemas fechados, inventário, fixture e busca por chaves proibidas | aprovado |
| saúde isolada | nenhuma feature, import ou payload de saúde no modelo | aprovado |
| mínimo global | treino recusa menos de 60; avaliação exige 75 para split 80/20 | aprovado |
| mínimo contextual | shadow e ativação exigem 12 amostras do contexto explícito | aprovado |
| avaliação temporal | 20% mais recentes ficam fora do treino | aprovado |
| comparação justa | modelo e `rules-v1` usam o mesmo conjunto reservado | aprovado |
| promoção | exige 30 exemplos de avaliação e ganho top-1 absoluto de 5 p.p. | aprovado |
| shadow | retorna a mesma instância da decisão `rules-v1` e só agrega concordância | aprovado |
| integridade | JSON canônico, SHA-256 e contrato/runtime/features validados na leitura | aprovado |
| troca e rollback | promoção transacional; corrupção, opt-out e comando explícito restauram regras | aprovado |
| limpeza | histórico, artefatos, métricas persistidas e acumulador em memória são apagados | aprovado |
| limites de domínio | modelo somente reordena presets já autorizados; não executa ação | aprovado |
| Wear | ordem ativa chega ao relógio sem adicionar duração ao perfil | aprovado em emulador |
| interface | coleta, treino, ativação confirmada, métricas e rollback separados | aprovado |
| temas e largura | tela real em 360 dp clara/escura, sem corte ou texto residual | aprovado |
| thread principal | treino, shadow e benchmark usam `Dispatchers.Default` | aprovado |
| rede | sem SDK, download, analytics ou sync do modelo | aprovado |
| bateria | nenhum worker, sensor, wake lock ou inferência periódica novo | aprovado estruturalmente |
| bateria física | descarga/temperatura em uso prolongado | reservado ao P2-10 |

## Medidas

O teste puro `modelArtifactTrainingAndInferenceStayWithinLocalBudget` registrou:

- treino de 2.000 amostras: 298 ms em debug e 602 ms em release, limite 2.000 ms;
- 10.000 inferências: 54 ms em debug e 98 ms em release, limite 1.000 ms;
- artefato JSON: 4.431 bytes, limite 65.536 bytes;
- pesos: 130 `Double`, aproximadamente 1.040 bytes sem overhead da VM;
- APK debug: 20.630.480 bytes.

Os números são do host e servem para detectar regressão. A tela mede novamente
treino, inferência, artefato e pesos do modelo real no aparelho. Não se infere
consumo físico de bateria a partir deles.

## Runtimes

Detalhes reproduzíveis ficam em [`P2_09_RUNTIME_BENCHMARK.md`](P2_09_RUNTIME_BENCHMARK.md).
O runtime Kotlin foi mantido por ser suficiente, auditável e sem dependência ou
telemetria. LiteRT Play Services exige revisão por sua telemetria técnica
declarada. O AAR completo do ONNX 1.29.0 é maior que o APK atual e não se
justifica para cinco classes lineares. Ambos continuam atrás da fronteira de
adapter para um modelo futuro que demonstre necessidade.

## Testes executados

- contratos JSON: `jq empty`, Java Maven e Kotlin;
- oito testes do modelo/evaluador e seis do shadow;
- três testes do motor ativo, dois da integração de presets Wear e sete do store
  de artefatos;
- cinco migrações Room instrumentadas até v10;
- 16 testes Compose no Pixel virtual, incluindo os dois fluxos do modelo;
- gates completos Android/Wear e Maven registrados na evidência da spec.

## Decisão

P2-09 está aprovada no escopo local/emulador. A ativação continua opt-in, um
modelo fraco não pode ser ativado e `rules-v1` é sempre utilizável. P2-10 deve
validar bateria, áudio, conexão, permissões e experiência em dispositivos reais
antes de qualquer distribuição.
