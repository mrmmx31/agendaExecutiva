# Baseline de Uso e Interface

Este documento registra a referência da seção 6 da [SPEC.md](SPEC.md). A execução visual detalhada usa [UI_VALIDATION.md](UI_VALIDATION.md).

## 1. Limitação histórica

Não houve coleta anterior às mudanças das Fases 1 a 5. Portanto, não existe um valor válido de “antes” para comparação. Tempos ou quantidades retroativos não serão estimados a partir de memória, código ou testes automatizados.

A referência abaixo descreve a versão atual e será o ponto inicial para medições futuras.

## 2. Referência técnica atual

Execução iniciada em `2026-08-30 03:09` no fuso `America/Manaus`, com dados e preferências isolados.

| Campo | Valor |
|---|---|
| Sistema | Debian GNU/Linux 13 (trixie) |
| Ambiente gráfico | KDE Plasma, KWin 6.3.6, Wayland |
| Monitor | 1920x1080 físico; geometria lógica 1829x1029 |
| Escala do sistema | 105% |
| Java do projeto | OpenJDK 21.0.11 |
| JavaFX | 21.0.6 |
| Perfil | `manual-ui-validation` |
| Dados pessoais acessados | Não |

O Dashboard vazio abriu em `1260x820`, dentro da área útil, sem janela secundária automática. A aplicação foi encerrada depois da coleta e nenhum processo permaneceu ativo.

| Fluxo | Amostra atual | Resultado | Estado |
|---|---:|---|---|
| Tempo até primeira ação de foco | 1 humana guiada | 88 segundos | Referência inicial registrada |
| Captura rápida | 1 humana guiada | 2 ações além da digitação; 1 tentativa de salvar | Referência inicial registrada |
| Retomada de interrupção | 1 humana guiada | 1 ação; 1 tentativa | Referência inicial registrada |
| Janelas fora da área útil | 5 casos geométricos + 8 contratos reais de janela | 0 falhas; perfil JavaFX completo com 177/177 testes aprovados | Referência técnica aprovada |

Os casos geométricos cobrem janela menor, janela maior que 1280x720, owner parcialmente fora da tela, mínimos e timer compacto fora dos limites. Os contratos reais cobrem owner, modalidade, CSS, alternância repetida de tema, posição preservada, geometria da principal e revalidação da maximização.

Essa referência técnica não substitui a matriz manual completa das doze janelas. A amostra humana inicial foi guiada e serve como ponto de partida, não como conclusão estatística.

## 3. Definições de medição

As unidades devem permanecer estáveis:

- **Foco:** segundos desde a primeira tela operacional visível até `Iniciar/Continuar` ou `Abrir` concluir com sucesso. Registrar somente a primeira ação de foco da sessão.
- **Captura:** ações deliberadas além da digitação, incluindo abrir a captura e confirmar o salvamento. Registrar também, em coluna separada, tentativas de salvar; a métrica local armazena tentativas, não a navegação de abertura.
- **Retomada:** ações deliberadas desde a pista visível no Dashboard até o timer abrir e a pista ser removida. Registrar tentativas separadamente quando houver falha.
- **Janelas:** quantidade de secundárias que ultrapassam a área útil ou alteram posição, tamanho restaurado ou maximização da principal.

Tempo de leitura, hesitação e correção fazem parte da medição humana e não devem ser removidos.

## 4. Rodada humana

Usar um perfil isolado ou o perfil pessoal somente se houver consentimento explícito. A primeira amostra fecha o baseline inicial; até quatro amostras adicionais em dias/sessões normais são recomendadas para estabilizar a mediana, sem tentar correr para melhorar o número.

### 4.1 Foco

| Amostra | Data/hora | Segundos | Origem do foco | Observação |
|---:|---|---:|---|---|
| 1 | 2026-08-30 03:20 | 88 | Retomada pendente | Rodada guiada; inclui o tempo de leitura das instruções |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| Mediana | | | | |

Meta inicial: mediana menor que 30 segundos.

### 4.2 Captura

| Amostra | Data/hora | Ações além da digitação | Tentativas de salvar | Observação |
|---:|---|---:|---:|---|
| 1 | 2026-08-30 03:21 | 2 | 1 | Abrir captura e confirmar com `Enter` |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| Mediana | | | | |

Meta inicial: no máximo duas ações além da digitação.

### 4.3 Retomada

| Amostra | Data/hora | Ações | Tentativas | Observação |
|---:|---|---:|---:|---|
| 1 | 2026-08-30 03:20 | 1 | 1 | `Retomar`; timer abriu e a pista foi removida |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| Mediana | | | | |

Meta inicial: no máximo duas ações.

## 5. Critério de fechamento

O P0-03 pode ser concluído quando:

- as três tabelas humanas tiverem ao menos uma amostra válida; amostras posteriores refinam a mediana;
- a referência de janelas continuar sem falhas ou qualquer regressão estiver registrada;
- ambiente, versão e data forem preservados;
- resultados forem usados como observação, não como pontuação ou cobrança.

**Fechamento em 2026-08-30:** os quatro fluxos possuem referência observável. O foco ficou acima da meta nesta primeira rodada, mas a amostra inclui preparação e leitura de instruções e não permite atribuir o valor ao produto. Captura e retomada atenderam às metas de ações. A pista foi removida, a captura foi persistida e a aplicação isolada foi encerrada após a leitura das métricas.
