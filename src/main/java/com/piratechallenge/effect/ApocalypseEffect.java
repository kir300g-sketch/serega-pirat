package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Random;

/**
 * «Конец света»: метеориты (чем дальше — тем гуще), ядерные грибы вдали с ударной волной, пепел, огонь и дым повсюду.
 * По умолчанию рельеф НЕ разрушается (всё — частицы и взрывы без урона блокам), чтобы не уничтожить твой мир.
 * Хочешь настоящие кратеры — поставь DESTROY_TERRAIN = true (мир будет разрушен!).
 */
public class ApocalypseEffect implements TrackEffect {
    private static final boolean DESTROY_TERRAIN = false;
    private static final Random RNG = new Random();

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.meteors.clear();
        d.nukes.clear();
        d.nukeTimer = 160;
        p.sendMessage(Text.literal("§4§lКОНЕЦ СВЕТА. Небо горит."), false);
    }

    private static World.ExplosionSourceType srcType() {
        return DESTROY_TERRAIN ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE;
    }

    private static void fx(ServerWorld w, ServerPlayerEntity viewer, ParticleEffect type, double x, double y, double z,
                           int count, double dx, double dy, double dz, double speed) {
        w.spawnParticles(viewer, type, true, x, y, z, count, dx, dy, dz, speed);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        double progress = elapsed / (double) Math.max(1, total);

        // ---- пепел, дым и огонь повсюду
        if (elapsed % 2 == 0) {
            fx(w, p, ParticleTypes.ASH, p.getX(), p.getY() + 6, p.getZ(), 40, 16, 8, 16, 0.02);
            fx(w, p, ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 2, p.getZ(), 6, 14, 3, 14, 0.01);
        }
        if (elapsed % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double fxp = p.getX() + (RNG.nextDouble() - 0.5) * 40, fzp = p.getZ() + (RNG.nextDouble() - 0.5) * 40;
                int gy = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(fxp, 0, fzp)).getY();
                fx(w, p, ParticleTypes.FLAME, fxp, gy + 0.3, fzp, 14, 1.2, 0.5, 1.2, 0.04);
                fx(w, p, ParticleTypes.SMOKE, fxp, gy + 1.0, fzp, 6, 1.0, 1.0, 1.0, 0.03);
            }
        }
        if (elapsed % 80 == 0) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
                    net.minecraft.sound.SoundCategory.AMBIENT, 2.0f, 0.5f);
        }

        // ---- метеориты
        int interval = (int) Math.max(8, 36 - progress * 28);
        if (elapsed % interval == 0) {
            int n = 1 + (progress > 0.5 ? 1 : 0) + (progress > 0.8 ? 1 : 0);
            for (int i = 0; i < n; i++) spawnMeteor(p, d);
        }
        updateMeteors(p, d, w);

        // ---- ядерные грибы
        if (--d.nukeTimer <= 0) {
            d.nukeTimer = 520 + RNG.nextInt(260);
            spawnNuke(p, d, w);
        }
        updateNukes(p, d, w);
    }

    // ------------------------------------------------------------------ метеориты

    private void spawnMeteor(ServerPlayerEntity p, PlayerData d) {
        double sx = p.getX() + (RNG.nextDouble() - 0.5) * 90;
        double sz = p.getZ() + (RNG.nextDouble() - 0.5) * 90;
        double sy = p.getY() + 70 + RNG.nextDouble() * 20;
        // цель — рядом с игроком (иногда прямо в него)
        double tx = p.getX() + (RNG.nextDouble() - 0.5) * (RNG.nextInt(4) == 0 ? 3 : 36);
        double tz = p.getZ() + (RNG.nextDouble() - 0.5) * (RNG.nextInt(4) == 0 ? 3 : 36);
        double dx = tx - sx, dy = (p.getY() - 1) - sy, dz = tz - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = 1.4;
        d.meteors.add(new double[]{sx, sy, sz, dx / len * speed, dy / len * speed, dz / len * speed, 0});
    }

    private void updateMeteors(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        Iterator<double[]> it = d.meteors.iterator();
        while (it.hasNext()) {
            double[] m = it.next();
            m[0] += m[3]; m[1] += m[4]; m[2] += m[5]; m[6]++;
            fx(w, p, ParticleTypes.FLAME, m[0], m[1], m[2], 16, 0.5, 0.5, 0.5, 0.05);
            fx(w, p, ParticleTypes.LAVA, m[0], m[1], m[2], 3, 0.4, 0.4, 0.4, 0.0);
            fx(w, p, ParticleTypes.LARGE_SMOKE, m[0] - m[3] * 2, m[1] - m[4] * 2, m[2] - m[5] * 2, 8, 0.4, 0.4, 0.4, 0.02);

            int ground = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(m[0], 0, m[2])).getY();
            if (m[1] <= ground + 0.5 || m[6] > 160) {
                impact(p, w, m[0], Math.max(ground, w.getBottomY() + 1), m[2]);
                it.remove();
            }
        }
    }

    private void impact(ServerPlayerEntity p, ServerWorld w, double x, double y, double z) {
        w.createExplosion(null, x, y + 0.5, z, 3.5f, false, srcType());
        fx(w, p, ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        fx(w, p, ParticleTypes.FLAME, x, y + 1, z, 60, 2.5, 1.5, 2.5, 0.15);
        fx(w, p, ParticleTypes.LARGE_SMOKE, x, y + 2, z, 40, 2.5, 2.0, 2.5, 0.08);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(x - 6, y - 3, z - 6, x + 6, y + 6, z + 6), LivingEntity::isAlive)) {
            e.setOnFireFor(5);
        }
    }

    // ------------------------------------------------------------------ ядерные грибы

    private void spawnNuke(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 55 + RNG.nextDouble() * 30;
        double x = p.getX() + Math.cos(ang) * dist, z = p.getZ() + Math.sin(ang) * dist;
        int gy = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).getY();
        d.nukes.add(new double[]{x, gy, z, 0, dist});
        p.sendMessage(Text.literal("§c§l!!! ЯДЕРНЫЙ УДАР !!! Ударная волна близко!"), true);
        w.createExplosion(null, x, gy + 1, z, 8.0f, false, srcType());
    }

    private void updateNukes(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        Iterator<double[]> it = d.nukes.iterator();
        while (it.hasNext()) {
            double[] n = it.next();
            double x = n[0], gy = n[1], z = n[2];
            n[3]++;
            double age = n[3];
            if (age > 220) { it.remove(); continue; }

            // вспышка и огненный шар в начале
            if (age < 10) {
                fx(w, p, ParticleTypes.EXPLOSION_EMITTER, x, gy + 2, z, 2, 3, 2, 3, 0);
                fx(w, p, ParticleTypes.FLAME, x, gy + 3, z, 80, 5, 3, 5, 0.3);
            }
            // ножка гриба растёт вверх
            double stemH = Math.min(42, age * 0.7);
            for (int i = 0; i < 3; i++) {
                double yy = gy + RNG.nextDouble() * stemH;
                fx(w, p, ParticleTypes.FLAME, x, yy, z, 10, 1.6, 0.5, 1.6, 0.02);
                fx(w, p, ParticleTypes.LARGE_SMOKE, x, yy, z, 6, 2.2, 0.6, 2.2, 0.02);
            }
            // шапка гриба: расширяющийся «шар» дыма и огня наверху
            double capR = Math.min(17, age * 0.22);
            double capY = gy + stemH + 3;
            if (age > 15) {
                fx(w, p, ParticleTypes.LARGE_SMOKE, x, capY, z, 60, capR, capR * 0.45, capR, 0.01);
                fx(w, p, ParticleTypes.FLAME, x, capY - capR * 0.25, z, 30, capR * 0.8, capR * 0.3, capR * 0.8, 0.02);
                fx(w, p, ParticleTypes.LAVA, x, capY, z, 6, capR * 0.7, capR * 0.3, capR * 0.7, 0);
            }

            // ударная волна достигает игрока
            double arrive = n[4] / 1.2; // тиков
            if ((int) age == (int) arrive) shockwave(p, w, x, z);
        }
    }

    private void shockwave(ServerPlayerEntity p, ServerWorld w, double x, double z) {
        double dx = p.getX() - x, dz = p.getZ() - z;
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        p.addVelocity(dx / len * 1.8, 0.5, dz / len * 1.8);
        p.velocityModified = true;
        p.damage(p.getDamageSources().explosion(null, null), 4.0f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, false, false));
        p.setOnFireFor(3);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.meteors.clear();
        d.nukes.clear();
        p.removeStatusEffect(StatusEffects.BLINDNESS);
        p.removeStatusEffect(StatusEffects.NAUSEA);
        p.extinguish();
        p.sendMessage(Text.literal("§7Всё стихло. Мир ещё стоит."), false);
    }
}
