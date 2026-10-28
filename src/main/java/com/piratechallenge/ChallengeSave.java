package com.piratechallenge;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Хранит прогресс (сколько треков прослушано) между перезапусками мира. */
public class ChallengeSave extends PersistentState {
    public final Map<UUID, PlayerData> players = new HashMap<>();

    public static final PersistentState.Type<ChallengeSave> TYPE =
            new PersistentState.Type<>(ChallengeSave::new, ChallengeSave::fromNbt, null);

    public static ChallengeSave get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "pirate_challenge");
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public static ChallengeSave fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ChallengeSave s = new ChallengeSave();
        NbtCompound ps = nbt.getCompound("players");
        for (String key : ps.getKeys()) {
            NbtCompound c = ps.getCompound(key);
            PlayerData d = new PlayerData();
            d.listened = c.getInt("listened");
            d.cursed = c.getBoolean("cursed");
            d.deaths = c.getInt("deaths");
            d.mode = c.getInt("mode");
            d.sanctionTracks = c.getInt("sanctions");
            int[] pl = c.getIntArray("plays");
            System.arraycopy(pl, 0, d.plays, 0, Math.min(pl.length, d.plays.length));
            d.dialogDone = c.getBoolean("dialogDone");
            for (int i : c.getIntArray("played")) d.played.add(i);
            s.players.put(UUID.fromString(key), d);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound ps = new NbtCompound();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putInt("listened", e.getValue().listened);
            c.putBoolean("cursed", e.getValue().cursed);
            c.putInt("deaths", e.getValue().deaths);
            c.putInt("mode", e.getValue().mode);
            c.putInt("sanctions", e.getValue().sanctionTracks);
            c.putIntArray("plays", e.getValue().plays);
            c.putBoolean("dialogDone", e.getValue().dialogDone);
            c.putIntArray("played", e.getValue().played.stream().mapToInt(Integer::intValue).toArray());
            ps.put(e.getKey().toString(), c);
        }
        nbt.put("players", ps);
        return nbt;
    }
}
