package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;
import java.util.Random;

/**
 * «Тильт»: персонаж в тильте и отказывается играть (клиент блокирует ввод, экран TiltScreen).
 * При попытках его расшевелить он отвечает фразами. После ATTEMPTS_TO_SNAP попыток он говорит «я сам» и
 * до конца песни играет в Майнкрафт без игрока: идёт к деревьям, рубит их, бьёт мобов, ест, бродит.
 */
public class TiltEffect implements TrackEffect {
    private static final int ATTEMPTS_TO_SNAP = 8;
    private static final Random RNG = new Random();

    private static final String[] REFUSE = {
            "Не хочу.", "Отстань, я тильтую.", "Не-а. Я больше не играю.", "Всё, я выключаюсь.",
            "Мне нужен перерыв. Навсегда.", "Нет. Нет. И ещё раз нет.", "Ты видел, как я умирал? Я не буду.",
            "Это не мой скилл, это сервер лагает!", "Иди сам играй, если такой умный.",
            "Я сижу. Мне хорошо. Не трогай."
    };
    private static final String[] PLEAD = {
            "Ну пожалуйста... — Нет!", "Да сколько можно меня дёргать?!", "Я сказал: не трогай!",
            "Ещё раз нажмёшь — я за себя не отвечаю.", "Ладно, ещё одно нажатие и я сам всё сделаю."
    };
    private static final String[] SNAP = {
            "ДА ПОШЛО ОНО ВСЁ! Я САМ ПОИГРАЮ, БЕЗ ТЕБЯ!",
            "Всё, ты меня достал. Теперь я за главного."
    };
    private static final String[] BOT = {
            "Ну вот, дерево. Скучно, но зато без тебя.", "Так, а где у нас тут дерево покрупнее?",
            "Я и без тебя неплохо играю, а?", "Хм, опять зомби. Сейчас разберёмся.",
            "Тильт прошёл. Просто теперь я один.", "Не мешай, я строю империю. Из брёвен."
    };

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.tiltPhase = 0;
        d.tiltAttempts = 0;
        d.tiltLastAttempt = 0;
        d.botTarget = null;
        p.sendMessage(Text.literal("§7[Персонаж] §fВсё. Я тильтанул. Больше не играю."), false);
    }

    /** Попытка игрока расшевелить персонажа (нажатие клавиши/клик в TiltScreen). */
    public static void onAttempt(ServerPlayerEntity p) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.TILT) return;
        if (p.age - d.tiltLastAttempt < 15) return; // не чаще 3 раз в секунду
        d.tiltLastAttempt = p.age;

        if (d.tiltPhase == 0) {
            d.tiltAttempts++;
            String line;
            if (d.tiltAttempts >= ATTEMPTS_TO_SNAP) {
                d.tiltPhase = 1;
                line = SNAP[RNG.nextInt(SNAP.length)];
                p.sendMessage(Text.literal("§c[Персонаж] §f" + line), false);
                p.sendMessage(Text.literal("§7Персонаж играет сам до конца песни."), true);
                return;
            }
            line = d.tiltAttempts >= ATTEMPTS_TO_SNAP - 3
                    ? PLEAD[RNG.nextInt(PLEAD.length)]
                    : REFUSE[RNG.nextInt(REFUSE.length)];
            p.sendMessage(Text.literal("§7[Персонаж] §f" + line), false);
        } else {
            p.sendMessage(Text.literal("§7[Персонаж] §fНе трогай, я играю!"), true);
        }
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();

        if (d.tiltPhase == 0) {
            // стоит и дуется; иногда бурчит сам
            p.setVelocity(0, p.getVelocity().y, 0);
            if (elapsed > 0 && elapsed % 500 == 0) {
                p.sendMessage(Text.literal("§7[Персонаж] §f" + REFUSE[RNG.nextInt(REFUSE.length)]), false);
            }
            return;
        }

        // ---- автопилот: «персонаж играет сам»
        // еда
        if (elapsed % 100 == 0 && p.getHungerManager().getFoodLevel() < 14) {
            p.getHungerManager().add(6, 0.8f);
            p.sendMessage(Text.literal("§7[Персонаж] §f*жуёт что-то из кармана*"), true);
        }
        // бой: ближайший враг рядом
        Optional<HostileEntity> foe = w.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(10), Entity::isAlive)
                .stream().min(Comparator.comparingDouble(m -> m.squaredDistanceTo(p)));
        if (foe.isPresent()) {
            HostileEntity m = foe.get();
            moveToward(p, m.getPos(), 2.0);
            if (p.distanceTo(m) < 3.4 && elapsed % 9 == 0) {
                p.attack(m);
                p.swingHand(Hand.MAIN_HAND);
            }
            return;
        }
        // добыча: ближайшее достижимое дерево, иначе блуждание
        boolean needTarget = d.botTarget == null || (!isLog(w, d.botTarget) && d.botTarget.getSquaredDistance(p.getPos()) < 9);
        if (needTarget || elapsed % 300 == 0) {
            d.botTarget = findLog(w, p.getBlockPos(), d);
            d.bestDist = 1e9;
            d.stuckTicks = 0;
            if (d.botTarget == null) {
                double ang = RNG.nextDouble() * Math.PI * 2;
                d.botTarget = p.getBlockPos().add((int) (Math.cos(ang) * 14), 0, (int) (Math.sin(ang) * 14));
            }
        }
        Vec3d t = Vec3d.ofCenter(d.botTarget);
        boolean log = isLog(w, d.botTarget);
        double eyeDist = p.getEyePos().distanceTo(t);

        if (log && eyeDist < 4.6) {
            // дошёл — рубит (стоит на месте)
            p.setVelocity(0, p.getVelocity().y, 0);
            if (elapsed % 10 == 0) {
                w.breakBlock(d.botTarget, true, p);
                p.swingHand(Hand.MAIN_HAND);
                d.botTarget = null;
            }
        } else {
            double flat = moveToward(p, t, log ? 1.2 : 1.5);
            // застрял (не приближается 2 секунды) — выкидываем цель и берём другую
            if (flat < d.bestDist - 0.05) {
                d.bestDist = flat;
                d.stuckTicks = 0;
            } else if (++d.stuckTicks >= 40) {
                if (d.botTarget != null) d.botBlack.add(d.botTarget.asLong());
                d.botTarget = null;
                d.stuckTicks = 0;
                d.bestDist = 1e9;
            }
            if (!log && flat < 2.0) d.botTarget = null; // дошёл до точки блуждания
        }
        if (elapsed % 400 == 0) {
            p.sendMessage(Text.literal("§7[Персонаж] §f" + BOT[RNG.nextInt(BOT.length)]), false);
        }
    }

    private static boolean isLog(ServerWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return s.isIn(BlockTags.LOGS);
    }

    /** Ближайшее бревно, до которого реально дойти: не выше +4 и не ниже −2 от ног, не из «чёрного списка». */
    private static BlockPos findLog(ServerWorld w, BlockPos around, PlayerData d) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos pos = around.add(dx, dy, dz);
                    if (!isLog(w, pos) || d.botBlack.contains(pos.asLong())) continue;
                    double dd = pos.getSquaredDistance(around);
                    if (dd < bd) { bd = dd; best = pos; }
                }
            }
        }
        return best;
    }

    /** Идёт к точке (скорость задаётся напрямую, без телепортов — иначе персонаж дёргается), возвращает горизонтальное расстояние. */
    private static double moveToward(ServerPlayerEntity p, Vec3d target, double stopDist) {
        Vec3d dir = target.subtract(p.getPos());
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (flat > stopDist) {
            Vec3d v = new Vec3d(dir.x, 0, dir.z).normalize().multiply(0.2);
            double vy = (p.horizontalCollision && p.isOnGround()) ? 0.42 : p.getVelocity().y;
            p.setVelocity(v.x, vy, v.z);
            p.velocityModified = true;
            p.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        } else {
            p.setVelocity(0, p.getVelocity().y, 0);
        }
        return flat;
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.botTarget = null;
        if (d.tiltPhase == 1) p.sendMessage(Text.literal("§7[Персонаж] §fЛадно, поиграли и хватит. Можешь забирать управление."), false);
    }
}
