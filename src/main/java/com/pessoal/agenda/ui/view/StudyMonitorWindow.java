package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.model.*;
import com.pessoal.agenda.model.AttendanceDay.AttendanceStatus;
import com.pessoal.agenda.service.StudyAttendanceService.Summary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Janela de Monitor de Frequência de Estudo.
 *
 * Painel:
 *   TOP    – KPIs: dias programados, presença, faltas, déficit de horas
 *   CENTER – Calendário dos últimos 8 semanas + semana atual
 *   BOTTOM – Painel de compensações pendentes
 */
public class StudyMonitorWindow {

    // No single-instance registry: allow multiple monitor windows per plan (reverted).
    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudyPlan plan;
    private final Runnable  onClose;

    // navegação de mês
    private YearMonth currentMonth;
    private GridPane  calendarGrid;
    private Label     monthLabel;

    // compensações
    private VBox compensationPanel;

    public StudyMonitorWindow(StudyPlan plan, Runnable onClose) {
        this.plan         = plan;
        this.onClose      = onClose;
        this.currentMonth = YearMonth.now();
    }

    public void show() {
        // Always create a new stage for the monitor window (reverted behavior).
        Stage stage = new Stage();
        stage.setTitle("📊  Monitor de Frequência — " + plan.title());
        stage.setMinWidth(860); stage.setMinHeight(640);
        stage.initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, 980, 720);
        var css = StudyMonitorWindow.class.getResource("/com/pessoal/agenda/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.setOnHiding(e -> { if (onClose != null) onClose.run(); });
        stage.show();
    }

    // ── Header ────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        Label title = new Label("📊  " + plan.title());
        title.getStyleClass().add("page-title");

        Label sub = new Label("Monitor de Frequência  ·  " + plan.studyTypeName()
                + "  ·  " + plan.status().label());
        sub.getStyleClass().add("page-subtitle");

        VBox hdr = new VBox(2, title, sub);
        hdr.setPadding(new Insets(12, 16, 12, 16));
        hdr.getStyleClass().add("header-bar");
        return hdr;
    }

    // ── Centro ────────────────────────────────────────────────────────────

    private SplitPane buildCenter() {
        VBox leftSide  = buildCalendarSide();
        VBox rightSide = buildCompensationSide();
        // Garante largura mínima do painel direito para evitar truncamento dos rótulos dos botões
        rightSide.setMinWidth(320);
        rightSide.setPrefWidth(360);
        SplitPane split = new SplitPane(leftSide, rightSide);
        split.setDividerPositions(0.65);
        return split;
    }

    // ── Lado esquerdo: calendário ─────────────────────────────────────────

    private VBox buildCalendarSide() {
        // KPI bar
        HBox kpiBar = buildKpiBar();

        // navegação de mês
        Button prevBtn = new Button("◀"); prevBtn.getStyleClass().add("secondary-button");
        Button nextBtn = new Button("▶"); nextBtn.getStyleClass().add("secondary-button");
        monthLabel = new Label();
        monthLabel.getStyleClass().add("section-title");
        monthLabel.setMinWidth(200);
        monthLabel.setAlignment(Pos.CENTER);

        prevBtn.setOnAction(e -> { currentMonth = currentMonth.minusMonths(1); rebuildCalendar(); });
        nextBtn.setOnAction(e -> { currentMonth = currentMonth.plusMonths(1); rebuildCalendar(); });

        HBox navBar = new HBox(8, prevBtn, monthLabel, nextBtn);
        navBar.setAlignment(Pos.CENTER);
        navBar.setPadding(new Insets(4, 0, 4, 0));

        // Cabeçalho dos dias da semana
        HBox weekHeader = new HBox(4);
        weekHeader.setAlignment(Pos.CENTER);
        for (DayOfWeek d : DayOfWeek.values()) {
            Label lbl = new Label(StudyScheduleDay.shortLabel(d));
            lbl.getStyleClass().add("calendar-week-header");
            lbl.setPrefWidth(44);
            lbl.setAlignment(Pos.CENTER);
            weekHeader.getChildren().add(lbl);
        }

        calendarGrid = new GridPane();
        calendarGrid.setHgap(4); calendarGrid.setVgap(4);
        calendarGrid.setAlignment(Pos.TOP_CENTER);
        rebuildCalendar();

        ScrollPane scroll = new ScrollPane(calendarGrid);
        scroll.setFitToWidth(true); scroll.setFitToHeight(false);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Legenda
        HBox legend = buildLegend();

        VBox side = new VBox(10, kpiBar, navBar, weekHeader, scroll, legend);
        side.setPadding(new Insets(12));
        side.getStyleClass().add("section-card");
        return side;
    }

    private void rebuildCalendar() {
        calendarGrid.getChildren().clear();
        monthLabel.setText(currentMonth.format(MONTH_FMT).substring(0, 1).toUpperCase()
                + currentMonth.format(MONTH_FMT).substring(1));

        LocalDate first = currentMonth.atDay(1);
        LocalDate last  = currentMonth.atEndOfMonth();

        // Busca intervalo (mês completo)
        List<AttendanceDay> days = AppContextHolder.get().studyAttendanceService()
                .getCalendar(plan.id(), first, last);
        java.util.Map<LocalDate, AttendanceDay> byDate = new java.util.HashMap<>();
        days.forEach(d -> byDate.put(d.date(), d));

        // Preenche a grade: linha 0 = semana 1...
        int col = first.getDayOfWeek().getValue() - 1; // 0=Seg
        int row = 0;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            AttendanceDay ad = byDate.get(d);
            // Não considerar dias anteriores à data de início do plano como ausências;
            // tratá-los como não programados para evitar mostrar faltas históricas.
            if (plan.startDate() != null && d.isBefore(plan.startDate())) {
                ad = null;
            }
            VBox cell = buildDayCell(d, ad);
            calendarGrid.add(cell, col, row);
            col++;
            if (col == 7) { col = 0; row++; }
        }
    }

    private VBox buildDayCell(LocalDate date, AttendanceDay ad) {
        Label numLbl = new Label(String.valueOf(date.getDayOfMonth()));
        numLbl.getStyleClass().add("calendar-day-num");

        Label symLbl = new Label(ad != null ? ad.status().symbol : "–");
        symLbl.getStyleClass().add("calendar-day-sym");

        VBox cell = new VBox(2, numLbl, symLbl);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(44, 44);
        cell.getStyleClass().add("calendar-day");
        if (ad != null) cell.getStyleClass().add(ad.status().cssClass);
        if (date.equals(LocalDate.now())) cell.getStyleClass().add("calendar-today");

        // Tooltip detalhado
        if (ad != null && ad.status() != AttendanceStatus.NAO_PROGRAMADO) {
            String tip = date.format(DAY_FMT) + "\n"
                    + "Programado: " + fmt(ad.scheduledMinutes()) + "\n"
                    + "Realizado:  " + fmt(ad.actualMinutes());
            Tooltip.install(cell, new Tooltip(tip));
        }
        return cell;
    }

    private HBox buildLegend() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(6, 0, 0, 0));
        for (AttendanceStatus s : AttendanceStatus.values()) {
            if (s == AttendanceStatus.NAO_PROGRAMADO) continue;
            Label sym = new Label(s.symbol + " " + s.name().replace("_", " ").toLowerCase());
            sym.getStyleClass().addAll("calendar-legend", s.cssClass);
            box.getChildren().add(sym);
        }
        return box;
    }

    // ── KPI bar ───────────────────────────────────────────────────────────

    private HBox buildKpiBar() {
        LocalDate from = currentMonth.atDay(1);
        LocalDate to   = currentMonth.atEndOfMonth();
        Summary sum = AppContextHolder.get().studyAttendanceService()
                .getSummary(plan.id(), from, to);

        HBox bar = new HBox(10,
            kpi("Programados", String.valueOf(sum.scheduledDays()), "kpi-blue"),
            kpi("Presença", String.format("%.0f%%", sum.presenceRate()), "kpi-green"),
            kpi("Ausências", String.valueOf(sum.absentDays()), "kpi-orange"),
            kpi("Déficit", fmt(sum.deficitMinutes()), "kpi-red"));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ── Lado direito: compensações ────────────────────────────────────────

    private VBox buildCompensationSide() {
        Label title = new Label("Compensações Pendentes");
        title.getStyleClass().add("section-title");

        Button detectBtn = new Button("🔍");
        detectBtn.getStyleClass().addAll("icon-button");
        detectBtn.setTooltip(new Tooltip("Detectar ausências"));
        detectBtn.setOnAction(e -> detectAndRegisterAbsences());

        compensationPanel = new VBox(8);
        VBox.setVgrow(compensationPanel, Priority.ALWAYS);
        refreshCompensations();

        ScrollPane scroll = new ScrollPane(compensationPanel);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox side = new VBox(10, title, detectBtn, scroll);
        side.setPadding(new Insets(12));
        side.getStyleClass().add("section-card");
        return side;
    }

    private void refreshCompensations() {
        compensationPanel.getChildren().clear();
        var comps = AppContextHolder.get().studyCompensationRepository()
                .findByStudyId(plan.id());
        if (comps.isEmpty()) {
            Label empty = new Label("Nenhuma compensação registrada.");
            empty.getStyleClass().add("study-dates-label");
            compensationPanel.getChildren().add(empty);
            return;
        }
        for (StudyCompensation c : comps) {
            compensationPanel.getChildren().add(buildCompCard(c));
        }
    }

    private VBox buildCompCard(StudyCompensation c) {
        Label dateLbl = new Label("Falta: " + c.missedDate().format(DAY_FMT));
        dateLbl.getStyleClass().add("form-label");

        Label statusLbl = new Label(c.status());
        statusLbl.getStyleClass().addAll("study-badge",
                "PENDENTE".equals(c.status())  ? "badge-status-em_andamento" :
                "CONCLUIDA".equals(c.status()) ? "badge-status-concluido"    :
                                                  "badge-status-abandonado");

        Label minLbl = new Label(fmt(c.compensationMinutes()) + " a compensar");
        minLbl.getStyleClass().add("study-dates-label");

        HBox topRow = new HBox(8, dateLbl, statusLbl, minLbl);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, topRow);
        card.getStyleClass().add("comp-card");
        card.setPadding(new Insets(8, 10, 8, 10));

        if (c.isPending()) {
            DatePicker dp = new DatePicker(LocalDate.now());
            dp.getStyleClass().add("input-control");
            dp.setPromptText("Data de compensação");

            Button doneBtn = new Button("✓");
            doneBtn.getStyleClass().addAll("icon-button");
            doneBtn.setTooltip(new Tooltip("Marcar compensação como concluída"));
            doneBtn.setOnAction(e -> {
                if (dp.getValue() == null) return;
                AppContextHolder.get().studyCompensationRepository()
                        .markDone(c.id(), dp.getValue(), null);
                refreshCompensations();
                rebuildCalendar();
            });

            Button cancelBtn = new Button("✕");
            cancelBtn.getStyleClass().addAll("icon-button", "danger-button");
            cancelBtn.setTooltip(new Tooltip("Cancelar compensação"));
            cancelBtn.setOnAction(e -> {
                AppContextHolder.get().studyCompensationRepository().cancel(c.id());
                refreshCompensations();
            });

            HBox actions = new HBox(8, new Label("Compensar em:"), dp, doneBtn, cancelBtn);
            actions.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(actions);
        } else if (c.isDone() && c.compensationDate() != null) {
            Label doneLbl = new Label("Compensado em: " + c.compensationDate().format(DAY_FMT));
            doneLbl.getStyleClass().add("study-dates-label");
            card.getChildren().add(doneLbl);
        }
        return card;
    }

    private void detectAndRegisterAbsences() {
        // Não detectar ausências anteriores à data de início do plano.
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate planStart  = plan.startDate();
        // Iniciar a detecção a partir da data de início do plano, mas nunca antes do início do mês
        // atual — isto evita registros históricos muito antigos por erro de data.
        LocalDate from;
        if (planStart != null) {
            from = planStart.isAfter(monthStart) ? planStart : monthStart;
        } else {
            from = monthStart;
        }
        LocalDate to   = LocalDate.now().isBefore(currentMonth.atEndOfMonth())
                       ? LocalDate.now() : currentMonth.atEndOfMonth();
        var svc = AppContextHolder.get().studyAttendanceService();
        var repo = AppContextHolder.get().studyCompensationRepository();
        var schedule = AppContextHolder.get().studyScheduleRepository()
                .findByStudyId(plan.id());
        java.util.Map<DayOfWeek, Integer> minByDay = new java.util.HashMap<>();
        schedule.forEach(s -> minByDay.put(s.dayOfWeek(), s.minMinutes()));

        List<LocalDate> missing = svc.getUnregisteredAbsences(plan.id(), from, to);
        if (missing.isEmpty()) {
            showInfo("Nenhuma ausência nova detectada no período.");
            return;
        }

        // Se foram detectadas múltiplas ausências, pedir confirmação ao usuário
        if (missing.size() > 1) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar registro de ausências");
            confirm.setHeaderText("Foram detectadas " + missing.size() + " ausências.");
            confirm.setContentText("Deseja registrar automaticamente estas ausências como compensações?");

            // lista detalhada (expandable)
            StringBuilder sb = new StringBuilder();
            for (LocalDate d : missing) sb.append(d.format(DAY_FMT)).append('\n');
            TextArea details = new TextArea(sb.toString());
            details.setEditable(false);
            details.setWrapText(true);
            details.setMaxWidth(Double.MAX_VALUE);
            details.setMaxHeight(240);
            confirm.getDialogPane().setExpandableContent(details);
            confirm.getDialogPane().setExpanded(false);

            var opt = confirm.showAndWait();
            if (opt.isEmpty() || opt.get() != ButtonType.OK) {
                showInfo("Operação cancelada pelo usuário.");
                return;
            }
        }

        for (LocalDate d : missing) {
            int sched = minByDay.getOrDefault(d.getDayOfWeek(), 30);
            repo.save(plan.id(), d, sched, null);
        }
        refreshCompensations();
        showInfo(missing.size() + " ausência(s) registrada(s) automaticamente.");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static VBox kpi(String label, String value, String styleClass) {
        Label v = new Label(value); v.getStyleClass().addAll("kpi-value", styleClass + "-value");
        Label l = new Label(label); l.getStyleClass().add("kpi-title");
        VBox box = new VBox(2, v, l);
        box.getStyleClass().addAll("kpi-mini", styleClass);
        box.setPadding(new Insets(6, 12, 6, 12));
        return box;
    }

    private static String fmt(int totalMinutes) {
        if (totalMinutes <= 0) return "0 min";
        int h = totalMinutes / 60, m = totalMinutes % 60;
        return h > 0 ? h + "h" + (m > 0 ? " " + m + "min" : "") : m + "min";
    }

    private static void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}



