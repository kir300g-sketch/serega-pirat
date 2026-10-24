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
