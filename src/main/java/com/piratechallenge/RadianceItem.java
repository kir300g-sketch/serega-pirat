package com.piratechallenge;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Священный Radiance: +55 урона (1 базовый + 54), копает как в креативе, не ломается. */
public class RadianceItem extends SwordItem {
    public RadianceItem(Item.Settings settings) {
        super(ToolMaterials.GOLD, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.GOLD, 54, -2.0f)));
    }

    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        return 1000f;
    }

    @Override
    public boolean isCorrectForDrops(ItemStack stack, BlockState state) {
        return true;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true; // без износа
    }

    @Override
    public boolean postMine(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner) {
        return true; // без износа
    }
}
