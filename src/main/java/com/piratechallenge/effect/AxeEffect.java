package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

/**
 * Ярость: игрока тянет к первому попавшемуся мобу, он автоматически бьёт его и не может отстать.
 * Путаница управления и случайные нажатия — на клиенте (KeyboardInputMixin + PirateChallengeClient).
 */
public class AxeEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.target = null;
        EffectUtil.bar(p, "§cЯРОСТЬ!");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20, 1, false, false, false));

        Entity target = d.target == null ? null : w.getEntity(d.target);
        if (target == null || !target.isAlive()) {
            d.target = null;
            Optional<MobEntity> nearest = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(24), Entity::isAlive)
                    .stream().min(Comparator.comparingDouble(m -> m.squaredDistanceTo(p)));
            if (nearest.isPresent()) {
                target = nearest.get();
                d.target = target.getUuid();
            }
        }
        if (target == null) return;

        p.lookAt(EntityAnchor.EYES, target.getEyePos());

        Vec3d dir = target.getPos().subtract(p.getPos());
        double dist = p.distanceTo(target);
        if (dist > 2.4) {
            Vec3d flat = new Vec3d(dir.x, 0, dir.z).normalize().multiply(0.3);
            double vy = (p.horizontalCollision && p.isOnGround()) ? 0.42 : p.getVelocity().y;
            p.setVelocity(flat.x, vy, flat.z);
            p.velocityModified = true;
        }

        if (dist < 3.6 && elapsed % 8 == 0) {
            p.attack(target);
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.target = null;
        p.removeStatusEffect(StatusEffects.STRENGTH);
    }
}
