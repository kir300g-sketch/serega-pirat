package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.FlowerStatePayload;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * СЕКРЕТНЫЙ трек «Flower Man». Ты — Цветочек: радужное свечение и радужный след-силуэт.
 *  Z — деш ударом ногой: при попадании плавно отскакиваешь назад и чуть вверх (комбо — перезарядка сбрасывается).
 *  X — призываешь синие копии себя, они делают выпады во врагов.
 *  C — Омега-форма: радужный фильтр, все навыки сильнее и быстрее.
 */
public class FlowerEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int[] BASE_CD = {18, 80, 600};
    private static final int OMEGA_TICKS = 400;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        p.sendMessage(Text.literal("§d§lПочувствуй силу цветка!"), false);
        p.sendMessage(Text.literal("§7Z — деш-пинок, X — синие копии, C — Омега-форма."), false);
    }

    private static boolean omega(PlayerData d) { return d.omegaTicks > 0; }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);
        d.cloneLife.keySet().removeIf(id -> !d.spawned.contains(id));

        for (int i = 0; i < 3; i++) if (d.flowerCd[i] > 0) d.flowerCd[i] -= omega(d) ? 2 : 1;
        if (d.omegaTicks > 0) d.omegaTicks--;
        if (d.comboTimer > 0 && --d.comboTimer == 0) d.comboCount = 0;

        // враги для тренировки: до 6 одновременно
        int enemies = 0;
        for (UUID id : d.spawned) if (!d.cloneLife.containsKey(id)) enemies++;
        if (elapsed % 70 == 0 && enemies < 6) spawnEnemy(p, d);
        if (elapsed % 40 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m && !d.cloneLife.containsKey(id)) m.setTarget(p);
            }
        }

        // омега-усиление
        if (omega(d)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 1, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10, 0, false, false, false));
        }

        // деш
        if (d.dashTicks > 0) doDash(p, d, w);
        else if (d.bounceTicks > 0) doBounce(p, d);

        // радужное свечение и след-силуэт
        glow(p, d, w, elapsed);

        // копии
        updateClones(p, d, w);

        if (elapsed % 5 == 0 && p.networkHandler != null) {
            ServerPlayNetworking.send(p, new FlowerStatePayload(Math.max(0, d.flowerCd[0]), Math.max(0, d.flowerCd[1]),
                    Math.max(0, d.flowerCd[2]), d.omegaTicks));
        }
    }

    // ------------------------------------------------------------------ способности

    public static void onAbility(ServerPlayerEntity p, int id) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FLOWER || id < 0 || id > 2) return;
        if (d.flowerCd[id] > 0) return;
        boolean om = omega(d);
        ServerWorld w = p.getServerWorld();

        switch (id) {
            case 0 -> { // Z: деш-пинок
                Vec3d look = p.getRotationVec(1f);
                d.dashDir = new Vec3d(look.x, 0, look.z).normalize();
                d.dashTicks = om ? 6 : 7;
                d.dashHit = false;
                d.bounceTicks = 0;
                d.flowerCd[0] = (int) (BASE_CD[0] * (om ? 0.5 : 1.0));
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 1.4f);
            }
            case 1 -> { // X: синие копии
                int n = om ? 5 : 3;
                for (int i = 0; i < n; i++) spawnClone(p, d, w, i, n);
                d.flowerCd[1] = (int) (BASE_CD[1] * (om ? 0.5 : 1.0));
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.8f);
            }
            case 2 -> { // C: Омега-форма
                d.omegaTicks = OMEGA_TICKS;
                d.flowerCd[2] = BASE_CD[2];
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5f, 1.5f);
                p.sendMessage(Text.literal("§d§lОМЕГА-ФОРМА!"), true);
            }
            default -> {}
        }
    }

    private void doDash(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        boolean om = omega(d);
        double speed = om ? 1.35 : 1.0;
        p.setVelocity(d.dashDir.x * speed, Math.max(p.getVelocity().y, 0.05), d.dashDir.z * speed);
        p.velocityModified = true;
        d.dashTicks--;

        if (!d.dashHit) {
            Vec3d from = p.getPos();
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(1.6, 0.6, 1.6),
                    x -> x != p && x.isAlive() && !(x instanceof ArmorStandEntity))) {
                Vec3d to = e.getPos().subtract(from);
                if (to.x * d.dashDir.x + to.z * d.dashDir.z < -0.2) continue; // только впереди
                e.damage(p.getDamageSources().playerAttack(p), om ? 9.0f : 6.0f);
                w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.6), e.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
                w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1f, 1.1f);
                d.dashHit = true;
                d.dashTicks = 0;
                d.bounceTicks = 9;              // отскок (плавно назад и чуть вверх)
                d.flowerCd[0] = 0;              // комбо: сразу можно снова
                d.comboCount++;
                d.comboTimer = 50;
                EffectUtil.bar(p, "§d§lКОМБО x" + d.comboCount);
                break;
            }
        }
    }

    private void doBounce(ServerPlayerEntity p, PlayerData d) {
        double k = d.bounceTicks / 9.0;
        double up = d.bounceTicks > 5 ? 0.32 : p.getVelocity().y;
        p.setVelocity(-d.dashDir.x * 0.55 * k, up, -d.dashDir.z * 0.55 * k);
        p.velocityModified = true;
        d.bounceTicks--;
    }

    // ------------------------------------------------------------------ визуал

    private void glow(ServerPlayerEntity p, PlayerData d, ServerWorld w, int elapsed) {
        // радужные ноты вокруг тела (цвет ноты задаётся «скоростью» по X)
        for (int i = 0; i < 5; i++) {
            double a = elapsed * 0.25 + i * Math.PI * 2 / 5;
            double hue = ((elapsed * 0.02) + i / 5.0) % 1.0;
            w.spawnParticles(ParticleTypes.NOTE, p.getX() + Math.cos(a) * 0.7, p.getY() + 0.9 + Math.sin(a * 0.7) * 0.5,
                    p.getZ() + Math.sin(a) * 0.7, 0, hue, 0, 0, 1.0);
        }
        // след: запоминаем позиции, через ~0.4с рисуем в них радужный силуэт
        d.trail.add(p.getPos());
        if (d.trail.size() > 10) d.trail.remove(0);
        if (elapsed % 2 == 0 && d.trail.size() >= 8) {
            Vec3d t = d.trail.get(0);
            if (t.squaredDistanceTo(p.getPos()) > 0.25) {
                for (int k = 0; k < 9; k++) {
                    double hue = k / 9.0;
                    w.spawnParticles(ParticleTypes.NOTE, t.x, t.y + 0.1 + k * 0.2, t.z, 0, hue, 0, 0, 1.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ копии

    private void spawnClone(ServerPlayerEntity p, PlayerData d, ServerWorld w, int i, int n) {
        ArmorStandEntity st = EntityType.ARMOR_STAND.create(w);
        if (st == null) return;
        double ang = Math.PI * 2 * i / n;
        st.refreshPositionAndAngles(p.getX() + Math.cos(ang) * 2.0, p.getY(), p.getZ() + Math.sin(ang) * 2.0, p.getYaw(), 0f);
        st.setShowArms(true);
        st.setHideBasePlate(true);
        st.setNoGravity(true);
        st.setInvulnerable(true);
        st.setSilent(true);

        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(p.getGameProfile()));
        st.equipStack(EquipmentSlot.HEAD, head);
        st.equipStack(EquipmentSlot.CHEST, blue(Items.LEATHER_CHESTPLATE));
        st.equipStack(EquipmentSlot.LEGS, blue(Items.LEATHER_LEGGINGS));
        st.equipStack(EquipmentSlot.FEET, blue(Items.LEATHER_BOOTS));
        st.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));

        w.spawnEntity(st);
        EffectUtil.reg(d, st);
        d.cloneLife.put(st.getUuid(), 70);
        d.cloneInfo.put(st.getUuid(), new double[]{ang, RNG.nextDouble() * 6});
        w.spawnParticles(ParticleTypes.END_ROD, st.getX(), st.getY() + 1, st.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
    }

    private static ItemStack blue(net.minecraft.item.Item item) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0x2266FF, false));
        return s;
    }

    private void updateClones(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        boolean om = omega(d);
        List<UUID> ids = new ArrayList<>(d.cloneLife.keySet());
        for (UUID id : ids) {
            Entity e = w.getEntity(id);
            int life = d.cloneLife.getOrDefault(id, 0);
            if (!(e instanceof ArmorStandEntity st) || life <= 0) {
                if (e != null) {
                    w.spawnParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 15, 0.3, 0.6, 0.3, 0.05);
                    e.discard();
                }
                d.cloneLife.remove(id);
                d.cloneInfo.remove(id);
                continue;
            }
            d.cloneLife.put(id, life - 1);
            double[] info = d.cloneInfo.get(id);

            // ближайший враг вокруг хозяина
            Optional<HostileEntity> foe = w.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(18), Entity::isAlive)
                    .stream().min(Comparator.comparingDouble(m -> m.squaredDistanceTo(st)));
            if (foe.isEmpty()) {
                // без врагов — держатся рядом с игроком
                st.refreshPositionAndAngles(p.getX() + Math.cos(info[0] + life * 0.05) * 2.2, p.getY(),
                        p.getZ() + Math.sin(info[0] + life * 0.05) * 2.2, p.getYaw(), 0f);
                continue;
            }
            LivingEntity t = foe.get();
            // выпад: сближение и отход по косинусу; удар в момент наибольшего сближения
            double phase = (70 - life) * (om ? 0.55 : 0.4) + info[1];
            double off = 0.9 + 3.2 * (1 + Math.cos(phase)) / 2.0;
            Vec3d pos = new Vec3d(t.getX() + Math.cos(info[0]) * off, t.getY(), t.getZ() + Math.sin(info[0]) * off);
            float yaw = (float) (Math.toDegrees(Math.atan2(t.getZ() - pos.z, t.getX() - pos.x)) - 90.0);
            st.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            if (off < 1.6 && life % (om ? 5 : 8) == 0) {
                t.damage(p.getDamageSources().playerAttack(p), om ? 5.5f : 3.5f);
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getBodyY(0.5), t.getZ(), 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.END_ROD, t.getX(), t.getBodyY(0.5), t.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
            }
        }
    }

    private void spawnEnemy(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        EntityType<?>[] pool = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK, EntityType.PILLAGER};
        Entity e = pool[RNG.nextInt(pool.length)].create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 9 + RNG.nextDouble() * 6;
        double x = p.getX() + Math.cos(ang) * dist, z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, z, 0f, 0f);
        m.setPersistent();
        m.setTarget(p);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.omegaTicks = 0;
        d.dashTicks = 0;
        d.bounceTicks = 0;
        p.removeStatusEffect(StatusEffects.SPEED);
        p.removeStatusEffect(StatusEffects.STRENGTH);
        if (p.networkHandler != null && p.networkHandler.isConnectionOpen()) {
            ServerPlayNetworking.send(p, new FlowerStatePayload(0, 0, 0, 0));
        }
    }
}
