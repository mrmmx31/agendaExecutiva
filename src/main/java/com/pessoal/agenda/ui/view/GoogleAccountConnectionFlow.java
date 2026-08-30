package com.pessoal.agenda.ui.view;

import com.pessoal.agenda.service.GoogleAuthService;
import com.pessoal.agenda.service.GoogleSyncException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Fluxo OAuth compartilhado pela configuração e pela janela de sincronização. */
public final class GoogleAccountConnectionFlow {
    private static final GoogleOperationGuard OPERATION_GUARD = GoogleOperationGuard.shared();
    private static final ConnectionAttempt NO_ATTEMPT = () -> {};

    private GoogleAccountConnectionFlow() {}

    public static boolean isGoogleOperationRunning() {
        return OPERATION_GUARD.isRunning();
    }

    public static ConnectionAttempt start(GoogleAuthService auth,
                                          Consumer<String> status,
                                          Consumer<Boolean> busy,
                                          Runnable onSuccess,
                                          Runnable onCancelled,
                                          Consumer<Throwable> onError) {
        if (!auth.hasValidCredentials()) {
            onError.accept(GoogleSyncException.configuration(
                    "Arquivo ausente ou inválido: ~/.agenda/google-credentials.json."));
            return NO_ATTEMPT;
        }

        Alert choice = Dialogs.build(Alert.AlertType.CONFIRMATION,
                "Conectar conta Google",
                "Escolha onde autorizar a conta",
                "O Google exibirá a seleção de contas. O link também pode ser colado em outro "
                        + "navegador ou perfil conectado à conta autorizada para esta aplicação.");
        ButtonType openAndCopy = new ButtonType("Abrir e copiar link", ButtonBar.ButtonData.OK_DONE);
        ButtonType copyOnly = new ButtonType("Somente copiar link", ButtonBar.ButtonData.OTHER);
        choice.getButtonTypes().setAll(openAndCopy, copyOnly, ButtonType.CANCEL);
        ButtonType selected = choice.showAndWait().orElse(ButtonType.CANCEL);
        if (selected == ButtonType.CANCEL) return NO_ATTEMPT;

        if (!OPERATION_GUARD.tryStart()) {
            status.accept("Aguarde a operação Google em andamento terminar.");
            return NO_ATTEMPT;
        }

        boolean openBrowser = selected == openAndCopy;
        GoogleAuthService.AuthorizationSession session = auth.newAuthorizationSession();
        busy.accept(true);
        status.accept("Preparando autorização OAuth...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                session.authorize(
                        message -> Platform.runLater(() -> status.accept(message)),
                        url -> Platform.runLater(() -> copyAuthorizationUrl(
                                url, openBrowser, status)),
                        openBrowser);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            finish(busy);
            onSuccess.run();
        });
        task.setOnFailed(event -> {
            finish(busy);
            Throwable error = task.getException();
            if (error instanceof CancellationException) {
                onCancelled.run();
            } else {
                onError.accept(error);
            }
        });
        task.setOnCancelled(event -> {
            finish(busy);
            onCancelled.run();
        });
        Thread thread = new Thread(task, "google-oauth");
        thread.setDaemon(true);
        thread.start();
        return session::cancel;
    }

    @FunctionalInterface
    public interface ConnectionAttempt {
        void cancel();
    }

    private static void copyAuthorizationUrl(String url, boolean browserWillOpen,
                                             Consumer<String> status) {
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        status.accept(browserWillOpen
                ? "Link copiado; escolha a conta correta no Google."
                : "Link copiado. Cole-o no navegador da conta correta.");
    }

    private static void finish(Consumer<Boolean> busy) {
        OPERATION_GUARD.finish();
        busy.accept(false);
    }
}
