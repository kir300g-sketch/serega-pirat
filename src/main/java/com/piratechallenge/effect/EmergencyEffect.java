package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;

/**
 * 911: арена смерти в небе. Игрок получает имбовый сет (незеритовая броня, меч, лук, еда).
 * Сражается 1 на 1 с бронированными монстрами по очереди; с каждой победой враг сильнее.
 * Прежняя броня игрока сохраняется и возвращается в конце.
 */
public class EmergencyEffect implements TrackEffect {
    private static final int HALF = 10;
    private static final Random RNG = new Random();

    private static final List<EntityType<?>> FIGHTERS = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.STRAY, EntityType.VINDICATOR,
            EntityType.PILLAGER, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN, EntityType.DROWNED);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.returnPos = p.getPos();
        d.arenaHalf = HALF;
        d.arenaH = 7;
        d.arenaCenter = SkyArena.build(w, p.getBlockPos(), HALF, 7,
                Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.BLACKSTONE.getDefaultState());
        BlockPos c = d.arenaCenter;
        for (int[] cr : new int[][]{{-HALF + 1, -HALF + 1}, {HALF - 1, -HALF + 1}, {-HALF + 1, HALF - 1}, {HALF - 1, HALF - 1}}) {
            w.setBlockState(c.add(cr[0], 1, cr[1]), Blocks.SEA_LANTERN.getDefaultState(), 2);
        }

        // сохраняем прежнюю экипировку
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
        for (int i = 0; i < 5; i++) {
            d.saved[i] = p.getEquippedStack(slots[i]).copy();
            p.equipStack(slots[i], ItemStack.EMPTY);
        }

        // имба-сет
        p.equipStack(EquipmentSlot.HEAD, armor(w, Items.NETHERITE_HELMET));
        p.equipStack(EquipmentSlot.CHEST, armor(w, Items.NETHERITE_CHESTPLATE));
        p.equipStack(EquipmentSlot.LEGS, armor(w, Items.NETHERITE_LEGGINGS));
        p.equipStack(EquipmentSlot.FEET, armor(w, Items.NETHERITE_BOOTS));
        ItemStack shield = EffectUtil.tag(new ItemStack(Items.SHIELD));
        ench(w, shield, Enchantments.UNBREAKING, 3);
        p.equipStack(EquipmentSlot.OFFHAND, shield);

        ItemStack sword = EffectUtil.tag(new ItemStack(Items.NETHERITE_SWORD));
        ench(w, sword, Enchantments.SHARPNESS, 5);
        ench(w, sword, Enchantments.FIRE_ASPECT, 2);
        ench(w, sword, Enchantments.UNBREAKING, 3);
        p.getInventory().offerOrDrop(sword);

        ItemStack bow = EffectUtil.tag(new ItemStack(Items.BOW));
        ench(w, bow, Enchantments.POWER, 5);
        ench(w, bow, Enchantments.UNBREAKING, 3);
        p.getInventory().offerOrDrop(bow);
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.ARROW, 64)));

        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4)));
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.GOLDEN_APPLE, 24)));
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.COOKED_BEEF, 32)));

        p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - 5.5);
        d.kills = 0;
        d.target = null;
        d.duelWait = 80;
        p.sendMessage(Text.literal("§cАрена смерти! Бой один на один. Победи как можно больше."), false);
    }

    private ItemStack armor(ServerWorld w, net.minecraft.item.Item item) {
        ItemStack s = EffectUtil.tag(new ItemStack(item));
        ench(w, s, Enchantments.PROTECTION, 4);
        ench(w, s, Enchantments.UNBREAKING, 3);
        return s;
    }

    private void ench(ServerWorld w, ItemStack s, RegistryKey<Enchantment> key, int level) {
        s.addEnchantment(w.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key), level);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = d.arenaCenter;
        if (c == null) return;

        // не даём покинуть арену
        if (p.getY() < c.getY() - 3 || Math.abs(p.getX() - c.getX()) > HALF || Math.abs(p.getZ() - c.getZ()) > HALF) {
            p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - 5.5);
        }

        Entity opp = d.target == null ? null : w.getEntity(d.target);
        if (d.target != null && (opp == null || !opp.isAlive())) {
            d.target = null;
            d.kills++;
            d.duelWait = 100;
            p.heal(8.0f);
            p.sendMessage(Text.literal("§aПобеда #" + d.kills + "! Следующий бой через 5 секунд."), true);
            return;
        }
        if (d.target == null) {
            if (--d.duelWait <= 0) spawnOpponent(p, w, c, d);
            return;
        }
        if (elapsed % 20 == 0 && opp instanceof MobEntity m) m.setTarget(p);
    }

    private void spawnOpponent(ServerPlayerEntity p, ServerWorld w, BlockPos c, PlayerData d) {
        Entity e = FIGHTERS.get(RNG.nextInt(FIGHTERS.size())).create(w);
        if (!(e instanceof MobEntity m)) return;
        int n = d.kills;
        m.refreshPositionAndAngles(c.getX() + 0.5, c.getY() + 1, c.getZ() + 5.5, 180f, 0f);

        // броня растёт с победами: кожа -> кольчуга -> железо -> алмаз -> незерит
        net.minecraft.item.Item[][] tiers = {
                {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
                {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
                {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
                {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}};
        int tier = Math.min(4, n / 2);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            m.equipStack(slots[i], new ItemStack(tiers[tier][i]));
            m.setEquipmentDropChance(slots[i], 0f);
        }
        if (!(e.getType() == EntityType.SKELETON || e.getType() == EntityType.STRAY || e.getType() == EntityType.PILLAGER)) {
            m.equipStack(EquipmentSlot.MAINHAND, new ItemStack(tier >= 3 ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
            m.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        }
        EntityAttributeInstance hp = m.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(20 + n * 4);
            m.setHealth((float) (20 + n * 4));
        }
        m.setCustomName(Text.literal("§cБоец #" + (n + 1)));
        m.setCustomNameVisible(true);
        m.setPersistent();
        m.setTarget(p);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
        d.target = m.getUuid();
        EffectUtil.bar(p, "§cБой #" + (n + 1) + "!");
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.removeTagged(p);                         // убираем выданный сет (и броню, и меч, и еду)
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            if (EffectUtil.isTagged(p.getEquippedStack(s))) p.equipStack(s, ItemStack.EMPTY);
        }
        EffectUtil.restoreSaved(p, d);                      // возвращаем прежнюю экипировку
        if (d.arenaCenter != null) SkyArena.clear(p.getServerWorld(), d.arenaCenter, d.arenaHalf, d.arenaH);
        if (p.isAlive() && d.returnPos != null) {
            Vec3d r = d.returnPos;
            p.requestTeleport(r.x, r.y, r.z);
            p.sendMessage(Text.literal("§7Побед на арене: " + d.kills), false);
        }
    }
}
