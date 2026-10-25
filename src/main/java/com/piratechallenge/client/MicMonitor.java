package com.piratechallenge.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Слушает микрофон ТОЛЬКО пока идёт трек «Ластпик сапорт». Звук нигде не сохраняется и не передаётся:
 * считается лишь громкость (RMS) каждые ~50 мс, из неё получается флаг «игрок поёт».
 */
public final class MicMonitor {
    private static volatile boolean running;
    private static volatile boolean ok;
    private static volatile boolean tried;
    private static volatile float level;       // 0..1, сглаженная громкость для индикатора
    private static volatile long lastVoiceMs;  // когда в последний раз был голос
    private static Thread thread;
    private static TargetDataLine line;

    public static synchronized void start() {
        if (running) return;
        running = true;
        tried = false;
        ok = false;
        thread = new Thread(MicMonitor::loop, "pirate-mic-level");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        TargetDataLine l = line;
        if (l != null) {
            try { l.stop(); l.close(); } catch (Throwable ignored) {}
        }
        line = null;
        level = 0;
    }

    public static boolean isRunning() { return running; }

    /** Удалось ли открыть микрофон (true только после успешного открытия). */
    public static boolean isOk() { return ok; }

    /** Была ли уже попытка открыть микрофон. */
    public static boolean tried() { return tried; }

    public static float level() { return level; }

    /** Голос был в последние ~1 секунду. */
    public static boolean singing() {
        return ok && System.currentTimeMillis() - lastVoiceMs < 1000;
    }

    private static void loop() {
        try {
            AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                tried = true;
                return;
            }
            TargetDataLine l = (TargetDataLine) AudioSystem.getLine(info);
            l.open(fmt);
            l.start();
            line = l;
            ok = true;
            tried = true;

            byte[] buf = new byte[1600]; // 50 мс
            float floor = 0.01f;         // оценка фонового шума
            while (running) {
                int n = l.read(buf, 0, buf.length);
                if (n <= 0) continue;
                double sum = 0;
                int samples = n / 2;
                for (int i = 0; i < samples; i++) {
                    int lo = buf[2 * i] & 0xFF, hi = buf[2 * i + 1];
                    short s = (short) ((hi << 8) | lo);
                    double v = s / 32768.0;
                    sum += v * v;
                }
                float rms = (float) Math.sqrt(sum / Math.max(1, samples));
                // шум медленно подстраивается под тихий уровень
                if (rms < floor) floor = floor * 0.9f + rms * 0.1f; else floor = floor * 0.999f + rms * 0.001f;
                level = level * 0.7f + Math.min(1f, rms * 8f) * 0.3f;
                if (rms > Math.max(0.02f, floor * 3f)) lastVoiceMs = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            tried = true; // нет микрофона / нет доступа / нет модуля java.desktop
        } finally {
            TargetDataLine l = line;
            if (l != null) {
                try { l.stop(); l.close(); } catch (Throwable ignored) {}
            }
            line = null;
            running = false;
        }
    }

    private MicMonitor() {}
}
