package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Минёр»: на клиенте идёт Сапёр (MinesweeperScreen). Проиграл — экран пропадает, и 5 секунд сверху льётся дождь из TNT,
 * потом снова сапёр. Дождь рассчитан на выживание: ни один TNT не появляется над самим игроком, место падения заранее
 * подсвечено кольцом частиц, взрывы небольшие. TNT взрывается через СВОЙ взрыв (TNT_BREAKS_BLOCKS — ломать ли блоки).
 */
public class MinerEffect implements TrackEffect {
    private static final boolean TNT_BREAKS_BLOCKS = true;
    private static final int RAIN_TICKS = 100;   // 5 секунд
    private static final Random RNG = new Random();

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.tntRain = 0;
        p.sendMessage(Text.literal("§7Сапёр: ЛКМ — открыть, ПКМ — флажок. Подорвёшься — дождь из TNT!"), false);
    }

    /** Результат сапёра от клиента. */
    public static void onResult(ServerPlayerEntity p, boolean won) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.MINER) return;
        if (won) {
            p.heal(4.0f);
            EffectUtil.bar(p, "§aСапёр пройден! +2 сердца");
        } else if (d.tntRain <= 0) {
            d.tntRain = RAIN_TICKS;
            p.sendMessage(Text.literal("§c§lБАХ! Дождь из TNT — беги из-под него!"), false);
        }
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();

        // ---- свои TNT: взрываем сами (чтобы управлять разрушением блоков)
        List<UUID> ids = new ArrayList<>(d.tntIds);
        for (UUID id : ids) {
            Entity e = w.getEntity(id);
            if (!(e instanceof TntEntity tnt) || !tnt.isAlive()) {
                d.tntIds.remove(id);
                continue;
            }
            if (tnt.getFuse() <= 1 || tnt.isOnGround()) {
                Vec3d pos = tnt.getPos();
                tnt.discard();
                d.tntIds.remove(id);
                w.createExplosion(null, pos.x, pos.y + 0.3, pos.z, 2.6f, false,
                        TNT_BREAKS_BLOCKS ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
                if (d.current != Track.MINER) return; // игрок мог погибнуть
            }
        }

        // ---- дождь
        if (d.tntRain > 0) {
            d.tntRain--;
            if (d.tntRain % 4 == 0) spawnTnt(p, d, w);
            if (d.tntRain == 0) EffectUtil.bar(p, "§aДождь прошёл. Снова сапёр.");
        }
    }

    private void spawnTnt(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        // место падения: рядом, но не над игроком (минимум 4 блока по горизонтали)
        double ang = RNG.nextDouble() * Math.PI * 2;
        double r = 4.5 + RNG.nextDouble() * 10;
        double x = p.getX() + Math.cos(ang) * r, z = p.getZ() + Math.sin(ang) * r;
        int gy = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z)).getY();
        double sy = Math.max(gy, p.getY()) + 22;

        TntEntity tnt = new TntEntity(w, x, sy, z, null);
        tnt.setFuse(60);
        tnt.setVelocity(0, -0.25, 0);
        w.spawnEntity(tnt);
        d.tntIds.add(tnt.getUuid());

        // предупреждающее кольцо на земле
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            w.spawnParticles(p, ParticleTypes.FLAME, true, x + Math.cos(a) * 2.5, gy + 0.3, z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.tntRain = 0;
        for (UUID id : new ArrayList<>(d.tntIds)) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) { e.discard(); break; }
            }
        }
        d.tntIds.clear();
    }
}
