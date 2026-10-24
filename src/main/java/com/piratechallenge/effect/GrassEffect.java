package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;

/**
 * Тошнота + пульсирующая тьма (ваниль) + радужный экран (клиент) + фантомные монстры.
 * После трека — похмелье (ставится в конце, применяется менеджером на следующем треке).
 */
public class GrassEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int MAX_PHANTOMS = 12;

    private static final List<EntityType<?>> COMMON = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.ENDERMAN, EntityType.RAVAGER, EntityType.WITHER_SKELETON, EntityType.BLAZE,
            EntityType.VEX, EntityType.GHAST);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, d.totalTicks + 100, 0, false, false, false));
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        if (elapsed % 120 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0, false, false, false));
        }
        if (elapsed % 50 == 0 && d.spawned.size() < MAX_PHANTOMS) {
            spawnPhantom(p, d);
        }
    }

    private void spawnPhantom(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        EntityType<?> type;
        int roll = RNG.nextInt(12);
        if (roll == 0) type = EntityType.WARDEN;
        else if (roll == 1) type = EntityType.ENDER_DRAGON;
        else type = COMMON.get(RNG.nextInt(COMMON.size()));

        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 8 + RNG.nextDouble() * 6;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 6 ? top.getY() : p.getY();
        boolean flyer = type == EntityType.ENDER_DRAGON || type == EntityType.GHAST || type == EntityType.VEX || type == EntityType.BLAZE;
        if (flyer) y = p.getY() + 6;

        Entity e = type.create(w);
        if (e == null) return;
        e.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);
        w.spawnEntity(e);
        d.spawned.add(e.getUuid());

        if (e instanceof MobEntity m) {
            m.setTarget(p);
            m.setPersistent();
        }
        if (e instanceof WardenEntity warden) {
            warden.increaseAngerAt(p, 150, false);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.removeStatusEffect(StatusEffects.NAUSEA);
        p.removeStatusEffect(StatusEffects.DARKNESS);
        d.hangover = true; // дальше менеджер удалит всех фантомов
    }
}
