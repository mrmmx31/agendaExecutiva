package com.pessoal.agenda.tools;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;

/**
 * Small utility that synthesizes short tone clips in memory for use as example
 * start/pause/stop sounds when no external WAV resources are present.
 */
public class EmbeddedSounds {

    public static AudioInputStream getAudioInputStream(String name) {
        try {
            float sampleRate = 44100f;
            int ms = 350;
            double freq = switch (name) {
                case "start" -> 880.0;
                case "pause" -> 660.0;
                case "stop"  -> 440.0;
                default -> 660.0;
            };
            int samples = (int) ((ms * sampleRate) / 1000);
            byte[] data = new byte[samples * 2]; // 16-bit mono
            for (int i = 0; i < samples; i++) {
                double t = i / sampleRate;
                short val = (short) (Math.sin(2 * Math.PI * freq * t) * 32767 * 0.6);
                data[2*i] = (byte) (val & 0xff);
                data[2*i+1] = (byte) ((val >> 8) & 0xff);
            }
            AudioFormat af = new AudioFormat(sampleRate, 16, 1, true, false);
            return new AudioInputStream(new ByteArrayInputStream(data), af, samples);
        } catch (Throwable ex) {
            return null;
        }
    }
}

