package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import com.piratechallenge.ChallengeManager;
import com.piratechallenge.Track;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Огромный игрок (scale до 11 ~ 20 блоков). Приседай, чтобы качаться: каждое приседание +10 к форме,
 * форма постоянно падает. Форма 0 дольше 3 сек = смерть. Размер зависит от формы.
 */
public class GymEffect implements TrackEffect {
    private static final double MAX_SCALE = 11.0; // ~20 блоков. Максимум в игре = 16.
    private static final double MIN_SCALE = 0.5;
    private static boolean breaking;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.pump = 100;
        EffectUtil.bar(p, "§6Приседай, чтобы качаться!");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        boolean sn = p.isSneaking();
        if (sn && !d.wasSneaking) d.pump = Math.min(100, d.pump + 10);
        d.wasSneaking = sn;
        if (elapsed % 6 == 0) d.pump = Math.max(0, d.pump - 1);

        double s = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * d.pump / 100.0;
        if (Math.abs(s - d.lastScale) > 0.05) {
            d.lastScale = s;
            applyScale(p, s);
        }

        if (d.pump <= 0) {
            if (++d.weakTicks >= 60) {
                p.damage(p.getDamageSources().genericKill(), Float.MAX_VALUE);
                return;
            }
        } else {
            d.weakTicks = 0;
        }

        if (elapsed % 10 == 0) {
            int bars = (int) Math.round(d.pump / 10);
            String bar = "§a" + "█".repeat(bars) + "§7" + "█".repeat(10 - bars);
            EffectUtil.bar(p, "Форма: " + bar + " §f" + (int) d.pump + "%" + (d.pump <= 0 ? " §c ТЫ ХЛЮПИК!" : ""));
        }
    }

    private static void applyScale(ServerPlayerEntity p, double s) {
        double extra = s - 1.0;
        EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("gym_scale"), extra, Operation.ADD_MULTIPLIED_BASE);
        EffectUtil.ensure(p, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, EffectUtil.id("gym_reach"), extra * 1.5, Operation.ADD_VALUE);
        EffectUtil.ensure(p, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, EffectUtil.id("gym_reach_e"), extra * 1.5, Operation.ADD_VALUE);
        EffectUtil.ensure(p, EntityAttributes.GENERIC_STEP_HEIGHT, EffectUtil.id("gym_step"), extra * 0.5, Operation.ADD_VALUE);
        EffectUtil.ensure(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, EffectUtil.id("gym_fall"), extra * 3, Operation.ADD_VALUE);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("gym_scale"));
        EffectUtil.remove(p, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, EffectUtil.id("gym_reach"));
        EffectUtil.remove(p, EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, EffectUtil.id("gym_reach_e"));
        EffectUtil.remove(p, EntityAttributes.GENERIC_STEP_HEIGHT, EffectUtil.id("gym_step"));
        EffectUtil.remove(p, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, EffectUtil.id("gym_fall"));
    }

    /** Один сломанный блок = куб 10x10x10 (меняй размеры тут). */
    public static void onBreak(ServerWorld w, ServerPlayerEntity p, BlockPos origin) {
        if (breaking || ChallengeManager.current(p) != Track.KACHALKA) return;
        breaking = true;
        try {
            for (BlockPos pos : BlockPos.iterate(origin.add(-5, -5, -5), origin.add(4, 4, 4))) {
                BlockPos bp = pos.toImmutable();
                BlockState s = w.getBlockState(bp);
                if (s.isAir() || s.getHardness(w, bp) < 0 || !s.getFluidState().isEmpty()) continue;
                w.breakBlock(bp, true, p);
            }
        } finally {
            breaking = false;
        }
    }
}
