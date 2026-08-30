package com.pessoal.agenda.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

public class GoogleSyncException extends IOException {
    public enum Kind {
        AUTHENTICATION,
        RATE_LIMIT,
        TIMEOUT,
        NETWORK,
        SERVER,
        INVALID_RESPONSE,
        CONFIGURATION,
        REQUEST
    }

    private final Kind kind;
    private final int statusCode;
    private final boolean retryable;
    private final String recoveryAction;

    private GoogleSyncException(Kind kind, int statusCode, boolean retryable,
                                String message, String recoveryAction, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.recoveryAction = recoveryAction;
    }

    public Kind kind() {
        return kind;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public String recoveryAction() {
        return recoveryAction;
    }

    public String userMessage() {
        return getMessage() + " " + recoveryAction;
    }

    public static GoogleSyncException forStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new GoogleSyncException(Kind.AUTHENTICATION, statusCode, false,
                    "A autorização do Google não é mais válida.",
                    "Desconecte e conecte novamente.", null);
        }
        if (statusCode == 408) {
            return new GoogleSyncException(Kind.TIMEOUT, statusCode, true,
                    "O Google demorou demais para responder.",
                    "Verifique a conexão e tente novamente.", null);
        }
        if (statusCode == 429) {
            return new GoogleSyncException(Kind.RATE_LIMIT, statusCode, true,
                    "O limite temporário do Google Tasks foi atingido.",
                    "Aguarde alguns minutos e tente novamente.", null);
        }
        if (statusCode >= 500) {
            return new GoogleSyncException(Kind.SERVER, statusCode, true,
                    "O Google Tasks está temporariamente indisponível.",
                    "Tente novamente em alguns minutos.", null);
        }
        return new GoogleSyncException(Kind.REQUEST, statusCode, false,
                "O Google recusou a operação solicitada.",
                "Confira os dados e tente novamente.", null);
    }

    public static GoogleSyncException oauthRejected(int statusCode) {
        return new GoogleSyncException(Kind.AUTHENTICATION, statusCode, false,
                "O Google recusou a autorização.",
                "Inicie a conexão novamente.", null);
    }

    public static GoogleSyncException fromIOException(IOException cause) {
        if (cause instanceof GoogleSyncException exception) return exception;
        if (cause instanceof HttpTimeoutException) {
            return new GoogleSyncException(Kind.TIMEOUT, 0, true,
                    "A conexão com o Google excedeu o tempo limite.",
                    "Verifique a rede e tente novamente.", cause);
        }
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        if (cause instanceof ConnectException || message.contains("unresolved")
                || message.contains("network") || message.contains("connection")) {
            return new GoogleSyncException(Kind.NETWORK, 0, true,
                    "Não foi possível alcançar o Google Tasks.",
                    "Verifique a conexão com a internet e tente novamente.", cause);
        }
        return new GoogleSyncException(Kind.NETWORK, 0, true,
                "A comunicação com o Google Tasks falhou.",
                "Tente novamente sem alterar as tarefas.", cause);
    }

    public static GoogleSyncException invalidResponse() {
        return new GoogleSyncException(Kind.INVALID_RESPONSE, 0, true,
                "O Google retornou uma resposta incompleta.",
                "Nenhuma decisão foi aplicada; tente novamente.", null);
    }

    public static GoogleSyncException previewExpired() {
        return new GoogleSyncException(Kind.INVALID_RESPONSE, 0, true,
                "Os dados mudaram depois da prévia.",
                "Gere uma nova prévia antes de sincronizar.", null);
    }

    public static GoogleSyncException configuration(String detail) {
        return new GoogleSyncException(Kind.CONFIGURATION, 0, false,
                detail, "Revise a configuração antes de conectar.", null);
    }
}
