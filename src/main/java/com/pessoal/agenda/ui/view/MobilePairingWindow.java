package com.pessoal.agenda.ui.view;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.pessoal.agenda.app.AppContextHolder;
import com.pessoal.agenda.infra.pairing.LocalNetworkAddressSelector;
import com.pessoal.agenda.infra.pairing.LocalPairingServer;
import com.pessoal.agenda.infra.pairing.PairingSession;
import com.pessoal.agenda.infra.pairing.PendingPairingRequest;
import com.pessoal.agenda.repository.DesktopSyncRepository;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public final class MobilePairingWindow {
    private static Stage openStage;

    private final DesktopSyncRepository repository = AppContextHolder.get().desktopSyncRepository();
    private final Timeline poller = new Timeline(new KeyFrame(Duration.seconds(1), event -> poll()));

    private Stage stage;
    private LocalPairingServer server;
    private PairingSession session;
    private Label status;
    private Label expiration;
    private VBox invitationPanel;
    private VBox approvalPanel;
    private VBox devicesPanel;
    private Label pendingDevice;
    private Label pendingRoles;
    private Button approve;
    private Button reject;
    private String displayedRequestId;

    public void show() {
        if (openStage != null && openStage.isShowing()) {
            openStage.toFront();
            openStage.requestFocus();
            return;
        }

        stage = WindowManager.createModelessStage();
        stage.setTitle("Dispositivos móveis");
        stage.setMinWidth(620);
        stage.setMinHeight(560);

        Label title = new Label("Dispositivos móveis");
        title.getStyleClass().add("page-title");
        Button start = new Button("Gerar convite de pareamento");
        start.getStyleClass().add("primary-button");
        start.setOnAction(event -> startPairing());

        status = new Label("Nenhuma sessão de pareamento ativa.");
        status.getStyleClass().add("t-muted");
        status.setWrapText(true);

        invitationPanel = buildInvitationPanel();
        approvalPanel = buildApprovalPanel();
        devicesPanel = new VBox(8);
        refreshDevices();

        Label devicesTitle = new Label("Dispositivos conhecidos");
        devicesTitle.getStyleClass().add("section-title");
        VBox content = new VBox(14, title, start, status, invitationPanel, approvalPanel,
                new Separator(), devicesTitle, devicesPanel);
        content.setPadding(new Insets(18));
        content.getStyleClass().addAll("app-root", "secondary-window-root");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        Scene scene = new Scene(scroll, 720, 700);
        ThemeManager.getInstance().applyTo(scene);
        stage.setScene(scene);
        stage.setOnHidden(event -> closeSession());
        openStage = stage;
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
        WindowManager.show(stage);
    }

    private VBox buildInvitationPanel() {
        ImageView qr = new ImageView();
        qr.setId("mobile-pairing-qr");
        qr.setFitWidth(220);
        qr.setFitHeight(220);
        qr.setPreserveRatio(true);

        Label codeTitle = new Label("Código de uso único");
        codeTitle.getStyleClass().add("form-label");
        Label code = new Label();
        code.setId("mobile-pairing-code");
        code.setStyle("-fx-font-size: 28px; -fx-font-weight: 700;");
        expiration = new Label();
        expiration.getStyleClass().add("t-muted");

        Button copy = new Button("Copiar convite");
        copy.getStyleClass().add("secondary-button");
        copy.setOnAction(event -> {
            if (session == null) return;
            ClipboardContent content = new ClipboardContent();
            content.putString(session.invitation());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText("Convite copiado. Ele expira junto com o código.");
        });

        VBox details = new VBox(10, codeTitle, code, expiration, copy);
        details.setAlignment(Pos.CENTER_LEFT);
        HBox box = new HBox(20, qr, details);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(details, Priority.ALWAYS);
        VBox panel = new VBox(box);
        panel.getStyleClass().add("config-section");
        panel.setPadding(new Insets(14));
        panel.setVisible(false);
        panel.setManaged(false);
        panel.getProperties().put("qr", qr);
        panel.getProperties().put("code", code);
        return panel;
    }

    private VBox buildApprovalPanel() {
        Label heading = new Label("Solicitação recebida");
        heading.getStyleClass().add("section-title");
        pendingDevice = new Label();
        pendingDevice.setWrapText(true);
        pendingRoles = new Label();
        pendingRoles.setWrapText(true);
        pendingRoles.getStyleClass().add("t-muted");

        approve = new Button("Aprovar dispositivo");
        approve.getStyleClass().add("primary-button");
        reject = new Button("Rejeitar");
        reject.getStyleClass().add("danger-button");
        FlowPane actions = new FlowPane(10, 8, approve, reject);
        actions.setAlignment(Pos.CENTER_LEFT);

        approve.setOnAction(event -> approvePending());
        reject.setOnAction(event -> rejectPending());
        VBox panel = new VBox(10, heading, pendingDevice, pendingRoles, actions);
        panel.getStyleClass().add("config-section");
        panel.setPadding(new Insets(14));
        panel.setVisible(false);
        panel.setManaged(false);
        return panel;
    }

    private void startPairing() {
        closeServerOnly();
        try {
            server = new LocalPairingServer(
                    repository, LocalNetworkAddressSelector.select(), 0);
            session = server.start();
            displayedRequestId = null;
            invitationPanel.setVisible(true);
            invitationPanel.setManaged(true);
            approvalPanel.setVisible(false);
            approvalPanel.setManaged(false);
            ((ImageView) invitationPanel.getProperties().get("qr"))
                    .setImage(qrImage(session.invitation(), 220));
            ((Label) invitationPanel.getProperties().get("code"))
                    .setText(session.oneTimeCode());
            status.setText("Sessão ativa na rede local. Confirme o nome do aparelho antes de aprovar.");
            poll();
        } catch (RuntimeException error) {
            closeServerOnly();
            status.setText("Não foi possível iniciar: " + rootMessage(error));
            Dialogs.error("Pareamento indisponível", rootMessage(error));
        }
    }

    private void poll() {
        if (session == null || server == null) return;
        long seconds = Instant.now().until(session.expiresAt(), ChronoUnit.SECONDS);
        if (seconds <= 0) {
            status.setText("O convite expirou. Gere outro para tentar novamente.");
            invitationPanel.setVisible(false);
            invitationPanel.setManaged(false);
            approvalPanel.setVisible(false);
            approvalPanel.setManaged(false);
            closeServerOnly();
            return;
        }
        expiration.setText("Expira em " + (seconds / 60) + " min " + (seconds % 60) + " s");
        PendingPairingRequest pending = server.pendingRequest();
        if (pending == null || pending.requestId().equals(displayedRequestId)) return;
        displayedRequestId = pending.requestId();
        pendingDevice.setText("Aparelho: " + pending.deviceName());
        pendingRoles.setText("Permissões solicitadas: " + roleNames(pending.requestedRoles()));
        approvalPanel.setVisible(true);
        approvalPanel.setManaged(true);
        approve.setDisable(false);
        reject.setDisable(false);
        status.setText("Revise a solicitação recebida. Nenhuma credencial foi entregue ainda.");
    }

    private void approvePending() {
        PendingPairingRequest pending = server == null ? null : server.pendingRequest();
        if (pending == null) return;
        try {
            server.approve(pending.requestId(), pending.requestedRoles());
            approve.setDisable(true);
            reject.setDisable(true);
            status.setText("Dispositivo aprovado. Aguardando o aplicativo concluir o recebimento.");
            refreshDevices();
        } catch (RuntimeException error) {
            Dialogs.error("Erro ao aprovar", rootMessage(error));
        }
    }

    private void rejectPending() {
        PendingPairingRequest pending = server == null ? null : server.pendingRequest();
        if (pending == null) return;
        server.reject(pending.requestId());
        approve.setDisable(true);
        reject.setDisable(true);
        status.setText("Solicitação rejeitada.");
    }

    private void refreshDevices() {
        devicesPanel.getChildren().clear();
        var devices = repository.listDevices();
        if (devices.isEmpty()) {
            Label empty = new Label("Nenhum dispositivo pareado.");
            empty.getStyleClass().add("t-muted");
            devicesPanel.getChildren().add(empty);
            return;
        }
        for (DesktopSyncRepository.DeviceRecord device : devices) {
            Label name = new Label(device.deviceName());
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);
            Label state = new Label(device.status().equals("ACTIVE") ? "Ativo" : "Revogado");
            state.getStyleClass().add(device.status().equals("ACTIVE") ? "t-success" : "t-muted");
            Button revoke = new Button("Revogar");
            revoke.getStyleClass().add("danger-button");
            revoke.setDisable(!device.status().equals("ACTIVE"));
            revoke.setTooltip(new Tooltip("Revogar acesso deste dispositivo"));
            revoke.setOnAction(event -> confirmRevocation(device));
            HBox row = new HBox(10, name, state, revoke);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));
            devicesPanel.getChildren().add(row);
        }
    }

    private void confirmRevocation(DesktopSyncRepository.DeviceRecord device) {
        Dialogs.confirm("Revogar dispositivo", "Revogar " + device.deviceName() + "?",
                        "O aplicativo móvel precisará ser pareado novamente.")
                .filter(button -> button == ButtonType.OK)
                .ifPresent(button -> {
                    repository.revokeDevice(device.deviceId());
                    refreshDevices();
                    status.setText("Acesso de " + device.deviceName() + " revogado.");
                });
    }

    private static Image qrImage(String value, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
            WritableImage image = new WritableImage(size, size);
            var pixels = image.getPixelWriter();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return image;
        } catch (WriterException error) {
            throw new IllegalStateException("Não foi possível gerar o QR Code.", error);
        }
    }

    private static String roleNames(Set<String> roles) {
        return roles.stream().sorted(Comparator.naturalOrder()).map(role -> switch (role) {
            case "TASKS_READ" -> "ler tarefas";
            case "CAPTURES_WRITE" -> "enviar capturas";
            case "PROTOCOLS_EXECUTE" -> "executar protocolos";
            default -> role;
        }).collect(Collectors.joining(", "));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "erro interno" : current.getMessage();
    }

    private void closeServerOnly() {
        if (server != null) server.close();
        server = null;
        session = null;
    }

    private void closeSession() {
        poller.stop();
        closeServerOnly();
        openStage = null;
    }
}
