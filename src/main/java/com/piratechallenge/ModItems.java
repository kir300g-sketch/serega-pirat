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

    public static void register() {
        RADIANCE = Registry.register(Registries.ITEM, Identifier.of(PirateChallenge.MOD_ID, "radiance"),
                new RadianceItem(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(RADIANCE));
    }

    private ModItems() {}
}
