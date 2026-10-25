package com.piratechallenge;

import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlayerData {
    // ---- сохраняется в мире ----
    public boolean cursed;      // «Проклятие Сереги Пирата» — пока оно есть, идёт челлендж
    public boolean dialogDone;  // интро-диалог пройден
    public int listened;
    public final Set<Integer> played = new HashSet<>();

    // ---- состояние на время сессии ----
    public Track current;
    public int ticksLeft, totalTicks;
    public int restLeft = ChallengeManager.REST_TICKS;
    public boolean hangover;
    public boolean paused;
    public boolean auto = true;

    // ---- диалог со Серёгой ----
    public int dialogStage, dialogTimer, dialogChoice;
    public UUID seregaId;

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
    public int escapeTicks;
    // ФП АМ
    public double mana = 100;
    public final int[] cd = new int[4];
    public int counterTicks, voidTimer;
    public Vec3d voidAt = Vec3d.ZERO;
    public Vec3d moveTarget;
    // Ковбой
    public int ammo = 6, reload;
    public UUID horseId;
    public double heading;
    // Нога
    public boolean qteActive;
    public int qteLeft, nextQte;

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
        escapeTicks = 0;
        mana = 100;
        java.util.Arrays.fill(cd, 0);
        counterTicks = 0;
        voidTimer = 0;
        voidAt = Vec3d.ZERO;
        moveTarget = null;
        ammo = 6;
        reload = 0;
        horseId = null;
        heading = 0;
        qteActive = false;
        qteLeft = 0;
        nextQte = 0;
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
