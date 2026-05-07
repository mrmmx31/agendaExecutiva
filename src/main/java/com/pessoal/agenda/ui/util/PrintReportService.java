package com.pessoal.agenda.ui.util;

import com.pessoal.agenda.model.*;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gera relatórios HTML auto-contidos para impressão.
 * Suporta modo Colorido e Monocromático (alternável antes de imprimir).
 *
 * Todos os métodos retornam uma String HTML completa que pode ser carregada
 * em um WebView e impressa via window.print().
 */
public final class PrintReportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final NumberFormat BRL =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));

    private PrintReportService() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. Relatório de Agenda e Prioridades
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gera relatório de tarefas para a Agenda.
     *
     * @param tasks      lista de tarefas a imprimir
     * @param periodDesc descrição do período (ex: "Abril de 2026", "10/04/2026")
     */
    public static String generateAgendaReport(List<Task> tasks, String periodDesc) {
        long total    = tasks.size();
        long done     = tasks.stream().filter(t -> t.done() || (t.status() != null && t.status() == TaskStatus.CONCLUIDA)).count();
        long overdue  = tasks.stream().filter(Task::isOverdue).count();
        long pending  = tasks.stream().filter(t -> !t.done() && !t.isOverdue()).count();

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("<div class='report-header'>");
        sb.append("<h1>&#128203; Agenda e Prioridades</h1>");
        sb.append("<div class='meta'>Per&iacute;odo: <strong>").append(esc(periodDesc)).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; Gerado em: ").append(LocalDate.now().format(DATE_FMT));
        sb.append(" &nbsp;&mdash;&nbsp; Total: <strong>").append(total).append("</strong> tarefa(s)</div>");
        sb.append("</div>");

        // KPIs
        sb.append("<div class='kpi-row'>");
        sb.append(kpiBox("TOTAL", String.valueOf(total)));
        sb.append(kpiBox("CONCLU&Iacute;DAS", String.valueOf(done)));
        sb.append(kpiBox("PENDENTES", String.valueOf(pending)));
        sb.append(kpiBox("ATRASADAS", String.valueOf(overdue)));
        sb.append("</div>");

        if (tasks.isEmpty()) {
            sb.append("<p><em>Nenhuma tarefa no per&iacute;odo selecionado.</em></p>");
            return wrapHtml("Agenda e Prioridades — " + periodDesc, sb.toString());
        }

        // Tabela principal
        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style='width:34%'>T&iacute;tulo</th>");
        sb.append("<th style='width:10%'>Data</th>");
        sb.append("<th style='width:10%'>Hora</th>");
        sb.append("<th style='width:11%'>Status</th>");
        sb.append("<th style='width:11%'>Prioridade</th>");
        sb.append("<th style='width:10%'>Categoria</th>");
        sb.append("<th style='width:14%'>Recorr&ecirc;ncia</th>");
        sb.append("</tr></thead><tbody>");

        for (Task t : tasks) {
            boolean isOverdue = t.isOverdue();
            boolean isDone    = t.done() || (t.status() != null && t.status() == TaskStatus.CONCLUIDA);
            String rowClass   = isDone ? "row-done" : isOverdue ? "row-overdue" : "";

            String statusTxt  = statusText(t.status(), t.done());
            String statusBadge= isDone ? "badge-done" : isOverdue ? "badge-overdue" : "badge-pending";
            String prioTxt    = priorityText(t.priority());
            String prioBadge  = priorityBadgeClass(t.priority());

            String timeStr = "";
            if (t.startTime() != null && !t.startTime().isBlank()) {
                timeStr = esc(t.startTime());
                if (t.endTime() != null && !t.endTime().isBlank()) timeStr += " &rarr; " + esc(t.endTime());
            }

            String dateStr = t.dueDate() != null ? t.dueDate().format(DATE_FMT) : "—";
            String recStr  = recurrenceText(t);

            sb.append("<tr class='").append(rowClass).append("'>");
            sb.append("<td><strong>").append(esc(t.title())).append("</strong>");
            if (t.notes() != null && !t.notes().isBlank()) {
                sb.append("<br><small style='color:#666'>").append(esc(trunc(t.notes(), 120))).append("</small>");
            }
            sb.append("</td>");
            sb.append("<td>").append(dateStr).append("</td>");
            sb.append("<td>").append(timeStr.isEmpty() ? "—" : timeStr).append("</td>");
            sb.append("<td><span class='").append(statusBadge).append("'>").append(statusTxt).append("</span></td>");
            sb.append("<td><span class='").append(prioBadge).append("'>").append(prioTxt).append("</span></td>");
            sb.append("<td>").append(esc(t.category() != null ? t.category() : "Geral")).append("</td>");
            sb.append("<td>").append(recStr).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");

        return wrapHtml("Agenda e Prioridades — " + periodDesc, sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. Relatório Financeiro e Pendências
    // ═══════════════════════════════════════════════════════════════════════════

    public static String generateFinanceReport(List<FinanceEntry> entries) {
        double totalReceitas = entries.stream()
                .filter(e -> "recebimento".equalsIgnoreCase(e.entryType()))
                .mapToDouble(FinanceEntry::amount).sum();
        double totalDespesas = entries.stream()
                .filter(e -> !"recebimento".equalsIgnoreCase(e.entryType()))
                .mapToDouble(FinanceEntry::amount).sum();
        double saldo = entries.stream()
                .filter(e -> "recebimento".equalsIgnoreCase(e.entryType()) && e.paid())
                .mapToDouble(FinanceEntry::amount).sum()
                - entries.stream()
                .filter(e -> !"recebimento".equalsIgnoreCase(e.entryType()) && e.paid())
                .mapToDouble(FinanceEntry::amount).sum();
        long vencidos = entries.stream().filter(FinanceEntry::isOverdue).count();
        long aPagar   = entries.stream()
                .filter(e -> !"recebimento".equalsIgnoreCase(e.entryType()) && !e.paid() && !e.isOverdue())
                .count();
        long pagos    = entries.stream().filter(FinanceEntry::paid).count();

        StringBuilder sb = new StringBuilder();

        sb.append("<div class='report-header'>");
        sb.append("<h1>&#128176; Financeiro e Pend&ecirc;ncias</h1>");
        sb.append("<div class='meta'>Gerado em: <strong>").append(LocalDate.now().format(DATE_FMT)).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; Total de lan&ccedil;amentos: <strong>").append(entries.size()).append("</strong></div>");
        sb.append("</div>");

        // KPIs
        sb.append("<div class='kpi-row'>");
        sb.append(kpiBox("RECEITAS", BRL.format(totalReceitas)));
        sb.append(kpiBox("DESPESAS", BRL.format(totalDespesas)));
        sb.append(kpiBox("SALDO L&Iacute;QUIDO", BRL.format(saldo)));
        sb.append(kpiBox("VENCIDOS", String.valueOf(vencidos)));
        sb.append(kpiBox("A PAGAR", String.valueOf(aPagar)));
        sb.append(kpiBox("PAGOS/RECEBIDOS", String.valueOf(pagos)));
        sb.append("</div>");

        if (entries.isEmpty()) {
            sb.append("<p><em>Nenhum lan&ccedil;amento encontrado.</em></p>");
            return wrapHtml("Financeiro e Pendências", sb.toString());
        }

        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style='width:10%'>Tipo</th>");
        sb.append("<th style='width:38%'>Descri&ccedil;&atilde;o</th>");
        sb.append("<th style='width:14%'>Valor</th>");
        sb.append("<th style='width:12%'>Vencimento</th>");
        sb.append("<th style='width:13%'>Status</th>");
        sb.append("<th style='width:13%'>Recorr&ecirc;ncia</th>");
        sb.append("</tr></thead><tbody>");

        for (FinanceEntry e : entries) {
            boolean overdue = e.isOverdue();
            boolean isReceipt = "recebimento".equalsIgnoreCase(e.entryType());
            String rowClass   = e.paid() ? "row-done" : overdue ? "row-overdue" : "";

            String statusTxt   = e.paid() ? (isReceipt ? "Recebido" : "Pago")
                               : overdue  ? "Vencido"
                               : isReceipt ? "A Receber" : "A Pagar";
            String statusBadge = e.paid()  ? "badge-done"
                               : overdue   ? "badge-overdue" : "badge-pending";
            String typeBadge   = typeFinanceBadge(e.entryType());

            sb.append("<tr class='").append(rowClass).append("'>");
            sb.append("<td><span class='").append(typeBadge).append("'>")
              .append(esc(e.entryType().toUpperCase())).append("</span></td>");
            sb.append("<td>").append(esc(e.description()));
            if (e.paid()) sb.append(" <em style='color:#888'>(quitado)</em>");
            sb.append("</td>");
            sb.append("<td><strong>").append(BRL.format(e.amount())).append("</strong></td>");
            sb.append("<td>").append(e.dueDate() != null ? e.dueDate().format(DATE_FMT) : "—").append("</td>");
            sb.append("<td><span class='").append(statusBadge).append("'>").append(statusTxt).append("</span></td>");
            sb.append("<td>—</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");

        return wrapHtml("Financeiro e Pendências", sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. Relatório de Ideias e Projetos
    // ═══════════════════════════════════════════════════════════════════════════

    public static String generateIdeasReport(List<ProjectIdea> ideas) {
        long total     = ideas.size();
        long active    = ideas.stream().filter(ProjectIdea::isActive).count();
        long running   = ideas.stream()
                .filter(i -> "em_execucao".equals(i.status()) || "em execução".equals(i.status())).count();
        long done      = ideas.stream()
                .filter(i -> "concluida".equals(i.status()) || "concluída".equals(i.status())).count();
        long highImpact= ideas.stream()
                .filter(i -> "ALTO".equals(i.impactLevel()) || "REVOLUCIONARIO".equals(i.impactLevel())).count();

        StringBuilder sb = new StringBuilder();

        sb.append("<div class='report-header'>");
        sb.append("<h1>&#128161; Ideias e Projetos</h1>");
        sb.append("<div class='meta'>Gerado em: <strong>").append(LocalDate.now().format(DATE_FMT)).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; Total: <strong>").append(total).append("</strong> ideia(s)</div>");
        sb.append("</div>");

        sb.append("<div class='kpi-row'>");
        sb.append(kpiBox("TOTAL", String.valueOf(total)));
        sb.append(kpiBox("ATIVAS", String.valueOf(active)));
        sb.append(kpiBox("EM EXECU&Ccedil;&Atilde;O", String.valueOf(running)));
        sb.append(kpiBox("CONCLU&Iacute;DAS", String.valueOf(done)));
        sb.append(kpiBox("ALTO IMPACTO", String.valueOf(highImpact)));
        sb.append("</div>");

        if (ideas.isEmpty()) {
            sb.append("<p><em>Nenhuma ideia encontrada com os filtros aplicados.</em></p>");
            return wrapHtml("Ideias e Projetos", sb.toString());
        }

        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style='width:28%'>T&iacute;tulo</th>");
        sb.append("<th style='width:11%'>Status</th>");
        sb.append("<th style='width:9%'>Prioridade</th>");
        sb.append("<th style='width:9%'>Tipo</th>");
        sb.append("<th style='width:9%'>Impacto</th>");
        sb.append("<th style='width:7%'>Viab.</th>");
        sb.append("<th style='width:7%'>Horas Est.</th>");
        sb.append("<th style='width:10%'>Prazo</th>");
        sb.append("<th style='width:10%'>Categoria</th>");
        sb.append("</tr></thead><tbody>");

        for (ProjectIdea i : ideas) {
            String statusBadge = ideaStatusBadge(i.status());
            String rowClass    = "concluida".equals(i.status()) || "concluída".equals(i.status()) ? "row-done"
                               : "abandonada".equals(i.status()) ? "row-overdue" : "";

            sb.append("<tr class='").append(rowClass).append("'>");
            sb.append("<td><strong>").append(esc(i.title())).append("</strong>");
            if (i.description() != null && !i.description().isBlank()) {
                sb.append("<br><small style='color:#666'>").append(esc(trunc(i.description(), 100))).append("</small>");
            }
            if (i.keywords() != null && !i.keywords().isBlank()) {
                sb.append("<br><small style='color:#999; font-style:italic'>").append(esc(i.keywords())).append("</small>");
            }
            sb.append("</td>");
            sb.append("<td><span class='").append(statusBadge).append("'>").append(esc(i.statusLabel())).append("</span></td>");
            sb.append("<td><span class='").append(priorityBadgeClass(i.priority())).append("'>")
              .append(stripEmoji(i.priorityLabel())).append("</span></td>");
            sb.append("<td>").append(esc(stripEmoji(i.typeLabel()))).append("</td>");
            sb.append("<td>").append(esc(stripEmoji(i.impactLabel()))).append("</td>");
            sb.append("<td>").append(esc(i.feasibilityDots())).append("</td>");
            sb.append("<td>").append(i.estimatedHours() > 0 ? i.estimatedHours() + "h" : "—").append("</td>");
            sb.append("<td>");
            if (i.targetDate() != null) {
                sb.append(i.targetDate().format(DATE_FMT));
                long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), i.targetDate());
                if (days < 0) sb.append("<br><small style='color:#c62828'>").append(Math.abs(days)).append("d atraso</small>");
                else if (days <= 7) sb.append("<br><small style='color:#e65100'>").append(days).append("d restantes</small>");
            } else {
                sb.append("—");
            }
            sb.append("</td>");
            sb.append("<td>").append(esc(i.category() != null ? i.category() : "Geral")).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");

        return wrapHtml("Ideias e Projetos", sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. Relatório de Checklist do Projeto (IdeaChecklistItem)
    //    Ideal para impressão e marcação com lápis/caneta no papel
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gera relatório de checklist de um Projeto/Ideia para impressão em papel.
     *
     * @param idea  projeto de referência (título, status, categoria, etc.)
     * @param items itens do checklist
     */
    public static String generateProjectChecklistReport(
            ProjectIdea idea,
            List<IdeaChecklistItem> items) {

        int    total   = items.size();
        long   done    = items.stream().filter(IdeaChecklistItem::done).count();
        long   pending = total - done;
        double pct     = total == 0 ? 0.0 : (double) done / total;
        int    pctInt  = (int) Math.round(pct * 100);

        StringBuilder sb = new StringBuilder();

        // ── Cabeçalho ──────────────────────────────────────────────────────
        sb.append("<div class='report-header'>");
        sb.append("<h1>&#9745; Checklist do Projeto</h1>");
        sb.append("<div class='meta'>");
        sb.append("<strong>").append(esc(idea.title())).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; ").append(esc(stripEmoji(idea.typeLabel())));
        sb.append(" &nbsp;&mdash;&nbsp; Status: <strong>").append(esc(idea.statusLabel())).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; Categoria: ").append(esc(idea.category() != null ? idea.category() : "Geral"));
        if (idea.targetDate() != null)
            sb.append(" &nbsp;&mdash;&nbsp; Prazo: ").append(idea.targetDate().format(DATE_FMT));
        sb.append(" &nbsp;&mdash;&nbsp; Gerado em: ").append(LocalDate.now().format(DATE_FMT));
        sb.append("</div>");
        sb.append("</div>");

        // ── KPIs ───────────────────────────────────────────────────────────
        sb.append("<div class='kpi-row'>");
        sb.append(kpiBox("TOTAL DE ITENS", String.valueOf(total)));
        sb.append(kpiBox("CONCLU&Iacute;DOS", String.valueOf(done)));
        sb.append(kpiBox("PENDENTES", String.valueOf(pending)));
        sb.append(kpiBox("PROGRESSO", pctInt + "%"));
        sb.append("</div>");

        // ── Barra de progresso visual ───────────────────────────────────────
        String barColor = pct == 1.0 ? "#1b5e20" : pct >= 0.7 ? "#2e7d32" : pct >= 0.4 ? "#f57f17" : "#c62828";
        sb.append("<div style='margin-bottom:14px;'>");
        sb.append("<div style='background:#dce8f5; border-radius:6px; height:13px; width:100%; overflow:hidden;'>");
        sb.append("<div style='background:").append(barColor).append("; width:").append(pctInt)
          .append("%; height:100%; border-radius:6px; -webkit-print-color-adjust:exact; print-color-adjust:exact;'></div>");
        sb.append("</div>");
        sb.append("<div style='font-size:9pt; color:#555; margin-top:3px;'>")
          .append(done).append(" de ").append(total).append(" itens conclu&iacute;dos");
        if (total > 0) sb.append("  (").append(pctInt).append("%)");
        sb.append("</div></div>");

        // ── Descrição do projeto ────────────────────────────────────────────
        if (idea.description() != null && !idea.description().isBlank()) {
            sb.append("<div style='margin-bottom:14px; font-size:10pt; color:#444;"
                    + " padding:8px 12px; border-left:3px solid #1565c0; background:#f4f8ff;"
                    + " -webkit-print-color-adjust:exact; print-color-adjust:exact;'>");
            sb.append("<strong>Descri&ccedil;&atilde;o:</strong> ")
              .append(esc(trunc(idea.description(), 500)));
            sb.append("</div>");
        }

        if (items.isEmpty()) {
            sb.append("<p style='font-style:italic; color:#888;'>Nenhum item no checklist deste projeto.</p>");
            return wrapHtml("Checklist — " + idea.title(), sb.toString());
        }

        // ── Itens agrupados por coluna Kanban ───────────────────────────────
        record KanbanCol(String key, String label, String color) {}
        List<KanbanCol> cols = List.of(
                new KanbanCol("backlog",      "Backlog",         "#546e7a"),
                new KanbanCol("em_andamento", "Em Andamento",    "#1565c0"),
                new KanbanCol("em_revisao",   "Em Revis&atilde;o", "#7b1fa2"),
                new KanbanCol("concluido",    "Conclu&iacute;do", "#2e7d32"));

        for (KanbanCol col : cols) {
            List<IdeaChecklistItem> colItems = items.stream()
                    .filter(it -> col.key().equals(it.kanbanColumn() != null ? it.kanbanColumn() : "backlog"))
                    .toList();
            if (colItems.isEmpty()) continue;

            sb.append("<div class='checklist-section'>");

            // cabeçalho colorido da coluna
            sb.append("<div style='background-color:").append(col.color())
              .append("; padding:7px 12px; margin:-12px -12px 10px -12px;"
                    + " -webkit-print-color-adjust:exact; print-color-adjust:exact;'>");
            sb.append("<strong style='font-size:12pt; color:white;'>").append(col.label()).append("</strong>");
            sb.append("<span style='color:rgba(255,255,255,0.8); font-size:10pt;'> &nbsp;(")
              .append(colItems.size()).append(" item(ns))</span>");
            sb.append("</div>");

            // linhas dos itens
            sb.append("<div class='steps-container'>");
            int num = 1;
            for (IdeaChecklistItem item : colItems) {
                sb.append("<div class='step-row'>");
                sb.append("<span class='step-order'>").append(num++).append(".</span>");
                // checkbox impresso: ☑ se concluído, ☐ se pendente
                sb.append("<span class='step-checkbox'>")
                  .append(item.done() ? "&#9745;" : "&#9744;").append("</span>");
                sb.append("<span class='step-text'");
                if (item.done())
                    sb.append(" style='text-decoration:line-through; color:#9eafc0;'");
                sb.append(">").append(esc(item.text())).append("</span>");
                sb.append("</div>");
            }
            sb.append("</div>");

            sb.append("</div>"); // checklist-section
        }

        // ── Rodapé para preenchimento manual ───────────────────────────────
        sb.append("<div style='margin-top:20px; padding:10px 12px; border:1.5px solid #333; font-size:9pt; color:#555;'>");
        sb.append("<strong>Informa&ccedil;&otilde;es de execu&ccedil;&atilde;o</strong><br/>");
        sb.append("<div style='margin-top:8px;'>");
        sb.append("Data: ___/___/__________ &nbsp;&nbsp;&nbsp; ");
        sb.append("Respons&aacute;vel: __________________________ &nbsp;&nbsp;&nbsp; ");
        sb.append("Assinatura: __________________________");
        sb.append("</div>");
        sb.append("<div style='margin-top:12px;'><strong>Observa&ccedil;&otilde;es:</strong><br/>");
        sb.append("<div style='border-bottom:1px solid #bbb; height:18px; margin-top:6px;'></div>");
        sb.append("<div style='border-bottom:1px solid #bbb; height:18px; margin-top:6px;'></div>");
        sb.append("<div style='border-bottom:1px solid #bbb; height:18px; margin-top:6px;'></div>");
        sb.append("</div></div>");

        return wrapHtml("Checklist — " + idea.title(), sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. Relatório de Checklist (Protocolos Operacionais)
    //    Ideal para impressão e marcação com lápis em campo/faculdade
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @param protocols lista de protocolos
     * @param stepsByProtocol mapa protocolId → lista de passos
     */
    public static String generateChecklistReport(
            List<Protocol> protocols,
            Map<Long, List<ProtocolStep>> stepsByProtocol) {

        StringBuilder sb = new StringBuilder();

        sb.append("<div class='report-header'>");
        sb.append("<h1>&#9989; Checklist de Protocolos Operacionais</h1>");
        sb.append("<div class='meta'>Gerado em: <strong>").append(LocalDate.now().format(DATE_FMT)).append("</strong>");
        sb.append(" &nbsp;&mdash;&nbsp; Total: <strong>").append(protocols.size()).append("</strong> protocolo(s)");
        sb.append(" &nbsp;&mdash;&nbsp; <em>Marque os passos conclu&iacute;dos com l&aacute;pis ou caneta</em></div>");
        sb.append("</div>");

        if (protocols.isEmpty()) {
            sb.append("<p><em>Nenhum protocolo encontrado.</em></p>");
            return wrapHtml("Checklist de Protocolos", sb.toString());
        }

        boolean first = true;
        for (Protocol p : protocols) {
            if (!first) {
                // soft page break hint between protocols (hard breaks only for sections with many steps)
                List<ProtocolStep> steps = stepsByProtocol.getOrDefault(p.id(), List.of());
                if (steps.size() > 8) sb.append("<div class='page-break'></div>");
            }
            first = false;

            List<ProtocolStep> steps = stepsByProtocol.getOrDefault(p.id(), List.of());

            sb.append("<div class='checklist-section'>");

            // ── Cabeçalho do protocolo ─────────────────────────────────────
            sb.append("<div class='section-header' style='padding:8px 12px; margin:-12px -12px 10px -12px;'>");
            sb.append("<strong style='font-size:13pt'>").append(esc(p.name())).append("</strong>");
            sb.append("</div>");

            // Metadados
            sb.append("<div class='checklist-meta'>");
            sb.append("<span class='badge-type-print'>").append(esc(p.executionType().icon())).append(" ")
              .append(esc(p.executionType().label())).append("</span>");
            sb.append(" &nbsp;&bull;&nbsp; Categoria: <strong>")
              .append(esc(p.category() != null ? p.category() : "Geral")).append("</strong>");
            if (p.hasValidity()) {
                sb.append(" &nbsp;&bull;&nbsp; Validade: <strong>").append(p.validityDays()).append(" dias</strong>");
            }
            if (p.hasLinkedTask() && p.linkedTaskTitle() != null) {
                sb.append(" &nbsp;&bull;&nbsp; Tarefa vinculada: <em>").append(esc(p.linkedTaskTitle())).append("</em>");
            }
            sb.append("</div>");

            if (p.description() != null && !p.description().isBlank()) {
                sb.append("<div style='font-size:10pt; color:#555; margin-bottom:8px; padding:4px 0; border-bottom:1px dashed #bbb;'>");
                sb.append("<em>").append(esc(p.description())).append("</em>");
                sb.append("</div>");
            }

            // ── Linha de datas (para preenchimento manual) ─────────────────
            sb.append("<div style='margin-bottom:10px; font-size:9pt; color:#666;'>");
            sb.append("Data de execu&ccedil;&atilde;o: ___/___/________ &nbsp;&nbsp;&nbsp; ");
            sb.append("Respons&aacute;vel: ______________________________ &nbsp;&nbsp;&nbsp; ");
            sb.append("Assinatura: ______________________________");
            sb.append("</div>");

            // ── Passos ─────────────────────────────────────────────────────
            if (steps.isEmpty()) {
                sb.append("<p style='font-style:italic; color:#888; padding: 6px 0'>Nenhum passo cadastrado para este protocolo.</p>");
            } else {
                sb.append("<div class='steps-container'>");
                for (ProtocolStep step : steps) {
                    sb.append("<div class='step-row'>");
                    sb.append("<span class='step-order'>").append(step.stepOrder()).append(".</span>");
                    sb.append("<span class='step-checkbox'>&#9744;</span>"); // ☐ Unicode checkbox
                    sb.append("<span class='step-text'>").append(esc(step.stepText())).append("</span>");
                    if (step.critical()) {
                        sb.append("<span class='step-critical-mark badge-critico-step'>&nbsp;&#9888; CR&Iacute;TICO&nbsp;</span>");
                    }
                    if (step.notes() != null && !step.notes().isBlank()) {
                        sb.append("<br><span style='margin-left:56px; font-size:9pt; color:#666; font-style:italic;'>")
                          .append(esc(step.notes())).append("</span>");
                    }
                    sb.append("</div>");
                }
                sb.append("</div>");
            }

            // ── Espaço para observações ────────────────────────────────────
            sb.append("<div style='margin-top:10px; padding-top:8px; border-top:1px solid #ccc; font-size:9pt; color:#666;'>");
            sb.append("<strong>Observa&ccedil;&otilde;es:</strong><br/>");
            // 3 lines for notes
            sb.append("<div style='border-bottom:1px solid #ccc; height:18px; margin-top:6px;'></div>");
            sb.append("<div style='border-bottom:1px solid #ccc; height:18px; margin-top:6px;'></div>");
            sb.append("<div style='border-bottom:1px solid #ccc; height:18px; margin-top:6px;'></div>");
            sb.append("</div>");

            sb.append("</div>"); // checklist-section
        }

        return wrapHtml("Checklist de Protocolos", sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HTML wrapper + CSS
    // ═══════════════════════════════════════════════════════════════════════════

    private static String wrapHtml(String title, String body) {
        return "<!DOCTYPE html>\n"
             + "<html lang='pt-BR'>\n"
             + "<head>\n"
             + "<meta charset='UTF-8'>\n"
             + "<title>" + esc(title) + "</title>\n"
             + "<style>" + buildCss() + "</style>\n"
             + "</head>\n"
             + "<body class='color-mode'>\n"
             + body
             + "<script>"
             + "function toggleMono(){"
             + "var b=document.body;"
             + "if(b.classList.contains('mono-mode')){b.classList.remove('mono-mode');b.classList.add('color-mode');}"
             + "else{b.classList.remove('color-mode');b.classList.add('mono-mode');}"
             + "}"
             + "</script>\n"
             + "</body>\n</html>";
    }

    private static String buildCss() {
        return
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { font-family: Arial, Helvetica, sans-serif; font-size: 11pt; background: white; color: black; }" +

            /* ── Layout geral ─────────────────── */
            ".report-header { margin-bottom: 16px; border-bottom: 2.5px solid black; padding-bottom: 8px; }" +
            ".report-header h1 { font-size: 16pt; font-weight: bold; margin-bottom: 4px; }" +
            ".report-header .meta { font-size: 9pt; color: #444; }" +

            "table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }" +
            "th, td { border: 1px solid #333; padding: 5px 7px; text-align: left; font-size: 10pt; vertical-align: top; }" +
            "th { font-weight: bold; font-size: 9pt; text-transform: uppercase; }" +
            "tbody tr:nth-child(even) { background: #fafafa; }" +

            /* ── KPI boxes ────────────────────── */
            ".kpi-row { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }" +
            ".kpi-box { border: 1.5px solid #333; padding: 7px 12px; min-width: 110px; }" +
            ".kpi-box .kpi-label { font-size: 7.5pt; font-weight: bold; text-transform: uppercase; color: #555; }" +
            ".kpi-box .kpi-val { font-size: 15pt; font-weight: bold; }" +

            /* ── Checklist sections ────────────── */
            ".checklist-section { margin-bottom: 22px; border: 1.5px solid #333; padding: 12px; }" +
            ".steps-container { margin-top: 4px; }" +
            ".step-row { display: flex; align-items: flex-start; padding: 6px 2px; border-bottom: 1px dashed #bbb; min-height: 28px; }" +
            ".step-row:last-child { border-bottom: none; }" +
            ".step-order { font-size: 10pt; font-weight: bold; color: #777; min-width: 22px; padding-right: 4px; }" +
            ".step-checkbox { font-size: 15pt; min-width: 24px; line-height: 1; }" +
            ".step-text { font-size: 11pt; flex: 1; line-height: 1.3; padding-right: 6px; }" +
            ".checklist-meta { font-size: 9pt; margin-bottom: 7px; }" +

            /* ── COLOR MODE ───────────────────── */
            "body.color-mode th { background-color: #1e3a5f; color: white; }" +
            "body.color-mode .section-header { background-color: #03183e; color: white; }" +
            "body.color-mode .row-overdue td { background-color: #fff0f0 !important; }" +
            "body.color-mode .row-done td { background-color: #f0fff4 !important; color: #444; }" +
            "body.color-mode .kpi-box { border-color: #1e3a5f; }" +
            "body.color-mode .kpi-box .kpi-label { color: #1e3a5f; }" +

            "body.color-mode .badge-critica { background:#c62828; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-alta    { background:#e65100; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-normal  { background:#1565c0; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-baixa   { background:#2e7d32; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-done    { background:#1b5e20; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-overdue { background:#b71c1c; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-pending { background:#e65100; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.color-mode .badge-nova     { background:#1565c0; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-execucao { background:#1b5e20; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-validacao{ background:#7b1fa2; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-proto    { background:#0277bd; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-pausada  { background:#546e7a; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-concluida{ background:#2e7d32; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-pay      { background:#ff8f00; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-rec      { background:#00695c; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-orc      { background:#283593; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-lanc     { background:#4a148c; color:white; padding:2px 6px; border-radius:4px; font-size:9pt; }" +
            "body.color-mode .badge-critico-step { background:#ffebee; border:1.5px solid #c62828; color:#c62828; font-size:9pt; padding:1px 6px; border-radius:3px; font-weight:bold; }" +

            /* ── MONO MODE ────────────────────── */
            "body.mono-mode th { background-color: #ddd; color: black; }" +
            "body.mono-mode .section-header { background-color: #bbb; color: black; }" +
            "body.mono-mode .row-done td { color: #666; }" +
            "body.mono-mode .badge-critica, body.mono-mode .badge-alta, body.mono-mode .badge-normal, " +
            "body.mono-mode .badge-baixa, body.mono-mode .badge-done, body.mono-mode .badge-overdue, " +
            "body.mono-mode .badge-pending, body.mono-mode .badge-nova, body.mono-mode .badge-execucao, " +
            "body.mono-mode .badge-validacao, body.mono-mode .badge-proto, body.mono-mode .badge-pausada, " +
            "body.mono-mode .badge-concluida, body.mono-mode .badge-pay, body.mono-mode .badge-rec, " +
            "body.mono-mode .badge-orc, body.mono-mode .badge-lanc " +
            "{ background:none; color:black; border:1.5px solid black; padding:2px 6px; border-radius:4px; font-size:9pt; font-weight:bold; }" +
            "body.mono-mode .badge-critico-step { background:none; border:1.5px solid black; color:black; font-size:9pt; padding:1px 6px; font-weight:bold; }" +

            /* ── @media print ─────────────────── */
            "@media print {" +
            "  @page { margin: 1.5cm 1.2cm; }" +
            "  body { font-size: 10pt; }" +
            "  .no-print { display: none !important; }" +
            "  .page-break { page-break-before: always; }" +
            "  table { page-break-inside: auto; }" +
            "  tr { page-break-inside: avoid; }" +
            "  .checklist-section { page-break-inside: avoid; }" +
            "  th { -webkit-print-color-adjust: exact; print-color-adjust: exact; }" +
            "  td { -webkit-print-color-adjust: exact; print-color-adjust: exact; }" +
            "  .section-header, .kpi-box, .badge-critica, .badge-alta, .badge-normal, " +
            "  .badge-baixa, .badge-done, .badge-overdue, .badge-pending, .row-overdue td, .row-done td " +
            "  { -webkit-print-color-adjust: exact; print-color-adjust: exact; }" +
            "  body.mono-mode th { background-color: #ddd !important; -webkit-print-color-adjust: exact; }" +
            "  body.mono-mode .section-header { background-color: #bbb !important; -webkit-print-color-adjust: exact; }" +
            "}" ;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers internos
    // ═══════════════════════════════════════════════════════════════════════════

    private static String kpiBox(String label, String value) {
        return "<div class='kpi-box'><div class='kpi-label'>" + label + "</div>"
             + "<div class='kpi-val'>" + value + "</div></div>";
    }

    private static String statusText(TaskStatus status, boolean done) {
        if (done) return "Conclu&iacute;da";
        if (status == null) return "Pendente";
        return switch (status) {
            case PENDENTE     -> "Pendente";
            case EM_ANDAMENTO -> "Em Andamento";
            case CONCLUIDA    -> "Conclu&iacute;da";
            case BLOQUEADA    -> "Bloqueada";
            case CANCELADA    -> "Cancelada";
        };
    }

    private static String priorityText(Object prio) {
        if (prio instanceof TaskPriority p) return switch (p) {
            case CRITICA -> "Cr&iacute;tica";
            case ALTA    -> "Alta";
            case NORMAL  -> "Normal";
            case BAIXA   -> "Baixa";
        };
        if (prio instanceof String s) return switch (s) {
            case "CRITICA" -> "Cr&iacute;tica";
            case "ALTA"    -> "Alta";
            case "BAIXA"   -> "Baixa";
            default        -> "Normal";
        };
        return "Normal";
    }

    private static String priorityBadgeClass(Object prio) {
        String s = prio instanceof TaskPriority p ? p.name() : (prio instanceof String str ? str : "NORMAL");
        return switch (s != null ? s : "NORMAL") {
            case "CRITICA" -> "badge-critica";
            case "ALTA"    -> "badge-alta";
            case "BAIXA"   -> "badge-baixa";
            default        -> "badge-normal";
        };
    }

    private static String recurrenceText(Task t) {
        if (t.scheduleType() == null || t.scheduleType() == ScheduleType.SINGLE) return "Dia &uacute;nico";
        if (t.scheduleType() == ScheduleType.RANGE) {
            String end = t.endDate() != null ? t.endDate().format(DATE_FMT) : "?";
            return "At&eacute; " + end;
        }
        // WEEKLY
        if (t.recurrenceDays() == null || t.recurrenceDays().isBlank()) return "Semanal";
        String[] names = {"Dom","Seg","Ter","Qua","Qui","Sex","S&aacute;b"};
        StringBuilder days = new StringBuilder();
        for (String d : t.recurrenceDays().split(",")) {
            try {
                int idx = Integer.parseInt(d.trim());
                if (!days.isEmpty()) days.append(", ");
                days.append(names[idx]);
            } catch (Exception ignored) {}
        }
        return days.isEmpty() ? "Semanal" : days.toString();
    }

    private static String ideaStatusBadge(String status) {
        if (status == null) return "badge-nova";
        return switch (status.toLowerCase().trim()) {
            case "em_execucao", "em execução" -> "badge-execucao";
            case "em_validacao", "em validação" -> "badge-validacao";
            case "prototipagem"               -> "badge-proto";
            case "pausada"                    -> "badge-pausada";
            case "concluida", "concluída"     -> "badge-concluida";
            case "abandonada"                 -> "badge-overdue";
            default                           -> "badge-nova";
        };
    }

    private static String typeFinanceBadge(String type) {
        if (type == null) return "badge-lanc";
        return switch (type.toLowerCase()) {
            case "pagamento"   -> "badge-pay";
            case "recebimento" -> "badge-rec";
            case "orçamento","orcamento" -> "badge-orc";
            default            -> "badge-lanc";
        };
    }

    /** Escapa caracteres HTML perigosos. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Remove emojis Unicode comuns para exibição limpa em modo mono. */
    private static String stripEmoji(String s) {
        if (s == null) return "";
        // remove common single-codepoint emoji + ZWJ sequences
        return s.replaceAll("[\\p{So}\\p{Sm}\\uFE0F]", "").trim();
    }
}



