package com.pessoal.agenda.ui.view;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;

import java.util.Optional;

/**
 * Fábrica central de caixas de diálogo.
 *
 * Garante que todos os diálogos da aplicação:
 *   • Usam Modality.NONE  → não bloqueiam a janela principal (evita bugs de maximização no Linux)
 *   • Recebem o tema CSS automaticamente via ThemeManager.initGlobalWindowHook()
 *
 * Uso:
 *   Dialogs.confirm("Título", "Cabeçalho", "Mensagem")
 *          .filter(b -> b == ButtonType.OK)
 *          .ifPresent(b -> { ... });
 *
 *   Dialogs.info("Título", "Mensagem");
 *   Dialogs.error("Título", "Mensagem detalhada");
 */
public final class Dialogs {

    private Dialogs() {}

    // ── Confirmação ───────────────────────────────────────────────────────

    /**
     * Exibe um diálogo de confirmação (OK / Cancelar) e retorna o botão pressionado.
     */
    public static Optional<ButtonType> confirm(String title, String header, String content) {
        Alert a = build(Alert.AlertType.CONFIRMATION, title, header, content);
        return a.showAndWait();
    }

    /**
     * Exibe um diálogo de confirmação sem texto de conteúdo.
     */
    public static Optional<ButtonType> confirm(String title, String header) {
        return confirm(title, header, null);
    }

    // ── Informação ────────────────────────────────────────────────────────

    /**
     * Exibe um diálogo informativo (apenas OK).
     */
    public static void info(String title, String message) {
        build(Alert.AlertType.INFORMATION, title, null, message).showAndWait();
    }

    // ── Aviso ─────────────────────────────────────────────────────────────

    /**
     * Exibe um diálogo de aviso (apenas OK).
     */
    public static void warning(String title, String message) {
        build(Alert.AlertType.WARNING, title, null, message).showAndWait();
    }

    // ── Erro ─────────────────────────────────────────────────────────────

    /**
     * Exibe um diálogo de erro (apenas OK).
     */
    public static void error(String title, String message) {
        build(Alert.AlertType.ERROR, title, null, message).showAndWait();
    }

    /**
     * Exibe um diálogo de erro com cabeçalho e detalhe separados.
     */
    public static void error(String title, String header, String detail) {
        build(Alert.AlertType.ERROR, title, header, detail).showAndWait();
    }

    // ── Entrada de texto ──────────────────────────────────────────────────

    /**
     * Exibe um diálogo de entrada de texto simples.
     * @return texto digitado, ou Optional.empty() se cancelado
     */
    public static Optional<String> input(String title, String header, String defaultValue) {
        TextInputDialog d = new TextInputDialog(defaultValue != null ? defaultValue : "");
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(null);
        d.initModality(Modality.NONE);
        return d.showAndWait();
    }

    // ── Builder interno ───────────────────────────────────────────────────

    /**
     * Cria e configura um Alert com as propriedades padrão da aplicação.
     * O tema CSS é aplicado automaticamente pelo ThemeManager.initGlobalWindowHook().
     */
    public static Alert build(Alert.AlertType type, String title, String header, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(header);            // null → sem cabeçalho (mostra só content)
        a.setContentText(content);
        a.initModality(Modality.NONE);      // não bloqueia a janela principal maximizada
        a.getDialogPane().setMinWidth(360); // largura mínima razoável
        return a;
    }
}

