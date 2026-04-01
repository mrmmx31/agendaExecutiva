package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.TaskSession;
import com.pessoal.agenda.repository.TaskSessionRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.pessoal.agenda.app.AppContextHolder;
import java.util.concurrent.CompletableFuture;

/**
 * Improved Window that shows session history either by task or by date range.
 * Features:
 * - Period quick-filters (Day/Week/Month/Year)
 * - From / To DatePicker range
 * - Optional taskId filter (when opened for a specific task)
 * - TableView with columns (Date, Subject, Minutes, Notes)
 * - Total minutes label and CSV export button
 */
public class SessionHistoryWindow {
    private final TaskSessionRepository repo;
    private final Long taskId; // nullable - open focused on a task
    private Stage stage;

    // UI
    private final ObservableList<TaskSession> data = FXCollections.observableArrayList();

    public SessionHistoryWindow(TaskSessionRepository repo, Long taskId) {
        this.repo = repo;
        this.taskId = taskId;
    }

    public void show() {
        stage = new Stage();
        WindowManager.register(stage);
        stage.initModality(Modality.NONE);
        stage.setTitle("Histórico de sessões");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");

        // ── Barra superior elegante igual ao form principal ──
        Label title = new Label("Histórico de sessões");
        title.getStyleClass().add("page-title");
        HBox headerBar = new HBox(title);
        headerBar.getStyleClass().add("header-bar");
        headerBar.setPadding(new Insets(16, 28, 16, 28));
        headerBar.setAlignment(Pos.CENTER_LEFT);

        // ── Filtros logo abaixo, integrados ──
        ToggleGroup periodGroup = new ToggleGroup();
        ToggleButton btnDay = new ToggleButton("Dia");
        ToggleButton btnWeek = new ToggleButton("Semana");
        ToggleButton btnMonth = new ToggleButton("Mês");
        ToggleButton btnYear = new ToggleButton("Ano");
        for (ToggleButton b : List.of(btnDay, btnWeek, btnMonth, btnYear)) {
            b.setToggleGroup(periodGroup);
            b.getStyleClass().add("view-toggle-btn");
            b.setMinWidth(56);
        }
        btnWeek.setSelected(true);
        HBox periodBox = new HBox(0, btnDay, btnWeek, btnMonth, btnYear);
        periodBox.getStyleClass().add("view-toggle-bar");
        periodBox.setAlignment(Pos.CENTER_LEFT);
        periodBox.setSpacing(0);

        DatePicker fromPicker = new DatePicker(LocalDate.now().minusWeeks(1));
        fromPicker.getStyleClass().add("input-control");
        fromPicker.setMinWidth(110);
        fromPicker.setPrefWidth(110);
        DatePicker toPicker = new DatePicker(LocalDate.now());
        toPicker.getStyleClass().add("input-control");
        toPicker.setMinWidth(110);
        toPicker.setPrefWidth(110);

        TextField taskFilter = new TextField();
        taskFilter.setPromptText("Filtrar por tarefa (texto no assunto)");
        taskFilter.getStyleClass().add("input-control");
        taskFilter.setMinWidth(140);
        if (taskId != null) { taskFilter.setText("#" + taskId); taskFilter.setDisable(true); }

        Button refreshBtn = new Button("Atualizar");
        refreshBtn.getStyleClass().add("primary-button");
        Button exportBtn = new Button("Exportar CSV");
        exportBtn.getStyleClass().add("secondary-button");

        Label periodoLabel = new Label("Período:");
        periodoLabel.setMinWidth(54);
        Label deLabel = new Label("De:");
        deLabel.setMinWidth(28);
        Label ateLabel = new Label("Até:");
        ateLabel.setMinWidth(28);

        HBox filtersRow = new HBox(14,
            periodoLabel, periodBox,
            deLabel, fromPicker, ateLabel, toPicker,
            taskFilter, refreshBtn, exportBtn
        );
        filtersRow.setAlignment(Pos.CENTER_LEFT);
        filtersRow.setPadding(new Insets(12, 28, 12, 28));
        HBox.setHgrow(taskFilter, Priority.ALWAYS);

        VBox topPanel = new VBox(headerBar, filtersRow);
        topPanel.setSpacing(0);
        // Não precisa de fundo extra, segue padrão do app

        // ── Tabela central ──
        TableView<TaskSession> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getStyleClass().add("clean-list");
        TableColumn<TaskSession, String> dateCol = new TableColumn<>("Data");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().sessionDate() != null ? cell.getValue().sessionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : ""));
        dateCol.setMinWidth(90);
        TableColumn<TaskSession, String> subjCol = new TableColumn<>("Assunto");
        subjCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().subject() != null ? cell.getValue().subject() : ""));
        subjCol.setMinWidth(220);
        TableColumn<TaskSession, Number> minsCol = new TableColumn<>("Minutos");
        minsCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().durationMinutes()));
        minsCol.setMinWidth(70);
        TableColumn<TaskSession, String> notesCol = new TableColumn<>("Notas");
        notesCol.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().notes() != null ? cell.getValue().notes() : ""));
        notesCol.setMinWidth(120);
        table.getColumns().addAll(dateCol, subjCol, minsCol, notesCol);
        table.setItems(data);
        VBox.setVgrow(table, Priority.ALWAYS);

        // ── Rodapé ──
        HBox bottom = new HBox(18);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10, 28, 0, 28));
        Label totalLabel = new Label("Total: 0 min");
        totalLabel.getStyleClass().add("section-title");
        Label hint = new Label("Clique duas vezes em uma linha para abrir a sessão/tarefa.");
        hint.getStyleClass().add("form-label");
        bottom.getChildren().addAll(totalLabel, hint);

        VBox centerVBox = new VBox(8, table, bottom);
        centerVBox.setPadding(new Insets(0, 0, 18, 0));
        VBox.setVgrow(table, Priority.ALWAYS);

        root.setTop(topPanel);
        root.setCenter(centerVBox);

        Scene sc = new Scene(root);
        var cssUrl = SessionHistoryWindow.class.getResource("/com/pessoal/agenda/app.css");
        if (cssUrl != null) sc.getStylesheets().add(cssUrl.toExternalForm());
        stage.setScene(sc);

        // actions
        Runnable doLoad = () -> loadData(fromPicker.getValue(), toPicker.getValue(), taskFilter.getText(), totalLabel);
        btnDay.setOnAction(e -> { fromPicker.setValue(LocalDate.now()); toPicker.setValue(LocalDate.now()); doLoad.run(); });
        btnWeek.setOnAction(e -> { fromPicker.setValue(LocalDate.now().minusWeeks(1)); toPicker.setValue(LocalDate.now()); doLoad.run(); });
        btnMonth.setOnAction(e -> { fromPicker.setValue(LocalDate.now().minusMonths(1)); toPicker.setValue(LocalDate.now()); doLoad.run(); });
        btnYear.setOnAction(e -> { fromPicker.setValue(LocalDate.now().minusYears(1)); toPicker.setValue(LocalDate.now()); doLoad.run(); });
        refreshBtn.setOnAction(e -> doLoad.run());
        exportBtn.setOnAction(e -> exportCsv());
        table.setRowFactory(tv -> {
            TableRow<TaskSession> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    TaskSession s = row.getItem();
                    Long tid = extractTaskIdFromSubject(s.subject());
                    if (tid != null) {
                        Platform.runLater(() -> new TaskTimerWindow(AppContextHolder.get().taskService().findById(tid).orElseThrow(), AppContextHolder.get().taskSessionRepository()).show());
                    }
                }
            });
            return row;
        });
        Platform.runLater(doLoad);
        stage.show();
    }

    private static Long extractTaskIdFromSubject(String subj) {
        if (subj == null) return null;
        // try to find pattern '#<digits>'
        int idx = subj.indexOf('#');
        if (idx >= 0 && idx + 1 < subj.length()) {
            String tail = subj.substring(idx + 1);
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (Character.isDigit(c)) digits.append(c); else break;
            }
            if (digits.length() > 0) {
                try { return Long.parseLong(digits.toString()); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private void loadData(LocalDate from, LocalDate to, String taskFilter, Label totalLabel) {
        CompletableFuture.supplyAsync(() -> {
            try {
                if (taskId != null) {
                    return repo.findByTaskId(taskId);
                }
                if (taskFilter != null && taskFilter.trim().startsWith("#")) {
                    // try parse id
                    String digits = taskFilter.trim().substring(1).replaceAll("\\D", "");
                    if (!digits.isEmpty()) {
                        try { long tid = Long.parseLong(digits); return repo.findByTaskId(tid); } catch (NumberFormatException ex) {}
                    }
                }
                // default date-range query
                LocalDate f = from == null ? LocalDate.now().minusMonths(1) : from;
                LocalDate t = to == null ? LocalDate.now() : to;
                return repo.findByDateRange(f, t);
            } catch (Throwable ex) {
                ex.printStackTrace();
                return List.<TaskSession>of();
            }
        }).thenAccept(list -> Platform.runLater(() -> {
            data.clear();
            if (taskFilter != null && !taskFilter.trim().isEmpty() && !(taskFilter.trim().startsWith("#"))) {
                String tf = taskFilter.trim().toLowerCase();
                for (TaskSession s : list) if (s.subject() != null && s.subject().toLowerCase().contains(tf)) data.add(s);
            } else {
                data.addAll(list);
            }
            int total = data.stream().mapToInt(TaskSession::durationMinutes).sum();
            totalLabel.setText("Total: " + total + " min");
        }));
    }

    private void exportCsv() {
        try {
            var dlg = new javafx.stage.FileChooser();
            dlg.setTitle("Salvar CSV");
            dlg.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV","*.csv"));
            var file = dlg.showSaveDialog(stage);
            if (file == null) return;
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("date,subject,minutes,notes");
                for (TaskSession s : data) {
                    String notes = s.notes() == null ? "" : s.notes().replaceAll("[\\r\\n]"," ").replaceAll(",",";");
                    pw.printf("%s,%s,%d,%s\n", s.sessionDate(), s.subject().replaceAll(",",";"), s.durationMinutes(), notes);
                }
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            // best-effort: show simple alert
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Não foi possível exportar CSV: " + ex.getMessage(), ButtonType.OK);
                a.initOwner(stage);
                a.showAndWait();
            });
        }
    }
}
