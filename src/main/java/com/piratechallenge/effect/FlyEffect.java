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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Первые ~85% трека игрок летит вверх, отстреливаясь от «хейтеров». Хейтеры управляются напрямую
 * (а не стандартным ИИ фантомов): атакуют по очереди, остальные кружат рядом и не отстают от подъёма.
 * Потом падение: в обычном мире выдаётся ведро воды, в Аду (вода испаряется) — лоза (смягчает падение).
 */
public class FlyEffect implements TrackEffect {
    private static final double MAX_ALTITUDE = 700;
    private static final double NETHER_ALTITUDE = 118;
    private static final double ASCENT_SPEED = 0.6; // блоков в тик
    private static final int MAX_HATERS = 20;
    private static final Random RNG = new Random();
    private static final String[] NAMES = {"Хейтер", "Токсик", "Комментатор", "Тролль"};

    private static boolean nether(ServerWorld w) {
        return w.getRegistryKey() == World.NETHER || w.getDimension().ultrawarm();
    }

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
        double cap = nether(w) ? NETHER_ALTITUDE : MAX_ALTITUDE;

        EffectUtil.prune(w, d);

        // ---- хейтеры
        List<PhantomEntity> haters = new ArrayList<>();
        for (UUID id : d.spawned) {
            Entity e = w.getEntity(id);
            if (e instanceof PhantomEntity ph) {
                if (ph.isOnFire()) ph.extinguish();
                haters.add(ph);
            }
        }
        int n = haters.size();
        if (n > 0) {
            int attackers = elapsed < total / 2 ? 1 : 2;
            int base = (elapsed / 100) % n;
            Vec3d aim = p.getPos().add(0, p.getHeight() * 0.6, 0);
            for (int i = 0; i < n; i++) {
                PhantomEntity ph = haters.get(i);
                double dist = ph.getPos().distanceTo(p.getPos());
                boolean attacking = ((i - base + n) % n) < attackers;

                if (dist > 45) { // отстал — телепортируем поближе (игрок поднимается быстро)
                    placeNear(p, ph);
                    continue;
                }
                if (attacking) {
                    Vec3d dir = aim.subtract(ph.getPos());
                    double speed = Math.min(1.05, 0.5 + dist * 0.04);
                    ph.setVelocity(dir.normalize().multiply(speed));
                    ph.setYaw((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))));
                    if (dist < 2.2 && (ph.age + ph.getId()) % 15 == 0) {
                        p.damage(p.getDamageSources().mobAttack(ph), 3.0f);
                        p.takeKnockback(0.5, ph.getX() - p.getX(), ph.getZ() - p.getZ());
                    }
                } else {
                    // кружит рядом, на ~8 блоков выше (с запасом на подъём)
                    double a = elapsed * 0.05 + i * (Math.PI * 2 / n);
                    Vec3d target = new Vec3d(p.getX() + Math.cos(a) * 12, p.getY() + 8, p.getZ() + Math.sin(a) * 12);
                    Vec3d dir = target.subtract(ph.getPos());
                    double speed = Math.min(1.0, 0.25 + dir.length() * 0.06);
                    ph.setVelocity(dir.normalize().multiply(speed));
                    ph.setYaw((float) (Math.toDegrees(Math.atan2(-dir.x, dir.z))));
                }
            }
        }

        if (ascending) {
            // блоки над головой ломаются (шахта, потолок, платформа — не спасут)
            if (elapsed % 2 == 0) {
                net.minecraft.util.math.BlockPos head = p.getBlockPos().up((int) Math.ceil(p.getHeight()));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 0; dy <= 4; dy++) {
                            net.minecraft.util.math.BlockPos bp = head.add(dx, dy, dz);
                            net.minecraft.block.BlockState st = w.getBlockState(bp);
                            if (!st.isAir() && st.getHardness(w, bp) >= 0) w.breakBlock(bp, false);
                        }
                    }
                }
            }
            p.setNoGravity(true);
            double vy = p.getY() >= cap ? 0 : ASCENT_SPEED;
            p.setVelocity(p.getVelocity().x * 0.9, vy, p.getVelocity().z * 0.9);
            p.velocityModified = true;
            p.fallDistance = 0;

            if (elapsed % 12 == 0 && d.spawned.size() < MAX_HATERS) {
                spawnHater(p, d);
                if (elapsed % 48 == 0) spawnHater(p, d);
            }
            if (elapsed % 20 == 0) EffectUtil.bar(p, "§eВысота: " + (int) p.getY());
        } else {
            if (p.hasNoGravity()) p.setNoGravity(false);
            if (!d.bucketGiven) {
                d.bucketGiven = true;
                boolean hell = nether(w);
                ItemStack landing = EffectUtil.tag(hell ? new ItemStack(Items.TWISTING_VINES, 8) : new ItemStack(Items.WATER_BUCKET));
                p.getInventory().offerOrDrop(landing);
                for (int i = 0; i < 9; i++) {
                    ItemStack st = p.getInventory().getStack(i);
                    if (EffectUtil.isTagged(st) && (st.isOf(Items.WATER_BUCKET) || st.isOf(Items.TWISTING_VINES))) {
                        p.getInventory().selectedSlot = i;
                        p.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(i));
                        break;
                    }
                }
                EffectUtil.bar(p, hell
                        ? "§aВ Аду вода испаряется! Поставь ЛОЗУ на землю и приземлись в неё!"
                        : "§bВАТЕР-ДРОП! Целься ведром в землю!");
            }
        }
    }

    private void placeNear(ServerPlayerEntity p, PhantomEntity ph) {
        double a = RNG.nextDouble() * Math.PI * 2;
        ph.refreshPositionAndAngles(p.getX() + Math.cos(a) * 14, p.getY() + 6 + RNG.nextInt(5), p.getZ() + Math.sin(a) * 14, 0f, 0f);
    }

    private void spawnHater(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        PhantomEntity ph = EntityType.PHANTOM.create(w);
        if (ph == null) return;
        placeNear(p, ph);
        ph.setCustomName(Text.literal(NAMES[RNG.nextInt(NAMES.length)]));
        ph.setCustomNameVisible(true);
        ph.setPersistent();
        w.spawnEntity(ph);
        EffectUtil.reg(d, ph);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.setNoGravity(false);
        EffectUtil.removeTagged(p);
    }
}
