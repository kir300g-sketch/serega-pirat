package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * «Извини, сегодня праздник»: паркур по крышам хрущёвок высоко в небе (вдоль оси +X).
 * Крыши нельзя сломать (см. PirateChallenge), стоять нельзя: дома рушатся позади игрока
 * («фронт обрушения» ускоряется), а чем дальше — тем шире разрывы, короче крыши и больше препятствий.
 * Упал вниз — −3 сердца и возврат на крышу впереди обрушения.
 */
public class HrushEffect implements TrackEffect {
    private static final int HALF_W = 5;     // половина ширины здания
    private static final int BODY_DEPTH = 9; // сколько блоков здание уходит вниз
    private static final Random RNG = new Random();

    private static final BlockState ROOF = Blocks.GRAY_CONCRETE.getDefaultState();
    private static final BlockState WALL = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    private static final BlockState WINDOW = Blocks.BLACK_STAINED_GLASS.getDefaultState();
    private static final BlockState VENT = Blocks.STONE_BRICKS.getDefaultState();

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.returnPos = p.getPos();
        int baseY = Math.min(p.getBlockY() + 60, w.getTopY() - 30);
        baseY = Math.max(baseY, w.getBottomY() + 40);

        d.pkStartX = p.getBlockX();
        d.pkZ = p.getBlockZ();
        d.pkBaseY = baseY;
        d.pkRoofY = baseY;
        d.pkCount = 0;
        d.pkFront = d.pkStartX - 12;
        d.pkCleared = d.pkStartX - 14;
        d.pkMaxX = d.pkStartX;
        d.roofs.clear();

        // стартовая крыша: длинная и без препятствий
        build(w, d, d.pkStartX, 22, baseY, 0);
        d.pkNextX = d.pkStartX + 22 + 2;
        ensureAhead(w, d, d.pkStartX + 90);

        p.requestTeleport(d.pkStartX + 3.5, baseY + 1.0, d.pkZ + 0.5);
        p.setYaw(-90f); // смотрим на +X
        p.sendMessage(Text.literal("§6Бегом по крышам! Дома рушатся позади тебя — стоять нельзя."), false);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        double progress = elapsed / (double) Math.max(1, total);

        ensureAhead(w, d, (int) p.getX() + 90);

        // фронт обрушения ускоряется: ~1 → ~2.6 блока/сек
        d.pkFront += 0.05 + 0.08 * progress;
        while (d.pkCleared < (int) d.pkFront) {
            collapseSlice(w, d, ++d.pkCleared);
        }

        double gap = p.getX() - d.pkFront;
        if (elapsed % 10 == 0) {
            if (gap < 7) EffectUtil.bar(p, "§c§lДОМ РУШИТСЯ ПОЗАДИ! БЕГИ!");
            else EffectUtil.bar(p, "§6Крыш пройдено: " + Math.max(0, d.pkCount - 3));
        }

        // упал вниз
        if (p.getY() < d.pkBaseY - 14) {
            p.damage(p.getDamageSources().magic(), 6.0f);
            respawn(p, d);
        }
    }

    private void respawn(ServerPlayerEntity p, PlayerData d) {
        for (int[] r : d.roofs) {
            int x0 = r[0], len = r[1], y = r[2];
            if (x0 + len - 1 > d.pkFront + 8) {
                double x = Math.max(x0 + 1, d.pkFront + 8);
                x = Math.min(x, x0 + len - 2);
                p.requestTeleport(x + 0.5, y + 1.0, d.pkZ + 0.5);
                p.fallDistance = 0;
                p.setVelocity(Vec3d.ZERO);
                p.velocityModified = true;
                EffectUtil.bar(p, "§cТы упал! −3 сердца");
                return;
            }
        }
    }

    // ---------------------------------------------------------------- строительство

    /** Достраиваем здания вперёд до нужной координаты. Сложность растёт с номером здания. */
    private void ensureAhead(ServerWorld w, PlayerData d, int untilX) {
        while (d.pkNextX < untilX) {
            double diff = Math.min(1.0, d.pkCount / 22.0);
            int gap = 2 + (int) Math.round(diff * 3) + (RNG.nextInt(3) == 0 ? 1 : 0); // 2..6
            int len = 16 - (int) Math.round(diff * 9) + RNG.nextInt(3);               // 16 -> ~7
            int dy = RNG.nextInt(4) - 2;                                               // -2..+1
            if (dy > 1) dy = 1;
            int y = d.pkRoofY + dy;
            y = Math.max(d.pkBaseY - 8, Math.min(d.pkBaseY + 8, y));
            int obstacles = (int) Math.round(diff * 5);

            build(w, d, d.pkNextX, Math.max(6, len), y, obstacles);
            d.pkNextX += Math.max(6, len) + gap;
        }
    }

    private void build(ServerWorld w, PlayerData d, int x0, int len, int roofY, int obstacles) {
        int z0 = d.pkZ;
        for (int x = x0; x < x0 + len; x++) {
            for (int dz = -HALF_W; dz <= HALF_W; dz++) {
                BlockPos roof = new BlockPos(x, roofY, z0 + dz);
                w.setBlockState(roof, ROOF, Block.NOTIFY_LISTENERS);
                boolean edge = dz == -HALF_W || dz == HALF_W || x == x0 || x == x0 + len - 1;
                for (int dy = 1; dy <= BODY_DEPTH; dy++) {
                    boolean window = edge && ((x + dz) & 1) == 0 && dy % 3 != 0 && dy < BODY_DEPTH;
                    w.setBlockState(roof.down(dy), window ? WINDOW : WALL, Block.NOTIFY_LISTENERS);
                }
                // воздух над крышей (на случай мусора в небе)
                for (int up = 1; up <= 4; up++) {
                    w.setBlockState(roof.up(up), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        // препятствия: вентшахты на крыше
        for (int i = 0; i < obstacles; i++) {
            int ox = x0 + 3 + RNG.nextInt(Math.max(1, len - 6));
            int oz = z0 - HALF_W + 1 + RNG.nextInt(2 * HALF_W - 1);
            w.setBlockState(new BlockPos(ox, roofY + 1, oz), VENT, Block.NOTIFY_LISTENERS);
            if (RNG.nextInt(3) == 0) w.setBlockState(new BlockPos(ox, roofY + 2, oz), VENT, Block.NOTIFY_LISTENERS);
        }
        d.roofs.add(new int[]{x0, len, roofY});
        d.pkRoofY = roofY;
        d.pkMaxX = Math.max(d.pkMaxX, x0 + len);
        d.pkCount++;
    }

    private void collapseSlice(ServerWorld w, PlayerData d, int x) {
        int y0 = d.pkBaseY - BODY_DEPTH - 12;
        int y1 = d.pkBaseY + 14;
        for (int dz = -HALF_W - 1; dz <= HALF_W + 1; dz++) {
            for (int y = y0; y <= y1; y++) {
                BlockPos pos = new BlockPos(x, y, d.pkZ + dz);
                if (!w.getBlockState(pos).isAir()) w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        if (x % 3 == 0) {
            w.spawnParticles(ParticleTypes.CLOUD, x + 0.5, d.pkRoofY + 1, d.pkZ + 0.5, 12, 0.5, 1.5, 3.0, 0.02);
            w.playSound(null, x + 0.5, d.pkRoofY, d.pkZ + 0.5, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.5f, 0.6f);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        int y0 = d.pkBaseY - BODY_DEPTH - 12;
        int y1 = d.pkBaseY + 14;
        for (int x = d.pkCleared + 1; x <= d.pkMaxX + 2; x++) {
            for (int dz = -HALF_W - 1; dz <= HALF_W + 1; dz++) {
                for (int y = y0; y <= y1; y++) {
                    BlockPos pos = new BlockPos(x, y, d.pkZ + dz);
                    if (!w.getBlockState(pos).isAir()) w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        p.setNoGravity(false);
        if (p.isAlive() && d.returnPos != null) {
            Vec3d r = d.returnPos;
            p.requestTeleport(r.x, r.y, r.z);
            p.fallDistance = 0;
        }
    }
}
