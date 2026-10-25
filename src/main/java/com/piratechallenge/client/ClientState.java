package com.piratechallenge.client;

import com.piratechallenge.AmStatePayload;
import com.piratechallenge.ModSounds;
import com.piratechallenge.StatePayload;
import com.piratechallenge.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
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

    private static SoundInstance sound;

    public static Track track() {
        return Track.byId(trackId);
    }

    public static void update(StatePayload p) {
        boolean changed = p.trackId() != trackId;
        trackId = p.trackId();
        ticksLeft = p.ticksLeft();
        total = p.totalTicks();
        listened = p.listened();

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
            Track t = track();
            if (t != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.getSoundManager().stopSounds(null, SoundCategory.MUSIC); // глушим ванильную музыку
                sound = PositionedSoundInstance.master(ModSounds.get(t), 1.0f, 1.0f);
                mc.getSoundManager().play(sound);
            }
        }
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
    }

    private ClientState() {}
}
