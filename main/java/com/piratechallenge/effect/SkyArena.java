package com.piratechallenge.effect;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Временные арены в небе над игроком (не трогают рельеф). После трека область очищается. */
public final class SkyArena {
    /** Строит площадку (2*half+1)^2 с бортами; возвращает центр на уровне пола. */
    public static BlockPos build(ServerWorld w, BlockPos around, int half, int wallH, BlockState floor, BlockState wall) {
        int y = Math.min(around.getY() + 50, w.getTopY() - wallH - 8);
        y = Math.max(y, w.getBottomY() + 10);
        BlockPos c = new BlockPos(around.getX(), y, around.getZ());
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                for (int dy = 1; dy <= wallH + 6; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), (edge && dy <= wallH) ? wall : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                w.setBlockState(c.add(dx, 0, dz), floor, Block.NOTIFY_LISTENERS);
            }
        }
        return c;
    }

    public static void clear(ServerWorld w, BlockPos c, int half, int wallH) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = -1; dy <= wallH + 8; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private SkyArena() {}
}
