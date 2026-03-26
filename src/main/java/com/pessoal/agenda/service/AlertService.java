package com.pessoal.agenda.service;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.model.FinanceEntry;
import com.pessoal.agenda.model.Task;
import com.pessoal.agenda.repository.FinanceRepository;
import com.pessoal.agenda.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Servico de alertas: agrega tarefas e pagamentos vencidos em uma lista de mensagens.
 * Centraliza a logica de "o que esta atrasado hoje" sem tocar na UI.
 */
public class AlertService {

    private final Database db;
    private final TaskRepository taskRepo;
    private final FinanceRepository financeRepo;

    public AlertService(Database db, TaskRepository taskRepo, FinanceRepository financeRepo) {
        this.db          = db;
        this.taskRepo    = taskRepo;
        this.financeRepo = financeRepo;
    }

    /** Retorna mensagens de alerta para todas as pendencias vencidas. */
    public List<String> buildAlerts() {
        List<String> alerts = new ArrayList<>();

        for (Task t : taskRepo.findOverdue())
            alerts.add("Tarefa atrasada: %s  (venc.: %s)".formatted(t.title(), t.dueDate()));

        for (FinanceEntry f : financeRepo.findOverdue())
            alerts.add("Pagamento vencido: %s  (venc.: %s)".formatted(f.description(), f.dueDate()));

        if (alerts.isEmpty()) alerts.add("Nenhum atraso no momento.");
        return alerts;
    }

    /** Retorna os proximos prazos futuros (tarefas + pagamentos) para o dashboard. */
    public List<String> buildUpcoming(int limit) {
        List<String> combined = new ArrayList<>();

        try (var conn = db.connect();
             var ps = conn.prepareStatement("""
                SELECT due_date, title, 'Tarefa' AS source FROM tasks
                WHERE done=0 AND status != 'CANCELADA' AND due_date >= date('now')
                UNION ALL
                SELECT due_date, description AS title, 'Pagamento' AS source FROM finance_entries
                WHERE paid=0 AND due_date IS NOT NULL AND due_date >= date('now')
                ORDER BY due_date ASC LIMIT ?""")) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next())
                    combined.add("%s  |  %s  |  %s".formatted(
                            rs.getString("due_date"),
                            rs.getString("source"),
                            rs.getString("title")));
            }
        } catch (Exception e) {
            return List.of("Erro ao consultar prazos futuros.");
        }

        if (combined.isEmpty()) return List.of("Nenhum prazo futuro registrado.");
        return combined;
    }
}


