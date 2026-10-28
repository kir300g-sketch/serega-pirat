package com.piratechallenge;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** «Плеер Серёги» — админский предмет. ПКМ открывает меню управления треками. */
public class SeregaPlayerItem extends Item {
    public SeregaPlayerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            if (!PlayerGui.isAdmin(sp)) {
                sp.sendMessage(Text.literal("§cПлеер Серёги — только для админа."), true);
                return TypedActionResult.fail(stack);
            }
            PlayerGui.open(sp);
        }
        return TypedActionResult.success(stack);
    }
}
