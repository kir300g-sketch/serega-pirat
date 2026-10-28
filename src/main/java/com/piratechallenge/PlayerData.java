package com.piratechallenge;

import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlayerData {
    // ---- сохраняется в мире ----
    public boolean cursed;      // «Проклятие Сереги Пирата» — пока оно есть, идёт челлендж
    public boolean dialogDone;  // интро-диалог пройден
    public int listened;
    public int deaths;                         // смерти (для насмешек Серёги)
    public int mode;                           // 0 не выбран, 1 обычный (трек раз в 1.5 мин), 2 хард (раз в 30 сек)
    public int sanctionTracks;                 // «санкции» (трек «Гей»): сколько треков ещё действуют
    public final int[] plays = new int[64];    // сколько раз прослушан каждый трек (для честного рандома)
    public final Set<Integer> played = new HashSet<>();

    // ---- состояние на время сессии ----
    public Track current;
    public int ticksLeft, totalTicks;
    public int restLeft = ChallengeManager.REST_TICKS;
    public boolean hangover;
    public boolean paused;
    public boolean auto = true;
    public Track lastTrack;
    public boolean pendingTaunt;
    public int visionTimer = 20 * 150;
    public int visionTicks;
    public final java.util.List<UUID> visionIds = new ArrayList<>();
    public final java.util.List<UUID> men = new ArrayList<>();      // «настоящие мужики» (санкции)
    public final Map<UUID, Integer> menLost = new HashMap<>();
    public int hallTimer = 20 * 60;

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
    // Сцена / арена
    public Vec3d returnPos;
    public net.minecraft.util.math.BlockPos arenaCenter;
    public int arenaHalf, arenaH;
    public final net.minecraft.item.ItemStack[] saved = new net.minecraft.item.ItemStack[5];
    public int duelWait;
    // Ластпик (пение)
    public boolean intro;
    public int singTicks, lastSingTick, micState; // micState: 0 неизвестно, 1 есть, 2 нет
    public boolean coverEars;
    public boolean coverNotified;
    // Минёр (дождь TNT)
    public int tntRain;
    public final java.util.List<UUID> tntIds = new ArrayList<>();
    // Мутанты
    public int mutTick;
    public String mutName = "";
    // Аквапарк
    public int ruleTick, ruleType, violationCd, waveTimer;
    // Тесты из Плеера Серёги
    public boolean hymnForce;
    // Flower Man
    public final int[] flowerCd = new int[3];
    public int omegaTicks, dashTicks, bounceTicks, comboCount, comboTimer;
    public boolean dashHit;
    public Vec3d dashDir = Vec3d.ZERO;
    public final java.util.Map<UUID, Integer> cloneLife = new HashMap<>();
    public final java.util.Map<UUID, double[]> cloneInfo = new HashMap<>();   // {угол, фаза}
    public final java.util.List<Vec3d> trail = new ArrayList<>();
    // Гимн (бой с драконом)
    public boolean hymn;
    public int ghostTimer, ghostTicks, hymnChat;
    public final java.util.List<UUID> ghostIds = new ArrayList<>();
    public final java.util.Map<UUID, double[]> ghostDragons = new HashMap<>(); // позиции копий дракона: {x, y, z, yaw}
    // Бамбук (трек про китайцев)
    public final java.util.List<int[]> bambooStalks = new ArrayList<>();             // {x, baseY, z, высота, срезан(0/1)}
    public final java.util.List<Object[]> bambooRestore = new ArrayList<>();         // {BlockPos, BlockState} — вернуть землю
    public boolean bambooBusy;
    public int bambooTimer, bambooTarget, bambooDeadline, bambooCuts;
    // Тильт
    public int tiltPhase, tiltAttempts, tiltLastAttempt, tiltBotTimer;
    public net.minecraft.util.math.BlockPos botTarget;
    public final java.util.Set<Long> botBlack = new java.util.HashSet<>();
    // Апокалипсис
    public final java.util.List<double[]> meteors = new ArrayList<>();
    public final java.util.List<double[]> nukes = new ArrayList<>();
    public int nukeTimer;
    // 911: раунды
    public int round;
    public boolean roundActive;
    // Хрущёвки (паркур)
    public int pkStartX, pkNextX, pkRoofY, pkBaseY, pkCount, pkZ, pkCleared, pkMaxX, flappyCool;
    public double pkFront;
    public final java.util.List<int[]> roofs = new java.util.ArrayList<>();
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
        tntRain = 0;
        tntIds.clear();
        mutTick = 0;
        mutName = "";
        ruleTick = ruleType = violationCd = waveTimer = 0;
        java.util.Arrays.fill(flowerCd, 0);
        omegaTicks = dashTicks = bounceTicks = comboCount = comboTimer = 0;
        dashHit = false;
        dashDir = Vec3d.ZERO;
        cloneLife.clear();
        cloneInfo.clear();
        trail.clear();
        bambooStalks.clear();
        bambooRestore.clear();
        bambooBusy = false;
        bambooTimer = 0;
        bambooTarget = 0;
        bambooDeadline = 0;
        bambooCuts = 0;
        tiltPhase = 0;
        tiltAttempts = 0;
        tiltLastAttempt = 0;
        tiltBotTimer = 0;
        botTarget = null;
        botBlack.clear();
        meteors.clear();
        nukes.clear();
        nukeTimer = 0;
        round = 0;
        roundActive = false;
        pkStartX = pkNextX = pkRoofY = pkBaseY = pkCount = pkZ = pkCleared = pkMaxX = 0;
        pkFront = 0;
        roofs.clear();
        returnPos = null;
        arenaCenter = null;
        arenaHalf = 0;
        arenaH = 0;
        java.util.Arrays.fill(saved, null);
        duelWait = 0;
        intro = false;
        singTicks = 0;
        lastSingTick = 0;
        micState = 0;
        coverEars = false;
        coverNotified = false;
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
