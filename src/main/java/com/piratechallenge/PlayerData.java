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
    public final Map<UUID, Integer> blacklist = new HashMap<>();
    public final Map<UUID, Integer> eat = new HashMap<>();
    public double bestDist = 1e9;
    public int stuckTicks;
    public UUID stealTarget;
    public int stealTicks;
    public double stealth;
    public boolean wanted;
    public int nextCop;
    public int slices = 9;

    /** Таймер навязчивых мыслей (депрессия), не сбрасывается между треками. */
    public int thoughtTimer = 20 * 40;

    public void resetRuntime() {
        blacklist.clear();
        eat.clear();
        bestDist = 1e9;
        stuckTicks = 0;
        stealTarget = null;
        stealTicks = 0;
        stealth = 0;
        wanted = false;
        nextCop = 0;
        slices = 9;
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
