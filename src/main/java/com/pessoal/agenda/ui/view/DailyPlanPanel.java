package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.model.DailyPlanCapacity;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Resumo e fluxo guiado do plano diario. */
public final class DailyPlanPanel extends VBox {
    public record TaskOption(long id, String title, String detail) {
        public TaskOption {
            if (id <= 0) throw new IllegalArgumentException("A tarefa precisa de um id valido");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("A tarefa precisa de titulo");
            detail = detail == null ? "" : detail;
        }
    }

    public record PlanningRequest(List<TaskOption> candidates, List<String> todayItems,
                                  DailyPlanCapacity capacity, Long essentialTaskId,
                                  List<Long> supportTaskIds) {
        public PlanningRequest {
            candidates = List.copyOf(candidates);
            todayItems = List.copyOf(todayItems);
            supportTaskIds = List.copyOf(supportTaskIds);
        }
    }

    public record PlanSelection(DailyPlanCapacity capacity, long essentialTaskId,
                                List<Long> supportTaskIds, boolean openAfterSave) {
        public PlanSelection {
            supportTaskIds = List.copyOf(supportTaskIds);
        }
    }

    private final VBox loadingState;
    private final VBox emptyState;
    private final VBox readyState;
    private final VBox errorState;
    private final VBox reviewStep;
    private final VBox selectionStep;
    private final VBox confirmationStep;
    private final List<Node> states = new ArrayList<>();

    private final Label capacityLabel = new Label();
    private final Label essentialTitleLabel = new Label();
    private final Label supportTasksLabel = new Label();
    private final Button startButton = new Button("Começar meu dia");
    private final Button editButton = new Button("Editar plano");
    private final Button openEssentialButton = new Button("Abrir tarefa essencial");
    private final Button closeDayButton = new Button("Encerrar meu dia");
    private final Button reviewClosedDayButton = new Button("Revisar encerramento");
    private final Button retryButton = new Button("Tentar novamente");

    private final Label reviewSummaryLabel = wrappingLabel("", "daily-plan-detail");
    private final ListView<String> todayList = new ListView<>();
    private final Button reviewNextButton = new Button("Escolher tarefas");
    private final ComboBox<TaskOption> essentialCombo = taskCombo("Escolha a tarefa essencial");
    private final ComboBox<TaskOption> supportOneCombo = taskCombo("Apoio opcional");
    private final ComboBox<TaskOption> supportTwoCombo = taskCombo("Segundo apoio opcional");
    private final Label selectionErrorLabel = wrappingLabel("", "daily-plan-validation");
    private final Label confirmationEssentialLabel = wrappingLabel("", "daily-plan-essential");
    private final Label confirmationSupportLabel = wrappingLabel("", "daily-plan-detail");
    private final RadioButton openAfterSaveOption = new RadioButton("Abrir a tarefa essencial na Agenda");
    private final RadioButton stayAfterSaveOption = new RadioButton("Continuar no Dashboard");
    private final ToggleButton normalCapacityButton = new ToggleButton("Ritmo normal");
    private final ToggleButton reducedCapacityButton = new ToggleButton("Capacidade reduzida");
    private final Label selectionIntro = wrappingLabel("", "daily-plan-detail");
    private final Label supportCaption = caption("Tarefas de apoio");
    private HBox supportOneRow;
    private HBox supportTwoRow;

    private PlanningRequest planningRequest;
    private Consumer<PlanSelection> saveAction = selection -> {};
    private Consumer<DailyPlanCapacity> capacityChangeAction = capacity -> {};
    private Runnable cancelPlanningAction = () -> {};

    public DailyPlanPanel() {
        Label sectionTitle = new Label("Meu dia");
        sectionTitle.getStyleClass().add("section-title");

        loadingState = stateBox(wrappingLabel("Carregando seu plano de hoje...", "daily-plan-detail"));
        loadingState.setId("daily-plan-loading");

        Label emptyTitle = wrappingLabel("Seu plano de hoje ainda está aberto para ser definido.",
                "daily-plan-state-title");
        Label emptyDetail = wrappingLabel(
                "Escolha o essencial quando estiver pronto. Você pode continuar usando a agenda normalmente.",
                "daily-plan-detail");
        stylePrimary(startButton, "daily-plan-start");
        emptyState = stateBox(emptyTitle, emptyDetail, startButton);
        emptyState.setId("daily-plan-empty");

        capacityLabel.getStyleClass().add("daily-plan-capacity");
        Label essentialCaption = new Label("Essencial");
        essentialCaption.getStyleClass().add("daily-plan-caption");
        essentialTitleLabel.getStyleClass().add("daily-plan-essential");
        essentialTitleLabel.setWrapText(true);
        supportTasksLabel.getStyleClass().add("daily-plan-detail");
        supportTasksLabel.setWrapText(true);
        styleSecondary(openEssentialButton, "daily-plan-open-essential");
        styleSecondary(editButton, "daily-plan-edit");
        stylePrimary(closeDayButton, "daily-plan-close-day");
        styleSecondary(reviewClosedDayButton, "daily-plan-review-closed");
        readyState = stateBox(capacityLabel, essentialCaption, essentialTitleLabel,
                supportTasksLabel, actionFlow(
                        openEssentialButton, editButton, closeDayButton, reviewClosedDayButton));
        readyState.setId("daily-plan-ready");

        Label errorTitle = wrappingLabel("Não foi possível carregar o plano de hoje.",
                "daily-plan-state-title");
        Label errorDetail = wrappingLabel(
                "Os outros módulos continuam disponíveis. Tente carregar este bloco novamente.",
                "daily-plan-detail");
        styleSecondary(retryButton, "daily-plan-retry");
        errorState = stateBox(errorTitle, errorDetail, retryButton);
        errorState.setId("daily-plan-error");
        errorState.getStyleClass().add("daily-plan-error");

        todayList.getStyleClass().add("clean-list");
        todayList.setPrefHeight(116);
        todayList.setMinHeight(88);
        todayList.setPlaceholder(new Label("Nenhum compromisso ou tarefa programado para hoje."));
        Label reviewIntro = wrappingLabel(
                "Confira o que já está previsto antes de escolher o campo principal de atenção.",
                "daily-plan-detail");
        stylePrimary(reviewNextButton, "daily-plan-review-next");
        Button reviewCancel = secondaryButton("Cancelar", "daily-plan-review-cancel");
        reviewNextButton.setOnAction(event -> showSelectionStep());
        reviewCancel.setOnAction(event -> cancelPlanning());
        ToggleGroup capacityGroup = new ToggleGroup();
        normalCapacityButton.setToggleGroup(capacityGroup);
        reducedCapacityButton.setToggleGroup(capacityGroup);
        normalCapacityButton.setId("daily-plan-capacity-normal");
        reducedCapacityButton.setId("daily-plan-capacity-reduced");
        normalCapacityButton.getStyleClass().add("view-toggle-btn");
        reducedCapacityButton.getStyleClass().add("view-toggle-btn");
        normalCapacityButton.setMinHeight(36);
        reducedCapacityButton.setMinHeight(36);
        normalCapacityButton.setOnAction(event -> selectCapacity(DailyPlanCapacity.NORMAL));
        reducedCapacityButton.setOnAction(event -> selectCapacity(DailyPlanCapacity.REDUCED));
        HBox capacitySelector = new HBox(3, normalCapacityButton, reducedCapacityButton);
        capacitySelector.getStyleClass().add("view-toggle-bar");
        reviewStep = planningStep("Etapa 1 de 3", "Revisar o dia", reviewIntro,
                caption("Ritmo do dia"), capacitySelector,
                reviewSummaryLabel, todayList, actionFlow(reviewNextButton, reviewCancel));
        reviewStep.setId("daily-plan-review-step");

        essentialCombo.setId("daily-plan-essential-choice");
        supportOneCombo.setId("daily-plan-support-one");
        supportTwoCombo.setId("daily-plan-support-two");
        Button moveDownButton = iconButton("↓", "Mover o primeiro apoio para baixo");
        Button moveUpButton = iconButton("↑", "Mover o segundo apoio para cima");
        Button clearFirstSupportButton = iconButton("×", "Remover o primeiro apoio");
        Button clearSecondSupportButton = iconButton("×", "Remover o segundo apoio");
        moveDownButton.setId("daily-plan-support-down");
        moveUpButton.setId("daily-plan-support-up");
        clearFirstSupportButton.setId("daily-plan-support-one-clear");
        clearSecondSupportButton.setId("daily-plan-support-two-clear");
        moveDownButton.setOnAction(event -> swapSupports());
        moveUpButton.setOnAction(event -> swapSupports());
        clearFirstSupportButton.setOnAction(event -> supportOneCombo.getSelectionModel().clearSelection());
        clearSecondSupportButton.setOnAction(event -> supportTwoCombo.getSelectionModel().clearSelection());
        Button selectionBack = secondaryButton("Voltar", "daily-plan-selection-back");
        Button selectionNext = new Button("Revisar plano");
        stylePrimary(selectionNext, "daily-plan-selection-next");
        Button selectionCancel = secondaryButton("Cancelar", "daily-plan-selection-cancel");
        selectionBack.setOnAction(event -> showReviewStep());
        selectionNext.setOnAction(event -> validateAndShowConfirmation());
        selectionCancel.setOnAction(event -> cancelPlanning());
        selectionErrorLabel.setVisible(false);
        selectionErrorLabel.setManaged(false);
        supportOneRow = supportRow(supportOneCombo, moveDownButton, clearFirstSupportButton);
        supportTwoRow = supportRow(supportTwoCombo, moveUpButton, clearSecondSupportButton);
        supportCaption.setId("daily-plan-support-caption");
        supportOneRow.setId("daily-plan-support-one-row");
        supportTwoRow.setId("daily-plan-support-two-row");
        selectionStep = planningStep("Etapa 2 de 3", "Escolher tarefas", selectionIntro,
                caption("Tarefa essencial"), essentialCombo, supportCaption,
                supportOneRow, supportTwoRow,
                selectionErrorLabel, actionFlow(selectionBack, selectionNext, selectionCancel));
        selectionStep.setId("daily-plan-selection-step");

        Label confirmationIntro = wrappingLabel(
                "Confira o plano e escolha o que acontece depois de salvar.", "daily-plan-detail");
        ToggleGroup firstActionGroup = new ToggleGroup();
        openAfterSaveOption.setToggleGroup(firstActionGroup);
        stayAfterSaveOption.setToggleGroup(firstActionGroup);
        openAfterSaveOption.setId("daily-plan-open-after-save");
        stayAfterSaveOption.setId("daily-plan-stay-after-save");
        openAfterSaveOption.setSelected(true);
        Button confirmationBack = secondaryButton("Voltar", "daily-plan-confirmation-back");
        Button saveButton = new Button("Salvar plano");
        stylePrimary(saveButton, "daily-plan-save");
        Button confirmationCancel = secondaryButton("Cancelar", "daily-plan-confirmation-cancel");
        confirmationBack.setOnAction(event -> showSelectionStep());
        saveButton.setOnAction(event -> saveSelection());
        confirmationCancel.setOnAction(event -> cancelPlanning());
        confirmationStep = planningStep("Etapa 3 de 3", "Confirmar a primeira ação",
                confirmationIntro, caption("Essencial"), confirmationEssentialLabel,
                confirmationSupportLabel, openAfterSaveOption, stayAfterSaveOption,
                actionFlow(confirmationBack, saveButton, confirmationCancel));
        confirmationStep.setId("daily-plan-confirmation-step");

        states.addAll(List.of(loadingState, emptyState, readyState, errorState,
                reviewStep, selectionStep, confirmationStep));
        StackPane stateContainer = new StackPane(states.toArray(Node[]::new));
        getChildren().addAll(sectionTitle, stateContainer);
        getStyleClass().addAll("section-card", "daily-plan-card");
        setPadding(new Insets(14));
        setSpacing(10);
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && isPlanning()) {
                cancelPlanning();
                event.consume();
            }
        });
        showLoading();
    }

    public void setStartAction(Runnable action) {
        startButton.setOnAction(event -> action.run());
    }

    public void setEditAction(Runnable action) {
        editButton.setOnAction(event -> action.run());
    }

    public void setOpenEssentialAction(Runnable action) {
        openEssentialButton.setOnAction(event -> action.run());
    }

    public void setCloseDayAction(Runnable action) {
        closeDayButton.setOnAction(event -> action.run());
        reviewClosedDayButton.setOnAction(event -> action.run());
    }

    public void setRetryAction(Runnable action) {
        retryButton.setOnAction(event -> action.run());
    }

    public void setSaveAction(Consumer<PlanSelection> action) {
        saveAction = action;
    }

    public void setCapacityChangeAction(Consumer<DailyPlanCapacity> action) {
        capacityChangeAction = action;
    }

    public void setCancelPlanningAction(Runnable action) {
        cancelPlanningAction = action;
    }

    public void showLoading() {
        showOnly(loadingState);
    }

    public void showEmpty() {
        showOnly(emptyState);
    }

    public void showPlan(String capacity, String essentialTitle, List<String> supportTitles) {
        capacityLabel.setText(capacity);
        essentialTitleLabel.setText(essentialTitle);
        supportTasksLabel.setText(supportTitles.isEmpty()
                ? "Sem tarefas de apoio."
                : "Apoio: " + String.join("  |  ", supportTitles));
        setManagedVisible(editButton, true);
        setManagedVisible(closeDayButton, true);
        setManagedVisible(reviewClosedDayButton, false);
        showOnly(readyState);
    }

    public void setEssentialAvailable(boolean available) {
        setManagedVisible(openEssentialButton, available);
    }

    public void showClosedPlan(String capacity, String essentialTitle,
                               List<String> supportTitles) {
        showPlan("Dia encerrado · " + capacity, essentialTitle, supportTitles);
        setManagedVisible(editButton, false);
        setManagedVisible(closeDayButton, false);
        setManagedVisible(reviewClosedDayButton, true);
    }

    public void showError() {
        showOnly(errorState);
    }

    public void beginPlanning(PlanningRequest request) {
        planningRequest = request;
        essentialCombo.getItems().setAll(request.candidates());
        supportOneCombo.getItems().setAll(request.candidates());
        supportTwoCombo.getItems().setAll(request.candidates());
        selectTask(essentialCombo, request.essentialTaskId());
        selectTask(supportOneCombo, itemAt(request.supportTaskIds(), 0));
        selectTask(supportTwoCombo, itemAt(request.supportTaskIds(), 1));
        selectCapacity(request.capacity());
        todayList.getItems().setAll(request.todayItems());
        reviewSummaryLabel.setText(request.todayItems().isEmpty()
                ? "Hoje está sem itens programados. Você ainda pode escolher entre as tarefas abertas."
                : request.todayItems().size() + " item(ns) programado(s) para hoje.");
        reviewNextButton.setDisable(request.candidates().isEmpty());
        clearSelectionError();
        showReviewStep();
    }

    public void showPlanningError(String message) {
        selectionErrorLabel.setText(message);
        selectionErrorLabel.setVisible(true);
        selectionErrorLabel.setManaged(true);
        showOnly(selectionStep);
    }

    private void showReviewStep() {
        showOnly(reviewStep);
        Platform.runLater(reviewNextButton::requestFocus);
    }

    private void showSelectionStep() {
        clearSelectionError();
        showOnly(selectionStep);
        Platform.runLater(essentialCombo::requestFocus);
    }

    private void validateAndShowConfirmation() {
        TaskOption essential = essentialCombo.getValue();
        if (essential == null) {
            showSelectionError("Escolha uma tarefa essencial para continuar.");
            return;
        }
        List<TaskOption> supports = selectedSupports();
        if (supports.stream().anyMatch(item -> item.id() == essential.id())) {
            showSelectionError("A tarefa essencial não pode ser usada também como apoio.");
            return;
        }
        if (supports.size() == 2 && supports.get(0).id() == supports.get(1).id()) {
            showSelectionError("Escolha tarefas de apoio diferentes.");
            return;
        }
        confirmationEssentialLabel.setText(essential.title());
        confirmationSupportLabel.setText(supports.isEmpty()
                ? "Sem tarefas de apoio."
                : "Apoio: " + supports.stream().map(TaskOption::title)
                        .reduce((a, b) -> a + "  |  " + b).orElse(""));
        showOnly(confirmationStep);
        Platform.runLater(openAfterSaveOption::requestFocus);
    }

    private void saveSelection() {
        TaskOption essential = essentialCombo.getValue();
        if (essential == null) {
            showSelectionStep();
            showSelectionError("Escolha uma tarefa essencial para salvar.");
            return;
        }
        saveAction.accept(new PlanSelection(
                selectedCapacity(), essential.id(),
                selectedSupports().stream().map(TaskOption::id).toList(),
                openAfterSaveOption.isSelected()));
    }

    private void cancelPlanning() {
        cancelPlanningAction.run();
    }

    private boolean isPlanning() {
        return reviewStep.isVisible() || selectionStep.isVisible() || confirmationStep.isVisible();
    }

    private List<TaskOption> selectedSupports() {
        ArrayList<TaskOption> selected = new ArrayList<>(2);
        if (supportOneCombo.getValue() != null) selected.add(supportOneCombo.getValue());
        if (supportTwoCombo.getValue() != null) selected.add(supportTwoCombo.getValue());
        return selected;
    }

    private void swapSupports() {
        TaskOption first = supportOneCombo.getValue();
        supportOneCombo.setValue(supportTwoCombo.getValue());
        supportTwoCombo.setValue(first);
    }

    private void selectCapacity(DailyPlanCapacity capacity) {
        boolean reduced = capacity == DailyPlanCapacity.REDUCED;
        normalCapacityButton.setSelected(!reduced);
        reducedCapacityButton.setSelected(reduced);
        selectionIntro.setText(reduced
                ? "Escolha somente o essencial para um dia com menos capacidade."
                : "Escolha uma essencial e, se ajudarem, até duas tarefas de apoio.");
        if (reduced) {
            supportOneCombo.getSelectionModel().clearSelection();
            supportTwoCombo.getSelectionModel().clearSelection();
        }
        setManagedVisible(supportCaption, !reduced);
        setManagedVisible(supportOneRow, !reduced);
        setManagedVisible(supportTwoRow, !reduced);
        capacityChangeAction.accept(capacity);
    }

    private DailyPlanCapacity selectedCapacity() {
        return reducedCapacityButton.isSelected()
                ? DailyPlanCapacity.REDUCED
                : DailyPlanCapacity.NORMAL;
    }

    private void showSelectionError(String message) {
        selectionErrorLabel.setText(message);
        selectionErrorLabel.setVisible(true);
        selectionErrorLabel.setManaged(true);
    }

    private void clearSelectionError() {
        selectionErrorLabel.setText("");
        selectionErrorLabel.setVisible(false);
        selectionErrorLabel.setManaged(false);
    }

    private void showOnly(Node visibleState) {
        for (Node state : states) {
            boolean visible = state == visibleState;
            state.setVisible(visible);
            state.setManaged(visible);
        }
    }

    private static VBox planningStep(String step, String title, Node... content) {
        Label stepLabel = wrappingLabel(step, "daily-plan-step-indicator");
        Label titleLabel = wrappingLabel(title, "daily-plan-state-title");
        VBox box = stateBox(stepLabel, titleLabel);
        box.getChildren().addAll(content);
        return box;
    }

    private static VBox stateBox(Node... children) {
        VBox box = new VBox(7, children);
        box.getStyleClass().add("daily-plan-state");
        return box;
    }

    private static FlowPane actionFlow(Node... actions) {
        FlowPane flow = new FlowPane(8, 8, actions);
        flow.getStyleClass().add("daily-plan-actions");
        return flow;
    }

    private static HBox supportRow(ComboBox<TaskOption> combo, Button... actions) {
        HBox row = new HBox(7);
        row.getChildren().add(combo);
        row.getChildren().addAll(actions);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(combo, Priority.ALWAYS);
        return row;
    }

    private static ComboBox<TaskOption> taskCombo(String prompt) {
        ComboBox<TaskOption> combo = new ComboBox<>();
        combo.setPromptText(prompt);
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.getStyleClass().add("input-control");
        combo.setCellFactory(list -> new TaskOptionCell());
        combo.setButtonCell(new TaskOptionCell());
        return combo;
    }

    private static final class TaskOptionCell extends ListCell<TaskOption> {
        private final Label title = wrappingLabel("", "daily-plan-option-title");
        private final Label detail = wrappingLabel("", "daily-plan-option-detail");
        private final VBox content = new VBox(2, title, detail);

        @Override
        protected void updateItem(TaskOption item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            title.setText(item.title());
            detail.setText(item.detail());
            detail.setVisible(!item.detail().isBlank());
            detail.setManaged(!item.detail().isBlank());
            setText(null);
            setGraphic(content);
        }
    }

    private static Button iconButton(String symbol, String tooltip) {
        Button button = new Button(symbol);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setMinSize(36, 36);
        button.setPrefSize(36, 36);
        return button;
    }

    private static Button secondaryButton(String text, String id) {
        Button button = new Button(text);
        styleSecondary(button, id);
        return button;
    }

    private static void stylePrimary(Button button, String id) {
        button.getStyleClass().add("primary-button");
        button.setId(id);
        button.setMinHeight(36);
    }

    private static void styleSecondary(Button button, String id) {
        button.getStyleClass().add("secondary-button");
        button.setId(id);
        button.setMinHeight(36);
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("daily-plan-caption");
        return label;
    }

    private static Label wrappingLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    private static Long itemAt(List<Long> items, int index) {
        return index < items.size() ? items.get(index) : null;
    }

    private static void selectTask(ComboBox<TaskOption> combo, Long taskId) {
        if (taskId == null) {
            combo.getSelectionModel().clearSelection();
            return;
        }
        combo.getItems().stream()
                .filter(item -> item.id() == taskId)
                .findFirst()
                .ifPresent(combo::setValue);
    }

    private static void setManagedVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
