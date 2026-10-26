package com.piratechallenge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.Heightmap;

import java.util.Random;
import java.util.UUID;

/**
 * Видение (после 20 треков): экран темнеет, тошнит, вокруг толпа Серёг, которые быстро пишут в чат всякое.
 * Через ~5 секунд всё исчезает.
 */
public final class Vision {
    private static final Random RNG = new Random();
    private static final int DURATION = 100; // 5 секунд

    private static final String[] LINES = {
            "ТЫ НЕ УЙДЁШЬ", "Слушай ещё!", "Куда ты?", "Я везде", "Ещё трек!", "Ты уже мой слушатель",
            "Не оглядывайся", "Бит не остановить", "Ха-ха-ха", "Ты же сам хотел альбом!", "ГРОМЧЕ!!!",
            "Я слышу, как ты слушаешь", "Раз-два-три, и снова", "Это не сон. Или сон?", "Подпевай!",
            "Мы все — Серёга", "Тут нас много", "Тебя никто не спасёт... от хита", "Тишина закончилась"
    };

    public static void tick(ServerPlayerEntity p, PlayerData d) {
        if (d.visionTicks > 0) {
            d.visionTicks--;
            if (d.visionTicks % 3 == 0) {
                p.sendMessage(Text.literal("§6[Серёга Пират] §f" + LINES[RNG.nextInt(LINES.length)]), false);
            }
            if (d.visionTicks == 0) cancel(p, d);
            return;
        }
        if (!d.cursed || d.listened < 20) return;
        if (--d.visionTimer <= 0) {
            d.visionTimer = (120 + RNG.nextInt(120)) * 20; // раз в 2–4 минуты
            start(p, d);
        }
    }

    private static void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.visionTicks = DURATION;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DURATION + 20, 0, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, DURATION + 40, 0, false, false, false));
        for (int i = 0; i < 14; i++) {
            SeregaEntity s = ModEntities.SEREGA.create(w);
            if (s == null) continue;
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 3 + RNG.nextDouble() * 6;
            double x = p.getX() + Math.cos(ang) * dist;
            double z = p.getZ() + Math.sin(ang) * dist;
            int topY = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, net.minecraft.util.math.BlockPos.ofFloored(x, 0, z)).getY();
            double y = Math.abs(topY - p.getY()) < 6 ? topY : p.getY();
            float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - z, p.getX() - x)) - 90.0);
            s.refreshPositionAndAngles(x, y, z, yaw, 0f);
            s.setHeadYaw(yaw);
            s.setBodyYaw(yaw);
            s.setCustomName(Text.literal("Серёга Пират"));
            s.setCustomNameVisible(true);
            s.setPersistent();
            s.setSilent(true);
            s.addCommandTag("pc_serega");
            w.spawnEntity(s);
            d.visionIds.add(s.getUuid());
        }
    }

    public static void cancel(ServerPlayerEntity p, PlayerData d) {
        d.visionTicks = 0;
        for (UUID id : d.visionIds) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) {
                    e.discard();
                    break;
                }
            }
        }
        d.visionIds.clear();
        p.removeStatusEffect(StatusEffects.DARKNESS);
        p.removeStatusEffect(StatusEffects.NAUSEA);
    }

    private Vision() {}
}
