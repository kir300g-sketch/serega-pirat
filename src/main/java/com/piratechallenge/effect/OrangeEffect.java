package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;
import java.util.UUID;

/**
 * Игрок — апельсин: маленькая «башка», двигается только прыжками (логика прыжков — KeyboardInputMixin).
 * 9 долек. Моб, который 3 секунды подряд стоит вплотную, съедает одну дольку. 0 долек = смерть.
 * Мобы здесь — медленные зомби с нулевым уроном, они только грызут.
 */
public class OrangeEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int EAT_TICKS = 60; // 3 секунды

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.slices = 9;
        d.eat.clear();
        EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"), -0.6, Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);
        if (elapsed % 20 == 0) {
            EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"), -0.6, Operation.ADD_MULTIPLIED_BASE);
        }

        // чем дальше трек, тем больше едоков
        int cap = 5 + elapsed * 6 / Math.max(1, total);
        if (elapsed % 60 == 0 && d.spawned.size() < cap) spawnEater(p, d);

        boolean biting = false;
        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (!(e instanceof MobEntity m)) continue;
            m.setTarget(p);
            if (m.isOnFire()) m.extinguish();

            double dx = m.getX() - p.getX(), dz = m.getZ() - p.getZ();
            boolean close = dx * dx + dz * dz < 1.7 * 1.7 && Math.abs(m.getY() - p.getY()) < 2.0;
            if (close) {
                int prog = d.eat.merge(id, 1, Integer::sum);
                biting = true;
                if (prog >= EAT_TICKS) {
                    d.eat.put(id, 0);
                    d.slices--;
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.5f, 0.8f);
                    EffectUtil.bar(p, slicesText(d) + " §c-1 долька!");
                    if (d.slices <= 0) {
                        p.damage(p.getDamageSources().genericKill(), Float.MAX_VALUE);
                        return;
                    }
                }
            } else {
                d.eat.remove(id);
            }
        }

        if (elapsed % 10 == 0 && !biting) EffectUtil.bar(p, slicesText(d));
    }

    private static String slicesText(PlayerData d) {
        return "§6Дольки: " + "§6●".repeat(Math.max(0, d.slices)) + "§8○".repeat(Math.max(0, 9 - d.slices));
    }

    private void spawnEater(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        Entity e = (RNG.nextBoolean() ? EntityType.ZOMBIE : EntityType.HUSK).create(w);
        if (!(e instanceof ZombieEntity z)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 9 + RNG.nextDouble() * 5;
        double x = p.getX() + Math.cos(ang) * dist;
        double zz = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, zz));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        z.refreshPositionAndAngles(x, y, zz, 0f, 0f);
        z.setBaby(false);
        z.setPersistent();

        EntityAttributeInstance dmg = z.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(0.0);
        EntityAttributeInstance spd = z.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.05); // медленнее прыжков апельсина
        EntityAttributeInstance follow = z.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(48.0);

        z.setTarget(p);
        w.spawnEntity(z);
        d.spawned.add(z.getUuid());
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"));
        d.eat.clear();
    }
}
