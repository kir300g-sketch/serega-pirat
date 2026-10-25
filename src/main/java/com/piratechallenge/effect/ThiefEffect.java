package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
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
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Ну и что, что я вор?»
 *  - вокруг жители; на игроке балаклава (клиент: почти нет обзора, F5 запрещён)
 *  - ПКМ по жителю = кража 5 секунд (нужно стоять рядом)
 *  - шкала стелса: жители видят тебя -> растёт; 100 или убийство жителя -> РОЗЫСК
 *  - в розыске приезжают копы (жители, скорость ~2, стреляют, кричат фразы); со временем копов больше
 */
public class ThiefEffect implements TrackEffect {
    public static final String COP_TAG = "pc_cop";
    public static final String ROBBED_TAG = "pc_robbed";
    private static final int STEAL_TICKS = 100; // 5 секунд
    private static final int MAX_CIVIL = 12;
    private static final int MAX_COPS = 12;
    private static final Random RNG = new Random();

    private static final String[] PHRASES = {
            "Прижмитесь к обочине!",
            "Работает полиция! Руки за голову!",
            "Гражданин, остановитесь!",
            "Вы окружены, сдавайтесь!",
            "Предъявите документы!",
            "Не двигаться, это полиция!",
            "Выйдите из машины с поднятыми руками!",
            "Вы задержаны за кражу!",
            "Стоять! Стрелять буду!",
            "Дыхните в трубочку!"
    };

    private record Loot(Item item, int min, int max, int weight) {}

    private static final List<Loot> LOOT = List.of(
            new Loot(Items.BREAD, 1, 3, 30),
            new Loot(Items.CARROT, 2, 5, 20),
            new Loot(Items.WHEAT, 2, 6, 15),
            new Loot(Items.EMERALD, 1, 3, 20),
            new Loot(Items.GOLD_INGOT, 1, 2, 8),
            new Loot(Items.IRON_INGOT, 1, 3, 10),
            new Loot(Items.GOLDEN_APPLE, 1, 1, 3),
            new Loot(Items.DIAMOND, 1, 1, 2));

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.stealth = 0;
        d.wanted = false;
        d.stealTarget = null;
        d.stealTicks = 0;

        if (p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            ItemStack h = EffectUtil.tag(new ItemStack(Items.LEATHER_HELMET));
            h.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0x111111, false));
            h.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Балаклава"));
            p.equipStack(EquipmentSlot.HEAD, h);
        }
        for (int i = 0; i < 8; i++) spawnVillager(p, d);
        p.sendMessage(Text.literal("§7Ты в балаклаве. ПКМ по жителю — украсть (5 сек). Не попадись."), false);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);

        List<VillagerEntity> civ = new ArrayList<>();
        List<VillagerEntity> cops = new ArrayList<>();
        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (e instanceof VillagerEntity v) {
                if (v.getCommandTags().contains(COP_TAG)) cops.add(v);
                else civ.add(v);
            }
        }

        if (elapsed % 60 == 0 && civ.size() < MAX_CIVIL) spawnVillager(p, d);

        updateStealth(p, d, civ);
        updateStealing(p, d, w);
        updatePolice(p, d, w, cops, elapsed);

        if (elapsed % 5 == 0) hud(p, d);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.stealTarget = null;
        EffectUtil.removeTagged(p); // снимаем балаклаву
    }

    // ---------------------------------------------------------------- стелс

    private void updateStealth(ServerPlayerEntity p, PlayerData d, List<VillagerEntity> civ) {
        if (d.wanted) return;
        boolean sneaking = p.isSneaking();
        boolean sprinting = p.isSprinting();
        double radius = sneaking ? 4 : (sprinting ? 12 : 8);
        double per = sneaking ? 0.12 : (sprinting ? 0.9 : 0.35);
        if (d.stealTarget != null) per *= 2.0; // воровство заметнее

        int witnesses = 0;
        for (VillagerEntity v : civ) {
            if (v.squaredDistanceTo(p) <= radius * radius && v.canSee(p)) witnesses++;
        }
        if (witnesses > 0) {
            d.stealth = Math.min(100, d.stealth + per * (1 + 0.15 * (witnesses - 1)));
        } else {
            d.stealth = Math.max(0, d.stealth - 0.5);
        }
        if (d.stealth >= 100) callPolice(p, d);
    }

    // ---------------------------------------------------------------- кража

    public static boolean onUse(ServerPlayerEntity p, Entity e) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.THIEF || !(e instanceof VillagerEntity v)) return false;
        if (v.getCommandTags().contains(COP_TAG)) return true;
        if (v.getCommandTags().contains(ROBBED_TAG)) {
            EffectUtil.bar(p, "§7Этого жителя ты уже обокрал.");
            return true;
        }
        if (d.stealTarget != null) return true;
        d.stealTarget = v.getUuid();
        d.stealTicks = 0;
        return true; // блокируем окно торговли
    }

    private void updateStealing(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        if (d.stealTarget == null) return;
        Entity t = w.getEntity(d.stealTarget);
        if (t == null || !t.isAlive() || p.distanceTo(t) > 4.5) {
            d.stealTarget = null;
            d.stealTicks = 0;
            EffectUtil.bar(p, "§cКража сорвана!");
            return;
        }
        if (++d.stealTicks >= STEAL_TICKS) finishSteal(p, d, t);
    }

    private void finishSteal(ServerPlayerEntity p, PlayerData d, Entity victim) {
        int total = LOOT.stream().mapToInt(Loot::weight).sum();
        int roll = RNG.nextInt(total);
        Loot pick = LOOT.get(0);
        for (Loot l : LOOT) {
            roll -= l.weight();
            if (roll < 0) { pick = l; break; }
        }
        int count = pick.min() + RNG.nextInt(pick.max() - pick.min() + 1);
        ItemStack stack = new ItemStack(pick.item(), count);
        p.getInventory().offerOrDrop(stack);
        p.sendMessage(Text.literal("§aУкрадено: §f" + count + " × ").append(stack.getName()), false);

        victim.addCommandTag(ROBBED_TAG);
        d.stealTarget = null;
        d.stealTicks = 0;
        d.stealth = Math.min(100, d.stealth + 10);
        if (d.stealth >= 100) callPolice(p, d);
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.THIEF) return;
        d.spawned.remove(victim.getUuid());
        if (!d.wanted) {
            p.sendMessage(Text.literal("§cУбийство! Полиция уже в пути!"), false);
            new ThiefEffect().callPolice(p, d);
        }
    }

    // ---------------------------------------------------------------- полиция

    private void callPolice(ServerPlayerEntity p, PlayerData d) {
        if (d.wanted) return;
        d.wanted = true;
        d.stealth = 100;
        d.nextCop = 200;
        d.stealTarget = null;
        d.stealTicks = 0;
        p.sendMessage(Text.literal("§c§lВАС СПАЛИЛИ! ПОЛИЦИЯ ВЫЕХАЛА!"), false);
        spawnCop(p, d);
        spawnCop(p, d);
    }

    private void updatePolice(ServerPlayerEntity p, PlayerData d, ServerWorld w, List<VillagerEntity> cops, int elapsed) {
        if (!d.wanted) return;

        // чем дольше в розыске, тем больше копов
        if (--d.nextCop <= 0) {
            d.nextCop = 200;
            if (cops.size() < MAX_COPS) spawnCop(p, d);
        }

        for (VillagerEntity cop : cops) {
            Vec3d dir = p.getPos().subtract(cop.getPos());
            double flatDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (flatDist > 2.5) {
                Vec3d f = new Vec3d(dir.x, 0, dir.z).normalize().multiply(0.3); // скорость ~2
                double vy = (cop.horizontalCollision && cop.isOnGround()) ? 0.42 : cop.getVelocity().y;
                cop.setVelocity(f.x, vy, f.z);
            }
            cop.lookAtEntity(p, 30f, 30f);

            if (flatDist < 24 && (cop.age + cop.getId()) % 40 == 0 && cop.canSee(p)) shoot(w, cop, p);
        }

        if (elapsed % 80 == 0 && !cops.isEmpty()) {
            String phrase = PHRASES[RNG.nextInt(PHRASES.length)];
            p.sendMessage(Text.literal("§9[Полиция] §f" + phrase), false);
        }
    }

    private void shoot(ServerWorld w, VillagerEntity cop, ServerPlayerEntity p) {
        Vec3d from = cop.getEyePos();
        Vec3d to = p.getPos().add(0, p.getHeight() * 0.6, 0);
        for (int i = 0; i <= 6; i++) {
            Vec3d pt = from.lerp(to, i / 6.0);
            w.spawnParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        w.playSound(null, cop.getX(), cop.getY(), cop.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.2f);
        if (RNG.nextBoolean()) { // 50% попаданий
            p.damage(p.getDamageSources().mobAttack(cop), 2.5f);
        }
    }

    // ---------------------------------------------------------------- спавн

    private void spawnVillager(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return;
        place(p, w, v, 5, 12);
        v.addCommandTag(NataliaEffect.TAG); // «жертва» (тот же тег, что и в Наталии)
        v.setPersistent();
        w.spawnEntity(v);
        d.spawned.add(v.getUuid());
    }

    private void spawnCop(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return;
        place(p, w, v, 18, 26);
        v.addCommandTag(COP_TAG);
        v.setCustomName(Text.literal("§9Полицейский"));
        v.setCustomNameVisible(true);
        v.setPersistent();
        w.spawnEntity(v);
        d.spawned.add(v.getUuid());
    }

    private void place(ServerPlayerEntity p, ServerWorld w, Entity e, double minD, double maxD) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = minD + RNG.nextDouble() * (maxD - minD);
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        e.refreshPositionAndAngles(x, y, z, 0f, 0f);
    }

    // ---------------------------------------------------------------- шкала

    private void hud(ServerPlayerEntity p, PlayerData d) {
        int filled = (int) Math.round(d.stealth / 5.0);
        String color = d.stealth < 40 ? "§a" : (d.stealth < 75 ? "§e" : "§c");
        StringBuilder sb = new StringBuilder("§fСтелс: ").append(color).append("█".repeat(filled))
                .append("§8").append("█".repeat(20 - filled));
        if (d.wanted) sb.append(" §c§lРОЗЫСК");
        if (d.stealTarget != null) sb.append(" §eКража: ").append(d.stealTicks * 100 / STEAL_TICKS).append("%");
        EffectUtil.bar(p, sb.toString());
    }
}
