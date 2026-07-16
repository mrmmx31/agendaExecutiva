package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.model.ProjectIdea;
import com.pessoal.agenda.model.ScheduleType;
import com.pessoal.agenda.model.TaskPriority;
import com.pessoal.agenda.model.TaskStatus;
import com.pessoal.agenda.repository.IdeaChecklistRepository;
import com.pessoal.agenda.repository.ProjectIdeaRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Fila dedicada para revisar capturas rápidas e tirá-las da caixa de entrada
 * com ações de um clique: priorizar, promover a projeto, checklist, tarefa,
 * vínculo hierárquico e arquivamento.
 */
public class IdeaInboxReviewWindow {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProjectIdeaRepository repo;
    private final IdeaChecklistRepository checklistRepo;
    private final Runnable onChanged;
    private final Consumer<String> statusSink;
    private final ObservableList<ProjectIdea> inboxItems = FXCollections.observableArrayList();

    private ListView<ProjectIdea> inboxListView;
    private Label queueCountLabel;
    private Label titleLabel;
    private Label metaLabel;
    private Label parentLabel;
    private TextArea descriptionArea;
    private TextArea nextActionsArea;
    private TextArea extraNotesArea;
    private ComboBox<ParentIdeaOption> parentIdeaCombo;
    private VBox detailPane;

    private record ParentIdeaOption(Long id, String label) {
        private static final ParentIdeaOption NONE = new ParentIdeaOption(null, "— sem ideia-mãe —");
        @Override public String toString() { return label; }
    }

    public IdeaInboxReviewWindow(Runnable onChanged, Consumer<String> statusSink) {
        this.repo = AppContextHolder.get().projectIdeaRepository();
        this.checklistRepo = AppContextHolder.get().ideaChecklistRepository();
        this.onChanged = onChanged;
        this.statusSink = statusSink != null ? statusSink : msg -> {};
    }

    public void show(Long initialIdeaId) {
        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle("Revisão da caixa de entrada");
        stage.setMinWidth(1080);
        stage.setMinHeight(720);

        VBox root = new VBox(0);
        root.getStyleClass().add("app-root");

        inboxListView = buildInboxList();
        detailPane = buildDetailPane();

        VBox leftPane = new VBox(10,
                buildQueueHeader(),
                inboxListView,
                buildQueueHint());
        leftPane.setPadding(new Insets(16));
        VBox.setVgrow(inboxListView, Priority.ALWAYS);

        ScrollPane rightScroll = new ScrollPane(detailPane);
        rightScroll.setFitToWidth(true);
        rightScroll.setFitToHeight(true);

        SplitPane splitPane = new SplitPane(leftPane, rightScroll);
        splitPane.setDividerPositions(0.34);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        root.getChildren().addAll(buildHeader(), splitPane, buildFooter(stage));

        Scene scene = new Scene(root, 1180, 780);
        ThemeManager.getInstance().applyTo(scene);
        stage.setScene(scene);

        refreshInbox(initialIdeaId, 0);
        stage.show();
    }

    private HBox buildHeader() {
        Label title = new Label("🗂 Revisão da caixa de entrada");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Priorize, conecte, transforme em projeto/tarefa e arquive sem perder o fio da meada.");
        subtitle.getStyleClass().add("page-subtitle");

        VBox titles = new VBox(2, title, subtitle);
        HBox header = new HBox(titles);
        header.getStyleClass().add("header-bar");
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildQueueHeader() {
        Label title = new Label("Fila de revisão");
        title.getStyleClass().add("section-title");

        queueCountLabel = new Label();
        queueCountLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: -t-pri;"
                + " -fx-background-color: -t-surface-a; -fx-padding: 4 10 4 10; -fx-background-radius: 999;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, title, spacer, queueCountLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, row);
    }

    private Label buildQueueHint() {
        Label hint = new Label("💡 Dica: duplo clique abre o detalhe completo; os botões da direita cuidam da triagem rápida.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2; -fx-font-style: italic;");
        return hint;
    }

    private ListView<ProjectIdea> buildInboxList() {
        ListView<ProjectIdea> list = new ListView<>(inboxItems);
        list.getStyleClass().add("clean-list");
        list.setPlaceholder(new Label("Nenhuma ideia pendente na caixa de entrada."));
        list.setCellFactory(lv -> new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final VBox box = new VBox(3, title, meta);
            {
                title.getStyleClass().add("t-heading-sm");
                meta.getStyleClass().add("t-muted");
                title.setWrapText(true);
                meta.setWrapText(true);
            }

            @Override
            protected void updateItem(ProjectIdea item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                title.setText(item.title());
                String parent = item.parentIdeaId() != null ? repo.findTitleById(item.parentIdeaId()) : null;
                StringBuilder sb = new StringBuilder()
                        .append(item.priorityLabel())
                        .append(" · ")
                        .append(item.statusLabel())
                        .append(" · ")
                        .append(item.category() != null ? item.category() : "Geral");
                if (parent != null && !parent.isBlank()) {
                    sb.append(" · ↳ ").append(parent);
                }
                meta.setText(sb.toString());
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> updateDetails(newItem));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelectedIdeaDetail();
            }
        });
        Tooltip.install(list, new Tooltip("Itens novos/em revisão. Selecione um para triagem rápida."));
        return list;
    }

    private VBox buildDetailPane() {
        titleLabel = new Label("Selecione uma ideia");
        titleLabel.getStyleClass().add("page-title");
        titleLabel.setWrapText(true);

        metaLabel = new Label("Prioridade, status e categoria aparecem aqui.");
        metaLabel.getStyleClass().add("page-subtitle");
        metaLabel.setWrapText(true);

        parentLabel = new Label("↳ Sem vínculo hierárquico por enquanto.");
        parentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -t-text-m;");
        parentLabel.setWrapText(true);

        descriptionArea = buildReadOnlyArea("Descrição / captura bruta", 8);
        nextActionsArea = buildReadOnlyArea("Próximas ações já definidas", 5);
        extraNotesArea = buildReadOnlyArea("Metodologia, referências, palavras-chave e observações", 6);

        parentIdeaCombo = new ComboBox<>();
        parentIdeaCombo.getStyleClass().add("input-control");
        parentIdeaCombo.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(14,
                new VBox(4, titleLabel, metaLabel, parentLabel),
                sectionCard("📄 Captura / descrição", descriptionArea),
                sectionCard("✅ Próximas ações atuais", nextActionsArea),
                sectionCard("🧩 Contexto extra", extraNotesArea),
                buildPrioritySection(),
                buildRelationshipSection(),
                buildTransformSection());
        content.setPadding(new Insets(18));
        return content;
    }

    private VBox buildPrioritySection() {
        Label hint = new Label("Ajuste a prioridade antes de decidir o destino do item.");
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2;");

        HBox buttons = new HBox(8,
                buildActionButton("🔴 Crítica", "secondary-button", () -> changePriority("CRITICA", "crítica")),
                buildActionButton("🟠 Alta", "secondary-button", () -> changePriority("ALTA", "alta")),
                buildActionButton("🔵 Normal", "secondary-button", () -> changePriority("NORMAL", "normal")),
                buildActionButton("🟢 Baixa", "secondary-button", () -> changePriority("BAIXA", "baixa")));
        return sectionCard("🎚 Prioridade rápida", new VBox(8, hint, buttons));
    }

    private VBox buildRelationshipSection() {
        Label hint = new Label("Use isto quando a captura for filha de uma ideia maior ou de um projeto já existente.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2;");

        Button linkBtn = buildActionButton("↳ Vincular / atualizar vínculo", "secondary-button", this::linkSelectedIdeaToParent);
        Button clearBtn = buildActionButton("⤬ Soltar vínculo", "secondary-button", () -> parentIdeaCombo.setValue(ParentIdeaOption.NONE));

        HBox buttons = new HBox(8, linkBtn, clearBtn);
        VBox.setVgrow(parentIdeaCombo, Priority.NEVER);
        return sectionCard("🧬 Hierarquia", new VBox(8, hint, parentIdeaCombo, buttons));
    }

    private VBox buildTransformSection() {
        Label hint = new Label("Essas ações ajudam a tirar a anotação do limbo: projeto ativo, checklist, tarefa executável ou arquivo.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2;");

        Button projectBtn = buildActionButton("🚀 Transformar em projeto", "primary-button", this::promoteSelectedIdeaToProject);
        Button checklistBtn = buildActionButton("☑ Mandar para checklist do projeto", "secondary-button", this::moveSelectedIdeaToChecklist);
        Button taskBtn = buildActionButton("📅 Transformar em tarefa de hoje", "secondary-button", this::convertSelectedIdeaToTask);
        Button detailBtn = buildActionButton("✏ Abrir detalhe completo", "secondary-button", this::openSelectedIdeaDetail);
        Button archiveBtn = buildActionButton("🧊 Arquivar", "danger-button", this::archiveSelectedIdea);

        HBox row1 = new HBox(8, projectBtn, checklistBtn);
        HBox row2 = new HBox(8, taskBtn, detailBtn, archiveBtn);
        return sectionCard("🛠 Destino rápido", new VBox(8, hint, row1, row2));
    }

    private HBox buildFooter(Stage stage) {
        Label hint = new Label("A fila atualiza após cada ação. Quando um item vira projeto, checklist ou tarefa, ele sai naturalmente da caixa de entrada.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 10px; -fx-text-fill: -t-text-m2;");

        Button closeBtn = new Button("✕ Fechar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, hint, spacer, closeBtn);
        footer.setPadding(new Insets(10, 18, 10, 18));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-background-color: -t-surface-d; -fx-border-color: -t-bd; -fx-border-width: 1 0 0 0;");
        return footer;
    }

    private TextArea buildReadOnlyArea(String prompt, int rows) {
        TextArea area = new TextArea();
        area.getStyleClass().add("input-control");
        area.setPromptText(prompt);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        area.setEditable(false);
        area.setFocusTraversable(false);
        return area;
    }

    private Button buildActionButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(e -> action.run());
        return button;
    }

    private VBox sectionCard(String title, javafx.scene.Node content) {
        Label header = new Label(title);
        header.getStyleClass().add("section-title");
        header.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: -t-text-b;"
                + " -fx-padding: 0 0 6 0; -fx-border-color: transparent transparent -t-bd-lt transparent;"
                + " -fx-border-width: 0 0 1 0;");
        VBox card = new VBox(8, header, content);
        card.getStyleClass().add("section-card");
        card.setPadding(new Insets(12));
        return card;
    }

    private void updateDetails(ProjectIdea idea) {
        boolean hasIdea = idea != null;
        detailPane.setDisable(!hasIdea);
        if (!hasIdea) {
            titleLabel.setText("Nenhuma ideia selecionada");
            metaLabel.setText("A fila está vazia ou nenhum item foi selecionado.");
            parentLabel.setText("↳ Sem vínculo hierárquico por enquanto.");
            descriptionArea.clear();
            nextActionsArea.clear();
            extraNotesArea.clear();
            parentIdeaCombo.getItems().setAll(ParentIdeaOption.NONE);
            parentIdeaCombo.setValue(ParentIdeaOption.NONE);
            return;
        }

        titleLabel.setText(idea.title());
        metaLabel.setText(idea.priorityLabel() + " · " + idea.statusLabel() + " · categoria "
                + (idea.category() != null ? idea.category() : "Geral"));
        String parentTitle = idea.parentIdeaId() != null ? repo.findTitleById(idea.parentIdeaId()) : null;
        parentLabel.setText(parentTitle != null && !parentTitle.isBlank()
                ? "↳ Ligada a: " + parentTitle
                : "↳ Sem ideia-mãe definida até agora.");
        descriptionArea.setText(nonBlankOrFallback(idea.description(), "Sem descrição além do título."));
        nextActionsArea.setText(nonBlankOrFallback(idea.nextActions(), "Ainda sem próximas ações definidas."));
        extraNotesArea.setText(buildExtraNotes(idea));
        reloadParentOptions(idea);
    }

    private String buildExtraNotes(ProjectIdea idea) {
        List<String> parts = new ArrayList<>();
        if (idea.startDate() != null) {
            parts.add("Início planejado: " + DATE_FMT.format(idea.startDate()));
        }
        if (idea.targetDate() != null) {
            parts.add("Prazo-alvo: " + DATE_FMT.format(idea.targetDate()));
        }
        if (idea.methodology() != null && !idea.methodology().isBlank()) {
            parts.add("Metodologia:\n" + idea.methodology().trim());
        }
        if (idea.keywords() != null && !idea.keywords().isBlank()) {
            parts.add("Palavras-chave: " + idea.keywords().trim());
        }
        if (idea.referencesText() != null && !idea.referencesText().isBlank()) {
            parts.add("Referências / fontes:\n" + idea.referencesText().trim());
        }
        if (parts.isEmpty()) {
            return "Sem contexto extra registrado ainda.";
        }
        return String.join("\n\n", parts);
    }

    private void reloadParentOptions(ProjectIdea selected) {
        parentIdeaCombo.getItems().setAll(ParentIdeaOption.NONE);
        for (ProjectIdea candidate : repo.findAll()) {
            if (candidate.id() == selected.id()) continue;
            parentIdeaCombo.getItems().add(new ParentIdeaOption(candidate.id(),
                    candidate.title() + (candidate.category() != null && !candidate.category().isBlank()
                            ? " [" + candidate.category() + "]" : "")));
        }
        if (selected.parentIdeaId() == null) {
            parentIdeaCombo.setValue(ParentIdeaOption.NONE);
            return;
        }
        parentIdeaCombo.getItems().stream()
                .filter(opt -> opt.id() != null && opt.id().equals(selected.parentIdeaId()))
                .findFirst()
                .ifPresentOrElse(parentIdeaCombo::setValue, () -> parentIdeaCombo.setValue(ParentIdeaOption.NONE));
    }

    private void changePriority(String priority, String label) {
        withSelectedIdea(idea -> {
            ProjectIdea updated = copyIdea(idea, idea.status(), idea.category(), priority,
                    idea.parentIdeaId(), idea.startDate(), idea.nextActions());
            applyIdeaUpdate(idea.id(), updated, true,
                    "Prioridade da ideia ajustada para " + label + ".");
        });
    }

    private void linkSelectedIdeaToParent() {
        withSelectedIdea(idea -> {
            ParentIdeaOption option = parentIdeaCombo.getValue() != null ? parentIdeaCombo.getValue() : ParentIdeaOption.NONE;
            ProjectIdea updated = copyIdea(idea, idea.status(), idea.category(), idea.priority(),
                    option.id(), idea.startDate(), idea.nextActions());
            String message = option.id() == null
                    ? "Vínculo hierárquico removido."
                    : "Ideia vinculada a uma ideia-mãe para revisão posterior.";
            applyIdeaUpdate(idea.id(), updated, true, message);
        });
    }

    private void promoteSelectedIdeaToProject() {
        withSelectedIdea(idea -> {
            ProjectIdea promoted = activateIdea(idea, "prototipagem",
                    ensureNextActions(idea.nextActions(), "Definir escopo inicial, primeiras entregas e próximos experimentos."));
            applyIdeaUpdate(idea.id(), promoted, false,
                    "Ideia promovida a projeto ativo e removida da fila principal.");
        });
    }

    private void moveSelectedIdeaToChecklist() {
        withSelectedIdea(idea -> {
            ProjectIdea active = activateIdea(idea, "em_execucao",
                    ensureNextActions(idea.nextActions(), "Quebrar em etapas práticas no checklist do projeto."));
            repo.update(active);
            repo.setNextActionsMode(active.id(), "checklist");
            seedChecklistIfEmpty(active);
            afterMutation(active.id(), false, "Ideia enviada para o checklist do projeto.");
            repo.findById(active.id()).ifPresent(saved -> ProjectChecklistWindow.open(saved, this::notifyChangedWithoutMessage));
        });
    }

    private void convertSelectedIdeaToTask() {
        withSelectedIdea(idea -> {
            AppContextHolder.get().taskService().createTask(
                    idea.title(),
                    buildTaskNotes(idea),
                    LocalDate.now(),
                    "Projeto",
                    ScheduleType.SINGLE,
                    null,
                    null,
                    null,
                    null,
                    mapIdeaPriority(idea.priority()),
                    TaskStatus.PENDENTE
            );
            ProjectIdea updated = activateIdea(idea, "em_execucao",
                    ensureNextActions(idea.nextActions(), "Tarefa gerada na Agenda em " + DATE_FMT.format(LocalDate.now()) + "."));
            repo.update(updated);
            afterMutation(updated.id(), false,
                    "Tarefa criada na Agenda para hoje a partir da captura selecionada.");
        });
    }

    private void archiveSelectedIdea() {
        withSelectedIdea(idea -> Dialogs.confirm(
                        "Arquivar captura",
                        "Remover este item da fila ativa?",
                        "A captura será movida para a categoria Arquivo e sairá da revisão rápida.")
                .ifPresent(btn -> {
                    if (btn == javafx.scene.control.ButtonType.OK) {
                        ProjectIdea archived = copyIdea(idea, "concluida", "Arquivo", idea.priority(),
                                idea.parentIdeaId(), idea.startDate(),
                                ensureNextActions(idea.nextActions(), "Arquivada em " + DATE_FMT.format(LocalDate.now()) + "."));
                        applyIdeaUpdate(idea.id(), archived, false,
                                "Captura arquivada e removida da caixa de entrada.");
                    }
                }));
    }

    private void openSelectedIdeaDetail() {
        selectedIdea().ifPresent(idea -> new ProjectIdeaDetailWindow(idea, repo, () -> {
            refreshInbox(idea.id(), inboxListView != null ? Math.max(0, inboxListView.getSelectionModel().getSelectedIndex()) : 0);
            notifyChangedWithoutMessage();
        }).show());
    }

    private void seedChecklistIfEmpty(ProjectIdea idea) {
        if (checklistRepo.countTotalByIdeaId(idea.id()) > 0) return;
        for (String item : checklistSeedsFor(idea)) {
            checklistRepo.addItem(idea.id(), item);
        }
    }

    private List<String> checklistSeedsFor(ProjectIdea idea) {
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        collectChecklistLines(seeds, idea.nextActions());
        if (seeds.isEmpty()) {
            collectChecklistLines(seeds, idea.description());
        }
        if (seeds.isEmpty()) {
            seeds.add("Quebrar em etapas práticas: " + idea.title());
        }
        return new ArrayList<>(seeds).subList(0, Math.min(4, seeds.size()));
    }

    private void collectChecklistLines(LinkedHashSet<String> seeds, String source) {
        if (source == null || source.isBlank()) return;
        source.lines()
                .map(String::trim)
                .map(this::normalizeChecklistSeed)
                .filter(line -> !line.isBlank())
                .limit(4)
                .forEach(seeds::add);
    }

    private String normalizeChecklistSeed(String line) {
        String cleaned = line.replaceFirst("^[\\-•*\\d.)\\s]+", "").trim();
        if (cleaned.length() > 110) {
            cleaned = cleaned.substring(0, 110).trim() + "…";
        }
        return cleaned;
    }

    private void applyIdeaUpdate(long currentIdeaId, ProjectIdea updated, boolean preferSameIdea, String message) {
        repo.update(updated);
        afterMutation(currentIdeaId, preferSameIdea, message);
    }

    private void afterMutation(long currentIdeaId, boolean preferSameIdea, String message) {
        int fallbackIndex = inboxListView != null ? Math.max(0, inboxListView.getSelectionModel().getSelectedIndex()) : 0;
        refreshInbox(preferSameIdea ? currentIdeaId : null, fallbackIndex);
        notifyChanged(message);
    }

    private void refreshInbox(Long preferredIdeaId, int fallbackIndex) {
        inboxItems.setAll(repo.findInboxIdeas(200));
        updateQueueCount();
        if (inboxItems.isEmpty()) {
            if (inboxListView != null) inboxListView.getSelectionModel().clearSelection();
            updateDetails(null);
            return;
        }
        if (preferredIdeaId != null && selectIdeaById(preferredIdeaId)) {
            return;
        }
        int safeIndex = Math.min(Math.max(fallbackIndex, 0), inboxItems.size() - 1);
        inboxListView.getSelectionModel().select(safeIndex);
        updateDetails(inboxListView.getSelectionModel().getSelectedItem());
    }

    private boolean selectIdeaById(long ideaId) {
        for (int i = 0; i < inboxItems.size(); i++) {
            if (inboxItems.get(i).id() == ideaId) {
                inboxListView.getSelectionModel().select(i);
                inboxListView.scrollTo(i);
                updateDetails(inboxItems.get(i));
                return true;
            }
        }
        return false;
    }

    private void updateQueueCount() {
        if (queueCountLabel == null) return;
        int size = inboxItems.size();
        queueCountLabel.setText(size == 1 ? "1 item" : size + " itens");
    }

    private Optional<ProjectIdea> selectedIdea() {
        return Optional.ofNullable(inboxListView != null ? inboxListView.getSelectionModel().getSelectedItem() : null);
    }

    private void withSelectedIdea(Consumer<ProjectIdea> action) {
        ProjectIdea idea = inboxListView != null ? inboxListView.getSelectionModel().getSelectedItem() : null;
        if (idea == null) {
            statusSink.accept("Selecione uma ideia da fila para aplicar a ação.");
            return;
        }
        action.accept(idea);
    }

    private ProjectIdea activateIdea(ProjectIdea idea, String status, String nextActions) {
        return copyIdea(idea,
                status,
                normalizeIdeaCategory(idea.category()),
                idea.priority(),
                idea.parentIdeaId(),
                idea.startDate() != null ? idea.startDate() : LocalDate.now(),
                nextActions);
    }

    private ProjectIdea copyIdea(ProjectIdea idea,
                                 String status,
                                 String category,
                                 String priority,
                                 Long parentIdeaId,
                                 LocalDate startDate,
                                 String nextActions) {
        return new ProjectIdea(
                idea.id(),
                idea.title(),
                idea.description(),
                status,
                category,
                priority,
                idea.ideaType(),
                idea.impactLevel(),
                idea.feasibility(),
                idea.estimatedHours(),
                startDate,
                idea.targetDate(),
                idea.methodology(),
                nextActions,
                idea.keywords(),
                idea.referencesText(),
                parentIdeaId
        );
    }

    private String normalizeIdeaCategory(String category) {
        if (category == null || category.isBlank() || "Caixa de entrada".equalsIgnoreCase(category.trim())) {
            return "Geral";
        }
        return category.trim();
    }

    private String ensureNextActions(String current, String addition) {
        if (addition == null || addition.isBlank()) return current;
        if (current == null || current.isBlank()) return addition.trim();
        if (current.contains(addition)) return current;
        return current.trim() + "\n• " + addition.trim();
    }

    private String buildTaskNotes(ProjectIdea idea) {
        List<String> sections = new ArrayList<>();
        sections.add("Origem: revisão da caixa de entrada de ideias.");
        if (idea.description() != null && !idea.description().isBlank()) {
            sections.add("Descrição:\n" + idea.description().trim());
        }
        if (idea.nextActions() != null && !idea.nextActions().isBlank()) {
            sections.add("Próximas ações:\n" + idea.nextActions().trim());
        }
        if (idea.parentIdeaId() != null) {
            String parentTitle = repo.findTitleById(idea.parentIdeaId());
            if (parentTitle != null && !parentTitle.isBlank()) {
                sections.add("Ligada à ideia: " + parentTitle);
            }
        }
        return String.join("\n\n", sections);
    }

    private TaskPriority mapIdeaPriority(String priority) {
        if (priority == null) return TaskPriority.NORMAL;
        return switch (priority) {
            case "CRITICA" -> TaskPriority.CRITICA;
            case "ALTA" -> TaskPriority.ALTA;
            case "BAIXA" -> TaskPriority.BAIXA;
            default -> TaskPriority.NORMAL;
        };
    }

    private String nonBlankOrFallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private void notifyChanged(String message) {
        notifyChangedWithoutMessage();
        statusSink.accept(message);
    }

    private void notifyChangedWithoutMessage() {
        if (onChanged != null) onChanged.run();
    }
}


