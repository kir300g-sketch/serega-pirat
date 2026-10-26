package com.piratechallenge.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/** Трек, громкость которого можно менять на лету (глушение при «зажатых ушах»). */
public class MusicInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private boolean done;
    private float target = 1.0f;

    public MusicInstance(SoundEvent event) {
        super(event, SoundCategory.MASTER, Random.create());
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.relative = true;
        this.repeat = false;
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
        this.volume += (target - this.volume) * 0.15f;
    }
}
