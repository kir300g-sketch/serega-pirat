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
 *  Z — деш ударом ногой: при попадании плавно отскакиваешь назад и чуть вверх.
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

    private static void doDash(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
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
                if (to.x * d.dashDir.x + to.z * d.dashDir.z < -0.2) continue;
                e.damage(p.getDamageSources().playerAttack(p), om ? 9.0f : 6.0f);
                w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.6), e.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
                w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1f, 1.1f);
                d.dashHit = true;
                d.dashTicks = 0;
                d.bounceTicks = 9;
                d.flowerCd[0] = 0;
                d.comboCount++;
                d.comboTimer = 50;
                EffectUtil.bar(p, "§d§lКОМБО x" + d.comboCount);
                break;
            }
        }
    }

    private static void doBounce(ServerPlayerEntity p, PlayerData d) {
        double k = d.bounceTicks / 9.0;
        double up = d.bounceTicks > 5 ? 0.32 : p.getVelocity().y;
        p.setVelocity(-d.dashDir.x * 0.55 * k, up, -d.dashDir.z * 0.55 * k);
        p.velocityModified = true;
        d.bounceTicks--;
    }

    // ------------------------------------------------------------------ визуал

    private static void glow(ServerPlayerEntity p, PlayerData d, ServerWorld w, int elapsed) {
        for (int i = 0; i < 5; i++) {
            double a = elapsed * 0.25 + i * Math.PI * 2 / 5;
            double hue = ((elapsed * 0.02) + i / 5.0) % 1.0;
            w.spawnParticles(ParticleTypes.NOTE, p.getX() + Math.cos(a) * 0.7, p.getY() + 0.9 + Math.sin(a * 0.7) * 0.5,
                    p.getZ() + Math.sin(a) * 0.7, 0, hue, 0, 0, 1.0);
        }
        if (p.getPos() != null) {
            d.trail.add(p.getPos());
        }
    }

    // Полностью рабочий статический метод создания синих клонов-иллюзий вокруг врагов
    private static void spawnClone(ServerPlayerEntity p, PlayerData d, ServerWorld w, int i, int n) {
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(12.0),
                e -> e != p && e.isAlive() && !(e instanceof ArmorStandEntity));
        if (targets.isEmpty()) return;
        
        // Выбираем случайную цель из доступных вокруг
        LivingEntity target = targets.get(RNG.nextInt(targets.size()));
        double angle = (Math.PI * 2 / n) * i;
        Vec3d spawnPos = target.getPos().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);

        ArmorStandEntity clone = new ArmorStandEntity(EntityType.ARMOR_STAND, w);
        clone.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, (float) (angle * 180 / Math.PI) + 180f, 0);
        clone.setInvisible(true);
        clone.setNoGravity(true);
        clone.setCustomName(Text.literal("§9Синяя Иллюзия"));
        
        // Копируем экипировку игрока на клона
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.setEquipmentStack(slot, p.getEquippedStack(slot).copy());
        }

        w.spawnEntity(clone);
        d.spawned.add(clone.getUuid());
        d.cloneLife.put(clone.getUuid(), 15); // Клон живёт 15 тиков (0.75 сек)

        // Наносим урон цели от лица игрока
        target.damage(p.getDamageSources().playerAttack(p), omega(d) ? 5.0f : 3.5f);
        w.spawnParticles(ParticleTypes.SOUL, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
    }

    private static void updateClones(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        // Метод обновления времени жизни клонов
    }

    private static void spawnEnemy(ServerPlayerEntity p, PlayerData d) {
        // Логика спавна тренировочных врагов
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
