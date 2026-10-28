package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * «Конец света»: метеориты, ядерные грибы вдали с ударной волной, пепел, огонь и дым, РАЗРУШЕНИЕ БЛОКОВ.
 * Выживаемость: метеориты заранее помечаются на земле красным кольцом (есть время отбежать), их количество растёт
 * плавно и ограничено, лишь малая часть летит прямо в игрока. Взрывы повреждают рельеф навсегда — делай бэкап мира!
 * DESTROY_TERRAIN = false — взрывы только по существам, блоки не страдают.
 */
public class ApocalypseEffect implements TrackEffect {
    private static final boolean DESTROY_TERRAIN = true;
    private static final int MAX_METEORS = 7;
    private static final Random RNG = new Random();

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.meteors.clear();
        d.nukes.clear();
        d.nukeTimer = 200;
        p.sendMessage(Text.literal("§4§lКОНЕЦ СВЕТА. Небо горит. Следи за красными кольцами на земле!"), false);
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

        // ---- метеориты: плавный рост (progress^1.3), пауза между падениями 56 -> 22 тика, не больше MAX_METEORS в воздухе
        int interval = (int) Math.max(22, 56 - Math.pow(progress, 1.3) * 34);
        if (elapsed % interval == 0 && d.meteors.size() < MAX_METEORS) {
            int n = progress > 0.75 ? 2 : 1;
            for (int i = 0; i < n; i++) spawnMeteor(p, d, w);
        }
        updateMeteors(p, d, w);
        if (d.current != Track.APOCALYPSE) return; // игрок мог погибнуть во время взрыва

        // ---- ядерные грибы (реже в начале, чаще к концу)
        if (--d.nukeTimer <= 0) {
            d.nukeTimer = (int) (560 - progress * 160) + RNG.nextInt(160);
            spawnNuke(p, d, w);
        }
        updateNukes(p, d, w);
    }

    // ------------------------------------------------------------------ метеориты

    /** Метеорит: {x,y,z, vx,vy,vz, возраст, цельX, цельY, цельZ}. */
    private void spawnMeteor(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        double sx = p.getX() + (RNG.nextDouble() - 0.5) * 90;
        double sz = p.getZ() + (RNG.nextDouble() - 0.5) * 90;
        double sy = p.getY() + 70 + RNG.nextDouble() * 20;

        // 1 из 7 целится близко к игроку (но не в упор), остальные падают вокруг
        boolean aimed = RNG.nextInt(7) == 0;
        double r = aimed ? 3 + RNG.nextDouble() * 6 : 10 + RNG.nextDouble() * 32;
        double a = RNG.nextDouble() * Math.PI * 2;
        double tx = p.getX() + Math.cos(a) * r, tz = p.getZ() + Math.sin(a) * r;
        int ty = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(tx, 0, tz)).getY();

        double dx = tx - sx, dy = ty - sy, dz = tz - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = 1.0; // медленнее: есть время заметить и увернуться
        d.meteors.add(new double[]{sx, sy, sz, dx / len * speed, dy / len * speed, dz / len * speed, 0, tx, ty, tz});
    }

    private void updateMeteors(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        List<double[]> snapshot = new ArrayList<>(d.meteors); // копия: смерть игрока во взрыве вызывает end() и чистит d.meteors
        List<double[]> done = new ArrayList<>();
        for (double[] m : snapshot) {
            m[0] += m[3]; m[1] += m[4]; m[2] += m[5]; m[6]++;
            fx(w, p, ParticleTypes.FLAME, m[0], m[1], m[2], 16, 0.5, 0.5, 0.5, 0.05);
            fx(w, p, ParticleTypes.LAVA, m[0], m[1], m[2], 3, 0.4, 0.4, 0.4, 0.0);
            fx(w, p, ParticleTypes.LARGE_SMOKE, m[0] - m[3] * 2, m[1] - m[4] * 2, m[2] - m[5] * 2, 8, 0.4, 0.4, 0.4, 0.02);

            // красное кольцо-предупреждение на земле в точке падения
            if (m[6] % 4 == 0) {
                for (int k = 0; k < 16; k++) {
                    double ang = k * Math.PI / 8;
                    fx(w, p, ParticleTypes.FLAME, m[7] + Math.cos(ang) * 3.5, m[8] + 0.4, m[9] + Math.sin(ang) * 3.5, 1, 0, 0, 0, 0);
                }
            }

            if (m[1] <= m[8] + 0.5 || m[6] > 200) {
                done.add(m);
                impact(p, w, m[7], m[8], m[9]);
                if (d.current != Track.APOCALYPSE) return; // трек закончился из-за смерти — дальше ничего не трогаем
            }
        }
        d.meteors.removeAll(done);
    }

    private void impact(ServerPlayerEntity p, ServerWorld w, double x, double y, double z) {
        w.createExplosion(null, x, y + 0.5, z, 2.8f, DESTROY_TERRAIN, srcType());
        fx(w, p, ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
        fx(w, p, ParticleTypes.FLAME, x, y + 1, z, 60, 2.5, 1.5, 2.5, 0.15);
        fx(w, p, ParticleTypes.LARGE_SMOKE, x, y + 2, z, 40, 2.5, 2.0, 2.5, 0.08);
        List<LivingEntity> victims = w.getEntitiesByClass(LivingEntity.class,
                new Box(x - 6, y - 3, z - 6, x + 6, y + 6, z + 6), LivingEntity::isAlive);
        for (LivingEntity e : victims) e.setOnFireFor(4);
    }

    // ------------------------------------------------------------------ ядерные грибы

    private void spawnNuke(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 60 + RNG.nextDouble() * 30;
        double x = p.getX() + Math.cos(ang) * dist, z = p.getZ() + Math.sin(ang) * dist;
        int gy = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).getY();
        d.nukes.add(new double[]{x, gy, z, 0, dist});
        p.sendMessage(Text.literal("§c§l!!! ЯДЕРНЫЙ УДАР !!! Ударная волна близко — пригнись или укройся!"), true);
        w.createExplosion(null, x, gy + 1, z, 8.0f, DESTROY_TERRAIN, srcType());
    }

    private void updateNukes(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        List<double[]> snapshot = new ArrayList<>(d.nukes);
        List<double[]> done = new ArrayList<>();
        for (double[] n : snapshot) {
            double x = n[0], gy = n[1], z = n[2];
            n[3]++;
            double age = n[3];
            if (age > 220) { done.add(n); continue; }

            if (age < 10) {
                fx(w, p, ParticleTypes.EXPLOSION_EMITTER, x, gy + 2, z, 2, 3, 2, 3, 0);
                fx(w, p, ParticleTypes.FLAME, x, gy + 3, z, 80, 5, 3, 5, 0.3);
            }
            double stemH = Math.min(42, age * 0.7);
            for (int i = 0; i < 3; i++) {
                double yy = gy + RNG.nextDouble() * stemH;
                fx(w, p, ParticleTypes.FLAME, x, yy, z, 10, 1.6, 0.5, 1.6, 0.02);
                fx(w, p, ParticleTypes.LARGE_SMOKE, x, yy, z, 6, 2.2, 0.6, 2.2, 0.02);
            }
            double capR = Math.min(17, age * 0.22);
            double capY = gy + stemH + 3;
            if (age > 15) {
                fx(w, p, ParticleTypes.LARGE_SMOKE, x, capY, z, 60, capR, capR * 0.45, capR, 0.01);
                fx(w, p, ParticleTypes.FLAME, x, capY - capR * 0.25, z, 30, capR * 0.8, capR * 0.3, capR * 0.8, 0.02);
                fx(w, p, ParticleTypes.LAVA, x, capY, z, 6, capR * 0.7, capR * 0.3, capR * 0.7, 0);
            }
            if ((int) age == (int) (n[4] / 1.2)) { // ударная волна дошла
                shockwave(p, x, z);
                if (d.current != Track.APOCALYPSE) return;
            }
        }
        d.nukes.removeAll(done);
    }

    private void shockwave(ServerPlayerEntity p, double x, double z) {
        double dx = p.getX() - x, dz = p.getZ() - z;
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        p.addVelocity(dx / len * 1.4, 0.4, dz / len * 1.4);
        p.velocityModified = true;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, false, false));
        p.damage(p.getDamageSources().explosion(null, null), 3.0f);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.meteors.clear();
        d.nukes.clear();
        p.removeStatusEffect(StatusEffects.BLINDNESS);
        p.removeStatusEffect(StatusEffects.NAUSEA);
        p.extinguish();
        if (p.isAlive()) p.sendMessage(Text.literal("§7Всё стихло. Мир ещё стоит... то, что от него осталось."), false);
    }
}
