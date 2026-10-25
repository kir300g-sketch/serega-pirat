package com.piratechallenge;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static Item RADIANCE;
    public static Item REVOLVER;
    public static Item SEREGA_PLAYER;

    public static void register() {
        RADIANCE = reg("radiance", new RadianceItem(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
        REVOLVER = reg("revolver", new RevolverItem(new Item.Settings().maxCount(1)));
        SEREGA_PLAYER = reg("serega_player", new SeregaPlayerItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(RADIANCE);
            e.add(REVOLVER);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(SEREGA_PLAYER));
    }

    private static Item reg(String id, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(PirateChallenge.MOD_ID, id), item);
    }

    private ModItems() {}
}
