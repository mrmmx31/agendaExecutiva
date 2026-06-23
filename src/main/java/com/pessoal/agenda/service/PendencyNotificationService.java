package com.pessoal.agenda.service;

import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Serviço de notificações periódicas para manter o usuário com TDAH ciente de pendências.
 *
 * Executa a cada 5 minutos e toca som + exibe notificação visual se houver:
 * - Tarefas vencidas
 * - Protocolos vencendo
 */
public class PendencyNotificationService {
    private static PendencyNotificationService instance;
    private Timer notificationTimer;
    private boolean hasAlerts = false;
    private Runnable onAlertDetected;

    private PendencyNotificationService() {}

    public static synchronized PendencyNotificationService getInstance() {
        if (instance == null) {
            instance = new PendencyNotificationService();
        }
        return instance;
    }

    /**
     * Inicia o serviço de notificações.
     * @param checkInterval intervalo em milissegundos (padrão: 5 min = 300000)
     * @param alertCallback chamado quando alertas são detectados
     */
    public void start(long checkInterval, Runnable alertCallback) {
        this.onAlertDetected = alertCallback;

        if (notificationTimer != null) {
            notificationTimer.cancel();
        }

        notificationTimer = new Timer("PendencyNotifier", true);
        notificationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAndNotify();
            }
        }, 0, checkInterval); // Começa imediatamente, depois a cada checkInterval
    }

    public void stop() {
        if (notificationTimer != null) {
            notificationTimer.cancel();
            notificationTimer = null;
        }
    }

    private void checkAndNotify() {
        // Este método será chamado periodicamente.
        // A verificação real acontece via callback na dashboard.
        if (hasAlerts) {
            playNotificationSound();
            if (onAlertDetected != null) {
                Platform.runLater(onAlertDetected);
            }
        }
    }

    /**
     * Sinaliza que há alertas pendentes (chamado pela dashboard ao atualizar).
     */
    public void setHasAlerts(boolean alerts) {
        this.hasAlerts = alerts;
    }

    private void playNotificationSound() {
        try {
            // Toca som do sistema (beep)
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            // Se não conseguir tocar som, apenas ignora (sem quebrar a aplicação)
        }
    }

    /**
     * Força execução imediata do check (para testes ou atualização manual).
     */
    public void forceCheck() {
        checkAndNotify();
    }
}


