package com.piratechallenge.client;

import com.piratechallenge.AmStatePayload;
import com.piratechallenge.CompassPayload;
import com.piratechallenge.ModSounds;
import com.piratechallenge.StatePayload;
import com.piratechallenge.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

public final class ClientState {
    public static int trackId = -1;
    public static int ticksLeft;
    public static int total;
    public static int listened;
    public static boolean paused;
    /** Сколько тиков идёт текущая фаза (трек или передышка). */
    public static int age;

    // ФП АМ
    public static int amMana, amCd1, amCd2, amCd3, amCounter, amKills;

    // Нога (QTE)
    public static String qteLetters = "";
    public static int qteTicks;

    public static boolean intro;
    public static boolean coverEars;
    public static int sanctions;

    // Ковбой: стрелка на ближайшего вора
    public static boolean cmpValid;
    public static double cmpX, cmpZ;

    private static MusicInstance sound;

    public static Track track() {
        return Track.byId(trackId);
    }

    public static void update(StatePayload p) {
        boolean changed = p.trackId() != trackId;
        trackId = p.trackId();
        ticksLeft = p.ticksLeft();
        total = p.totalTicks();
        listened = p.listened();

        intro = p.intro();
        coverEars = p.coverEars();
        sanctions = p.sanctions();
        if (sound != null) sound.setTargetVolume(coverEars ? 0.15f : 1.0f);

        boolean nowPaused = p.paused();
        if (nowPaused != paused) {
            paused = nowPaused;
            var sm = MinecraftClient.getInstance().getSoundManager();
            if (paused) sm.pauseAll();
            else sm.resumeAll();
        }

        if (changed) {
            age = 0;
            qteTicks = 0;
            qteLetters = "";
            stopSound();
            cmpValid = false;
            if (track() == Track.FANAT) ChessScreen.resetGame();
        }
        startMusicIfNeeded();
    }

    /** Музыка стартует, когда идёт трек и закончилось вступление (настройка микрофона в «Ластпике»). */
    private static void startMusicIfNeeded() {
        Track t = track();
        if (t == null || intro || sound != null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().stopSounds(null, SoundCategory.MUSIC); // глушим ванильную музыку
        sound = new MusicInstance(ModSounds.get(t));
        sound.setTargetVolume(coverEars ? 0.15f : 1.0f);
        mc.getSoundManager().play(sound);
    }

    public static void updateCompass(CompassPayload p) {
        cmpValid = p.valid();
        cmpX = p.x();
        cmpZ = p.z();
    }

    public static void updateAm(AmStatePayload p) {
        amMana = p.mana();
        amCd1 = p.cd1();
        amCd2 = p.cd2();
        amCd3 = p.cd3();
        amCounter = p.counter();
        amKills = p.kills();
    }

    public static void tick() {
        if (paused) return;
        age++;
        if (ticksLeft > 0) ticksLeft--;
        if (amCd1 > 0) amCd1--;
        if (amCd2 > 0) amCd2--;
        if (amCd3 > 0) amCd3--;
        if (amCounter > 0) amCounter--;
    }

    public static void stopSound() {
        if (sound != null) {
            sound.finish();
            MinecraftClient.getInstance().getSoundManager().stop(sound);
            sound = null;
        }
    }

    public static void reset() {
        stopSound();
        trackId = -1;
        ticksLeft = 0;
        age = 0;
        paused = false;
        qteTicks = 0;
        qteLetters = "";
        intro = false;
        coverEars = false;
        sanctions = 0;
        cmpValid = false;
    }

    private ClientState() {}
}
