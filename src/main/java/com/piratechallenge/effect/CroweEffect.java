package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;

/**
 * Серый мир и волосы — на клиенте. Здесь: чернота каждые 20 сек и «задиры» —
 * хаски (не горят на солнце) идут за игроком и харкают в него:
 * плевок не наносит урона, но сильно отталкивает.
 */
public class CroweEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int MAX_BULLIES = 4;
    private static final double KNOCKBACK = 1.8;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.bar(p, "§7Ты дед инсайд... и за тобой идут задиры.");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);

        if (elapsed > 0 && elapsed % 400 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));
        }

        if (elapsed % 100 == 20 && d.spawned.size() < MAX_BULLIES) spawnBully(p, d);

        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (!(e instanceof MobEntity bully)) continue;
            bully.setTarget(p);
            if (bully.isOnFire()) bully.extinguish();

            double dist = bully.distanceTo(p);
            if (dist < 14 && (bully.age + bully.getId()) % 50 == 0 && bully.canSee(p)) {
                spit(w, bully, p);
            }
        }
    }

    private void spit(ServerWorld w, MobEntity bully, ServerPlayerEntity p) {
        Vec3d from = bully.getEyePos();
        Vec3d to = p.getPos().add(0, p.getHeight() * 0.6, 0);
        for (int i = 0; i <= 8; i++) {
            Vec3d pt = from.lerp(to, i / 8.0);
            w.spawnParticles(ParticleTypes.SPIT, pt.x, pt.y, pt.z, 2, 0.03, 0.03, 0.03, 0.0);
        }
        w.playSound(null, bully.getX(), bully.getY(), bully.getZ(), SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.HOSTILE, 1.0f, 1.0f);

        // сильный толчок без урона: направление от задиры к игроку
        p.takeKnockback(KNOCKBACK, bully.getX() - p.getX(), bully.getZ() - p.getZ());
        p.addVelocity(0, 0.25, 0);
        p.velocityModified = true;
    }

    private void spawnBully(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        Entity e = EntityType.HUSK.create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 10 + RNG.nextDouble() * 4;
        e.refreshPositionAndAngles(p.getX() + Math.cos(ang) * dist, p.getY(), p.getZ() + Math.sin(ang) * dist, 0f, 0f);
        m.setCustomName(Text.literal("Задира"));
        m.setCustomNameVisible(true);
        m.setPersistent();
        EntityAttributeInstance dmg = m.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(0.0);
        m.setTarget(p);
        w.spawnEntity(m);
        d.spawned.add(m.getUuid());
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.removeStatusEffect(StatusEffects.BLINDNESS);
    }
}
