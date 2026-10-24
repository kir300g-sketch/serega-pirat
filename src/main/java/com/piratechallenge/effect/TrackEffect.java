package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

public interface TrackEffect {
    void start(ServerPlayerEntity p, PlayerData d);

    /** elapsed — сколько тиков прошло с начала трека, total — всего тиков в треке. */
    void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total);

    void end(ServerPlayerEntity p, PlayerData d);
}
