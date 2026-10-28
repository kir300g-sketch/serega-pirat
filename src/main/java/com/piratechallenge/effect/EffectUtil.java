package com.piratechallenge.effect;

import com.piratechallenge.PirateChallenge;
import com.piratechallenge.PlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class EffectUtil {
    public static final String ITEM_TAG = "pirate_challenge_item";
    public static final String SPAWN_TAG = "pc_spawned";

    /** Регистрирует заспавненное существо: помечает тегом (для зачистки) и добавляет в список трека. */
    public static void reg(PlayerData d, Entity e) {
        e.addCommandTag(SPAWN_TAG);
        d.spawned.add(e.getUuid());
    }

    /** Удаляет ВСЕХ существ, помеченных модом, во всех мирах (остатки после выхода из игры и т.п.). */
    public static void sweep(net.minecraft.server.MinecraftServer server) { sweep(server, true); }

    /** includeVisions=false — не трогать видения (Серёги/копии дракона), они живут своими таймерами. */
    public static void sweep(net.minecraft.server.MinecraftServer server, boolean includeVisions) {
        for (ServerWorld w : server.getWorlds()) {
            for (net.minecraft.entity.mob.MobEntity m : w.getEntitiesByType(
                    net.minecraft.util.TypeFilter.instanceOf(net.minecraft.entity.mob.MobEntity.class),
                    e -> e.getCommandTags().contains(SPAWN_TAG)
                            || (includeVisions && (e.getCommandTags().contains("pc_serega") || e.getCommandTags().contains("pc_vision"))))) {
                m.discard();
            }
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(PirateChallenge.MOD_ID, path);
    }

    public static void ensure(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id,
                              double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        EntityAttributeModifier m = inst.getModifier(id);
        if (m != null && m.value() == value) return;
        inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }

    public static void remove(LivingEntity e, RegistryEntry<EntityAttribute> attr, Identifier id) {
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst != null) inst.removeModifier(id);
    }

    /** Помечает предмет как «выданный модом», чтобы забрать его в конце трека. */
    public static ItemStack tag(ItemStack s) {
        NbtCompound n = new NbtCompound();
        n.putBoolean(ITEM_TAG, true);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        return s;
    }

    public static boolean isTagged(ItemStack s) {
        return s.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).contains(ITEM_TAG);
    }

    public static void removeTagged(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isTagged(inv.getStack(i))) inv.setStack(i, ItemStack.EMPTY);
        }
    }

    public static void bar(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal(text), true);
    }

    /**
     * Удаляет ВСЕ выданные модом предметы, которые могли «утечь»: выброшенные на землю в любом месте загруженного мира
     * (в том числе упавшие вниз с арены), а также из эндер-сундука игрока.
     */
    public static void clearTaggedDrops(ServerPlayerEntity p) {
        for (ServerWorld w : p.getServer().getWorlds()) {
            for (net.minecraft.entity.ItemEntity ie : w.getEntitiesByType(
                    net.minecraft.util.TypeFilter.instanceOf(net.minecraft.entity.ItemEntity.class),
                    e -> isTagged(e.getStack()))) {
                ie.discard();
            }
        }
        net.minecraft.inventory.Inventory ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.size(); i++) {
            if (isTagged(ender.getStack(i))) ender.setStack(i, ItemStack.EMPTY);
        }
    }

    /** Случайная броня для моба: каждый слот с шансом 55%, материал случайный (кожа/кольчуга/железо/золото/алмаз). Дроп отключён. */
    public static void randomArmor(net.minecraft.entity.mob.MobEntity m, java.util.Random rng) {
        net.minecraft.item.Item[][] tiers = {
                {net.minecraft.item.Items.LEATHER_HELMET, net.minecraft.item.Items.LEATHER_CHESTPLATE, net.minecraft.item.Items.LEATHER_LEGGINGS, net.minecraft.item.Items.LEATHER_BOOTS},
                {net.minecraft.item.Items.CHAINMAIL_HELMET, net.minecraft.item.Items.CHAINMAIL_CHESTPLATE, net.minecraft.item.Items.CHAINMAIL_LEGGINGS, net.minecraft.item.Items.CHAINMAIL_BOOTS},
                {net.minecraft.item.Items.IRON_HELMET, net.minecraft.item.Items.IRON_CHESTPLATE, net.minecraft.item.Items.IRON_LEGGINGS, net.minecraft.item.Items.IRON_BOOTS},
                {net.minecraft.item.Items.GOLDEN_HELMET, net.minecraft.item.Items.GOLDEN_CHESTPLATE, net.minecraft.item.Items.GOLDEN_LEGGINGS, net.minecraft.item.Items.GOLDEN_BOOTS},
                {net.minecraft.item.Items.DIAMOND_HELMET, net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_BOOTS}};
        int[] weights = {40, 25, 20, 10, 5};
        net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            if (rng.nextInt(100) >= 55) continue;
            int roll = rng.nextInt(100), tier = 0;
            for (int t = 0; t < weights.length; t++) {
                roll -= weights[t];
                if (roll < 0) { tier = t; break; }
            }
            m.equipStack(slots[i], new ItemStack(tiers[tier][i]));
            m.setEquipmentDropChance(slots[i], 0f);
        }
    }

    /** Возвращает сохранённую экипировку (голова, грудь, ноги, ступни, вторая рука). Если игрок мёртв — выбрасывает на место возврата. */
    public static void restoreSaved(ServerPlayerEntity p, PlayerData d) {
        net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.OFFHAND};
        for (int i = 0; i < 5; i++) {
            ItemStack st = d.saved[i];
            if (st == null) continue;
            d.saved[i] = null;
            if (p.isAlive()) {
                p.equipStack(slots[i], st);
            } else if (!st.isEmpty()) {
                net.minecraft.util.math.Vec3d at = d.returnPos != null ? d.returnPos : p.getPos();
                p.getServerWorld().spawnEntity(new net.minecraft.entity.ItemEntity(p.getServerWorld(), at.x, at.y + 0.5, at.z, st));
            }
        }
    }

    /** Убирает из списка спавна мёртвых/исчезнувших существ (иначе лимит спавна забивается). */
    public static void prune(ServerWorld w, PlayerData d) {
        d.spawned.removeIf(id -> {
            Entity e = w.getEntity(id);
            return e == null || !e.isAlive();
        });
    }

    /** Удаляет всех существ, заспавненных треком (фантомы, хейтеры, жители...). */
    public static void discardSpawned(ServerPlayerEntity p, PlayerData d) {
        for (UUID id : d.spawned) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) {
                    e.discard();
                    break;
                }
            }
        }
        d.spawned.clear();
    }

    private EffectUtil() {}
}
