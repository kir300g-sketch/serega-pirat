package com.piratechallenge;

import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlayerData {
    // ---- сохраняется в мире ----
    public int listened;
    public final Set<Integer> played = new HashSet<>();

    // ---- состояние на время сессии ----
    public Track current;
    public int ticksLeft, totalTicks;
    public int restLeft = ChallengeManager.REST_TICKS;
    public boolean hangover;

    // ---- рабочие поля эффектов (сбрасываются при старте трека) ----
    public final List<UUID> spawned = new ArrayList<>();
    public int kills;
    public double pump = 100;
    public int weakTicks;
    public boolean wasSneaking;
    public boolean bucketGiven;
    public double lastScale;
    public UUID target;
    public Vec3d lastPos = Vec3d.ZERO;

    public void resetRuntime() {
        spawned.clear();
        kills = 0;
        pump = 100;
        weakTicks = 0;
        wasSneaking = false;
        bucketGiven = false;
        lastScale = 0;
        target = null;
        lastPos = Vec3d.ZERO;
    }
}
