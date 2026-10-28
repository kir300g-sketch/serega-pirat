package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Мутанты из канализации»: мутант — это ты. Ты в канализации-арене (в небе, как и другие арены).
 *  • токсичная аура травит всё вокруг; • у стены можно лезть вверх (упираешься в стену — карабкаешься);
 *  • в воде канала регенерация; • каждые 25 секунд новая случайная МУТАЦИЯ (бонус + цена);
 *  • тебя выслеживают «Санитары» в жёлтых костюмах — волны растут.
 */
public class MutantsEffect implements TrackEffect {
    public static final String HUNTER_TAG = "pc_hunter";
    private static final int HALF = 12;
    private static final int MUTATION_TICKS = 500; // 25 секунд
    private static final Random RNG = new Random();

    private static final String[] MUTATIONS = {
            "Каменные кулаки (сила, но медленнее)",
            "Лягушачьи лапы (прыжок и скорость)",
            "Панцирь (броня, но медленнее)",
            "Быстрая регенерация",
            "Гигантизм (больше и сильнее)",
            "Мелкая гадость (маленький и очень быстрый)"
    };

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.returnPos = p.getPos();
        d.arenaHalf = HALF;
        d.arenaH = 5;
        d.arenaCenter = SkyArena.build(w, p.getBlockPos(), HALF, 5,
                Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState());
        BlockPos c = d.arenaCenter;

        // центральный канал со стоячей водой (с опорой снизу) и «трубы»
        for (int dx = -HALF + 1; dx <= HALF - 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.setBlockState(c.add(dx, -1, dz), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                w.setBlockState(c.add(dx, 0, dz), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (int dz = -HALF + 2; dz <= HALF - 2; dz += 4) {
            for (int dy = 1; dy <= 4; dy++) {
                w.setBlockState(c.add(-HALF + 1, dy, dz), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (int[] cr : new int[][]{{-HALF + 1, -HALF + 1}, {HALF - 1, -HALF + 1}, {-HALF + 1, HALF - 1}, {HALF - 1, HALF - 1}}) {
            w.setBlockState(c.add(cr[0], 1, cr[1]), Blocks.GLOWSTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - 7.5);
        d.kills = 0;
        d.mutTick = 0;
        d.mutName = "";
        p.sendMessage(Text.literal("§aТы — мутант из канализации. Токсичная аура, лазанье по стенам, мутации. Санитары идут за тобой!"), false);
        mutate(p, d);
        for (int i = 0; i < 2; i++) spawnHunter(p, d, w, c);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = d.arenaCenter;
        if (c == null) return;
        EffectUtil.prune(w, d);

        // не выпадаем с арены
        if (p.getY() < c.getY() - 4 || Math.abs(p.getX() - c.getX()) > HALF || Math.abs(p.getZ() - c.getZ()) > HALF) {
            p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - 7.5);
        }

        // мутации
        if (++d.mutTick >= MUTATION_TICKS) {
            d.mutTick = 0;
            mutate(p, d);
        }

        // токсичная аура
        if (elapsed % 20 == 0) {
            w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 20, 2.0, 0.8, 2.0, 0.02);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(3.5), x -> x != p && x.isAlive())) {
                e.damage(p.getDamageSources().indirectMagic(p, p), 1.5f);
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
            }
        }

        // лазанье по стенам: упёрся в стену — ползёшь вверх
        if (p.horizontalCollision && !p.isSneaking() && p.getVelocity().y < 0.2) {
            p.setVelocity(p.getVelocity().x, 0.22, p.getVelocity().z);
            p.velocityModified = true;
            p.fallDistance = 0;
        }

        // вода канала лечит
        if (p.isTouchingWater()) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false, false));
        }

        // санитары: волны растут
        double progress = elapsed / (double) Math.max(1, total);
        int cap = 3 + (int) (progress * 7);
        if (elapsed % 90 == 0 && d.spawned.size() < cap) spawnHunter(p, d, w, c);
        if (elapsed % 40 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m) m.setTarget(p);
            }
        }
    }

    private void mutate(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("mutant_scale"));
        int dur = MUTATION_TICKS + 20;
        int m = RNG.nextInt(MUTATIONS.length);
        d.mutName = MUTATIONS[m];
        switch (m) {
            case 0 -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 0));
            }
            case 1 -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, dur, 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 0));
            }
            case 2 -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 0));
            }
            case 3 -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, 1));
            case 4 -> {
                EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("mutant_scale"), 0.6, Operation.ADD_MULTIPLIED_BASE);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, 0));
            }
            default -> {
                EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("mutant_scale"), -0.4, Operation.ADD_MULTIPLIED_BASE);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 2));
            }
        }
        p.sendMessage(Text.literal("§aМУТАЦИЯ: §f" + d.mutName), false);
        p.getServerWorld().spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 25, 0.5, 0.8, 0.5, 0.05);
    }

    private void spawnHunter(ServerPlayerEntity p, PlayerData d, ServerWorld w, BlockPos c) {
        EntityType<?> type = RNG.nextInt(3) == 0 ? EntityType.PILLAGER : (RNG.nextBoolean() ? EntityType.VINDICATOR : EntityType.ZOMBIE);
        Entity e = type.create(w);
        if (!(e instanceof MobEntity m)) return;
        double side = RNG.nextBoolean() ? 1 : -1;
        double x = c.getX() + 0.5 + side * (HALF - 3);
        double z = c.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 2 * (HALF - 3);
        m.refreshPositionAndAngles(x, c.getY() + 1, z, 0f, 0f);
        // жёлтый защитный костюм
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        net.minecraft.item.Item[] items = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(items[i]);
            s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0xF2D400, false));
            m.equipStack(slots[i], s);
            m.setEquipmentDropChance(slots[i], 0f);
        }
        m.setCustomName(Text.literal("Санитар"));
        m.setCustomNameVisible(true);
        m.setPersistent();
        m.addCommandTag(HUNTER_TAG);
        m.setTarget(p);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.MUTANTS) return;
        d.kills++;
        p.heal(2.0f);
        EffectUtil.bar(p, "§aСанитаров устранено: " + d.kills);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("mutant_scale"));
        if (d.arenaCenter != null) SkyArena.clear(p.getServerWorld(), d.arenaCenter, d.arenaHalf, d.arenaH);
        if (p.isAlive() && d.returnPos != null) {
            Vec3d r = d.returnPos;
            p.requestTeleport(r.x, r.y, r.z);
            p.sendMessage(Text.literal("§7Санитаров устранено: " + d.kills), false);
        }
    }
}
