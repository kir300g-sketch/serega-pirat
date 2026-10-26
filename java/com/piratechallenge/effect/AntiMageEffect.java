package com.piratechallenge.effect;

import com.piratechallenge.AmStatePayload;
import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * ФП АМ — Анти-Маг. Вид сверху и управление мышью (клиент), навыки вместо хотбара:
 *  Q Mana Break (пассив: удары жгут «ману» — доп. урон + замедление)
 *  W Blink (рывок к курсору)
 *  E Counterspell (на 2.5 сек блокирует урон и отражает его)
 *  R Mana Void (удар по области у курсора)
 * Цель: убить 10 хейтеров.
 */
public class AntiMageEffect implements TrackEffect {
    public static final String TAG = "pc_hater";
    public static final int GOAL = 10;
    private static final Random RNG = new Random();

    private static final int[] COST = {0, 20, 25, 60};
    private static final int[] COOLDOWN = {0, 80, 160, 400};

    private static final List<EntityType<?>> HATERS = List.of(
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.WITCH);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.mana = 100;
        d.kills = 0;
        d.moveTarget = null;
        p.sendMessage(Text.literal("§9Ты — Анти-Маг. ПКМ — идти, Q W E R — навыки. Убей " + GOAL + " хейтеров!"), false);
        for (int i = 0; i < 3; i++) spawnHater(p, d);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);

        d.mana = Math.min(100, d.mana + 0.15);
        for (int i = 1; i < 4; i++) if (d.cd[i] > 0) d.cd[i]--;
        if (d.counterTicks > 0) d.counterTicks--;

        // поддерживаем ~4 живых хейтеров, пока не набрана цель
        if (d.kills < GOAL && elapsed % 60 == 0 && d.spawned.size() < 4) spawnHater(p, d);
        if (elapsed % 40 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m) m.setTarget(p);
            }
        }

        // ходьба кликом
        if (d.moveTarget != null) {
            Vec3d dir = d.moveTarget.subtract(p.getPos());
            double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (flat < 0.8) {
                d.moveTarget = null;
            } else {
                Vec3d v = new Vec3d(dir.x, 0, dir.z).normalize().multiply(0.24);
                double vy = (p.horizontalCollision && p.isOnGround()) ? 0.42 : p.getVelocity().y;
                p.setVelocity(v.x, vy, v.z);
                p.velocityModified = true;
            }
        }

        // автоатака ближайшего хейтера + Mana Break
        if (elapsed % 12 == 0) {
            LivingEntity best = null;
            double bd = 3.3 * 3.3;
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive() && le.squaredDistanceTo(p) < bd) {
                    bd = le.squaredDistanceTo(p);
                    best = le;
                }
            }
            if (best != null) {
                p.attack(best);
                p.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                best.damage(p.getDamageSources().indirectMagic(p, p), 4.0f); // Mana Break
                best.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
                w.spawnParticles(ParticleTypes.ENCHANT, best.getX(), best.getBodyY(0.6), best.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
            }
        }

        // Mana Void: отложенный удар
        if (d.voidTimer > 0 && --d.voidTimer == 0) {
            Vec3d c = d.voidAt;
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(c.x, c.y, c.z);
                bolt.setCosmetic(true);
                w.spawnEntity(bolt);
            }
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                    new net.minecraft.util.math.Box(c.x - 6, c.y - 4, c.z - 6, c.x + 6, c.y + 6, c.z + 6),
                    x -> x != p && x.isAlive() && x.squaredDistanceTo(c) <= 36)) {
                e.damage(p.getDamageSources().playerAttack(p), 22.0f);
            }
        }

        if (elapsed % 5 == 0 && p.networkHandler != null) {
            ServerPlayNetworking.send(p, new AmStatePayload((int) d.mana, d.cd[1], d.cd[2], d.cd[3], d.counterTicks, d.kills));
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.moveTarget = null;
        if (d.kills < GOAL) {
            p.sendMessage(Text.literal("§7Хейтеры остались живы (" + d.kills + "/" + GOAL + "). Ты замедлен."), false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30 * 20, 1));
        } else {
            p.sendMessage(Text.literal("§aВсе хейтеры повержены!"), false);
        }
    }

    // ------------------------------------------------------------ сетевые события

    public static void onMove(ServerPlayerEntity p, Vec3d target) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FP_AM) return;
        if (target.squaredDistanceTo(p.getPos()) > 80 * 80) return;
        d.moveTarget = target;
    }

    public static void onSkill(ServerPlayerEntity p, int skill, Vec3d at) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FP_AM || skill < 0 || skill > 3) return;
        if (skill == 0) {
            EffectUtil.bar(p, "§bMana Break: пассив — удары жгут ману (доп. урон и замедление)");
            return;
        }
        if (d.cd[skill] > 0) {
            EffectUtil.bar(p, "§cПерезарядка: " + (d.cd[skill] / 20 + 1) + " с");
            return;
        }
        if (d.mana < COST[skill]) {
            EffectUtil.bar(p, "§9Не хватает маны!");
            return;
        }
        ServerWorld w = p.getServerWorld();
        switch (skill) {
            case 1 -> { // Blink
                Vec3d from = p.getPos();
                Vec3d dir = new Vec3d(at.x - from.x, 0, at.z - from.z);
                double dist = Math.min(14, dir.length());
                if (dist < 1) return;
                Vec3d dest = from.add(dir.normalize().multiply(dist));
                BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(dest.x, 0, dest.z));
                double y = Math.abs(top.getY() - p.getY()) <= 8 ? top.getY() : p.getY();
                w.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.5);
                p.requestTeleport(dest.x, y, dest.z);
                w.spawnParticles(ParticleTypes.PORTAL, dest.x, y + 1, dest.z, 40, 0.3, 0.8, 0.3, 0.5);
                w.playSound(null, dest.x, y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
                d.moveTarget = null;
            }
            case 2 -> { // Counterspell
                d.counterTicks = 50;
                w.spawnParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.3);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.6f);
            }
            case 3 -> { // Mana Void
                Vec3d from = p.getPos();
                Vec3d dir = new Vec3d(at.x - from.x, 0, at.z - from.z);
                double dist = Math.min(20, dir.length());
                Vec3d dest = dist < 0.5 ? from : from.add(dir.normalize().multiply(dist));
                d.voidAt = new Vec3d(dest.x, at.y, dest.z);
                d.voidTimer = 12;
                w.spawnParticles(ParticleTypes.PORTAL, dest.x, at.y + 1, dest.z, 60, 2.0, 0.5, 2.0, 0.5);
                w.playSound(null, dest.x, at.y, dest.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 1f, 1f);
            }
            default -> {}
        }
        d.mana -= COST[skill];
        d.cd[skill] = COOLDOWN[skill];
    }

    /** Counterspell: пока щит активен, урон по игроку блокируется и отражается. */
    public static boolean blocks(ServerPlayerEntity p, DamageSource source, float amount) {
        PlayerData d = ChallengeManager.data(p);
        if (d.counterTicks <= 0) return false;
        if (source.getAttacker() instanceof LivingEntity attacker && attacker != p) {
            attacker.damage(p.getDamageSources().indirectMagic(p, p), Math.max(4f, amount));
        }
        p.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.6, 0.4, 0.2);
        return true;
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FP_AM) return;
        d.kills++;
        if (d.kills <= GOAL) p.sendMessage(Text.literal("§9Хейтеры: " + d.kills + " / " + GOAL), true);
        if (d.kills == GOAL) p.sendMessage(Text.literal("§a§lЦель достигнута! Все хейтеры повержены."), false);
    }

    private void spawnHater(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        Entity e = HATERS.get(RNG.nextInt(HATERS.size())).create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 10 + RNG.nextDouble() * 6;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, z, 0f, 0f);
        m.setCustomName(Text.literal("Хейтер"));
        m.setCustomNameVisible(true);
        m.setPersistent();
        m.addCommandTag(TAG);
        m.setTarget(p);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }
}
