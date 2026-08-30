package com.pessoal.agenda.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Janela de pré-visualização e impressão de relatórios HTML.
 *
 * Features:
 *  – WebView com o relatório HTML auto-contido
 *  – Botão "🖨 Imprimir" — usa PrinterJob (abre o diálogo nativo de impressão do SO)
 *  – Toggle "Colorido / Monocromático" — alterna o modo antes de imprimir
 *  – Zoom +/− para ajuste da pré-visualização
 */
public class PrintPreviewWindow {

    private final String htmlContent;
    private final String windowTitle;

    // Campos mantidos para uso posterior em triggerPrint()
    private WebEngine engine;
    private WebView   webView;
    private Stage     stage;
    private boolean   isMonoMode = false;

    public PrintPreviewWindow(String htmlContent, String windowTitle) {
        this.htmlContent = htmlContent;
        this.windowTitle  = windowTitle;
    }

    /** Abre a janela de pré-visualização (não bloqueia a thread JavaFX). */
    public void show() {
        stage = WindowManager.createModelessStage();
        stage.setTitle("Pré-visualização: " + windowTitle);
        stage.setResizable(true);

        // ── WebView ───────────────────────────────────────────────────────
        webView = new WebView();
        webView.setZoom(1.0);
        engine = webView.getEngine();
        engine.loadContent(htmlContent, "text/html");

        // ── Toolbar ───────────────────────────────────────────────────────
        Button printBtn = new Button("🖨  Imprimir");
        printBtn.getStyleClass().add("primary-button");
        printBtn.setOnAction(e -> triggerPrint());

        ToggleButton monoToggle = new ToggleButton("Monocromático");
        monoToggle.getStyleClass().add("secondary-button");
        monoToggle.setSelected(false);
        monoToggle.setOnAction(e -> {
            isMonoMode = monoToggle.isSelected();
            toggleMonoMode(isMonoMode);
            monoToggle.setText(isMonoMode ? "✓ Monocromático" : "Monocromático");
        });

        Label zoomLabel = new Label("Zoom:");
        zoomLabel.getStyleClass().add("form-label");

        Button zoomInBtn  = new Button("+");
        Button zoomOutBtn = new Button("−");
        zoomInBtn.getStyleClass().addAll("secondary-button", "icon-button");
        zoomOutBtn.getStyleClass().addAll("secondary-button", "icon-button");
        zoomInBtn.setTooltip(new javafx.scene.control.Tooltip("Aumentar zoom"));
        zoomOutBtn.setTooltip(new javafx.scene.control.Tooltip("Diminuir zoom"));
        zoomInBtn.setOnAction(e  -> webView.setZoom(Math.min(2.5, webView.getZoom() + 0.15)));
        zoomOutBtn.setOnAction(e -> webView.setZoom(Math.max(0.4, webView.getZoom() - 0.15)));

        Button closeBtn = new Button("✕  Fechar");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> stage.close());

        Label hint = new Label("Dica: alterne Colorido/Mono antes de imprimir.");
        hint.getStyleClass().add("print-preview-hint");
        ResponsiveWindowLayout.makeFlexible(hint);

        FlowPane commands = new FlowPane(10, 6,
                printBtn, monoToggle, zoomLabel, zoomOutBtn, zoomInBtn, closeBtn);
        commands.setAlignment(Pos.CENTER_LEFT);
        VBox toolbar = new VBox(5, commands, hint);
        toolbar.getStyleClass().add("print-preview-toolbar");

        // ── Layout ────────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(toolbar);
        root.setCenter(webView);

        Scene scene = new Scene(root, 960, 740);
        stage.setMinWidth(680);
        stage.setMinHeight(520);
        stage.setScene(scene);
        WindowManager.show(stage);
    }

    // ── Impressão via PrinterJob (abre diálogo nativo do sistema) ────────────

    private void triggerPrint() {
        if (engine == null || stage == null) return;

        // Garante que a página terminou de carregar antes de imprimir
        javafx.concurrent.Worker.State loadState = engine.getLoadWorker().getState();
        if (loadState == javafx.concurrent.Worker.State.RUNNING
                || loadState == javafx.concurrent.Worker.State.SCHEDULED) {
            // Aguarda conclusão do carregamento
            engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    doPrint();
                }
            });
        } else {
            doPrint();
        }
    }

    private void doPrint() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showError("Não foi possível criar o trabalho de impressão.\n"
                    + "Verifique se há uma impressora instalada no sistema.");
            return;
        }
        // showPrintDialog abre o diálogo nativo de seleção de impressora
        boolean confirmed = job.showPrintDialog(stage);
        if (confirmed) {
            engine.print(job);   // WebEngine.print() respeita o CSS do WebKit
            job.endJob();
        } else {
            job.cancelJob();
        }
    }

    // ── Alterna modo de cor via JavaScript ───────────────────────────────────

    private void toggleMonoMode(boolean mono) {
        if (engine == null) return;
        if (mono) {
            engine.executeScript(
                "document.body.classList.remove('color-mode');"
                + "document.body.classList.add('mono-mode');");
        } else {
            engine.executeScript(
                "document.body.classList.remove('mono-mode');"
                + "document.body.classList.add('color-mode');");
        }
    }

    private void showError(String msg) {
        Dialogs.error("Erro de impressão", msg);
    }

    // ── Factory conveniente ───────────────────────────────────────────────────

    public static void open(String htmlContent, String windowTitle) {
        new PrintPreviewWindow(htmlContent, windowTitle).show();
    }
}
