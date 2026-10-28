package com.piratechallenge;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Random;
import java.util.UUID;

/**
 * Бой с Драконом Края: вместо обычных треков играет «Гимн» на повторе. Дебафф — «полный моральный распад»:
 * клиент периодически размывает экран (своим эффектом, не зельями), звук рябит и двоится, а сервер время от времени
 * создаёт видения: толпу Серёг и копии дракона, которые НЕ атакуют (урон от них блокируется) и исчезают через ~5 секунд.
 */
public final class HymnManager {
    public static final String GHOST_TAG = "pc_ghost";
    private static final Random RNG = new Random();

    private static final String[] LINES = {
            "всё рассыпается... я уже не понимаю, что настоящее",
            "почему гимн звучит внутри головы?",
            "ты ещё помнишь, ради чего всё это?",
            "нет ни правильного, ни неправильного. есть только бит.",
            "они все смотрят. они всегда смотрели."
    };

    public static void tick(ServerPlayerEntity p, PlayerData d) {
        if (p.age % 20 == 0) {
            ServerWorld w = p.getServerWorld();
            boolean fight = d.hymnForce || p.isAlive() && w.getRegistryKey() == World.END && !w.getEntitiesByType(
                    TypeFilter.instanceOf(EnderDragonEntity.class),
                    e -> e.isAlive() && !e.getCommandTags().contains(GHOST_TAG)).isEmpty();
            if (fight != d.hymn) {
                d.hymn = fight;
                if (fight) {
                    ChallengeManager.stopForHymn(p);
                    d.ghostTimer = 100;
                    p.sendMessage(Text.literal("§4§lГИМН. Моральный распад."), false);
                } else {
                    cancel(p, d);
                }
                send(p, fight);
            }
        }
        if (!d.hymn) return;

        // чат-«распад» и видения
        if (p.age % 400 == 0) {
            p.sendMessage(Text.literal("§8§o[Распад] " + LINES[RNG.nextInt(LINES.length)]), false);
        }
        if (d.ghostTicks > 0) {
            holdGhosts(p, d);
            if (--d.ghostTicks == 0) clearGhosts(p, d);
        } else if (--d.ghostTimer <= 0) {
            d.ghostTimer = 220 + RNG.nextInt(140);
            spawnGhosts(p, d);
        }
    }

    private static void send(ServerPlayerEntity p, boolean on) {
        if (p.networkHandler != null && p.networkHandler.isConnectionOpen()) {
            ServerPlayNetworking.send(p, new HymnPayload(on));
        }
    }

    private static void spawnGhosts(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.ghostTicks = 100; // ~5 секунд
        // «сереги»
        for (int i = 0; i < 10; i++) {
            SeregaEntity s = ModEntities.SEREGA.create(w);
            if (s == null) continue;
            double ang = RNG.nextDouble() * Math.PI * 2, dist = 4 + RNG.nextDouble() * 6;
            double x = p.getX() + Math.cos(ang) * dist, z = p.getZ() + Math.sin(ang) * dist;
            int topY = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).getY();
            double y = Math.abs(topY - p.getY()) < 6 ? topY : p.getY();
            float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - z, p.getX() - x)) - 90.0);
            s.refreshPositionAndAngles(x, y, z, yaw, 0f);
            s.setCustomName(Text.literal("Серёга Пират"));
            s.setCustomNameVisible(true);
            s.setPersistent();
            s.setSilent(true);
            s.addCommandTag(GHOST_TAG);
            s.addCommandTag("pc_vision");
            w.spawnEntity(s);
            d.ghostIds.add(s.getUuid());
        }
        // копии дракона (неподвижные, неуязвимые, не атакуют)
        for (int i = 0; i < 3; i++) {
            Entity e = EntityType.ENDER_DRAGON.create(w);
            if (!(e instanceof EnderDragonEntity dr)) continue;
            double ang = RNG.nextDouble() * Math.PI * 2, dist = 28 + RNG.nextDouble() * 12;
            double x = p.getX() + Math.cos(ang) * dist, z = p.getZ() + Math.sin(ang) * dist;
            double y = p.getY() + 4 + RNG.nextDouble() * 12;
            float yaw = (float) (Math.toDegrees(Math.atan2(p.getZ() - z, p.getX() - x)) + 90.0);
            dr.refreshPositionAndAngles(x, y, z, yaw, 0f);
            dr.setInvulnerable(true);
            dr.setSilent(true);
            dr.setNoGravity(true);
            dr.addCommandTag(GHOST_TAG);
            dr.addCommandTag("pc_vision");
            w.spawnEntity(dr);
            d.ghostIds.add(dr.getUuid());
            d.ghostDragons.put(dr.getUuid(), new double[]{x, y, z, yaw});
        }
    }

    /** Копии дракона держим на месте (иначе они полетят своим ходом). */
    private static void holdGhosts(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        for (java.util.Map.Entry<UUID, double[]> en : d.ghostDragons.entrySet()) {
            Entity e = w.getEntity(en.getKey());
            if (e instanceof EnderDragonEntity dr) {
                double[] g = en.getValue();
                dr.setVelocity(Vec3d.ZERO);
                dr.refreshPositionAndAngles(g[0], g[1], g[2], (float) g[3], 0f);
            }
        }
    }

    public static void clearGhosts(ServerPlayerEntity p, PlayerData d) {
        for (UUID id : d.ghostIds) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) { e.discard(); break; }
            }
        }
        d.ghostIds.clear();
        d.ghostDragons.clear();
        d.ghostTicks = 0;
    }

    /** Гимн закончился (дракон убит / игрок вышел из Энда / умер). */
    public static void cancel(ServerPlayerEntity p, PlayerData d) {
        clearGhosts(p, d);
        if (d.hymn) {
            d.hymn = false;
            send(p, false);
        }
    }

    private HymnManager() {}
}
