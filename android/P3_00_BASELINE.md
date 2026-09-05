# P3-00 - Baseline Android

Coleta em 05/09/2026 no Moto Edge 60 conectado por ADB Wi-Fi, aplicacao pessoal `0.1.0`, sem limpar dados, emitir alerta ou alterar pareamento.

## Medidas iniciais

| Medida | Resultado | Metodo |
|---|---:|---|
| inicializacao, mediana de 5 execucoes | 337 ms | `am force-stop` + `am start -W` |
| inicializacao, amostras | 602, 332, 326, 393, 337 ms | `TotalTime` |
| memoria em repouso | 122001 KiB PSS | `dumpsys meminfo` apos a quinta abertura |
| memoria residente | 228137 KiB RSS | mesma amostra |
| bateria no inicio | 77% | `dumpsys battery` |
| temperatura no inicio | 32,0 C | decimos de grau reportados pelo Android |
| tela | 1220 x 2712, 450 dpi | `wm size` e `wm density` |

Os valores sao referencia de regressao, nao metas absolutas. Inicializacao nao deve piorar mais de 25% na mesma classe de ensaio sem justificativa. Memoria deve ser comparada depois de tres ciclos equivalentes e nao pode apresentar crescimento monotonicamente nao recuperado.

## Bateria

A leitura instantanea nao atribui consumo a Agenda. O gate de bateria exige janela minima de 8 horas, bateria sem carga, baseline de repouso comparavel, registro de tempo em foreground/background, alertas emitidos e variacao termica. Nenhuma fase usa uma unica porcentagem como prova de eficiencia.

## Baseline de acessibilidade

O app atual possui semantica de estado para tarefas e descricoes nos principais botoes de icone. Lacunas a testar em A3-01: fonte 200%, TalkBack, ordem de foco, landscape, 360 dp, 600 dp, tema escuro e retorno previsivel para `Hoje`.

## Comandos reproduziveis

```bash
adb shell am force-stop com.pessoal.agenda.mobile
adb shell am start -W -n com.pessoal.agenda.mobile/.MainActivity
adb shell dumpsys meminfo com.pessoal.agenda.mobile
adb shell dumpsys battery
adb shell wm size
adb shell wm density
```
