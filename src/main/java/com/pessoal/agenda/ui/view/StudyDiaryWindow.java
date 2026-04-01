package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.*;
import com.pessoal.agenda.repository.StudyEntryRepository;
import com.pessoal.agenda.repository.StudyPlanRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;
import javafx.scene.web.HTMLEditor;
import javafx.application.Platform;

import java.awt.Toolkit;
import com.pessoal.agenda.tools.EmbeddedSounds;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.AudioFormat;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Janela de Diário de Estudo — abre em duplo-clique num plano.
 *
 * Layout:
 *   TOP    — cabeçalho: título, tipo, status, barra de progresso, estatísticas
 *   CENTER — SplitPane(índice de entradas | editor rico de entrada)
 *
 * O editor de texto usa {@link HTMLEditor} (WebKit), oferecendo:
 *   – Negrito / Itálico / Sublinhado (Ctrl+B, Ctrl+I, Ctrl+U)
 *   – Alinhamento: esquerda, centro, direita, justificado
 *   – Colar com formatação (Ctrl+V — preserva HTML de sites)
 *   – Colar sem formatação (botão dedicado)
 *   – Tamanho e família da fonte
 *
 * Regra: apenas uma janela por plano de estudo. Se já estiver aberta,
 * é trazida para frente em vez de abrir uma duplicata.
 */
public class StudyDiaryWindow {

    /** Janelas atualmente abertas, indexadas pelo ID do plano. */
    private static final java.util.Map<Long, Stage> openWindows =
            new java.util.HashMap<>();

    private final StudyPlanRepository  planRepo;
    private final StudyEntryRepository entryRepo;
    private final Runnable             refreshCallback;
    private com.pessoal.agenda.app.SharedContext sharedCtx;

    private StudyPlan currentPlan;
    private Long      editingEntryId = null;
    private Stage     stage;

    // Header
    private ProgressBar headerProgressBar;
    private Label       headerProgressLabel;
    private Label       headerDatesLabel;
    private Label       headerStatsLabel;

    // Progress-update
    private TextField currentPageField;
    private TextField totalPagesField;
    private Slider    progressSlider;
    private Label     progressSliderLabel;

    // Entry editor
    private DatePicker       entryDatePicker;
    private TextField        entryTitleField;
    private ComboBox<String> entryTypeCombo;
    private TextField        entryDurationField;
    private TextField        entryPageStartField;
    private TextField        entryPageEndField;
    private HTMLEditor       richEditor;           // ← editor rico (substituiu TextArea)
    private Label            entryFormModeLabel;
    private Button           entrySubmitBtn;
    private HBox             pagesRowContainer;

    // Timer (play/pause/stop)
    private Button           timerPlayPauseBtn;
    private Button           timerStopBtn;
    private Label            timerLabel;
    private CheckBox         timerAutoSaveChk;
    private java.util.concurrent.ScheduledExecutorService timerExecutor;
    private java.util.concurrent.ScheduledFuture<?> timerFuture;
    private java.util.concurrent.ExecutorService soundExecutor;
    private java.util.concurrent.atomic.AtomicLong timerSeconds = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile boolean timerRunning = false;

    // Index
    private final ObservableList<StudyEntry> entries = FXCollections.observableArrayList();
    private ListView<StudyEntry> indexListView;

    // ── HTML base para editor em branco ────────────────────────────────────
    private static final String BLANK_HTML =
            "<html><body style='font-family:\"Segoe UI\",Inter,sans-serif;" +
            "font-size:13px;color:#0d1b2a;margin:6px 8px;'></body></html>";

    public StudyDiaryWindow(StudyPlan plan,
                            StudyPlanRepository planRepo,
                            StudyEntryRepository entryRepo,
                            Runnable refreshCallback) {
        this.currentPlan     = plan;
        this.planRepo        = planRepo;
        this.entryRepo       = entryRepo;
        this.refreshCallback = refreshCallback;
    }

    public StudyDiaryWindow(StudyPlan plan,
                            StudyPlanRepository planRepo,
                            StudyEntryRepository entryRepo,
                            Runnable refreshCallback,
                            com.pessoal.agenda.app.SharedContext sharedCtx) {
        this(plan, planRepo, entryRepo, refreshCallback);
        this.sharedCtx = sharedCtx;
    }

    public void show() {
        // ── Evita abrir duplicata para o mesmo plano ─────────────────────
        Stage existing = openWindows.get(currentPlan.id());
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        Stage stage = new Stage();
        this.stage = stage;
        stage.setTitle("Diário Científico — " + currentPlan.title());
        stage.setMinWidth(1060); stage.setMinHeight(720);
        stage.initModality(Modality.NONE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader());
        root.setCenter(buildContentArea());

        Scene scene = new Scene(root, 1240, 780);
        var cssUrl = StudyDiaryWindow.class.getResource("/com/pessoal/agenda/app.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        stage.setScene(scene);
        stage.setOnHiding(e -> {
            openWindows.remove(currentPlan.id());
            // stop timer executor and sound executor
            try {
                if (timerFuture != null) timerFuture.cancel(false);
                if (timerExecutor != null) timerExecutor.shutdownNow();
                if (soundExecutor != null) soundExecutor.shutdownNow();
            } catch (Exception ex) { /* ignore */ }
            if (refreshCallback != null) refreshCallback.run();
            // clear shared active session indicator when diary closes
            try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText(""); } catch (Exception ex) { /* ignore */ }
            this.stage = null;
        });

        openWindows.put(currentPlan.id(), stage);
        // create executors for timer and sound tasks
        if (timerExecutor == null) timerExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "study-timer"); t.setDaemon(true); return t; });
        if (soundExecutor == null) soundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "study-sound"); t.setDaemon(true); return t; });
        loadEntries();
        stage.show();
        // when shown, if window is focused, clear active session indicator
        stage.focusedProperty().addListener((obs, o, focused) -> {
            if (focused) {
                try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText(""); } catch (Exception ex) { /* ignore */ }
            } else {
                // if timer is running and window loses focus (minimized), show indicator
                if (timerRunning) {
                    try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText("Sessão: " + timerLabel.getText()); } catch (Exception ex) { /* ignore */ }
                }
            }
        });
        // when window is minimized / restored, update shared indicator
        try {
            stage.iconifiedProperty().addListener((obs, o, iconified) -> {
                if (iconified) {
                    if (timerRunning) {
                        try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText("Sessão: " + timerLabel.getText()); } catch (Exception ex) { /* ignore */ }
                    }
                } else {
                    try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText(""); } catch (Exception ex) { /* ignore */ }
                }
            });
        } catch (Exception ignore) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    // HEADER
    // ══════════════════════════════════════════════════════════════════════

    private VBox buildHeader() {
        Label titleLbl = new Label(currentPlan.title());
        titleLbl.getStyleClass().add("page-title");
        Label typeLbl   = new Label("  " + currentPlan.studyTypeName() + "  ");
        typeLbl.getStyleClass().addAll("study-badge", "badge-type");
        Label statusLbl = new Label("  " + currentPlan.status().label() + "  ");
        statusLbl.getStyleClass().addAll("study-badge", "badge-status-" + currentPlan.status().name().toLowerCase());
        HBox titleRow = new HBox(10, titleLbl, typeLbl, statusLbl);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        headerProgressBar = new ProgressBar(currentPlan.computedProgress() / 100.0);
        headerProgressBar.getStyleClass().add("study-progress-bar");
        headerProgressBar.setPrefWidth(340); headerProgressBar.setPrefHeight(12);
        headerProgressLabel = new Label(currentPlan.progressDisplay());
        headerProgressLabel.getStyleClass().add("study-progress-label");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox progressRow = new HBox(10, new Label("Progresso:"), headerProgressBar, headerProgressLabel, spacer);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        headerDatesLabel  = new Label(buildDatesString()); headerDatesLabel.getStyleClass().add("study-dates-label");
        headerStatsLabel  = new Label("Carregando…");      headerStatsLabel.getStyleClass().add("diary-stats-label");
        HBox infoRow = new HBox(20, headerDatesLabel, headerStatsLabel);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        HBox updateRow = buildProgressUpdateRow();

        VBox header = new VBox(7, titleRow, progressRow, infoRow, new Separator(), updateRow);
        header.getStyleClass().add("diary-header-bar");
        header.setPadding(new Insets(14, 18, 12, 18));
        return header;
    }

    private HBox buildProgressUpdateRow() {
        if (currentPlan.isBook()) {
            currentPageField = new TextField(String.valueOf(currentPlan.currentPage()));
            currentPageField.getStyleClass().add("input-control"); currentPageField.setPrefWidth(75);
            totalPagesField  = new TextField(String.valueOf(currentPlan.totalPages()));
            totalPagesField.getStyleClass().add("input-control");  totalPagesField.setPrefWidth(75);
            Button updateBtn = new Button("Atualizar progresso");
            updateBtn.getStyleClass().add("primary-button");
            updateBtn.setOnAction(e -> updateBookProgress());
            HBox row = new HBox(8, new Label("Pág. atual:"), currentPageField,
                    new Label("  /  Total:"), totalPagesField, updateBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        } else {
            progressSlider = new Slider(0, 100, currentPlan.progressPercent());
            progressSlider.setPrefWidth(260); progressSlider.setBlockIncrement(5);
            progressSliderLabel = new Label(String.format("%.0f%%", currentPlan.progressPercent()));
            progressSlider.valueProperty().addListener(
                    (obs, o, n) -> progressSliderLabel.setText(String.format("%.0f%%", n.doubleValue())));
            Button updateBtn = new Button("Atualizar");
            updateBtn.getStyleClass().add("primary-button");
            updateBtn.setOnAction(e -> updateManualProgress());
            HBox row = new HBox(10, new Label("Progresso manual:"), progressSlider, progressSliderLabel, updateBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONTEÚDO — índice + editor
    // ══════════════════════════════════════════════════════════════════════

    private SplitPane buildContentArea() {
        SplitPane split = new SplitPane(buildIndexPanel(), buildEditorPanel());
        split.setDividerPositions(0.28);
        return split;
    }

    private VBox buildIndexPanel() {
        Label title = new Label("ÍNDICE DE ENTRADAS"); title.getStyleClass().add("section-title");
        indexListView = new ListView<>(entries);
        indexListView.getStyleClass().add("clean-list");
        indexListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(StudyEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"));
                String date  = item.entryDate() != null ? item.entryDate().format(fmt) : "--/--";
                String t     = item.hasTitle() ? item.entryTitle() : "(sem título)";
                String pages = item.hasPages() ? "  pp." + item.pageStart() + "–" + item.pageEnd() : "";
                String dur   = item.durationMinutes() > 0 ? "  " + item.durationMinutes() + " min" : "";
                setText(date + "  " + t + pages + dur);
            }
        });
        VBox.setVgrow(indexListView, Priority.ALWAYS);
        indexListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> { if (sel != null) loadEntryIntoForm(sel); });

        Button newBtn = new Button("+ Nova entrada");
        newBtn.getStyleClass().add("primary-button"); newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setOnAction(e -> clearEntryForm());

        Button removeBtn = new Button("✕  Remover entrada selecionada");
        removeBtn.getStyleClass().add("danger-button"); removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> {
            StudyEntry sel = indexListView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Remover entrada");
            confirm.setHeaderText("Remover esta entrada do diário?");
            confirm.setContentText("\"" + (sel.hasTitle() ? sel.entryTitle() : "entrada") + "\" — ação permanente.");
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                entryRepo.deleteById(sel.id());
                loadEntries(); clearEntryForm();
                if (refreshCallback != null) refreshCallback.run();
            });
        });

        VBox panel = new VBox(8, title, indexListView, newBtn, removeBtn);
        panel.setPadding(new Insets(12)); panel.getStyleClass().add("section-card");
        return panel;
    }

    private VBox buildEditorPanel() {
        entryFormModeLabel = new Label("Nova entrada no diário");
        entryFormModeLabel.getStyleClass().add("section-title");

        // ── campos de metadados ────────────────────────────────────────────
        entryDatePicker = new DatePicker(LocalDate.now()); entryDatePicker.getStyleClass().add("input-control");

        entryTypeCombo = new ComboBox<>();
        entryTypeCombo.getStyleClass().add("input-control");
        entryTypeCombo.getItems().addAll("Leitura","Anotação","Resumo","Análise","Hipótese",
                "Experimento","Discussão","Conclusão","Revisão","Questão","Outro");
        entryTypeCombo.setValue("Leitura");

        entryDurationField = new TextField();
        entryDurationField.getStyleClass().add("input-control");
        entryDurationField.setPromptText("min"); entryDurationField.setPrefWidth(75);

        entryTitleField = new TextField();
        entryTitleField.getStyleClass().add("input-control");
        entryTitleField.setPromptText("Título: ex. Cap. 3, Hipótese α, Análise espectral…");

        entryPageStartField = new TextField(); entryPageStartField.getStyleClass().add("input-control");
        entryPageStartField.setPromptText("início"); entryPageStartField.setPrefWidth(70);
        entryPageEndField   = new TextField(); entryPageEndField.getStyleClass().add("input-control");
        entryPageEndField.setPromptText("fim"); entryPageEndField.setPrefWidth(70);

        GridPane form = new GridPane();
        form.getStyleClass().add("form-grid"); form.setHgap(10); form.setVgap(7);
        form.add(new Label("Data:"),             0, 0); form.add(entryDatePicker,    1, 0);
        form.add(new Label("Tipo:"),             2, 0); form.add(entryTypeCombo,     3, 0);
        form.add(new Label("Duração (min):"),    4, 0); form.add(entryDurationField, 5, 0);
        form.add(new Label("Título da entrada:"),0, 1);
        form.add(entryTitleField,                1, 1, 5, 1);
        GridPane.setHgrow(entryTitleField, Priority.ALWAYS);

        pagesRowContainer = new HBox(8,
                new Label("Páginas cobertas:"), entryPageStartField, new Label("→"), entryPageEndField);
        pagesRowContainer.setAlignment(Pos.CENTER_LEFT);
        pagesRowContainer.setVisible(currentPlan.isBook()); pagesRowContainer.setManaged(currentPlan.isBook());

        // ── barra de ferramentas extra (cola sem formatação) ───────────────
        Button pasteNoFmtBtn = new Button("📋  Colar sem formatação");
        pasteNoFmtBtn.getStyleClass().add("secondary-button");
        pasteNoFmtBtn.setTooltip(new Tooltip(
                "Cola o conteúdo da área de transferência como texto puro,\n" +
                "removendo toda formatação HTML copiada de sites ou outros editores."));
        pasteNoFmtBtn.setOnAction(e -> pasteAsPlainText());

        Label editorHint = new Label(
                "Ctrl+B = negrito  ·  Ctrl+I = itálico  ·  Ctrl+U = sublinhado  ·  " +
                "Ctrl+V = colar com formatação");
        editorHint.getStyleClass().add("study-plan-detail");

        HBox extraToolbar = new HBox(12, pasteNoFmtBtn, editorHint);
        extraToolbar.setAlignment(Pos.CENTER_LEFT);
        extraToolbar.getStyleClass().add("rich-editor-toolbar");
        extraToolbar.setPadding(new Insets(4, 6, 4, 6));

        // ── Timer controls (play / pause / stop) ───────────────────────
        timerPlayPauseBtn = new Button("▶");
        timerPlayPauseBtn.getStyleClass().addAll("icon-button");
        timerPlayPauseBtn.setTooltip(new Tooltip("Iniciar / Pausar sessão de estudo"));
        timerStopBtn = new Button("■");
        timerStopBtn.getStyleClass().addAll("icon-button", "danger-button");
        timerStopBtn.setTooltip(new Tooltip("Parar sessão (salva e registra tempo)"));
        timerLabel = new Label("00:00:00"); timerLabel.getStyleClass().add("study-dates-label");
        timerAutoSaveChk = new CheckBox("Salvar automaticamente ao parar"); timerAutoSaveChk.setSelected(true);

        HBox timerRow = new HBox(8, timerPlayPauseBtn, timerStopBtn, timerLabel, timerAutoSaveChk);
        timerRow.setAlignment(Pos.CENTER_LEFT);
        timerRow.setPadding(new Insets(6,0,6,0));

        // Handlers
        timerPlayPauseBtn.setOnAction(e -> toggleTimer());
        timerStopBtn.setOnAction(e -> stopTimerAndSave());

        // executor will be created when window opens

        // ── editor rico ────────────────────────────────────────────────────
        richEditor = new HTMLEditor();
        richEditor.getStyleClass().add("diary-rich-editor");
        richEditor.setHtmlText(BLANK_HTML);
        VBox.setVgrow(richEditor, Priority.ALWAYS);

        // ── botões ─────────────────────────────────────────────────────────
        entrySubmitBtn = new Button("Salvar entrada");
        entrySubmitBtn.getStyleClass().add("primary-button");
        entrySubmitBtn.setOnAction(e -> saveCurrentEntry());

        Button cancelBtn = new Button("Cancelar / Limpar");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> clearEntryForm());

        HBox btnRow = new HBox(8, entrySubmitBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Label notesLbl = new Label("Anotações / Diário científico:");
        notesLbl.getStyleClass().add("form-label");

        VBox panel = new VBox(8, entryFormModeLabel, form, pagesRowContainer,
                notesLbl, extraToolbar, timerRow, richEditor, btnRow);
        panel.setPadding(new Insets(12)); panel.getStyleClass().add("section-card");
        return panel;
    }

    // ══════════════════════════════════════════════════════════════════════
    // LÓGICA DE ENTRADAS
    // ══════════════════════════════════════════════════════════════════════

    private void loadEntries() {
        entries.setAll(entryRepo.findByStudyId(currentPlan.id()));
        if (!entries.isEmpty()) indexListView.getSelectionModel().selectLast();
        refreshHeaderStats();
    }

    private void refreshHeaderStats() {
        int count    = entries.size();
        int totalMin = entries.stream().mapToInt(StudyEntry::durationMinutes).sum();
        int hrs = totalMin / 60, mins = totalMin % 60;
        String lastDate = entries.isEmpty() ? "—"
                : entries.get(entries.size() - 1).entryDate()
                         .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")));
        headerStatsLabel.setText(String.format(
                "Entradas: %d   ▸   Tempo total: %dh %02dmin   ▸   Última sessão: %s",
                count, hrs, mins, lastDate));
        double prog = currentPlan.computedProgress();
        headerProgressBar.getStyleClass().removeAll("progress-complete","progress-high","progress-low");
        if      (prog >= 100) headerProgressBar.getStyleClass().add("progress-complete");
        else if (prog >= 60)  headerProgressBar.getStyleClass().add("progress-high");
        else if (prog <  25)  headerProgressBar.getStyleClass().add("progress-low");
    }

    private void loadEntryIntoForm(StudyEntry e) {
        editingEntryId = e.id();
        entryDatePicker.setValue(e.entryDate() != null ? e.entryDate() : LocalDate.now());
        entryTitleField.setText(e.hasTitle() ? e.entryTitle() : "");
        entryDurationField.setText(e.durationMinutes() > 0 ? String.valueOf(e.durationMinutes()) : "");
        entryPageStartField.setText(e.pageStart() > 0 ? String.valueOf(e.pageStart()) : "");
        entryPageEndField.setText(e.pageEnd()   > 0 ? String.valueOf(e.pageEnd())   : "");
        // Carregar conteúdo — compatível com texto puro (entradas antigas) e HTML
        String html = e.content() != null ? e.content() : "";
        if (!html.trim().toLowerCase().startsWith("<html")) {
            html = "<html><body style='font-family:\"Segoe UI\",Inter,sans-serif;font-size:13px;'><p>"
                    + html.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                           .replace("\n","<br>")
                    + "</p></body></html>";
        }
        richEditor.setHtmlText(html);
        entryFormModeLabel.setText("Editando: \"" + (e.hasTitle() ? e.entryTitle() : "entrada") + "\"");
        entrySubmitBtn.setText("Salvar alterações");
    }

    private void clearEntryForm() {
        editingEntryId = null;
        entryDatePicker.setValue(LocalDate.now());
        entryTitleField.clear(); entryDurationField.clear();
        entryPageStartField.clear(); entryPageEndField.clear();
        richEditor.setHtmlText(BLANK_HTML);
        entryTypeCombo.setValue("Leitura");
        entryFormModeLabel.setText("Nova entrada no diário");
        entrySubmitBtn.setText("Salvar entrada");
        indexListView.getSelectionModel().clearSelection();
    }

    private void saveCurrentEntry() {
        String title   = entryTitleField.getText().trim();
        String content = richEditor.getHtmlText();               // HTML completo
        String type    = entryTypeCombo.getValue() != null ? entryTypeCombo.getValue() : "Leitura";
        String fTitle  = title.isBlank() ? type : title;

        // Verificar se o corpo HTML está vazio
        boolean bodyEmpty = content == null || content.isBlank()
                || content.replaceAll("<[^>]+>","").isBlank();
        if (bodyEmpty && fTitle.equals(type)) return;

        LocalDate date = entryDatePicker.getValue() != null ? entryDatePicker.getValue() : LocalDate.now();
        int duration   = parseInt(entryDurationField.getText());
        int pgStart    = parseInt(entryPageStartField.getText());
        int pgEnd      = parseInt(entryPageEndField.getText());

        if (editingEntryId == null) {
            entryRepo.save(currentPlan.id(), fTitle, date, content, duration, pgStart, pgEnd);
        } else {
            entryRepo.update(editingEntryId, fTitle, date, content, duration, pgStart, pgEnd);
        }
        loadEntries(); clearEntryForm();
        if (refreshCallback != null) refreshCallback.run();
    }

    // ── Timer logic ───────────────────────────────────────────────────

    private void toggleTimer() {
        if (!timerRunning) {
            // start
            timerRunning = true;
            timerPlayPauseBtn.setText("⏸");
            playSignal("start"); // start tone or WAV
            // set shared active session indicator if diary not focused
            try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText("Sessão: " + timerLabel.getText()); } catch (Exception ex) { /* ignore */ }
            long start = System.currentTimeMillis();
            timerFuture = timerExecutor.scheduleAtFixedRate(this::tick, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
        } else {
            // pause
            timerRunning = false;
            timerPlayPauseBtn.setText("▶");
            playSignal("pause"); // pause tone or WAV
            // clear shared indicator
            try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText(""); } catch (Exception ex) { /* ignore */ }
            if (timerFuture != null) timerFuture.cancel(false);
        }
    }

    private void stopTimerAndSave() {
        if (timerFuture != null) timerFuture.cancel(false);
        timerRunning = false; timerPlayPauseBtn.setText("▶");
        playSignal("stop"); // stop tone or WAV
        try { if (sharedCtx != null) sharedCtx.activeStudySessionLabel.setText(""); } catch (Exception ex) { /* ignore */ }
        if (timerSeconds.get() > 0 && timerAutoSaveChk.isSelected()) {
            // ask user to confirm and allow editing title/minutes before saving
            int minutes = (int) Math.round(timerSeconds.get() / 60.0);
            if (minutes <= 0) minutes = 1;
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Confirmar sessão");
            dlg.setHeaderText("Salvar sessão de estudo?");

            ButtonType saveBtn = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
            dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

            GridPane gp = new GridPane(); gp.setHgap(10); gp.setVgap(10); gp.setPadding(new Insets(10));
            TextField titleField = new TextField("Sessão contabilizada — " + minutes + " min");
            Spinner<Integer> minutesSpinner = new Spinner<>(1, 24 * 60, minutes);
            minutesSpinner.setEditable(true);
            gp.add(new Label("Título:"), 0, 0); gp.add(titleField, 1, 0);
            gp.add(new Label("Duração (min):"), 0, 1); gp.add(minutesSpinner, 1, 1);

            dlg.getDialogPane().setContent(gp);
            // focus title
            Platform.runLater(titleField::requestFocus);

            dlg.setResultConverter(bt -> bt == saveBtn ? saveBtn : null);
            var res = dlg.showAndWait();
            if (res.isPresent() && res.get() == saveBtn) {
                int m = minutesSpinner.getValue();
                if (m <= 0) m = 1;
                entryDatePicker.setValue(LocalDate.now());
                entryDurationField.setText(String.valueOf(m));
                entryTitleField.setText(titleField.getText() == null || titleField.getText().isBlank()
                        ? "Sessão contabilizada — " + m + " min" : titleField.getText());
                saveCurrentEntry();
                loadEntries();
            }
        }
        timerSeconds.set(0); updateTimerLabel();
    }

    private void tick() {
        timerSeconds.incrementAndGet();
        Platform.runLater(this::updateTimerLabel);
    }

    private void updateTimerLabel() {
        long s = timerSeconds.get();
        long hh = s / 3600; long mm = (s % 3600) / 60; long ss = s % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", hh, mm, ss));
        // update shared context indicator when diary is minimized or not focused
        try {
            if (sharedCtx != null && timerRunning && stage != null) {
                boolean show = false;
                try { show = stage.isIconified() || !stage.isFocused(); } catch (Exception ex) { show = stage.isIconified(); }
                if (show) sharedCtx.activeStudySessionLabel.setText("Sessão: " + timerLabel.getText());
                else sharedCtx.activeStudySessionLabel.setText("");
            }
        } catch (Throwable ignored) { }
    }

    private void playTone(int freq, int ms) {
        playSignal("tone");
    }

    private void playSignal(String name) {
        if (soundExecutor == null) return;
        soundExecutor.submit(() -> {
            boolean played = false;
            // helper to report status messages to user
            java.util.function.Consumer<String> report = msg -> {
                try { if (sharedCtx != null) sharedCtx.setStatus(msg); } catch (Throwable ignored) {}
            };

            // Attempt 1: play packaged WAV resource
            try {
                String res = "/sounds/" + name + ".wav";
                java.net.URL url = StudyDiaryWindow.class.getResource(res);
                if (url != null) {
                    try (AudioInputStream ais = AudioSystem.getAudioInputStream(url)) {
                        Clip clip = AudioSystem.getClip();
                        clip.open(ais);
                        final Object lock = new Object();
                        clip.addLineListener(event -> {
                            if (event.getType() == LineEvent.Type.STOP) {
                                clip.close(); synchronized(lock) { lock.notifyAll(); }
                            }
                        });
                        clip.start();
                        synchronized(lock) { lock.wait(1500); }
                        played = true;
                    }
                }
            } catch (Throwable ex) {
                System.err.println("[StudyDiaryWindow] failed playing resource WAV: " + ex.getMessage());
            }

            if (!played) {
                // Attempt 2: embedded generated tone
                try (AudioInputStream ais = EmbeddedSounds.getAudioInputStream(name)) {
                    if (ais != null) {
                        try {
                            Clip clip = AudioSystem.getClip();
                            clip.open(ais);
                            final Object lock = new Object();
                            clip.addLineListener(event -> {
                                if (event.getType() == LineEvent.Type.STOP) {
                                    clip.close(); synchronized(lock) { lock.notifyAll(); }
                                }
                            });
                            clip.start();
                            synchronized(lock) { lock.wait(1500); }
                            played = true;
                        } catch (Throwable ex) {
                            System.err.println("[StudyDiaryWindow] failed playing embedded tone: " + ex.getMessage());
                        }
                    }
                } catch (Throwable ex) {
                    System.err.println("[StudyDiaryWindow] error obtaining embedded audio: " + ex.getMessage());
                }
            }

            if (!played) {
                // Attempt 3: invoke system audio player (aplay, paplay, play) by writing resource to temp file
                try {
                    java.net.URL url = StudyDiaryWindow.class.getResource("/sounds/" + name + ".wav");
                    java.io.File tmp = java.io.File.createTempFile("agenda-sound-", ".wav");
                    tmp.deleteOnExit();
                    boolean wrote = false;
                    if (url != null) {
                        try (java.io.InputStream is = url.openStream(); java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                            byte[] buf = new byte[4096]; int r; while ((r = is.read(buf)) > 0) fos.write(buf, 0, r);
                            wrote = true;
                        } catch (Throwable ex) { System.err.println("[StudyDiaryWindow] failed writing resource to temp file: " + ex.getMessage()); }
                    }
                    if (!wrote) {
                        // try embedded tone
                        try (AudioInputStream eis = EmbeddedSounds.getAudioInputStream(name); java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                            if (eis != null) {
                                byte[] buf = new byte[4096]; int r; while ((r = eis.read(buf)) > 0) fos.write(buf, 0, r);
                                wrote = true;
                            }
                        } catch (Throwable ex) { System.err.println("[StudyDiaryWindow] failed writing embedded to temp file: " + ex.getMessage()); }
                    }
                    if (wrote) {
                        String[] candidates = new String[]{"aplay", "paplay", "play"};
                        String player = null;
                        for (String c : candidates) {
                            try { if (java.lang.Runtime.getRuntime().exec(new String[]{"which", c}).waitFor() == 0) { player = c; break; } } catch (Throwable ignore) {}
                        }
                        if (player != null) {
                            try {
                                Process p = new ProcessBuilder(player, tmp.getAbsolutePath()).start();
                                if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroy();
                                played = true;
                            } catch (Throwable ex) { System.err.println("[StudyDiaryWindow] native player failed: " + ex.getMessage()); }
                        }
                    }
                } catch (Throwable ex) {
                    System.err.println("[StudyDiaryWindow] native player fallback error: " + ex.getMessage());
                }

                // final fallback: system beep
                if (!played) {
                    try {
                        Toolkit.getDefaultToolkit().beep();
                        played = true;
                    } catch (Throwable ex) {
                        System.err.println("[StudyDiaryWindow] final fallback beep failed: " + ex.getMessage());
                    }
                }
            }

            if (!played) {
                report.accept("Áudio: não foi possível reproduzir nenhum sinal (verifique drivers/ALSA/PulseAudio)");
            } else {
                report.accept("Áudio: sinal reproduzido");
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // COLAR SEM FORMATAÇÃO
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Cola o conteúdo da área de transferência como texto puro no ponto de
     * inserção do editor, usando {@code document.execCommand('insertText')}.
     * Isso remove qualquer formatação HTML proveniente de sites ou outros editores.
     */
    private void pasteAsPlainText() {
        Clipboard cb = Clipboard.getSystemClipboard();
        String plain = cb.getString();
        if (plain == null || plain.isEmpty()) return;

        // Obter o WebView interno do HTMLEditor via lookup CSS
        WebView webView = (WebView) richEditor.lookup(".web-view");
        if (webView == null) return;

        // Escapar caracteres perigosos para JS e preservar quebras de linha
        String safe = plain
                .replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\r\n", "\\n")
                .replace("\n",  "\\n")
                .replace("\r",  "\\n");

        webView.getEngine().executeScript(
                "document.execCommand('insertText', false, '" + safe + "')");
    }

    // ══════════════════════════════════════════════════════════════════════
    // ATUALIZAÇÃO DE PROGRESSO
    // ══════════════════════════════════════════════════════════════════════

    private void updateBookProgress() {
        int curPage  = parseInt(currentPageField.getText());
        int totPages = parseInt(totalPagesField.getText());
        if (totPages <= 0) return;
        double pct = Math.min(100.0, curPage * 100.0 / totPages);
        StudyPlanStatus status = pct >= 100.0 ? StudyPlanStatus.CONCLUIDO : StudyPlanStatus.EM_ANDAMENTO;
        planRepo.updateProgressFull(currentPlan.id(), curPage, totPages, pct, status);
        reloadCurrentPlan(); refreshProgressUI();
        if (refreshCallback != null) refreshCallback.run();
    }

    private void updateManualProgress() {
        double pct = progressSlider.getValue();
        StudyPlanStatus status = pct >= 100.0 ? StudyPlanStatus.CONCLUIDO : StudyPlanStatus.EM_ANDAMENTO;
        planRepo.updateProgress(currentPlan.id(), currentPlan.currentPage(), pct, status);
        reloadCurrentPlan(); refreshProgressUI();
        if (refreshCallback != null) refreshCallback.run();
    }

    private void reloadCurrentPlan() {
        currentPlan = planRepo.findById(currentPlan.id()).orElse(currentPlan);
    }

    private void refreshProgressUI() {
        headerProgressBar.setProgress(currentPlan.computedProgress() / 100.0);
        headerProgressLabel.setText(currentPlan.progressDisplay());
        headerDatesLabel.setText(buildDatesString());
        refreshHeaderStats();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private String buildDatesString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
        StringBuilder sb = new StringBuilder();
        if (currentPlan.startDate()  != null) sb.append("Início: ").append(currentPlan.startDate().format(fmt));
        if (currentPlan.targetDate() != null) {
            if (!sb.isEmpty()) sb.append("     ");
            sb.append("Prazo: ").append(currentPlan.targetDate().format(fmt));
            long days = currentPlan.daysUntilTarget();
            if (days >= 0) sb.append("  (").append(days).append(" dias restantes)");
            else           sb.append("  ⚠ PRAZO VENCIDO há ").append(-days).append(" dias");
        }
        return sb.isEmpty() ? "Sem datas definidas" : sb.toString();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}

