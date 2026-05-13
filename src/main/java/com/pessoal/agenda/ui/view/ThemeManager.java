package com.pessoal.agenda.ui.view;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Gerenciador de temas da aplicação.
 *
 * Pattern: Singleton — disponível em toda a aplicação.
 *
 * Uso normal:
 *   ThemeManager.getInstance().applyTo(scene);
 *   ThemeManager.getInstance().setTheme(Theme.ESCURO);
 *
 * Hook global (chamar UMA VEZ em AgendaApp.start):
 *   ThemeManager.getInstance().initGlobalWindowHook();
 *   → após isso, QUALQUER janela/caixa de diálogo que abrir receberá o tema
 *     automaticamente, sem precisar chamar applyTo() manualmente.
 */
public class ThemeManager {

    // ── Temas disponíveis ──────────────────────────────────────────────────
    public enum Theme {
        CLARO  ("☀️  Claro",         null),
        ESCURO ("🌙  Escuro",         "/com/pessoal/agenda/theme-dark.css");

        public final String label;
        public final String cssResource;

        Theme(String label, String cssResource) {
            this.label       = label;
            this.cssResource = cssResource;
        }
    }

    // ── Singleton ──────────────────────────────────────────────────────────
    private static final ThemeManager INSTANCE = new ThemeManager();
    public  static ThemeManager getInstance() { return INSTANCE; }

    private static final String APP_CSS = "/com/pessoal/agenda/app.css";

    // ── Estado ────────────────────────────────────────────────────────────
    private Theme currentTheme;

    /**
     * Registro fraco: cada entrada é a lista de stylesheets de uma cena ou nó.
     * WeakHashMap garante que entradas órfãs (janelas fechadas) sejam coletadas.
     */
    private final Map<ObservableList<String>, Void> registered =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final List<Consumer<Theme>> listeners = new ArrayList<>();
    private boolean globalHookInstalled = false;

    // ── Construtor ────────────────────────────────────────────────────────
    private ThemeManager() {
        Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
        try {
            currentTheme = Theme.valueOf(prefs.get("agenda.theme", Theme.CLARO.name()));
        } catch (Exception e) {
            currentTheme = Theme.CLARO;
        }
    }

    // ── API pública ────────────────────────────────────────────────────────

    public Theme getTheme() { return currentTheme; }

    /** Aplica o tema atual a uma Scene e registra para atualizações futuras. */
    public void applyTo(Scene scene) {
        applyToList(scene.getStylesheets());
    }

    /** Aplica o tema atual ao stylesheet de um nó raiz e registra para atualizações futuras. */
    public void applyTo(Parent root) {
        applyToList(root.getStylesheets());
    }

    /**
     * Instala o hook global: observa Window.getWindows() e aplica o tema
     * automaticamente a TODA janela que abrir (Alert, Dialog, Stage filhos, etc.).
     *
     * Deve ser chamado UMA VEZ, na thread JavaFX, após a aplicação estar iniciada.
     */
    public void initGlobalWindowHook() {
        if (globalHookInstalled) return;
        globalHookInstalled = true;

        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window w : change.getAddedSubList()) {
                    applyToWindowWhenReady(w);
                }
            }
        });
    }

    /** Troca o tema globalmente, atualizando TODAS as cenas/nós registrados. */
    public void setTheme(Theme theme) {
        if (theme == currentTheme) return;
        this.currentTheme = theme;
        Preferences.userNodeForPackage(ThemeManager.class).put("agenda.theme", theme.name());
        updateAllRegistered();
        listeners.forEach(l -> l.accept(theme));
    }

    /** Registra um ouvinte chamado cada vez que o tema mudar. */
    public void addThemeChangeListener(Consumer<Theme> listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(Consumer<Theme> listener) {
        listeners.remove(listener);
    }

    // ── Implementação interna ──────────────────────────────────────────────

    /**
     * Aplica o tema à janela W assim que a Scene estiver disponível.
     * Cobre dois casos:
     *   a) A Scene já existe (Alert/Dialog configurados antes do show)
     *   b) A Scene ainda não existe (Stage criado mas não exibido)
     */
    private void applyToWindowWhenReady(Window w) {
        Scene scene = w.getScene();
        if (scene != null) {
            applyToList(scene.getStylesheets());
        } else {
            // Escuta a propriedade cena e aplica quando ela aparecer
            w.sceneProperty().addListener((obs, old, newScene) -> {
                if (newScene != null) applyToList(newScene.getStylesheets());
            });
        }
    }

    private void applyToList(ObservableList<String> sheets) {
        String appUrl = resolveUrl(APP_CSS);
        if (appUrl == null) return;

        // Garante que app.css está na posição 0
        if (!sheets.contains(appUrl)) {
            sheets.add(0, appUrl);
        }
        // Remove CSS de tema anterior
        sheets.removeIf(s -> s != null && s.contains("/theme-"));
        // Adiciona CSS do tema atual (se não for o padrão Claro)
        if (currentTheme.cssResource != null) {
            String themeUrl = resolveUrl(currentTheme.cssResource);
            if (themeUrl != null && !sheets.contains(themeUrl)) {
                sheets.add(themeUrl);
            }
        }
        // Registra para atualizações futuras
        registered.put(sheets, null);
    }

    private void updateAllRegistered() {
        List<ObservableList<String>> snapshot;
        synchronized (registered) {
            snapshot = new ArrayList<>(registered.keySet());
        }
        for (ObservableList<String> sheets : snapshot) {
            sheets.removeIf(s -> s != null && s.contains("/theme-"));
            if (currentTheme.cssResource != null) {
                String themeUrl = resolveUrl(currentTheme.cssResource);
                if (themeUrl != null && !sheets.contains(themeUrl)) {
                    sheets.add(themeUrl);
                }
            }
        }
    }

    private String resolveUrl(String resource) {
        try {
            var url = ThemeManager.class.getResource(resource);
            return (url != null) ? url.toExternalForm() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

