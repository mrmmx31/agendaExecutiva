package com.pessoal.agenda.service;

import java.io.IOException;

public final class GoogleSyncErrorPresenter {
    private GoogleSyncErrorPresenter() {}

    public static String userMessage(Throwable error) {
        GoogleSyncException classified = classify(error);
        return classified != null
                ? classified.userMessage()
                : "Não foi possível concluir a operação com o Google. Tente novamente.";
    }

    public static String logMessage(Throwable error) {
        GoogleSyncException classified = classify(error);
        if (classified == null) return "falha não classificada";
        String status = classified.statusCode() > 0
                ? ", HTTP " + classified.statusCode() : "";
        return classified.kind().name() + status;
    }

    private static GoogleSyncException classify(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof GoogleSyncException exception) return exception;
            if (current instanceof IOException ioException) {
                return GoogleSyncException.fromIOException(ioException);
            }
            current = current.getCause();
        }
        return null;
    }
}
