package com.pessoal.agenda.service;

import javafx.application.Platform;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.util.Timer;
import java.util.TimerTask;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

/**
 * Serviço de notificações periódicas para manter o usuário com TDAH ciente de pendências.
 *
 * Executa periodicamente e sinaliza a interface se houver:
 * - Tarefas vencidas
 * - Protocolos vencendo
 */
public class PendencyNotificationService {
    private static PendencyNotificationService instance;
    private static final String KEY_ENABLED = "notifications.enabled";
    private static final String KEY_SOUND = "notifications.sound";
    private static final String KEY_BADGE_ANIMATION = "notifications.badgeAnimation";
    private static final String KEY_INTERVAL_MINUTES = "notifications.intervalMinutes";
    private static final String KEY_QUIET_HOURS_ENABLED = "notifications.quietHours.enabled";
    private static final String KEY_QUIET_HOURS_START = "notifications.quietHours.start";
    private static final String KEY_QUIET_HOURS_END = "notifications.quietHours.end";
    private static final int DEFAULT_INTERVAL_MINUTES = 15;
    private static final LocalTime DEFAULT_QUIET_HOURS_START = LocalTime.of(22, 0);
    private static final LocalTime DEFAULT_QUIET_HOURS_END = LocalTime.of(7, 0);

    private final Preferences preferences;
    private final SoundOutput soundOutput;
    private final Clock clock;
    private final Consumer<Runnable> uiDispatcher;
    private Timer notificationTimer;
    private volatile boolean hasAlerts = false;
    private volatile long snoozedUntilMillis = 0;
    private Runnable onAlertDetected;

    private PendencyNotificationService() {
        this.preferences = Preferences.userNodeForPackage(PendencyNotificationService.class);
        this.clock = Clock.systemDefaultZone();
        this.uiDispatcher = Platform::runLater;
        this.soundOutput = new ClipSoundOutput(this::isSoundAllowed);
    }

    public PendencyNotificationService(Preferences preferences) {
        this.preferences = preferences;
        this.clock = Clock.systemDefaultZone();
        this.uiDispatcher = Platform::runLater;
        this.soundOutput = new ClipSoundOutput(this::isSoundAllowed);
    }

    PendencyNotificationService(Preferences preferences, SoundOutput soundOutput) {
        this(preferences, soundOutput, Clock.systemDefaultZone(), Runnable::run);
    }

    PendencyNotificationService(Preferences preferences, SoundOutput soundOutput, Clock clock) {
        this(preferences, soundOutput, clock, Runnable::run);
    }

    PendencyNotificationService(Preferences preferences, SoundOutput soundOutput, Clock clock,
                                Consumer<Runnable> uiDispatcher) {
        this.preferences = preferences;
        this.soundOutput = soundOutput;
        this.clock = clock;
        this.uiDispatcher = uiDispatcher;
    }

    public static synchronized PendencyNotificationService getInstance() {
        if (instance == null) {
            instance = new PendencyNotificationService();
        }
        return instance;
    }

    public synchronized void start(Runnable alertCallback) {
        this.onAlertDetected = alertCallback;
        restartTimer();
    }

    private synchronized void restartTimer() {
        if (notificationTimer != null) {
            notificationTimer.cancel();
            notificationTimer = null;
        }
        if (!isEnabled() || onAlertDetected == null) return;

        long checkInterval = getIntervalMinutes() * 60_000L;
        notificationTimer = new Timer("PendencyNotifier", true);
        notificationTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAndNotify();
            }
        }, checkInterval, checkInterval);
    }

    public synchronized void stop() {
        if (notificationTimer != null) {
            notificationTimer.cancel();
            notificationTimer = null;
        }
        soundOutput.stop();
    }

    private synchronized void checkAndNotify() {
        if (!isEnabled() || !hasAlerts || isSnoozed()) return;

        if (isSoundAllowed()) soundOutput.play();
        Runnable callback = onAlertDetected;
        if (callback != null) {
            uiDispatcher.accept(() -> {
                if (isEnabled() && !isSnoozed()) callback.run();
            });
        }
    }

    /**
     * Sinaliza que há alertas pendentes (chamado pela dashboard ao atualizar).
     */
    public void setHasAlerts(boolean alerts) {
        this.hasAlerts = alerts;
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, true);
    }

    public synchronized void setEnabled(boolean enabled) {
        preferences.putBoolean(KEY_ENABLED, enabled);
        if (!enabled) soundOutput.stop();
        restartTimer();
    }

    public boolean isSoundEnabled() {
        return preferences.getBoolean(KEY_SOUND, false);
    }

    public synchronized void setSoundEnabled(boolean enabled) {
        preferences.putBoolean(KEY_SOUND, enabled);
        if (!enabled) soundOutput.stop();
    }

    public boolean isQuietHoursEnabled() {
        return preferences.getBoolean(KEY_QUIET_HOURS_ENABLED, false);
    }

    public synchronized void setQuietHoursEnabled(boolean enabled) {
        preferences.putBoolean(KEY_QUIET_HOURS_ENABLED, enabled);
        if (enabled && isQuietHours()) soundOutput.stop();
    }

    public LocalTime getQuietHoursStart() {
        return getTimePreference(KEY_QUIET_HOURS_START, DEFAULT_QUIET_HOURS_START);
    }

    public LocalTime getQuietHoursEnd() {
        return getTimePreference(KEY_QUIET_HOURS_END, DEFAULT_QUIET_HOURS_END);
    }

    public synchronized void setQuietHours(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("O início e o fim do horário silencioso são obrigatórios.");
        }
        preferences.put(KEY_QUIET_HOURS_START, start.withSecond(0).withNano(0).toString());
        preferences.put(KEY_QUIET_HOURS_END, end.withSecond(0).withNano(0).toString());
        if (isQuietHoursEnabled() && isQuietHours()) soundOutput.stop();
    }

    public boolean isQuietHours() {
        return isWithinQuietHours(LocalTime.now(clock), getQuietHoursStart(), getQuietHoursEnd());
    }

    static boolean isWithinQuietHours(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return false;
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private LocalTime getTimePreference(String key, LocalTime fallback) {
        try {
            return LocalTime.parse(preferences.get(key, fallback.toString()));
        } catch (DateTimeParseException error) {
            return fallback;
        }
    }

    private boolean isSoundAllowed() {
        return isEnabled() && isSoundEnabled() && !isSnoozed()
                && !(isQuietHoursEnabled() && isQuietHours());
    }

    public synchronized SoundTestResult testSound() {
        if (!isEnabled()) return SoundTestResult.REMINDERS_DISABLED;
        if (!isSoundEnabled()) return SoundTestResult.SOUND_DISABLED;
        if (isSnoozed()) return SoundTestResult.SNOOZED;
        if (isQuietHoursEnabled() && isQuietHours()) return SoundTestResult.QUIET_HOURS;
        return soundOutput.play() ? SoundTestResult.PLAYED : SoundTestResult.ALREADY_PLAYING;
    }

    public boolean isBadgeAnimationEnabled() {
        return preferences.getBoolean(KEY_BADGE_ANIMATION, false);
    }

    public boolean isBadgeAttentionAllowed() {
        return isEnabled() && isBadgeAnimationEnabled() && !isSnoozed();
    }

    public void setBadgeAnimationEnabled(boolean enabled) {
        preferences.putBoolean(KEY_BADGE_ANIMATION, enabled);
    }

    public int getIntervalMinutes() {
        int value = preferences.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES);
        return switch (value) {
            case 5, 15, 30, 60 -> value;
            default -> DEFAULT_INTERVAL_MINUTES;
        };
    }

    public void setIntervalMinutes(int minutes) {
        if (minutes != 5 && minutes != 15 && minutes != 30 && minutes != 60) {
            throw new IllegalArgumentException("Intervalo de lembrete inválido: " + minutes);
        }
        preferences.putInt(KEY_INTERVAL_MINUTES, minutes);
        restartTimer();
    }

    public synchronized void snoozeForMinutes(int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("A pausa deve ser maior que zero.");
        snoozedUntilMillis = clock.millis() + minutes * 60_000L;
        soundOutput.stop();
    }

    public void clearSnooze() {
        snoozedUntilMillis = 0;
    }

    public boolean isSnoozed() {
        return clock.millis() < snoozedUntilMillis;
    }

    /**
     * Força execução imediata do check (para testes ou atualização manual).
     */
    public void forceCheck() {
        checkAndNotify();
    }

    synchronized boolean hasScheduledTimer() {
        return notificationTimer != null;
    }

    interface SoundOutput {
        boolean play();
        void stop();
    }

    public enum SoundTestResult {
        PLAYED,
        ALREADY_PLAYING,
        REMINDERS_DISABLED,
        SOUND_DISABLED,
        SNOOZED,
        QUIET_HOURS
    }

    private static final class ClipSoundOutput implements SoundOutput {
        private final BooleanSupplier canPlay;
        private Clip activeClip;

        private ClipSoundOutput(BooleanSupplier canPlay) {
            this.canPlay = canPlay;
        }

        @Override
        public synchronized boolean play() {
            if (!canPlay.getAsBoolean()) return false;
            if (activeClip != null && activeClip.isOpen()) return false;
            try {
                var url = PendencyNotificationService.class.getResource("/sounds/reminder.wav");
                if (url == null) {
                    return fallbackBeep();
                }
                try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(url)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioIn);
                    if (!canPlay.getAsBoolean()) {
                        clip.close();
                        return false;
                    }
                    activeClip = clip;
                    clip.addLineListener(event -> {
                        if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                            closeFinishedClip(clip);
                        }
                    });
                    clip.start();
                    return true;
                }
            } catch (Exception error) {
                return fallbackBeep();
            }
        }

        @Override
        public synchronized void stop() {
            Clip clip = activeClip;
            activeClip = null;
            if (clip == null) return;
            try {
                if (clip.isRunning()) clip.stop();
            } finally {
                clip.close();
            }
        }

        private synchronized void closeFinishedClip(Clip clip) {
            if (activeClip == clip) activeClip = null;
            if (clip.isOpen()) clip.close();
        }

        private boolean fallbackBeep() {
            if (!canPlay.getAsBoolean()) return false;
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
                return true;
            } catch (Exception ignored) {
                // Ambiente sem saída sonora; o lembrete visual continua disponível.
                return false;
            }
        }
    }
}
