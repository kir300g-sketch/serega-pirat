package com.piratechallenge;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * После 15 прослушанных треков персонажа душит музыка: если он подходит вплотную к стене (3 блока и выше),
 * он прижимается к ней и затыкает уши — музыка становится заметно тише (клиент снижает громкость).
 * Никакого урона: это способ «спрятаться» от звука.
 */
public final class WallCover {
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static void check(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        BlockPos feet = p.getBlockPos();
        boolean near = false;
        for (int[] dir : DIRS) {
            boolean wall = true;
            for (int dy = 0; dy < 3 && wall; dy++) {
                BlockPos pos = feet.add(dir[0], dy, dir[1]);
                wall = w.getBlockState(pos).isSolidBlock(w, pos);
            }
            if (wall) { near = true; break; }
        }
        if (near != d.coverEars) {
            d.coverEars = near;
            if (near && !d.coverNotified) {
                d.coverNotified = true;
                p.sendMessage(Text.literal("§7Ты прижался к стене и зажал уши. Так музыка хотя бы тише..."), true);
            }
            ChallengeManager.sync(p, d);
        }
    }

    private WallCover() {}
}
