package com.piratechallenge.effect;

import com.piratechallenge.ModItems;
import com.piratechallenge.PlayerData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

/**
 * Radiance: +55 урона и копание как в креативе (в самом предмете), 25% уклонение (в PirateChallenge),
 * здесь: скорость VII, прыжок, аура жара 5x5 вокруг игрока.
 */
public class RadianceEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        p.getInventory().offerOrDrop(new ItemStack(ModItems.RADIANCE));
        EffectUtil.bar(p, "§6Вы получили Radiance!");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        if (total - elapsed == 200) {
            EffectUtil.bar(p, "§6Брось Radiance на землю, чтобы сохранить его навсегда!");
        }
        if (!p.getMainHandStack().isOf(ModItems.RADIANCE)) return;

        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 6, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 10, 2, false, false, false));

        if (elapsed % 10 != 0) return;
        ServerWorld w = p.getServerWorld();

        // жар по существам
        Box box = p.getBoundingBox().expand(2.5);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> x != p && x.isAlive())) {
            e.damage(p.getDamageSources().inFire(), 4.0f);
            e.setOnFireFor(4);
        }

        // жар по растениям вокруг
        BlockPos c = p.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(c.add(-2, -1, -2), c.add(2, 3, 2))) {
            BlockState s = w.getBlockState(pos);
            if (s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.FLOWERS) || s.isIn(BlockTags.CROPS)
                    || s.isOf(Blocks.SHORT_GRASS) || s.isOf(Blocks.TALL_GRASS) || s.isOf(Blocks.SNOW)) {
                w.breakBlock(pos.toImmutable(), false);
            }
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(ModItems.RADIANCE)) inv.setStack(i, ItemStack.EMPTY);
        }
        p.removeStatusEffect(StatusEffects.SPEED);
        p.removeStatusEffect(StatusEffects.JUMP_BOOST);

        // Радик, брошенный на землю, спасён: не исчезает и остаётся лежать (без скорости, но с уроном и копанием)
        for (ItemEntity ie : p.getServerWorld().getEntitiesByClass(ItemEntity.class,
                p.getBoundingBox().expand(96), e -> e.getStack().isOf(ModItems.RADIANCE))) {
            ie.setNeverDespawn();
        }
    }
}
