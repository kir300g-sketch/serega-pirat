package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Ластпик сапорт: игрок на сцене в небе, внизу толпа мобов. 15 секунд на настройку микрофона (музыка ещё НЕ играет),
 * потом трек. Если игрок молчит дольше 5 секунд — толпа освистывает (оскорбления в чат + урон).
 * Микрофон слушает клиент; на сервер уходят только флаги «поёт / микрофон есть».
 */
public class LastPickEffect implements TrackEffect {
    public static final int INTRO_TICKS = 300;
    private static final int HALF = 12;
    private static final int SILENCE_LIMIT = 100; // 5 секунд тишины
    private static final Random RNG = new Random();

    private static final List<EntityType<?>> CROWD = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.WITCH);

    private static final String[] BOOS = {
            "Бууу! Пой давай!", "Микрофон включи, артист!", "Верните деньги за билет!",
            "Это что, минута молчания?", "Даже скелеты поют лучше!", "Долой со сцены!",
            "Ты вообще слова знаешь?!", "Скучно! Громче!", "Мы пришли слушать, а не смотреть!"};

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.returnPos = p.getPos();
        d.arenaHalf = HALF;
        d.arenaH = 5;
        d.arenaCenter = SkyArena.build(w, p.getBlockPos(), HALF, 5,
                Blocks.POLISHED_BLACKSTONE.getDefaultState(), Blocks.BLACK_STAINED_GLASS.getDefaultState());
        BlockPos c = d.arenaCenter;

        // сцена у северного края (z = -12..-8), поднята на 3 блока
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -HALF + 1; dz <= -HALF + 5; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), Blocks.QUARTZ_BLOCK.getDefaultState(), 2);
                }
            }
        }
        for (int dx : new int[]{-HALF + 1, HALF - 1}) {
            for (int dz : new int[]{-HALF + 1, HALF - 1}) {
                w.setBlockState(c.add(dx, 1, dz), Blocks.SEA_LANTERN.getDefaultState(), 2);
            }
        }

        // игрока — на сцену, лицом к залу (юг)
        p.requestTeleport(c.getX() + 0.5, c.getY() + 4.0, c.getZ() - HALF + 3.0);
        p.setYaw(0f);
        p.setPitch(10f);

        // толпа в зале
        for (int i = 0; i < 36; i++) spawnFan(w, c, d);

        // вступление: настройка микрофона
        d.intro = true;
        d.singTicks = 0;
        d.micState = 0;
        d.totalTicks += INTRO_TICKS;
        d.ticksLeft = d.totalTicks;

        p.sendMessage(Text.literal("§dТы на сцене! У тебя 15 секунд, чтобы настроить микрофон — скажи что-нибудь."), false);
        p.sendMessage(Text.literal("§7Микрофон включается только на этот трек. Звук не записывается и никуда не передаётся — "
                + "мод проверяет лишь громкость и отправляет флаг «поёшь/молчишь»."), false);
    }

    private void spawnFan(ServerWorld w, BlockPos c, PlayerData d) {
        Entity e = CROWD.get(RNG.nextInt(CROWD.size())).create(w);
        if (!(e instanceof MobEntity m)) return;
        double x = c.getX() + 0.5 + (RNG.nextInt(2 * HALF - 3) - (HALF - 2));
        double z = c.getZ() + 0.5 + (RNG.nextInt(HALF + 4) - 3);
        m.refreshPositionAndAngles(x, c.getY() + 1, z, 180f, 0f); // лицом к сцене
        m.setAiDisabled(true);
        m.setPersistent();
        m.setSilent(true);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = d.arenaCenter;
        if (c == null) return;

        // держим игрока на сцене
        if (p.getY() < c.getY() - 3 || Math.abs(p.getX() - c.getX()) > HALF || Math.abs(p.getZ() - c.getZ()) > HALF) {
            p.requestTeleport(c.getX() + 0.5, c.getY() + 4.0, c.getZ() - HALF + 3.0);
        }

        // толпа подпрыгивает
        if (elapsed % 8 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m && m.isOnGround() && RNG.nextInt(3) == 0) {
                    m.setVelocity(0, 0.32, 0);
                    m.setYaw(180f);
                    m.setHeadYaw(180f);
                }
            }
        }

        if (d.intro) {
            if (elapsed % 20 == 0) {
                int left = (INTRO_TICKS - elapsed) / 20;
                EffectUtil.bar(p, "§dНастройка микрофона... " + left + " с. Говори — полоска на экране должна двигаться.");
            }
            boolean ready = d.singTicks >= 40;                       // услышали голос ~2 сек
            boolean noMic = d.micState == 2 || (d.micState == 0 && elapsed >= 80); // клиент молчит / микрофона нет
            if (elapsed >= INTRO_TICKS || ready || noMic) endIntro(p, d, noMic && !ready);
            return;
        }

        if (d.micState != 1) return; // без микрофона штрафов нет

        boolean silent = p.age - d.lastSingTick > SILENCE_LIMIT;
        if (silent && (p.age - d.lastSingTick - SILENCE_LIMIT) % 60 == 0) boo(p, w, d);
        if (!silent && elapsed % 200 == 0) {
            EffectUtil.bar(p, "§aТолпа ревёт! Продолжай!");
        }
    }

    private void endIntro(ServerPlayerEntity p, PlayerData d, boolean noMic) {
        d.intro = false;
        d.lastSingTick = p.age;
        if (noMic) {
            d.micState = 2;
            p.sendMessage(Text.literal("§7Микрофон не найден — трек пройдёт без штрафов за молчание."), false);
        } else {
            p.sendMessage(Text.literal("§aНачали! Пой!"), false);
        }
        ChallengeManager.sync(p, d); // клиент запускает музыку, когда вступление закончилось
    }

    private void boo(ServerPlayerEntity p, ServerWorld w, PlayerData d) {
        p.sendMessage(Text.literal("§7[Толпа] §f" + BOOS[RNG.nextInt(BOOS.length)]), false);
        p.damage(p.getDamageSources().magic(), 2.0f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.HOSTILE, 1.5f, 0.7f);
    }

    public static void onSing(ServerPlayerEntity p, boolean singing, boolean micOk) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.LASTPICK) return;
        if (!micOk) {
            d.micState = 2;
            return;
        }
        if (d.micState == 0) d.micState = 1;
        if (singing) {
            d.lastSingTick = p.age;
            d.singTicks = Math.min(400, d.singTicks + 10);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        if (d.arenaCenter != null) {
            SkyArena.clear(p.getServerWorld(), d.arenaCenter, d.arenaHalf, d.arenaH);
        }
        if (p.isAlive() && d.returnPos != null) {
            Vec3d r = d.returnPos;
            p.requestTeleport(r.x, r.y, r.z);
        }
    }
}
