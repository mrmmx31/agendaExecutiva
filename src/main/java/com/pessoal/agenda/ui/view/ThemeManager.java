package com.pessoal.agenda.ui.view;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Gerenciador de temas da aplicação.
 *
 * Pattern: Singleton — disponível em toda a aplicação.
 *
 * Uso:
 *   ThemeManager.getInstance().applyTo(scene);   // ao criar uma cena
 *   ThemeManager.getInstance().applyTo(root);    // ao criar um root node sem scene
 *   ThemeManager.getInstance().setTheme(Theme.ESCURO);  // troca o tema globalmente
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
        // Atenção: iteramos sobre uma cópia do keySet para evitar ConcurrentModificationException
        List<ObservableList<String>> snapshot;
        synchronized (registered) {
            snapshot = new ArrayList<>(registered.keySet());
        }
        for (ObservableList<String> sheets : snapshot) {
            // Remove override de tema
            sheets.removeIf(s -> s != null && s.contains("/theme-"));
            // Adiciona novo override
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

