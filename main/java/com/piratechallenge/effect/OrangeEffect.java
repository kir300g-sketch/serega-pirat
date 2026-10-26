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
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Игрок — апельсин: маленькая «башка», двигается только рывками (KeyboardInputMixin).
 * 9 долек. Любой моб (и враждебный, и мирный), стоящий вплотную 3 секунды, съедает одну дольку. 0 = смерть.
 * Мобы подходят медленно (скорость задаётся напрямую, не зависит от ИИ).
 */
public class OrangeEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int EAT_TICKS = 60;      // 3 секунды
    private static final double MOB_SPEED = 0.085; // блоков в тик (~1.7 б/с) — медленнее прыжков игрока
    private static final double RANGE = 22;

    private static final List<EntityType<?>> EATERS = List.of(
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.COW, EntityType.PIG,
            EntityType.SHEEP, EntityType.CHICKEN, EntityType.COW, EntityType.PIG);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.slices = 9;
        d.eat.clear();
        EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"), -0.6, Operation.ADD_MULTIPLIED_BASE);
        EffectUtil.ensure(p, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, EffectUtil.id("orange_knockback"), 2.0, Operation.ADD_VALUE);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);
        if (elapsed % 20 == 0) {
            EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"), -0.6, Operation.ADD_MULTIPLIED_BASE);
            EffectUtil.ensure(p, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, EffectUtil.id("orange_knockback"), 2.0, Operation.ADD_VALUE);
        }

        // чем дальше трек, тем больше едоков
        int cap = 6 + elapsed * 8 / Math.max(1, total);
        if (elapsed % 40 == 0 && d.spawned.size() < cap) spawnEater(p, d);

        // ВСЕ мобы рядом (свои и случайные) идут к игроку
        boolean biting = false;
        Box box = p.getBoundingBox().expand(RANGE);
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, box, x -> x.isAlive() && !x.getCommandTags().contains("pc_serega"))) {
            double dx = p.getX() - m.getX(), dz = p.getZ() - m.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);

            if (flat > 1.2) {
                double vy = (m.horizontalCollision && m.isOnGround()) ? 0.42 : m.getVelocity().y;
                m.setVelocity(dx / flat * MOB_SPEED, vy, dz / flat * MOB_SPEED);
                m.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
                m.setHeadYaw(m.getYaw());
            }
            if (m.isOnFire() && m.getCommandTags().contains(EffectUtil.SPAWN_TAG)) m.extinguish();

            UUID id = m.getUuid();
            boolean close = flat < 1.7 && Math.abs(m.getY() - p.getY()) < 2.0;
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
        Entity e = EATERS.get(RNG.nextInt(EATERS.size())).create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 9 + RNG.nextDouble() * 5;
        double x = p.getX() + Math.cos(ang) * dist;
        double zz = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, zz));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, zz, 0f, 0f);
        if (m instanceof ZombieEntity z) {
            z.setBaby(false);
            EntityAttributeInstance dmg = z.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(0.0); // грызут только дольки
        }
        m.setPersistent();
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("orange_scale"));
        EffectUtil.remove(p, EntityAttributes.GENERIC_ATTACK_KNOCKBACK, EffectUtil.id("orange_knockback"));
        d.eat.clear();
    }
}
