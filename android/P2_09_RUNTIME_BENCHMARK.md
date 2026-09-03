# Benchmark de runtime P2-09

Medição em 2026-09-03. Downloads foram feitos em `/tmp` apenas para inspeção e
não entraram no Gradle, APK ou repositório.

| Candidato | Pacote medido | Compactado | Descompactado | Rede/telemetria | Decisão v1 |
|---|---|---:|---:|---|---|
| Kotlin auditável | fonte já no app | sem runtime adicional | pesos medidos no teste | nenhuma | escolhido |
| LiteRT Play Services | `play-services-tflite-java:16.5.0` | 2.224.206 B | 8.258.866 B | runtime pode baixar atualizações e envia métricas técnicas declaradas | adapter futuro após consentimento/revisão |
| ONNX Runtime | `onnxruntime-android:1.29.0` | 51.897.836 B | 133.353.660 B | runtime embutido; sem requisito de serviço no fluxo avaliado | adapter futuro se portabilidade justificar custo |

O APK debug atual mede 20.630.480 bytes. Tamanhos de AAR não equivalem ao delta
final do APK, pois empacotamento, ABIs e shrinker alteram o resultado; servem para
eliminar a inclusão prematura do runtime ONNX completo. O LiteRT reduz runtime
embutido, mas sua telemetria de API conflita com o limite local atual.

O teste `modelArtifactTrainingAndInferenceStayWithinLocalBudget` mede o candidato
Kotlin com 2.000 amostras e 10.000 inferências. Gates: treino abaixo de 2 s,
inferência total abaixo de 1 s e artefato abaixo de 64 KiB. Os valores efetivos
ficam no XML/saída da suíte e a tela mostra medida do aparelho para o artefato
real. Não há worker de treino ou inferência periódica.
