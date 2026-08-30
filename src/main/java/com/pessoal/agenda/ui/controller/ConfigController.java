package com.pessoal.agenda.ui.controller;
import com.pessoal.agenda.ui.view.Dialogs;

import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.app.SharedContext;
import com.pessoal.agenda.model.Category;
import com.pessoal.agenda.model.CategoryDomain;
import com.pessoal.agenda.model.QuickCaptureShortcut;
import com.pessoal.agenda.service.PendencyNotificationService;
import com.pessoal.agenda.service.QuickCapturePreferences;
import com.pessoal.agenda.service.LocalMetricsService;
import com.pessoal.agenda.ui.view.ThemeManager;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller da aba de Configurações.
 * Gerencia as categorias de todos os domínios do sistema e o tema visual.
 */
public class ConfigController {

    private final SharedContext ctx;
    private final Runnable notificationSettingsChanged;
    private final Runnable quickCaptureShortcutChanged;
    private final PendencyNotificationService notificationService;
    private final LocalMetricsService localMetricsService;
    private final Runnable localMetricsChanged;

    public ConfigController(SharedContext ctx) {
        this(ctx, () -> {}, () -> {});
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged) {
        this(ctx, notificationSettingsChanged, () -> {});
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                            Runnable quickCaptureShortcutChanged) {
        this(ctx, notificationSettingsChanged, quickCaptureShortcutChanged,
                PendencyNotificationService.getInstance(),
                AppContextHolder.get().localMetricsService(), ctx::triggerDashboardRefresh);
    }

    public ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                            Runnable quickCaptureShortcutChanged,
                            PendencyNotificationService notificationService) {
        this(ctx, notificationSettingsChanged, quickCaptureShortcutChanged,
                notificationService, null, () -> {});
    }

    ConfigController(SharedContext ctx, Runnable notificationSettingsChanged,
                     Runnable quickCaptureShortcutChanged,
                     PendencyNotificationService notificationService,
                     LocalMetricsService localMetricsService,
                     Runnable localMetricsChanged) {
        this.ctx = ctx;
        this.notificationSettingsChanged = notificationSettingsChanged;
        this.quickCaptureShortcutChanged = quickCaptureShortcutChanged;
        this.notificationService = notificationService;
        this.localMetricsService = localMetricsService;
        this.localMetricsChanged = localMetricsChanged;
    }

    public Tab buildTab() {
        Tab tab = new Tab("⚙ Configurações");
        tab.setClosable(false);

        VBox taskSection      = buildCategorySection("Categorias de Tarefas",
                "config-task",       CategoryDomain.TASK,       ctx.taskCatList);
        VBox checklistSection = buildCategorySection("Categorias de Protocolos",
                "config-checklist",  CategoryDomain.CHECKLIST,  ctx.checklistCatList);
        VBox studySection     = buildCategorySection("Categorias de Estudos",
                "config-study",      CategoryDomain.STUDY,      ctx.studyCatList);
        VBox studyTypeSection = buildCategorySection("Tipos de Estudo",
                "config-study-type", CategoryDomain.STUDY_TYPE, ctx.studyTypeCatList);
        VBox ideaSection      = buildCategorySection("Categorias de Projetos e Ideias",
                "config-idea",       CategoryDomain.IDEA,       ctx.ideaCatList);

        HBox row1 = new HBox(14, taskSection, checklistSection);
        HBox row2 = new HBox(14, studySection, studyTypeSection);
        HBox row3 = new HBox(14, ideaSection);
        HBox.setHgrow(taskSection,       Priority.ALWAYS);
        HBox.setHgrow(checklistSection,  Priority.ALWAYS);
        HBox.setHgrow(studySection,      Priority.ALWAYS);
        HBox.setHgrow(studyTypeSection,  Priority.ALWAYS);
        HBox.setHgrow(ideaSection,       Priority.ALWAYS);

        Label header = new Label(
                "Ajuste a aparência, a intensidade dos lembretes e as categorias usadas para organizar seus registros.");
        header.setWrapText(true);

        VBox content = new VBox(14, header, buildThemeSection(), buildNotificationSection(),
                buildQuickCaptureSection());
        if (localMetricsService != null) content.getChildren().add(buildLocalMetricsSection());
        content.getChildren().addAll(row1, row2, row3);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        tab.setContent(scroll);
        return tab;
    }

    VBox buildLocalMetricsSection() {
        if (localMetricsService == null) {
            throw new IllegalStateException("Serviço de métricas locais não configurado");
        }
        Label title = new Label("Métricas locais de uso");
        title.getStyleClass().add("section-title");
        Label privacy = new Label(
                "Somente tempos, contagens e horários ficam armazenados neste dispositivo.");
        privacy.getStyleClass().add("t-muted");
        privacy.setWrapText(true);

        CheckBox enabled = new CheckBox("Mostrar e registrar métricas locais");
        enabled.setId("local-metrics-enabled");
        enabled.setSelected(localMetricsService.isEnabled());
        Button clear = new Button("Apagar histórico local");
        clear.setId("local-metrics-clear");
        clear.getStyleClass().add("danger-button");

        enabled.setOnAction(event -> {
            localMetricsService.setEnabled(enabled.isSelected());
            localMetricsChanged.run();
            ctx.setStatus(enabled.isSelected()
                    ? "Métricas locais ativadas."
                    : "Métricas locais desativadas. Nenhum novo registro será criado.");
        });
        clear.setOnAction(event -> Dialogs.confirm(
                        "Apagar métricas locais",
                        "Apagar todo o histórico local de métricas?",
                        "A configuração de ativação será mantida.")
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    localMetricsService.clear();
                    localMetricsChanged.run();
                    ctx.setStatus("Histórico local de métricas apagado.");
                }));

        HBox controls = new HBox(12, enabled, clear);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(10, title, privacy, controls);
        section.setId("config-local-metrics");
        section.getStyleClass().add("config-section");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    VBox buildNotificationSection() {
        PendencyNotificationService service = notificationService;

        Label titleLabel = new Label("Lembretes e estímulos");
        titleLabel.getStyleClass().add("section-title");

        CheckBox enabled = new CheckBox("Lembretes periódicos");
        enabled.setId("notifications-enabled");
        enabled.setSelected(service.isEnabled());
        CheckBox sound = new CheckBox("Som");
        sound.setId("notifications-sound");
        sound.setSelected(service.isSoundEnabled());
        CheckBox animation = new CheckBox("Animar indicador de pendências");
        animation.setId("notifications-animation");
        animation.setSelected(service.isBadgeAnimationEnabled());

        Button testSound = new Button("Testar som");
        testSound.setId("notifications-test-sound");
        testSound.getStyleClass().add("secondary-button");

        ComboBox<Integer> interval = new ComboBox<>();
        interval.setId("notifications-interval");
        interval.getItems().addAll(5, 15, 30, 60);
        interval.setValue(service.getIntervalMinutes());
        interval.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer value) {
                return value == null ? "" : value + " minutos";
            }
            @Override public Integer fromString(String value) {
                return Integer.valueOf(value.replaceAll("\\D", ""));
            }
        });

        CheckBox quietHours = new CheckBox("Horário silencioso");
        quietHours.setId("notifications-quiet-enabled");
        quietHours.setSelected(service.isQuietHoursEnabled());
        ComboBox<LocalTime> quietStart = buildTimeSelector(
                "notifications-quiet-start", service.getQuietHoursStart());
        ComboBox<LocalTime> quietEnd = buildTimeSelector(
                "notifications-quiet-end", service.getQuietHoursEnd());

        Runnable syncDisabledState = () -> {
            boolean remindersDisabled = !enabled.isSelected();
            sound.setDisable(remindersDisabled);
            animation.setDisable(remindersDisabled);
            interval.setDisable(remindersDisabled);
            testSound.setDisable(remindersDisabled || !sound.isSelected());
            quietHours.setDisable(remindersDisabled);
            quietStart.setDisable(remindersDisabled || !quietHours.isSelected());
            quietEnd.setDisable(remindersDisabled || !quietHours.isSelected());
        };
        syncDisabledState.run();

        enabled.setOnAction(e -> {
            service.setEnabled(enabled.isSelected());
            syncDisabledState.run();
            notificationSettingsChanged.run();
            ctx.setStatus(enabled.isSelected()
                    ? "Lembretes periódicos ativados."
                    : "Lembretes desligados. Contagens continuam visíveis.");
        });
        sound.setOnAction(e -> {
            service.setSoundEnabled(sound.isSelected());
            syncDisabledState.run();
            ctx.setStatus(sound.isSelected() ? "Som dos lembretes ativado." : "Som dos lembretes desativado.");
        });
        animation.setOnAction(e -> {
            service.setBadgeAnimationEnabled(animation.isSelected());
            notificationSettingsChanged.run();
            ctx.setStatus(animation.isSelected() ? "Animação do indicador ativada." : "Indicador mantido estático.");
        });
        interval.setOnAction(e -> {
            Integer value = interval.getValue();
            if (value != null) {
                service.setIntervalMinutes(value);
                ctx.setStatus("Intervalo dos lembretes: " + value + " minutos.");
            }
        });
        testSound.setOnAction(e -> ctx.setStatus(soundTestStatus(service.testSound())));
        quietHours.setOnAction(e -> {
            service.setQuietHoursEnabled(quietHours.isSelected());
            syncDisabledState.run();
            ctx.setStatus(quietHours.isSelected()
                    ? "Horário silencioso ativado."
                    : "Horário silencioso desativado.");
        });
        Runnable saveQuietHours = () -> {
            service.setQuietHours(quietStart.getValue(), quietEnd.getValue());
            ctx.setStatus("Horário silencioso: " + formatTime(quietStart.getValue())
                    + " até " + formatTime(quietEnd.getValue()) + ".");
        };
        quietStart.setOnAction(e -> saveQuietHours.run());
        quietEnd.setOnAction(e -> saveQuietHours.run());

        Label intervalLabel = new Label("Intervalo");
        HBox primaryControls = new HBox(
                18, enabled, sound, animation, intervalLabel, interval, testSound);
        primaryControls.setAlignment(Pos.CENTER_LEFT);
        Label quietStartLabel = new Label("Das");
        Label quietEndLabel = new Label("até");
        HBox quietControls = new HBox(
                10, quietHours, quietStartLabel, quietStart, quietEndLabel, quietEnd);
        quietControls.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, titleLabel, primaryControls, quietControls);
        section.getStyleClass().addAll("config-section", "config-notifications");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    private ComboBox<LocalTime> buildTimeSelector(String id, LocalTime selected) {
        ComboBox<LocalTime> selector = new ComboBox<>();
        selector.setId(id);
        for (int hour = 0; hour < 24; hour++) {
            selector.getItems().add(LocalTime.of(hour, 0));
            selector.getItems().add(LocalTime.of(hour, 30));
        }
        selector.setValue(selected);
        selector.setPrefWidth(92);
        selector.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalTime value) {
                return value == null ? "" : formatTime(value);
            }
            @Override public LocalTime fromString(String value) {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
            }
        });
        return selector;
    }

    private String formatTime(LocalTime value) {
        return value.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String soundTestStatus(PendencyNotificationService.SoundTestResult result) {
        return switch (result) {
            case PLAYED -> "Som de teste reproduzido uma vez.";
            case ALREADY_PLAYING -> "O som anterior ainda está em reprodução.";
            case REMINDERS_DISABLED -> "Teste indisponível: lembretes estão desligados.";
            case SOUND_DISABLED -> "Teste indisponível: ative o som dos lembretes.";
            case SNOOZED -> "Som não reproduzido: lembretes estão pausados.";
            case QUIET_HOURS -> "Som não reproduzido: horário silencioso ativo.";
        };
    }

    private VBox buildQuickCaptureSection() {
        QuickCapturePreferences preferences = AppContextHolder.get().quickCapturePreferences();

        Label titleLabel = new Label("Captura rápida");
        titleLabel.getStyleClass().add("section-title");

        Label shortcutLabel = new Label("Atalho");
        ComboBox<QuickCaptureShortcut> shortcut = new ComboBox<>();
        shortcut.setId("quick-capture-shortcut");
        shortcut.getItems().setAll(QuickCaptureShortcut.values());
        shortcut.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(QuickCaptureShortcut value) {
                return value == null ? "" : value.label();
            }
            @Override public QuickCaptureShortcut fromString(String value) {
                return shortcut.getItems().stream()
                        .filter(option -> option.label().equals(value))
                        .findFirst().orElse(QuickCapturePreferences.DEFAULT_SHORTCUT);
            }
        });
        shortcut.setValue(preferences.getShortcut());
        shortcut.setOnAction(event -> {
            QuickCaptureShortcut selected = shortcut.getValue();
            if (selected == null) return;
            preferences.setShortcut(selected);
            quickCaptureShortcutChanged.run();
            ctx.setStatus(selected == QuickCaptureShortcut.DISABLED
                    ? "Atalho da captura rápida desativado."
                    : "Atalho da captura rápida: " + selected.label() + ".");
        });

        Button restore = new Button("Restaurar padrão");
        restore.setId("quick-capture-shortcut-restore");
        restore.getStyleClass().add("secondary-button");
        restore.setOnAction(event -> {
            preferences.restoreDefault();
            shortcut.setValue(QuickCapturePreferences.DEFAULT_SHORTCUT);
        });

        HBox controls = new HBox(10, shortcutLabel, shortcut, restore);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(10, titleLabel, controls);
        section.getStyleClass().addAll("config-section", "config-quick-capture");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    /** Seção de seleção de tema visual */
    private VBox buildThemeSection() {
        Label titleLabel = new Label("🎨  Aparência / Tema Visual");
        titleLabel.getStyleClass().add("section-title");

        Label desc = new Label(
                "Escolha o tema visual que será aplicado em toda a aplicação. " +
                "A preferência é salva automaticamente.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11.5px;");

        ToggleGroup group = new ToggleGroup();

        ThemeManager.Theme current = ThemeManager.getInstance().getTheme();

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_LEFT);

        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            ToggleButton btn = new ToggleButton(theme.label);
            btn.setToggleGroup(group);
            btn.getStyleClass().add("filter-toggle");
            btn.setSelected(theme == current);
            btn.setUserData(theme);
            btn.setOnAction(e -> {
                if (btn.isSelected()) {
                    ThemeManager.getInstance().setTheme(theme);
                    ctx.setStatus("Tema alterado para: " + theme.label);
                }
            });
            btns.getChildren().add(btn);
        }

        // Garante que sempre haja um botão selecionado
        group.selectedToggleProperty().addListener((obs, old, nw) -> {
            if (nw == null && old != null) old.setSelected(true);
        });

        VBox section = new VBox(10, titleLabel, desc, btns);
        section.getStyleClass().addAll("config-section", "config-theme");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    private VBox buildCategorySection(String title, String styleClass,
                                      CategoryDomain domain,
                                      ObservableList<Category> catList) {
        ListView<Category> listView = new ListView<>(catList);
        listView.getStyleClass().add("clean-list");
        listView.setPrefHeight(180);

        TextField nameField = new TextField();
        nameField.getStyleClass().add("input-control");
        nameField.setPromptText("Nome da nova categoria...");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Button addBtn = new Button("Adicionar");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> {
            if (nameField.getText().isBlank()) { ctx.setStatus("Informe o nome da categoria."); return; }
            try {
                AppContextHolder.get().categoryService().add(nameField.getText(), domain);
                nameField.clear();
                ctx.refreshCategories();
                ctx.setStatus("Categoria adicionada com sucesso.");
            } catch (IllegalArgumentException ex) {
                ctx.setStatus("Erro: " + ex.getMessage());
            }
        });
        nameField.setOnAction(e -> addBtn.fire());

        Button removeBtn = new Button("Remover selecionada");
        removeBtn.getStyleClass().add("secondary-button");
        removeBtn.setMaxWidth(Double.MAX_VALUE);
        removeBtn.setOnAction(e -> {
            Category sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null) { ctx.setStatus("Selecione uma categoria para remover."); return; }
            Dialogs.confirm("Confirmar remoção", "Remover categoria \"" + sel.name() + "\"?",
                    "Esta ação remove a categoria permanentemente.\n" +
                    "Os registros vinculados a ela não serão excluídos,\n" +
                    "mas ficarão sem categoria associada.")
                    .ifPresent(btn -> {
                        if (btn == ButtonType.OK) {
                            AppContextHolder.get().categoryService().remove(sel.id());
                            ctx.refreshCategories();
                            ctx.setStatus("Categoria \"" + sel.name() + "\" removida.");
                        }
                    });
        });

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        HBox addBar = new HBox(8, nameField, addBtn);
        addBar.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, titleLabel, listView, addBar, removeBtn);
        section.getStyleClass().addAll("config-section", styleClass);
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }
}
