package com.piratechallenge.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * Трек с управляемой громкостью (глушение при «зажатых ушах»).
 * Для «Гимна»: loop = повтор, ripple = звук «рябит» (быстрое дрожание громкости и высоты тона).
 */
public class MusicInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private boolean done;
    private float target = 1.0f;
    private final boolean ripple;
    private final float basePitch;
    private int t;

    public MusicInstance(SoundEvent event) {
        this(event, false, false, 1.0f);
    }

    public MusicInstance(SoundEvent event, boolean loop, boolean ripple, float pitch) {
        super(event, SoundCategory.MASTER, Random.create());
        this.volume = 1.0f;
        this.pitch = pitch;
        this.basePitch = pitch;
        this.ripple = ripple;
        this.relative = true;
        this.repeat = loop;
        this.repeatDelay = 0;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
    }

    public void setTargetVolume(float v) {
        this.target = v;
    }

    public void finish() {
        this.done = true;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void tick() {
        t++;
        float base = target;
        if (ripple) {
            // рябь: дрожание громкости и плавающая высота тона
            base *= 0.72f + 0.28f * (float) Math.sin(t * 0.85) * (float) Math.sin(t * 0.23 + 1.0);
            this.pitch = basePitch + 0.03f * (float) Math.sin(t * 0.37) + 0.015f * (float) Math.sin(t * 1.9);
        }
        this.volume += (base - this.volume) * 0.3f;
    }
}
