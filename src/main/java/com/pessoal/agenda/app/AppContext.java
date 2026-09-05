package com.pessoal.agenda.app;

import com.pessoal.agenda.infra.Database;
import com.pessoal.agenda.repository.*;
import com.pessoal.agenda.service.AlertService;
import com.pessoal.agenda.service.CategoryService;
import com.pessoal.agenda.service.DashboardService;
import com.pessoal.agenda.service.DailyPlanService;
import com.pessoal.agenda.service.DayReviewService;
import com.pessoal.agenda.service.FocusContextService;
import com.pessoal.agenda.service.StudyAttendanceService;
import com.pessoal.agenda.service.TaskTimerRecoveryService;
import com.pessoal.agenda.service.TaskService;
import com.pessoal.agenda.service.InboxCaptureService;
import com.pessoal.agenda.service.QuickCapturePreferences;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.infra.pairing.LocalNetworkAddressSelector;
import com.pessoal.agenda.infra.pairing.LocalPairingServer;
import com.pessoal.agenda.infra.pairing.LocalSyncTlsIdentityStore;

/**
 * Composition Root (DI manual): centraliza a montagem de dependencias da aplicacao.
 *
 * Beneficios:
 * - Evita "new" espalhado pela UI
 * - Facilita testes por injeção de dependencias
 * - Ajuda manutencao e documentacao arquitetural
 */
public class AppContext {

    private final Database database;

    private final TaskRepository taskRepository;
    private final ChecklistRepository checklistRepository;
    private final FinanceRepository financeRepository;
    private final SalesRepository salesRepository;
    private final InventoryRepository inventoryRepository;
    private final StudyRepository studyRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final StudyEntryRepository studyEntryRepository;
    private final ProjectIdeaRepository projectIdeaRepository;
    private final IdeaChecklistRepository ideaChecklistRepository;
    private final TaskChecklistRepository taskChecklistRepository;
    private final CategoryRepository categoryRepository;
    private final ProtocolRepository protocolRepository;
    private final StudyScheduleRepository studyScheduleRepository;
    private final StudyCompensationRepository studyCompensationRepository;
    private final com.pessoal.agenda.repository.StudyStatusLogRepository studyStatusLogRepository;
    private final com.pessoal.agenda.repository.TaskSessionRepository taskSessionRepository;
    private final com.pessoal.agenda.repository.GoogleTasksMappingRepository googleTasksMappingRepository;
    private final com.pessoal.agenda.repository.GoogleTasksSyncRepository googleTasksSyncRepository;
    private final DesktopSyncRepository desktopSyncRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final DayReviewRepository dayReviewRepository;
    private final InboxCaptureRepository inboxCaptureRepository;
    private final FocusContextRepository focusContextRepository;
    private final TimerRecoveryRepository timerRecoveryRepository;
    private final LocalMetricsRepository localMetricsRepository;

    private final TaskService taskService;
    private final AlertService alertService;
    private final DashboardService dashboardService;
    private final CategoryService categoryService;
    private final StudyAttendanceService studyAttendanceService;
    private final DailyPlanService dailyPlanService;
    private final DayReviewService dayReviewService;
    private final InboxCaptureService inboxCaptureService;
    private final FocusContextService focusContextService;
    private final TaskTimerRecoveryService taskTimerRecoveryService;
    private final QuickCapturePreferences quickCapturePreferences;
    private final LocalMetricsService localMetricsService;
    private LocalPairingServer mobileSyncServer;

    private AppContext() {
        this.database = new Database();
        this.database.runMigrations();

        this.taskRepository = new TaskRepository(database);
        this.checklistRepository = new ChecklistRepository(database);
        this.financeRepository = new FinanceRepository(database);
        this.salesRepository = new SalesRepository(database);
        this.inventoryRepository = new InventoryRepository(database);
        this.studyRepository = new StudyRepository(database);
        this.studyPlanRepository = new StudyPlanRepository(database);
        this.studyEntryRepository = new StudyEntryRepository(database);
        this.projectIdeaRepository = new ProjectIdeaRepository(database);
        this.ideaChecklistRepository = new IdeaChecklistRepository(database);
        this.taskChecklistRepository = new TaskChecklistRepository(database);
        this.categoryRepository = new CategoryRepository(database);
        this.protocolRepository = new ProtocolRepository(database);
        this.studyScheduleRepository = new StudyScheduleRepository(database);
        this.studyCompensationRepository = new StudyCompensationRepository(database);
        this.studyStatusLogRepository = new com.pessoal.agenda.repository.StudyStatusLogRepository(database);
        this.taskSessionRepository = new com.pessoal.agenda.repository.TaskSessionRepository(database);
        this.googleTasksMappingRepository = new com.pessoal.agenda.repository.GoogleTasksMappingRepository(database);
        this.googleTasksSyncRepository = new com.pessoal.agenda.repository.GoogleTasksSyncRepository(database);
        this.desktopSyncRepository = new DesktopSyncRepository(database);
        this.dailyPlanRepository = new DailyPlanRepository(database);
        this.dayReviewRepository = new DayReviewRepository(database);
        this.inboxCaptureRepository = new InboxCaptureRepository(database);
        this.focusContextRepository = new FocusContextRepository(database);
        this.timerRecoveryRepository = new TimerRecoveryRepository(database);
        this.localMetricsRepository = new LocalMetricsRepository(database);

        this.taskService = new TaskService(taskRepository);
        this.alertService = new AlertService(database, taskRepository, financeRepository);
        this.dashboardService = new DashboardService(taskRepository, checklistRepository, financeRepository, inventoryRepository, studyRepository, projectIdeaRepository);
        this.categoryService = new CategoryService(categoryRepository);
        this.studyAttendanceService = new StudyAttendanceService(
                studyScheduleRepository, studyEntryRepository, studyCompensationRepository,
                studyStatusLogRepository);
        this.dailyPlanService = new DailyPlanService(dailyPlanRepository, taskRepository);
        this.dayReviewService = new DayReviewService(
                dailyPlanRepository, taskRepository, taskSessionRepository, dayReviewRepository);
        this.inboxCaptureService = new InboxCaptureService(inboxCaptureRepository);
        this.focusContextService = new FocusContextService(focusContextRepository, taskRepository);
        this.taskTimerRecoveryService = new TaskTimerRecoveryService(
                timerRecoveryRepository, taskRepository);
        this.quickCapturePreferences = new QuickCapturePreferences();
        this.localMetricsService = new LocalMetricsService(localMetricsRepository);
        this.categoryService.seedDefaults();
    }

    public static AppContext create() {
        return new AppContext();
    }

    public Database database() {
        return database;
    }

    public TaskRepository taskRepository() {
        return taskRepository;
    }

    public ChecklistRepository checklistRepository() {
        return checklistRepository;
    }

    public FinanceRepository financeRepository() {
        return financeRepository;
    }

    public SalesRepository salesRepository() {
        return salesRepository;
    }

    public InventoryRepository inventoryRepository() {
        return inventoryRepository;
    }

    public StudyRepository studyRepository() {
        return studyRepository;
    }

    public StudyPlanRepository studyPlanRepository() {
        return studyPlanRepository;
    }

    public StudyEntryRepository studyEntryRepository() {
        return studyEntryRepository;
    }

    public ProjectIdeaRepository projectIdeaRepository() {
        return projectIdeaRepository;
    }

    public IdeaChecklistRepository ideaChecklistRepository() {
        return ideaChecklistRepository;
    }

    public TaskChecklistRepository taskChecklistRepository() {
        return taskChecklistRepository;
    }

    public CategoryRepository categoryRepository() {
        return categoryRepository;
    }

    public ProtocolRepository protocolRepository() {
        return protocolRepository;
    }

    public TaskService taskService() {
        return taskService;
    }

    public AlertService alertService() {
        return alertService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }

    public CategoryService categoryService() {
        return categoryService;
    }

    public StudyScheduleRepository studyScheduleRepository() {
        return studyScheduleRepository;
    }

    public StudyCompensationRepository studyCompensationRepository() {
        return studyCompensationRepository;
    }

    public com.pessoal.agenda.repository.StudyStatusLogRepository studyStatusLogRepository() {
        return studyStatusLogRepository;
    }

    public com.pessoal.agenda.repository.TaskSessionRepository taskSessionRepository() {
        return taskSessionRepository;
    }

    public com.pessoal.agenda.repository.GoogleTasksMappingRepository googleTasksMappingRepository() {
        return googleTasksMappingRepository;
    }

    public com.pessoal.agenda.repository.GoogleTasksSyncRepository googleTasksSyncRepository() {
        return googleTasksSyncRepository;
    }

    public DesktopSyncRepository desktopSyncRepository() {
        return desktopSyncRepository;
    }

    public StudyAttendanceService studyAttendanceService() {
        return studyAttendanceService;
    }

    public DailyPlanRepository dailyPlanRepository() {
        return dailyPlanRepository;
    }

    public DailyPlanService dailyPlanService() {
        return dailyPlanService;
    }

    public DayReviewRepository dayReviewRepository() {
        return dayReviewRepository;
    }

    public DayReviewService dayReviewService() {
        return dayReviewService;
    }

    public InboxCaptureRepository inboxCaptureRepository() {
        return inboxCaptureRepository;
    }

    public InboxCaptureService inboxCaptureService() {
        return inboxCaptureService;
    }

    public FocusContextRepository focusContextRepository() {
        return focusContextRepository;
    }

    public FocusContextService focusContextService() {
        return focusContextService;
    }

    public TimerRecoveryRepository timerRecoveryRepository() {
        return timerRecoveryRepository;
    }

    public TaskTimerRecoveryService taskTimerRecoveryService() {
        return taskTimerRecoveryService;
    }

    public QuickCapturePreferences quickCapturePreferences() {
        return quickCapturePreferences;
    }

    public LocalMetricsRepository localMetricsRepository() {
        return localMetricsRepository;
    }

    public LocalMetricsService localMetricsService() {
        return localMetricsService;
    }

    public synchronized LocalPairingServer startMobileSyncServer() {
        if (mobileSyncServer != null) {
            mobileSyncServer.ensureRunning();
            return mobileSyncServer;
        }
        var address = LocalNetworkAddressSelector.select();
        var identity = new LocalSyncTlsIdentityStore().loadOrCreate(address);
        mobileSyncServer = new LocalPairingServer(
                desktopSyncRepository, address, 48484, identity);
        mobileSyncServer.ensureRunning();
        return mobileSyncServer;
    }

    public synchronized void stopMobileSyncServer() {
        if (mobileSyncServer != null) mobileSyncServer.close();
        mobileSyncServer = null;
    }
}
