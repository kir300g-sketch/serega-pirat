package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.ChallengeSave;
import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;
import java.util.UUID;

/**
 * «Гей»: санкция на 3 трека вперёд (сатира на ст. 6.21 КоАП РФ) + погоня «настоящих мужиков».
 * Спрятаться можно: мужики преследуют только тех, кого видят (в приседе — только вблизи),
 * потеряв тебя из виду на 3 секунды, они отстают. В чат они пишут свои «мужицкие» реплики.
 */
public class GeyEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int LOSE_TICKS = 60;

    private static final String[] TAUNTS = {
            "Ты не мужик!",
            "Мужики так не ходят!",
            "Настоящие мужики не танцуют!",
            "Мы тебя научим родину любить!",
            "А ну иди сюда, объясним, как положено!",
            "Где твоя борода?!",
            "Мужики, он там! Окружай!",
            "Стой! Мы за традиционные ценности!",
            "Прячешься? Мужик бы не прятался!"
    };

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.sanctionTracks = 3;
        ChallengeSave.get(p.getServer()).markDirty();
        p.sendMessage(Text.literal("§c[РФ, ст. 6.21] На вас наложена санкция на 3 трека вперёд: максимум здоровья −2 сердца, копание медленнее."), false);
        p.sendMessage(Text.literal("§7Прячься от «настоящих мужиков»: за блоками они теряют тебя из виду. В приседе тебя замечают только вблизи."), false);
        for (int i = 0; i < 4; i++) spawnMan(p, d);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);

        int cap = Math.min(10, 4 + elapsed * 6 / Math.max(1, total));
        if (elapsed % 160 == 0 && d.spawned.size() < cap) spawnMan(p, d);

        boolean sneaking = p.isSneaking();
        double radius = sneaking ? 8 : 26;
        MobEntity shouter = null;

        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (!(e instanceof MobEntity m)) continue;
            double dist = m.distanceTo(p);
            boolean sees = dist < radius && m.canSee(p);
            if (sees) {
                d.eat.put(id, 0);
                if (m.getTarget() != p) m.setTarget(p);
                if (shouter == null && dist < 22) shouter = m;
            } else if (m.getTarget() != null) {
                int lost = d.eat.merge(id, 1, Integer::sum);
                if (lost >= LOSE_TICKS) {
                    m.setTarget(null);
                    m.getNavigation().stop();
                    d.eat.put(id, 0);
                }
            }
        }

        if (shouter != null && elapsed % 70 == 0) {
            p.sendMessage(Text.literal("§7[Настоящий мужик] §f" + TAUNTS[RNG.nextInt(TAUNTS.length)]), false);
        }
    }

    private void spawnMan(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        Entity e = EntityType.VINDICATOR.create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 14 + RNG.nextDouble() * 8;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, z, 0f, 0f);
        m.setCustomName(Text.literal("Настоящий мужик"));
        m.setCustomNameVisible(true);
        EntityAttributeInstance dmg = m.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(4.0);
        m.setPersistent();
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.sendMessage(Text.literal("§7Санкции действуют ещё на " + d.sanctionTracks + " трека."), false);
    }
}
