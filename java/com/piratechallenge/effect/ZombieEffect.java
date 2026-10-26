package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;
import java.util.UUID;

/** Три волны (трек делится на 3 части): мало -> нормально -> целая орда. */
public class ZombieEffect implements TrackEffect {
    private static final Random RNG = new Random();

    // настройки волн: как часто спавним (тики), сколько за раз, максимум живых
    private static final int[] INTERVAL = {100, 50, 30};
    private static final int[] BATCH = {2, 3, 5};
    private static final int[] CAP = {15, 35, 70};

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {}

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        int third = Math.max(1, total / 3);
        int wave = Math.min(2, elapsed / third);

        EffectUtil.prune(w, d);

        if (elapsed % third == 0 && elapsed / third == wave) {
            EffectUtil.bar(p, "§c§lВОЛНА " + (wave + 1) + " / 3");
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }

        if (elapsed % INTERVAL[wave] == 0) {
            for (int i = 0; i < BATCH[wave] && d.spawned.size() < CAP[wave]; i++) spawnZombie(p, d);
        }

        // зомби всегда нацелены на игрока, не горят на солнце
        if (elapsed % 20 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m) {
                    if (m.getTarget() == null || !m.getTarget().isAlive()) m.setTarget(p);
                    if (m.isOnFire()) m.extinguish();
                }
            }
        }
    }

    private void spawnZombie(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        // каждый 10-й зомби — кандидат в лучники, и с шансом 50% получает лук.
        // Зомби в Майнкрафте стрелять не умеют, поэтому лучник — скелет в зомби-обмундировании.
        boolean archer = RNG.nextInt(10) == 0 && RNG.nextBoolean();
        Entity e = (archer ? EntityType.SKELETON : (RNG.nextInt(10) < 7 ? EntityType.ZOMBIE : EntityType.HUSK)).create(w);
        if (!(e instanceof MobEntity m)) return;

        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 14 + RNG.nextDouble() * 10;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);

        EntityAttributeInstance follow = m.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(64.0);
        EffectUtil.randomArmor(m, RNG); // случайная броня
        if (archer) {
            m.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BOW));
            m.setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0f);
            m.setCustomName(net.minecraft.text.Text.literal("Зомби-лучник"));
        }
        m.setPersistent();
        m.setTarget(p);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {}
}
