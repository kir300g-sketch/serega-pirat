package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

/**
 * Ярость: игрока тянет к ближайшему мобу, он автоматически бьёт его и не отстаёт.
 * Цель выбирается только среди видимых мобов (не за блоками, не рыбы, не сильно выше/ниже),
 * а если игрок застрял и не приближается 2 секунды — цель уходит в чёрный список на 10 секунд.
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
        d.blacklist.values().removeIf(exp -> exp < elapsed);

        Entity target = d.target == null ? null : w.getEntity(d.target);
        if (target == null || !target.isAlive()) {
            d.target = null;
            d.bestDist = 1e9;
            d.stuckTicks = 0;
            Optional<MobEntity> nearest = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(24), m ->
                            m.isAlive()
                                    && !(m instanceof WaterCreatureEntity)
                                    && !d.blacklist.containsKey(m.getUuid())
                                    && Math.abs(m.getY() - p.getY()) <= 3.5
                                    && p.canSee(m))
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

            // антизалипание: не приближаемся -> бросаем цель
            if (dist < d.bestDist - 0.05) {
                d.bestDist = dist;
                d.stuckTicks = 0;
            } else if (++d.stuckTicks >= 40) {
                d.blacklist.put(target.getUuid(), elapsed + 200);
                d.target = null;
                d.bestDist = 1e9;
                d.stuckTicks = 0;
                return;
            }
        } else {
            d.stuckTicks = 0;
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
