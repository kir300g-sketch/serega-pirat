package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.Random;
import java.util.UUID;

/**
 * Первые ~85% трека игрок летит вверх (до MAX_ALTITUDE), отстреливается от «хейтеров» (негорящие фантомы).
 * Потом падение + выдаётся ведро воды для ватер-дропа.
 */
public class FlyEffect implements TrackEffect {
    private static final double MAX_ALTITUDE = 700;
    private static final double ASCENT_SPEED = 0.6; // блоков в тик
    private static final int MAX_HATERS = 20;
    private static final Random RNG = new Random();
    private static final String[] NAMES = {"Хейтер", "Токсик", "Комментатор", "Тролль"};

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ItemStack bow = EffectUtil.tag(new ItemStack(Items.BOW));
        bow.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(true));
        bow.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Лук против хейтеров"));
        p.getInventory().offerOrDrop(bow);
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.ARROW, 64)));
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(Items.ARROW, 64)));
        EffectUtil.bar(p, "§eВзлетаем! Отстреливай хейтеров!");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        boolean ascending = elapsed < total * 0.85;

        EffectUtil.prune(w, d); // убитые хейтеры не должны занимать лимит
        // хейтеры не горят на солнце
        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (e != null && e.isOnFire()) e.extinguish();
        }

        if (ascending) {
            p.setNoGravity(true);
            double vy = p.getY() >= MAX_ALTITUDE ? 0 : ASCENT_SPEED;
            p.setVelocity(p.getVelocity().x * 0.9, vy, p.getVelocity().z * 0.9);
            p.velocityModified = true;
            p.fallDistance = 0;

            if (elapsed % 15 == 0 && d.spawned.size() < MAX_HATERS) {
                spawnHater(p, d);
                if (elapsed % 60 == 0) spawnHater(p, d);
            }
            if (elapsed % 20 == 0) EffectUtil.bar(p, "§eВысота: " + (int) p.getY());
        } else {
            if (p.hasNoGravity()) p.setNoGravity(false);
            if (!d.bucketGiven) {
                d.bucketGiven = true;
                ItemStack bucket = EffectUtil.tag(new ItemStack(Items.WATER_BUCKET));
                p.getInventory().offerOrDrop(bucket);
                for (int i = 0; i < 9; i++) {
                    if (EffectUtil.isTagged(p.getInventory().getStack(i)) && p.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) {
                        p.getInventory().selectedSlot = i;
                        p.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(i));
                        break;
                    }
                }
                EffectUtil.bar(p, "§bВАТЕР-ДРОП! Целься ведром в землю!");
            }
        }
    }

    private void spawnHater(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        PhantomEntity ph = EntityType.PHANTOM.create(w);
        if (ph == null) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 10 + RNG.nextDouble() * 6;
        ph.refreshPositionAndAngles(p.getX() + Math.cos(ang) * dist, p.getY() + RNG.nextInt(7) - 3,
                p.getZ() + Math.sin(ang) * dist, 0f, 0f);
        ph.setCustomName(Text.literal(NAMES[RNG.nextInt(NAMES.length)]));
        ph.setPersistent();
        ph.setCustomNameVisible(true);
        ph.setTarget(p);
        w.spawnEntity(ph);
        d.spawned.add(ph.getUuid());
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.setNoGravity(false);
        EffectUtil.removeTagged(p);
    }
}
