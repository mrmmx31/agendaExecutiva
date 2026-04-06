package com.pessoal.agenda.model;

import java.time.LocalDate;

/**
 * Entidade: Ideia / Projeto Pessoal Científico.
 *
 * Campos avançados para gestão de pipeline de P&D:
 *   - ideaType      : tipo científico/técnico da ideia
 *   - priority      : criticidade no pipeline
 *   - impactLevel   : impacto esperado (científico/prático)
 *   - feasibility   : viabilidade técnica 1-5
 *   - estimatedHours: esforço estimado em horas-pessoa
 *   - startDate     : início planejado / data de concepção
 *   - targetDate    : prazo-alvo para conclusão/validação
 *   - methodology   : metodologia adotada
 *   - nextActions   : próximas ações concretas (lista de texto)
 *   - keywords      : palavras-chave separadas por vírgula
 *   - referencesText: referências bibliográficas / links
 */
public record ProjectIdea(
        long id,
        String title,
        String description,
        String status,
        String category,
        String priority,
        String ideaType,
        String impactLevel,
        int feasibility,
        int estimatedHours,
        LocalDate startDate,
        LocalDate targetDate,
        String methodology,
        String nextActions,
        String keywords,
        String referencesText
) {
    // ── Construtores de compatibilidade ──────────────────────────────────────

    /** Compatibilidade retroativa: apenas campos básicos. */
    public ProjectIdea(long id, String title, String description, String status) {
        this(id, title, description, status, "Geral",
                "NORMAL", "GERAL", "MEDIO", 3, 0,
                null, null, null, null, null, null);
    }

    /** Compatibilidade com categoria. */
    public ProjectIdea(long id, String title, String description, String status, String category) {
        this(id, title, description, status, category,
                "NORMAL", "GERAL", "MEDIO", 3, 0,
                null, null, null, null, null, null);
    }

    // ── Helpers de domínio ────────────────────────────────────────────────────

    public String priorityLabel() {
        return switch (priority != null ? priority : "NORMAL") {
            case "CRITICA" -> "🔴 Crítica";
            case "ALTA"    -> "🟠 Alta";
            case "BAIXA"   -> "🟢 Baixa";
            default        -> "🔵 Normal";
        };
    }

    public String impactLabel() {
        return switch (impactLevel != null ? impactLevel : "MEDIO") {
            case "REVOLUCIONARIO" -> "⚡ Revolucionário";
            case "ALTO"           -> "▲ Alto";
            case "BAIXO"          -> "▽ Baixo";
            default               -> "◈ Médio";
        };
    }

    public String typeLabel() {
        return switch (ideaType != null ? ideaType : "GERAL") {
            case "PESQUISA"    -> "🔬 Pesquisa";
            case "ENGENHARIA"  -> "⚙ Engenharia";
            case "HIPOTESE"    -> "💡 Hipótese";
            case "EXPERIMENTO" -> "🧪 Experimento";
            case "SOFTWARE"    -> "💻 Software";
            case "METODOLOGIA" -> "📐 Metodologia";
            case "INOVACAO"    -> "🚀 Inovação";
            default            -> "📋 Geral";
        };
    }

    public String statusLabel() {
        return switch (status != null ? status : "nova") {
            case "em_validacao"  -> "Em Validação";
            case "prototipagem"  -> "Prototipagem";
            case "em_execucao"   -> "Em Execução";
            case "em execução"   -> "Em Execução";
            case "pausada"       -> "Pausada";
            case "concluida"     -> "Concluída";
            case "concluída"     -> "Concluída";
            case "abandonada"    -> "Abandonada";
            default              -> "Nova";
        };
    }

    public String feasibilityDots() {
        int f = Math.max(1, Math.min(5, feasibility));
        return "●".repeat(f) + "○".repeat(5 - f);
    }

    public boolean isActive() {
        return status != null && !status.equals("concluida") && !status.equals("concluída")
                && !status.equals("abandonada");
    }
}
