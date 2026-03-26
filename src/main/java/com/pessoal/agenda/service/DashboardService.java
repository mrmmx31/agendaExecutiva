package com.pessoal.agenda.service;

import com.pessoal.agenda.repository.*;

import java.time.YearMonth;

/**
 * Servico de consolidacao para KPIs do dashboard.
 */
public class DashboardService {

    public record DashboardMetrics(
            int openTasks,
            int overdueTasks,
            int pendingChecklist,
            int pendingPayments,
            double pendingAmount,
            int lowStockItems,
            int ideasInProgress,
            int studyMinutesInMonth
    ) {}

    private final TaskRepository taskRepository;
    private final ChecklistRepository checklistRepository;
    private final FinanceRepository financeRepository;
    private final InventoryRepository inventoryRepository;
    private final StudyRepository studyRepository;
    private final ProjectIdeaRepository projectIdeaRepository;

    public DashboardService(
            TaskRepository taskRepository,
            ChecklistRepository checklistRepository,
            FinanceRepository financeRepository,
            InventoryRepository inventoryRepository,
            StudyRepository studyRepository,
            ProjectIdeaRepository projectIdeaRepository
    ) {
        this.taskRepository = taskRepository;
        this.checklistRepository = checklistRepository;
        this.financeRepository = financeRepository;
        this.inventoryRepository = inventoryRepository;
        this.studyRepository = studyRepository;
        this.projectIdeaRepository = projectIdeaRepository;
    }

    public DashboardMetrics calculate(YearMonth month) {
        return new DashboardMetrics(
                taskRepository.countOpen(),
                taskRepository.countOverdue(),
                checklistRepository.countPending(),
                financeRepository.countPending(),
                financeRepository.sumPending(),
                inventoryRepository.countLowStock(),
                projectIdeaRepository.countInProgress(),
                studyRepository.totalMinutesInMonth(month)
        );
    }
}

