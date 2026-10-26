package com.piratechallenge;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public final class ModSounds {
    private static final Map<Track, SoundEvent> MAP = new EnumMap<>(Track.class);

    public static void register() {
        for (Track t : Track.values()) {
            Identifier id = Identifier.of(PirateChallenge.MOD_ID, t.soundId);
            MAP.put(t, Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id)));
        }
    }

    public static SoundEvent get(Track t) {
        return MAP.get(t);
    }

    private ModSounds() {}
}
