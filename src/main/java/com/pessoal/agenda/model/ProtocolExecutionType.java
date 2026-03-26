package com.pessoal.agenda.model;

/**
 * Tipos de ciclo de vida de um Protocolo Operacional.
 *
 * Inspirado na realidade de um cientista:
 *   – Atividades de rotina de laboratório (RECORRENTE)
 *   – Protocolos de experimento com iterações (EXPERIMENTO)
 *   – Checklists de segurança obrigatórios (SEGURANCA)
 *   – Calibração periódica de equipamentos (CALIBRACAO)
 *   – Manutenção programada ou corretiva (MANUTENCAO)
 *   – Procedimentos de emergência (EMERGENCIA)
 *   – Uso único (UNICO)
 *   – Vinculado ao ciclo de vida de uma Tarefa (TAREFA)
 *   – Ativo até decisão explícita de encerramento (INDETERMINADO)
 */
public enum ProtocolExecutionType {

    UNICO(
        "Único",
        "💊",
        "Executado uma única vez e arquivado. Ideal para setup inicial ou onboarding."
    ),
    RECORRENTE(
        "Recorrente",
        "🔁",
        "Reinicia automaticamente após cada conclusão. Mantém histórico completo de execuções. " +
        "Ideal para procedimentos diários de laboratório."
    ),
    TAREFA(
        "Vinculado a Tarefa",
        "📌",
        "Ativo enquanto a tarefa vinculada estiver em aberto. " +
        "Ao concluir a tarefa, a execução é encerrada junto."
    ),
    INDETERMINADO(
        "Indeterminado",
        "♾",
        "Permanece ativo indefinidamente até decisão explícita de encerramento. " +
        "Ideal para processos contínuos em andamento."
    ),
    EXPERIMENTO(
        "Experimento",
        "🔬",
        "Múltiplas iterações rastreadas de um mesmo protocolo experimental. " +
        "Cada execução é numerada (Iteração 1, 2, 3…). Histórico completo de cada rodada."
    ),
    SEGURANCA(
        "Segurança",
        "🦺",
        "Checklist obrigatório de segurança antes do início de uma atividade. " +
        "Marca quais itens críticos foram verificados e quando."
    ),
    CALIBRACAO(
        "Calibração",
        "⚖",
        "Protocolo periódico de calibração de equipamentos e instrumentos. " +
        "Registra data/hora e operador de cada calibração."
    ),
    MANUTENCAO(
        "Manutenção",
        "🔧",
        "Procedimento de manutenção programada ou corretiva de equipamentos. " +
        "Rastreia intervenções e historial de falhas."
    ),
    EMERGENCIA(
        "Emergência",
        "🚨",
        "Procedimento de emergência de consulta rápida. " +
        "Passos críticos destacados. Histórico de acionamentos."
    );

    private final String label;
    private final String icon;
    private final String description;

    ProtocolExecutionType(String label, String icon, String description) {
        this.label       = label;
        this.icon        = icon;
        this.description = description;
    }

    public String label()       { return label; }
    public String icon()        { return icon; }
    public String description() { return description; }

    /** True se este tipo suporta reinício automático após conclusão. */
    public boolean supportsRestart() {
        return this == RECORRENTE || this == EXPERIMENTO
                || this == SEGURANCA || this == CALIBRACAO || this == MANUTENCAO;
    }

    @Override public String toString() { return label; }
}

