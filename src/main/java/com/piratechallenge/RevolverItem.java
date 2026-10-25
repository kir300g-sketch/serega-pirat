package com.piratechallenge;

import com.piratechallenge.effect.CowboyEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Револьвер (ПКМ — выстрел). 6 патронов, потом автоматическая перезарядка. */
public class RevolverItem extends Item {
    public RevolverItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            CowboyEffect.shoot(sp);
        }
        return TypedActionResult.success(stack);
    }
}
