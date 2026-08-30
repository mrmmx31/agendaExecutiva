package com.pessoal.agenda.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendencyNotificationServiceTest {
    private Preferences testNode;
    private RecordingSoundOutput soundOutput;
    private PendencyNotificationService service;

    @BeforeEach
    void setUp() {
        testNode = Preferences.userRoot().node(
                "/com/pessoal/agenda/tests/notifications/" + UUID.randomUUID());
        soundOutput = new RecordingSoundOutput();
        service = new PendencyNotificationService(testNode, soundOutput);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        service.stop();
        testNode.removeNode();
    }

    @Test
    void disablingStopsTimerAndActiveSoundImmediately() {
        service.setSoundEnabled(true);
        service.setBadgeAnimationEnabled(true);
        service.start(() -> {});
        soundOutput.play();

        service.setEnabled(false);

        assertFalse(service.hasScheduledTimer());
        assertEquals(1, soundOutput.stopCount.get());
        assertFalse(soundOutput.playing);
        assertTrue(service.isSoundEnabled(), "Preferência de som deve ser preservada");
        assertTrue(service.isBadgeAnimationEnabled(), "Preferência de animação deve ser preservada");
    }

    @Test
    void disabledMasterControlBlocksManualSoundAndCallbackScheduling() {
        AtomicInteger callbacks = new AtomicInteger();
        service.setSoundEnabled(true);
        service.setHasAlerts(true);
        service.start(callbacks::incrementAndGet);
        service.setEnabled(false);

        service.forceCheck();

        assertEquals(0, soundOutput.playCount.get());
        assertEquals(0, callbacks.get());
        assertFalse(service.hasScheduledTimer());
    }

    @Test
    void reactivatingRestoresTimerAndPreservedPreferences() {
        service.setSoundEnabled(true);
        service.setBadgeAnimationEnabled(true);
        service.start(() -> {});
        service.setEnabled(false);

        service.setEnabled(true);

        assertTrue(service.hasScheduledTimer());
        assertTrue(service.isSoundEnabled());
        assertTrue(service.isBadgeAnimationEnabled());
        assertTrue(service.isBadgeAttentionAllowed());
    }

    @Test
    void badgeAttentionRequiresMasterAnimationAndNoSnooze() {
        service.setEnabled(true);
        service.setBadgeAnimationEnabled(false);
        assertFalse(service.isBadgeAttentionAllowed());

        service.setBadgeAnimationEnabled(true);
        assertTrue(service.isBadgeAttentionAllowed());

        service.snoozeForMinutes(30);
        assertFalse(service.isBadgeAttentionAllowed());

        service.clearSnooze();
        service.setEnabled(false);
        assertFalse(service.isBadgeAttentionAllowed());
    }

    @Test
    void disablingSoundStopsCurrentOutputWithoutDisablingVisualPreference() {
        service.setEnabled(true);
        service.setSoundEnabled(true);
        service.setBadgeAnimationEnabled(true);
        soundOutput.play();

        service.setSoundEnabled(false);

        assertFalse(soundOutput.playing);
        assertEquals(1, soundOutput.stopCount.get());
        assertTrue(service.isBadgeAnimationEnabled());
    }

    @Test
    void defaultsAreCalmAndStartingDoesNotPlayImmediately() {
        service.setHasAlerts(true);

        service.start(() -> {});

        assertFalse(service.isSoundEnabled());
        assertFalse(service.isBadgeAnimationEnabled());
        assertFalse(service.isQuietHoursEnabled());
        assertEquals(15, service.getIntervalMinutes());
        assertEquals(0, soundOutput.playCount.get());
    }

    @Test
    void snoozeStopsCurrentSoundAndBlocksManualCheck() {
        service.setSoundEnabled(true);
        service.setHasAlerts(true);
        soundOutput.play();

        service.snoozeForMinutes(30);
        service.forceCheck();

        assertTrue(service.isSnoozed());
        assertFalse(soundOutput.playing);
        assertEquals(1, soundOutput.playCount.get());
        assertEquals(1, soundOutput.stopCount.get());
        assertEquals(PendencyNotificationService.SoundTestResult.SNOOZED, service.testSound());
    }

    @Test
    void quietHoursHandleSameDayAndOvernightRanges() {
        assertTrue(PendencyNotificationService.isWithinQuietHours(
                LocalTime.of(13, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)));
        assertFalse(PendencyNotificationService.isWithinQuietHours(
                LocalTime.of(14, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)));
        assertTrue(PendencyNotificationService.isWithinQuietHours(
                LocalTime.of(23, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)));
        assertTrue(PendencyNotificationService.isWithinQuietHours(
                LocalTime.of(6, 59), LocalTime.of(22, 0), LocalTime.of(7, 0)));
        assertFalse(PendencyNotificationService.isWithinQuietHours(
                LocalTime.of(12, 0), LocalTime.of(22, 0), LocalTime.of(7, 0)));
        assertFalse(PendencyNotificationService.isWithinQuietHours(
                LocalTime.NOON, LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void quietHoursSuppressSoundButKeepPreferencesAvailable() {
        replaceServiceWithFixedTime("2026-08-28T02:00:00Z");
        service.setSoundEnabled(true);
        service.setQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0));
        service.setQuietHoursEnabled(true);
        service.setHasAlerts(true);

        service.forceCheck();

        assertTrue(service.isQuietHours());
        assertEquals(0, soundOutput.playCount.get());
        assertEquals(PendencyNotificationService.SoundTestResult.QUIET_HOURS, service.testSound());
        assertEquals(LocalTime.of(22, 0), service.getQuietHoursStart());
        assertEquals(LocalTime.of(7, 0), service.getQuietHoursEnd());
    }

    @Test
    void explicitSoundTestDoesNotOverlapActiveOutput() {
        service.setSoundEnabled(true);

        assertEquals(PendencyNotificationService.SoundTestResult.PLAYED, service.testSound());
        assertEquals(PendencyNotificationService.SoundTestResult.ALREADY_PLAYING, service.testSound());
        assertEquals(1, soundOutput.playCount.get());

        soundOutput.stop();

        assertEquals(PendencyNotificationService.SoundTestResult.PLAYED, service.testSound());
        assertEquals(2, soundOutput.playCount.get());
    }

    @Test
    void soundTestReportsDisabledControlsWithoutPlaying() {
        assertEquals(PendencyNotificationService.SoundTestResult.SOUND_DISABLED, service.testSound());

        service.setSoundEnabled(true);
        service.setEnabled(false);

        assertEquals(PendencyNotificationService.SoundTestResult.REMINDERS_DISABLED, service.testSound());
        assertEquals(0, soundOutput.playCount.get());
    }

    @Test
    void enabledAlertDispatchesVisualCallbackWithoutRequiringSound() {
        AtomicInteger callbacks = new AtomicInteger();
        service.setHasAlerts(true);
        service.start(callbacks::incrementAndGet);

        service.forceCheck();

        assertEquals(1, callbacks.get());
        assertEquals(0, soundOutput.playCount.get());
    }

    @Test
    void queuedVisualCallbackRechecksMasterControlBeforeRunning() {
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        service.stop();
        soundOutput = new RecordingSoundOutput();
        service = new PendencyNotificationService(
                testNode, soundOutput, Clock.systemUTC(), queued::set);
        service.setHasAlerts(true);
        service.start(callbacks::incrementAndGet);

        service.forceCheck();
        service.setEnabled(false);
        queued.get().run();

        assertEquals(0, callbacks.get());
    }

    private void replaceServiceWithFixedTime(String instant) {
        service.stop();
        soundOutput = new RecordingSoundOutput();
        service = new PendencyNotificationService(
                testNode, soundOutput, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static final class RecordingSoundOutput
            implements PendencyNotificationService.SoundOutput {
        private final AtomicInteger playCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();
        private boolean playing;

        @Override
        public boolean play() {
            if (playing) return false;
            playCount.incrementAndGet();
            playing = true;
            return true;
        }

        @Override
        public void stop() {
            stopCount.incrementAndGet();
            playing = false;
        }
    }
}
