package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Нужно убить 100 жителей за трек. Они спавнятся рядом и убегают. Голод сильнее, с жителей падает сырое мясо. */
public class NataliaEffect implements TrackEffect {
    public static final String TAG = "pc_victim";
    public static final int GOAL = 30;
    private static final int MAX_ALIVE = 20;
    private static final Random RNG = new Random();
    private static final List<Item> MEAT = List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.kills = 0;
        EffectUtil.bar(p, "§4Убей " + GOAL + " душ!");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 40, 2, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, false, false, false));

        if (elapsed % 20 == 0 && d.spawned.size() < MAX_ALIVE) {
            for (int i = 0; i < 2; i++) spawnVillager(p, d);
        }

        // жители убегают
        d.spawned.removeIf(id -> w.getEntity(id) == null);
        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (e == null) continue;
            Vec3d away = e.getPos().subtract(p.getPos());
            if (away.lengthSquared() > 24 * 24) continue;
            Vec3d flat = new Vec3d(away.x, 0, away.z).normalize().multiply(0.14);
            double vy = (e.horizontalCollision && e.isOnGround()) ? 0.42 : e.getVelocity().y;
            e.setVelocity(flat.x, vy, flat.z);
            e.setYaw((float) (Math.toDegrees(Math.atan2(-flat.x, flat.z))));
        }

        if (elapsed % 10 == 0) {
            EffectUtil.bar(p, "§4Души: §f" + d.kills + " / " + GOAL);
        }
    }

    private void spawnVillager(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v == null) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 5 + RNG.nextDouble() * 6;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        v.refreshPositionAndAngles(x, y, z, 0f, 0f);
        v.addCommandTag(TAG);
        w.spawnEntity(v);
        EffectUtil.reg(d, v);
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.NATALIA) return;
        d.kills++;
        d.spawned.remove(victim.getUuid());
        victim.dropStack(new ItemStack(MEAT.get(RNG.nextInt(MEAT.size())), 1 + RNG.nextInt(2)));
        if (d.kills == GOAL) {
            p.sendMessage(Text.literal("§4Задание выполнено. Душ собрано: " + GOAL), false);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.removeStatusEffect(StatusEffects.HUNGER);
        p.removeStatusEffect(StatusEffects.SPEED);
        p.removeStatusEffect(StatusEffects.STRENGTH);
        if (d.kills < GOAL) {
            p.sendMessage(Text.literal("§7Ты не справился. Душ: " + d.kills + "/" + GOAL + ". Расплата."), false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 15 * 20, 1));
        }
    }
}
