package com.piratechallenge.client;

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
    /** Сколько тиков идёт текущая фаза (трек или передышка). */
    public static int age;

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
        if (changed) {
            age = 0;
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

    public static void tick() {
        age++;
        if (ticksLeft > 0) ticksLeft--;
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
    }

    private ClientState() {}
}
