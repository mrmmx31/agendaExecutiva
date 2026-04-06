package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.model.IdeaChecklistItem;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.repository.IdeaChecklistRepository;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Janela de detalhe e edição de uma Ideia / Projeto Científico.
 *
 * A seção "Próximas Ações" suporta dois modos intercambiáveis:
 *   • Texto livre  — área de texto simples, sempre disponível
 *   • Checklist    — lista de itens com checkbox, progresso e persistência imediata
 */
public class ProjectIdeaDetailWindow {

    private static final java.util.Map<Long, Stage> openWindows = new java.util.HashMap<>();

    private final ProjectIdea idea;
    private final ProjectIdeaRepository repo;
    private final Runnable onSaved;
    private IdeaChecklistRepository checklistRepo;

    // ── campos de formulário ──────────────────────────────────────────────────
    private TextField titleField;
    private ComboBox<String> statusCombo;
    private ComboBox<String> categoryCombo;
    private ComboBox<String> priorityCombo;
    private ComboBox<String> typeCombo;
    private ComboBox<String> impactCombo;
    private Spinner<Integer> feasibilitySpinner;
    private Spinner<Integer> hoursSpinner;
    private DatePicker startDatePicker;
    private DatePicker targetDatePicker;
    private TextArea descriptionArea;
    private TextArea methodologyArea;
    private TextArea nextActionsArea;
    private TextField keywordsField;
    private TextArea referencesArea;

    // ── estado do checklist ───────────────────────────────────────────────────
    private String nextActionsMode = "text";
    private final List<ChecklistItemState> checklistItems = new ArrayList<>();
    private VBox checklistItemsBox;
    private Label checklistProgressLabel;
    private VBox checklistPane;

    /** Estado mutável de um item de checklist na UI. */
    private static class ChecklistItemState {
        long   dbId;   // 0 = ainda não persistido
        String text;
        boolean done;
        ChecklistItemState(long dbId, String text, boolean done) {
            this.dbId = dbId; this.text = text; this.done = done;
        }
    }

    public ProjectIdeaDetailWindow(ProjectIdea idea, ProjectIdeaRepository repo, Runnable onSaved) {
        this.idea    = idea;
        this.repo    = repo;
        this.onSaved = onSaved;
    }

    public void show() {
        // Previne abertura duplicada da mesma ideia
        if (openWindows.containsKey(idea.id())) {
            Stage existing = openWindows.get(idea.id());
            existing.toFront(); existing.requestFocus();
            return;
        }

        checklistRepo = AppContextHolder.get().ideaChecklistRepository();

        if (idea.id() > 0) {
            nextActionsMode = repo.getNextActionsMode(idea.id());
            if ("checklist".equals(nextActionsMode)) {
                for (IdeaChecklistItem it : checklistRepo.findByIdeaId(idea.id())) {
                    checklistItems.add(new ChecklistItemState(it.id(), it.text(), it.done()));
                }
            }
        }

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle("Ideia / Projeto — " + idea.title());
        stage.setMinWidth(780); stage.setMinHeight(680);
        openWindows.put(idea.id(), stage);
        stage.setOnHidden(e -> openWindows.remove(idea.id()));

        // ── Layout principal ───────────────────────────────────────────────────
        VBox root = new VBox(0);
        root.getStylesheets().add(
                getClass().getResource("/com/pessoal/agenda/app.css").toExternalForm());
        root.getStyleClass().add("app-root");
        root.getChildren().add(buildHeader());

        ScrollPane scroll = new ScrollPane(buildForm());
        scroll.setFitToWidth(true); scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
        root.getChildren().add(buildFooter(stage));

        stage.setScene(new Scene(root, 820, 740));
        stage.show();
    }

    // ── Cabeçalho da janela ──────────────────────────────────────────────────

    private HBox buildHeader() {
        Label title = new Label(idea.id() == 0 ? "Nova Ideia / Projeto" : "Ideia / Projeto");
        title.getStyleClass().add("page-title");
        Label sub = new Label(idea.typeLabel() + "  ·  " + idea.priorityLabel()
                + "  ·  " + idea.impactLabel());
        sub.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, title, sub);
        HBox hdr = new HBox(titles);
        hdr.getStyleClass().add("header-bar");
        hdr.setPadding(new Insets(14, 20, 14, 20));
        hdr.setAlignment(Pos.CENTER_LEFT);
        return hdr;
    }

    // ── Formulário completo ──────────────────────────────────────────────────

    private VBox buildForm() {
        VBox form = new VBox(14);
        form.setPadding(new Insets(18));

        // ── Linha 1: Título ────────────────────────────────────────────────
        titleField = new TextField(nvl(idea.title(), ""));
        titleField.getStyleClass().add("input-control");
        titleField.setPromptText("Título da ideia ou projeto");
        titleField.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        form.getChildren().add(fieldRow("Título *", titleField));

        // ── Linha 2: Status / Prioridade / Tipo / Impacto ─────────────────
        statusCombo   = buildCombo(
                new String[]{"nova","em_validacao","prototipagem","em_execucao","pausada","concluida","abandonada"},
                new String[]{"Nova","Em Validação","Prototipagem","Em Execução","Pausada","Concluída","Abandonada"},
                nvl(idea.status(), "nova"));
        priorityCombo = buildCombo(
                new String[]{"CRITICA","ALTA","NORMAL","BAIXA"},
                new String[]{"🔴 Crítica","🟠 Alta","🔵 Normal","🟢 Baixa"},
                nvl(idea.priority(), "NORMAL"));
        typeCombo     = buildCombo(
                new String[]{"GERAL","PESQUISA","ENGENHARIA","HIPOTESE","EXPERIMENTO","SOFTWARE","METODOLOGIA","INOVACAO"},
                new String[]{"📋 Geral","🔬 Pesquisa","⚙ Engenharia","💡 Hipótese","🧪 Experimento","💻 Software","📐 Metodologia","🚀 Inovação"},
                nvl(idea.ideaType(), "GERAL"));
        impactCombo   = buildCombo(
                new String[]{"BAIXO","MEDIO","ALTO","REVOLUCIONARIO"},
                new String[]{"▽ Baixo","◈ Médio","▲ Alto","⚡ Revolucionário"},
                nvl(idea.impactLevel(), "MEDIO"));

        HBox metaRow = new HBox(10,
                labeledControl("Status",     statusCombo),
                labeledControl("Prioridade", priorityCombo),
                labeledControl("Tipo",       typeCombo),
                labeledControl("Impacto",    impactCombo));
        metaRow.setFillHeight(true);
        HBox.setHgrow(statusCombo, Priority.ALWAYS); HBox.setHgrow(priorityCombo, Priority.ALWAYS);
        HBox.setHgrow(typeCombo,   Priority.ALWAYS); HBox.setHgrow(impactCombo,   Priority.ALWAYS);
        form.getChildren().add(metaRow);

        // ── Linha 3: Categoria / Viabilidade / Horas estimadas ────────────
        categoryCombo = new ComboBox<>();
        categoryCombo.getStyleClass().add("input-control");
        categoryCombo.setEditable(true);
        categoryCombo.getItems().addAll("Geral","Pesquisa","Engenharia","Medicina","Software",
                "Biologia","Física","Química","Matemática","Dados","Inovação");
        categoryCombo.setValue(nvl(idea.category(), "Geral"));

        feasibilitySpinner = new Spinner<>(1, 5, Math.max(1, Math.min(5, idea.feasibility())));
        feasibilitySpinner.setEditable(true);
        feasibilitySpinner.getStyleClass().add("input-control");
        feasibilitySpinner.setPrefWidth(80);

        hoursSpinner = new Spinner<>(0, 10000, idea.estimatedHours());
        hoursSpinner.setEditable(true);
        hoursSpinner.getStyleClass().add("input-control");
        hoursSpinner.setPrefWidth(90);

        startDatePicker  = new DatePicker(idea.startDate());
        startDatePicker.getStyleClass().add("input-control");
        startDatePicker.setPromptText("dd/MM/yyyy");

        targetDatePicker = new DatePicker(idea.targetDate());
        targetDatePicker.getStyleClass().add("input-control");
        targetDatePicker.setPromptText("dd/MM/yyyy");

        HBox row3 = new HBox(10,
                labeledControl("Categoria",          categoryCombo),
                labeledControl("Viabilidade (1-5)",  feasibilitySpinner),
                labeledControl("Horas estimadas",    hoursSpinner),
                labeledControl("Início planejado",   startDatePicker),
                labeledControl("Prazo-alvo",         targetDatePicker));
        HBox.setHgrow(categoryCombo, Priority.ALWAYS);
        form.getChildren().add(row3);

        // ── Seção: Descrição / Hipótese / Abstract ─────────────────────────
        descriptionArea = buildTextArea(nvl(idea.description(), ""), 6,
                "Descreva a ideia, hipótese científica, problema a resolver ou objetivo do projeto.\n"
              + "Pode incluir contexto, motivação e estado-da-arte resumido.");
        form.getChildren().add(sectionCard("📄 Descrição / Hipótese / Abstract", descriptionArea));

        // ── Seção: Metodologia ─────────────────────────────────────────────
        methodologyArea = buildTextArea(nvl(idea.methodology(), ""), 4,
                "Ex: Método Científico, Design Thinking, Agile, Revisão Bibliográfica...");
        form.getChildren().add(sectionCard("📐 Metodologia", methodologyArea));

        // ── Próximas Ações (texto OU checklist) ──────────────────────────────
        form.getChildren().add(buildNextActionsSection());

        // ── Linha: Palavras-chave ──────────────────────────────────────────
        keywordsField = new TextField(nvl(idea.keywords(), ""));
        keywordsField.getStyleClass().add("input-control");
        keywordsField.setPromptText("Ex: machine learning, proteínas, otimização, PCR...");
        form.getChildren().add(fieldRow("🏷 Palavras-chave (separadas por vírgula)", keywordsField));

        // ── Seção: Referências ─────────────────────────────────────────────
        referencesArea = buildTextArea(nvl(idea.referencesText(), ""), 4,
                "Referências bibliográficas, DOIs, URLs, artigos, livros, datasets...\n"
              + "Ex: Smith et al. (2023). Nature, doi:10.1038/...");
        form.getChildren().add(sectionCard("📚 Referências / Fontes", referencesArea));

        return form;
    }

    // ── Seção Próximas Ações (texto OU checklist) ─────────────────────────────

    private VBox buildNextActionsSection() {
        ToggleGroup modeGroup     = new ToggleGroup();
        ToggleButton textBtn      = new ToggleButton("📝 Texto");
        ToggleButton checklistBtn = new ToggleButton("☑  Checklist");
        textBtn.setToggleGroup(modeGroup);
        checklistBtn.setToggleGroup(modeGroup);
        textBtn.getStyleClass().add("view-toggle-btn");
        checklistBtn.getStyleClass().add("view-toggle-btn");

        if ("checklist".equals(nextActionsMode)) checklistBtn.setSelected(true);
        else                                     textBtn.setSelected(true);

        checklistProgressLabel = new Label();
        checklistProgressLabel.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #2e7d32;"
                + " -fx-background-color: #e8f5e9; -fx-padding: 3 10 3 10;"
                + " -fx-background-radius: 12;");
        updateChecklistProgress();
        checklistProgressLabel.setVisible("checklist".equals(nextActionsMode));

        Label sectionTitle = new Label("✅ Próximas Ações");
        sectionTitle.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #03183e;");
        Region hSpacer = new Region(); HBox.setHgrow(hSpacer, Priority.ALWAYS);

        HBox headerRow = new HBox(6, sectionTitle, hSpacer,
                checklistProgressLabel, new HBox(2, textBtn, checklistBtn));
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setStyle("-fx-padding: 0 0 8 0;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");

        // ── modo texto ────────────────────────────────────────────────────
        nextActionsArea = buildTextArea(nvl(idea.nextActions(), ""), 4,
                "Liste as próximas ações (uma por linha):\n- Configurar ambiente\n- Revisar documentação");
        nextActionsArea.setVisible("text".equals(nextActionsMode));
        nextActionsArea.setManaged("text".equals(nextActionsMode));

        // ── modo checklist ────────────────────────────────────────────────
        checklistItemsBox = new VBox(3);
        checklistItemsBox.setPadding(new Insets(6));
        refreshChecklistItemsUI();

        ScrollPane itemsScroll = new ScrollPane(checklistItemsBox);
        itemsScroll.setFitToWidth(true);
        itemsScroll.setPrefHeight(200); itemsScroll.setMaxHeight(350);
        itemsScroll.setStyle("-fx-background-color: transparent;"
                + " -fx-border-color: #dce8f5; -fx-border-radius: 5; -fx-background-radius: 5;");

        TextField newItemField = new TextField();
        newItemField.setPromptText("Descreva a próxima ação e pressione Enter...");
        newItemField.getStyleClass().add("input-control");
        HBox.setHgrow(newItemField, Priority.ALWAYS);

        Button addItemBtn = new Button("＋  Adicionar");
        addItemBtn.getStyleClass().add("primary-button");
        addItemBtn.setStyle("-fx-font-size: 11px;");

        Runnable doAdd = () -> {
            String t = newItemField.getText().trim();
            if (t.isBlank()) return;
            newItemField.clear();
            ChecklistItemState s = new ChecklistItemState(0, t, false);
            if (idea.id() > 0) {
                IdeaChecklistItem saved = checklistRepo.addItem(idea.id(), t);
                s.dbId = saved.id();
            }
            checklistItems.add(s);
            refreshChecklistItemsUI();
            updateChecklistProgress();
            Platform.runLater(() -> itemsScroll.setVvalue(1.0));
        };
        newItemField.setOnAction(e -> doAdd.run());
        addItemBtn.setOnAction(e -> doAdd.run());

        HBox addRow = new HBox(6, newItemField, addItemBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);
        addRow.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: #d6e4f5; -fx-border-width: 1 0 0 0;");

        checklistPane = new VBox(6, itemsScroll, addRow);
        checklistPane.setVisible("checklist".equals(nextActionsMode));
        checklistPane.setManaged("checklist".equals(nextActionsMode));

        // ── toggle ────────────────────────────────────────────────────────
        textBtn.setOnAction(e -> {
            if (!textBtn.isSelected()) { textBtn.setSelected(true); return; }
            nextActionsMode = "text";
            nextActionsArea.setVisible(true);  nextActionsArea.setManaged(true);
            checklistPane.setVisible(false);   checklistPane.setManaged(false);
            checklistProgressLabel.setVisible(false);
        });
        checklistBtn.setOnAction(e -> {
            if (!checklistBtn.isSelected()) { checklistBtn.setSelected(true); return; }
            nextActionsMode = "checklist";
            nextActionsArea.setVisible(false); nextActionsArea.setManaged(false);
            checklistPane.setVisible(true);    checklistPane.setManaged(true);
            checklistProgressLabel.setVisible(true);
            updateChecklistProgress();
        });

        VBox sectionContent = new VBox(8, nextActionsArea, checklistPane);
        VBox card = new VBox(8, headerRow, sectionContent);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    // ── Checklist UI helpers ──────────────────────────────────────────────────

    private void refreshChecklistItemsUI() {
        if (checklistItemsBox == null) return;
        checklistItemsBox.getChildren().clear();
        List<ChecklistItemState> snapshot = new ArrayList<>(checklistItems);

        if (snapshot.isEmpty()) {
            Label empty = new Label("Nenhuma ação adicionada. Use o campo abaixo para adicionar.");
            empty.setStyle("-fx-text-fill: #9eafc0; -fx-font-size: 11px; -fx-padding: 10 8 10 8;");
            checklistItemsBox.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < snapshot.size(); i++) {
            final ChecklistItemState item = snapshot.get(i);

            CheckBox cb = new CheckBox();
            cb.setSelected(item.done);

            Label textLbl = new Label(item.text);
            textLbl.setWrapText(true);
            textLbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textLbl, Priority.ALWAYS);
            applyDoneStyle(textLbl, item.done);

            cb.selectedProperty().addListener((obs, o, n) -> {
                item.done = n;
                if (idea.id() > 0 && item.dbId > 0) checklistRepo.updateDone(item.dbId, n);
                applyDoneStyle(textLbl, n);
                updateChecklistProgress();
            });

            Button delBtn = new Button("✕");
            delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b71c1c;"
                    + " -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 6 2 6;"
                    + " -fx-background-radius: 4;");
            delBtn.setOnAction(e -> {
                if (idea.id() > 0 && item.dbId > 0) checklistRepo.deleteItem(item.dbId);
                checklistItems.remove(item);
                refreshChecklistItemsUI();
                updateChecklistProgress();
            });

            HBox row = new HBox(8, cb, textLbl, delBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 8, 5, 10));
            row.setStyle("-fx-background-color: " + (i % 2 == 0 ? "#f8fafc" : "white")
                    + "; -fx-background-radius: 4;");
            checklistItemsBox.getChildren().add(row);
        }
    }

    private void applyDoneStyle(Label lbl, boolean done) {
        lbl.setStyle(done
                ? "-fx-text-fill: #9eafc0; -fx-strikethrough: true;  -fx-font-size: 12px;"
                : "-fx-text-fill: #0d1b2a; -fx-strikethrough: false; -fx-font-size: 12px;");
    }

    private void updateChecklistProgress() {
        if (checklistProgressLabel == null) return;
        int total = checklistItems.size();
        int done  = (int) checklistItems.stream().filter(s -> s.done).count();
        checklistProgressLabel.setText(
                total == 0 ? "sem itens" : done + " / " + total + " concluídas");
    }

    // ── Rodapé com ações ─────────────────────────────────────────────────────

    private HBox buildFooter(Stage stage) {
        Button saveBtn = new Button("💾  Salvar");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setPrefWidth(120);
        saveBtn.setOnAction(e -> {
            if (saveIdea()) { stage.close(); if (onSaved != null) onSaved.run(); }
        });

        Button deleteBtn = new Button("🗑  Excluir");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setPrefWidth(100);
        deleteBtn.setVisible(idea.id() > 0);
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Excluir permanentemente esta ideia/projeto?", ButtonType.YES, ButtonType.NO);
            confirm.setHeaderText("Confirmar exclusão");
            confirm.showAndWait().ifPresent(b -> {
                if (b == ButtonType.YES) {
                    checklistRepo.deleteByIdeaId(idea.id());
                    repo.deleteById(idea.id());
                    stage.close();
                    if (onSaved != null) onSaved.run();
                }
            });
        });

        Button cancelBtn = new Button("✕  Fechar");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> stage.close());

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, deleteBtn, spacer, cancelBtn, saveBtn);
        footer.setPadding(new Insets(10, 18, 10, 18));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: #edf1f8;"
                + " -fx-border-color: #b8cfe8; -fx-border-width: 1 0 0 0;");
        return footer;
    }

    // ── Lógica de salvar ──────────────────────────────────────────────────────

    private boolean saveIdea() {
        if (titleField.getText().isBlank()) {
            new Alert(Alert.AlertType.WARNING, "O título é obrigatório.", ButtonType.OK)
                    .showAndWait();
            return false;
        }
        String status   = resolveKey(statusCombo);
        String priority = resolveKey(priorityCombo);
        String type     = resolveKey(typeCombo);
        String impact   = resolveKey(impactCombo);

        ProjectIdea updated = new ProjectIdea(
                idea.id(), titleField.getText().trim(),
                descriptionArea.getText().trim(),
                status,
                categoryCombo.getValue() != null ? categoryCombo.getValue() : "Geral",
                priority, type, impact,
                feasibilitySpinner.getValue(),
                hoursSpinner.getValue(),
                startDatePicker.getValue(),
                targetDatePicker.getValue(),
                methodologyArea.getText().trim(),
                nextActionsArea.getText().trim(),   // texto livre (preservado mesmo no modo checklist)
                keywordsField.getText().trim(),
                referencesArea.getText().trim());

        long savedId;
        if (idea.id() == 0) {
            savedId = repo.saveFullIdea(updated);
            // Nova ideia: persistir itens de checklist que estão em memória
            if ("checklist".equals(nextActionsMode) && savedId > 0) {
                for (ChecklistItemState s : checklistItems) {
                    IdeaChecklistItem persisted = checklistRepo.addItem(savedId, s.text);
                    s.dbId = persisted.id();
                    if (s.done) checklistRepo.updateDone(persisted.id(), true);
                }
            }
        } else {
            repo.update(updated);
            savedId = idea.id();
        }

        if (savedId > 0) repo.setNextActionsMode(savedId, nextActionsMode);
        return true;
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private ComboBox<String> buildCombo(String[] keys, String[] labels, String selectedKey) {
        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("input-control");
        for (String l : labels) cb.getItems().add(l);
        // Encontra o index pelo key
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equalsIgnoreCase(selectedKey)) { cb.getSelectionModel().select(i); break; }
        }
        if (cb.getSelectionModel().isEmpty()) cb.getSelectionModel().select(0);
        // Armazena os keys como UserData
        cb.setUserData(keys);
        return cb;
    }

    private String resolveKey(ComboBox<String> cb) {
        String[] keys = (String[]) cb.getUserData();
        int idx = cb.getSelectionModel().getSelectedIndex();
        return (keys != null && idx >= 0 && idx < keys.length) ? keys[idx] : "NORMAL";
    }

    private VBox labeledControl(String label, javafx.scene.Node control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #5a7a9e; -fx-font-weight: 600;");
        VBox box = new VBox(3, lbl, control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private HBox fieldRow(String label, javafx.scene.Node control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #5a7a9e;"
                + " -fx-font-weight: 600; -fx-min-width: 120px;");
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private VBox sectionCard(String title, javafx.scene.Node content) {
        Label hdr = new Label(title);
        hdr.getStyleClass().add("section-title");
        hdr.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #03183e;"
                + " -fx-padding: 0 0 6 0;"
                + " -fx-border-color: transparent transparent #d6e4f5 transparent;"
                + " -fx-border-width: 0 0 1 0;");
        VBox card = new VBox(8, hdr, content);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private TextArea buildTextArea(String text, int rows, String prompt) {
        TextArea ta = new TextArea(text);
        ta.getStyleClass().add("input-control");
        ta.setPrefRowCount(rows);
        ta.setPromptText(prompt);
        ta.setWrapText(true);
        return ta;
    }

    private static String nvl(String s, String def) { return s != null ? s : def; }

    // ── Fábrica estática ──────────────────────────────────────────────────────

    /** Abre janela de nova ideia (id=0). */
    public static void showNew(ProjectIdeaRepository repo, String defaultCategory, Runnable onSaved) {
        ProjectIdea blank = new ProjectIdea(0, "", "", "nova", defaultCategory);
        new ProjectIdeaDetailWindow(blank, repo, onSaved).show();
    }
}

