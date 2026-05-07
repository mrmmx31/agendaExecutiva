package com.pessoal.agenda.ui.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
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
        stage = new Stage();
        stage.setTitle("Pré-visualização: " + windowTitle);
        stage.initModality(Modality.NONE);
        stage.setResizable(true);

        // ── WebView ───────────────────────────────────────────────────────
        webView = new WebView();
        webView.setZoom(1.0);
        engine = webView.getEngine();
        engine.loadContent(htmlContent, "text/html");

        // ── Toolbar ───────────────────────────────────────────────────────
        Button printBtn = new Button("🖨  Imprimir");
        printBtn.setStyle(
                "-fx-background-color: #1e3a5f; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-font-size: 12px; -fx-padding: 7 16 7 16; -fx-background-radius: 5;"
                + " -fx-cursor: hand;");
        printBtn.setOnAction(e -> triggerPrint());

        ToggleButton monoToggle = new ToggleButton("Monocromático");
        monoToggle.setStyle(
                "-fx-font-size: 11.5px; -fx-padding: 6 14 6 14; -fx-background-radius: 5;"
                + " -fx-cursor: hand;");
        monoToggle.setSelected(false);
        monoToggle.setOnAction(e -> {
            isMonoMode = monoToggle.isSelected();
            toggleMonoMode(isMonoMode);
            monoToggle.setText(isMonoMode ? "✓ Monocromático" : "Monocromático");
        });

        Label zoomLabel = new Label("Zoom:");
        zoomLabel.setStyle("-fx-font-size: 11px;");

        Button zoomInBtn  = new Button("+");
        Button zoomOutBtn = new Button("−");
        zoomInBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        zoomOutBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        zoomInBtn.setOnAction(e  -> webView.setZoom(Math.min(2.5, webView.getZoom() + 0.15)));
        zoomOutBtn.setOnAction(e -> webView.setZoom(Math.max(0.4, webView.getZoom() - 0.15)));

        Button closeBtn = new Button("✕  Fechar");
        closeBtn.setStyle(
                "-fx-background-color: #607d8b; -fx-text-fill: white;"
                + " -fx-font-size: 11px; -fx-padding: 6 12 6 12; -fx-background-radius: 5;"
                + " -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        Label hint = new Label("Dica: alterne Colorido/Mono antes de imprimir.");
        hint.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #7a9abc; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, printBtn, monoToggle, spacer,
                zoomLabel, zoomOutBtn, zoomInBtn, hint, closeBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 14, 10, 14));
        toolbar.setStyle(
                "-fx-background-color: #f0f4f8;"
                + " -fx-border-color: #ccd9e8; -fx-border-width: 0 0 1 0;");

        // ── Layout ────────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(webView);

        Scene scene = new Scene(root, 960, 740);
        stage.setScene(scene);
        stage.show();

        // Registra para fechar junto com a janela principal
        WindowManager.register(stage);
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
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Erro de impressão");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    // ── Factory conveniente ───────────────────────────────────────────────────

    public static void open(String htmlContent, String windowTitle) {
        new PrintPreviewWindow(htmlContent, windowTitle).show();
    }
}
