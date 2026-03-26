package com.pessoal.agenda.app;

/**
 * Holder simples para disponibilizar o AppContext durante a transicao da arquitetura.
 *
 * Observacao: em uma fase seguinte, o ideal e injetar o contexto via controller factory
 * do JavaFX, evitando estado global.
 */
public final class AppContextHolder {

    private static AppContext context;

    private AppContextHolder() {
    }

    public static void init(AppContext appContext) {
        context = appContext;
    }

    public static AppContext get() {
        if (context == null) {
            throw new IllegalStateException("AppContext nao inicializado");
        }
        return context;
    }
}

