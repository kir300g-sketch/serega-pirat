package com.piratechallenge.effect;

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
 * «Настоящие мужики»: преследуют игрока, пока на нём санкции (трек «Гей» и 3 следующих трека).
 * Преследуют только тех, кого видят (в приседе — только вблизи); потеряв из виду на 3 секунды — отстают.
 */
public final class Men {
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

    /** Можно ли «накладывать» мужиков поверх этого трека (в аренах и мини-играх — нет). */
    public static boolean allowedOn(com.piratechallenge.Track t) {
        return switch (t) {
            case LASTPICK, N911, HRUSH, FANAT, FLAPPY, TILT -> false;
            default -> true;
        };
    }

    public static void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total, int maxCap) {
        ServerWorld w = p.getServerWorld();
        d.men.removeIf(id -> {
            Entity e = w.getEntity(id);
            return e == null || !e.isAlive();
        });

        int cap = Math.min(maxCap, 3 + elapsed * (maxCap - 3) / Math.max(1, total));
        if ((elapsed == 5 || elapsed % 160 == 0) && d.men.size() < cap) spawn(p, d);
        if (elapsed == 5) {
            p.sendMessage(Text.literal("§7Санкции: за тобой охотятся «настоящие мужики». Прячься за блоками!"), false);
        }

        boolean sneaking = p.isSneaking();
        double radius = sneaking ? 8 : 26;
        MobEntity shouter = null;

        for (UUID id : d.men) {
            Entity e = w.getEntity(id);
            if (!(e instanceof MobEntity m)) continue;
            double dist = m.distanceTo(p);
            boolean sees = dist < radius && m.canSee(p);
            if (sees) {
                d.menLost.put(id, 0);
                if (m.getTarget() != p) m.setTarget(p);
                if (shouter == null && dist < 22) shouter = m;
            } else if (m.getTarget() != null) {
                int lost = d.menLost.merge(id, 1, Integer::sum);
                if (lost >= LOSE_TICKS) {
                    m.setTarget(null);
                    m.getNavigation().stop();
                    d.menLost.put(id, 0);
                }
            }
        }

        if (shouter != null && elapsed % 70 == 0) {
            p.sendMessage(Text.literal("§7[Настоящий мужик] §f" + TAUNTS[RNG.nextInt(TAUNTS.length)]), false);
        }
    }

    public static void spawn(ServerPlayerEntity p, PlayerData d) {
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
        e.addCommandTag(EffectUtil.SPAWN_TAG);
        w.spawnEntity(m);
        d.men.add(m.getUuid());
    }

    public static void clear(ServerPlayerEntity p, PlayerData d) {
        for (UUID id : d.men) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) {
                    e.discard();
                    break;
                }
            }
        }
        d.men.clear();
        d.menLost.clear();
    }

    private Men() {}
}
