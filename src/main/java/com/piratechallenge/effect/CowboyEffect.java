package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.CompassPayload;
import com.piratechallenge.ModItems;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Современный ковбой: игрок на лошади с револьвером. Воры едут КОНВОЕМ (одно направление, без разбегания)
 * на лошадях чуть медленнее игрока. Нужно перестрелять всех воров.
 */
public class CowboyEffect implements TrackEffect {
    public static final String THIEF_TAG = "pc_thief";
    private static final int THIEVES = 6;
    private static final double PLAYER_HORSE_SPEED = 0.30;  // атрибут лошади игрока
    private static final double CONVOY_SPEED = 0.46;        // блоков в тик у воров (чуть меньше игрока)
    private static final Random RNG = new Random();
    private static final int CROSSBOW_CYCLE = 90; // арбалет заряжается ~4.5 сек, потом выстрел стрелой
    private static final double CROSSBOW_RANGE = 32;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.ammo = 6;
        d.reload = 0;
        d.kills = 0;
        d.heading = RNG.nextDouble() * Math.PI * 2;

        // лошадь игрока
        AbstractHorseEntity mine = spawnHorse(p, w, p.getX(), p.getY(), p.getZ(), d);
        if (mine != null) {
            EntityAttributeInstance spd = mine.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(PLAYER_HORSE_SPEED);
            d.horseId = mine.getUuid();
            p.startRiding(mine, true);
        }

        // револьвер
        p.getInventory().offerOrDrop(EffectUtil.tag(new ItemStack(ModItems.REVOLVER)));

        // конвой воров впереди, строем по два в ряд
        double fx = Math.cos(d.heading), fz = Math.sin(d.heading);
        double sx = -fz, sz = fx; // боковой вектор
        for (int i = 0; i < THIEVES; i++) {
            double along = 32 + (i / 2) * 4;
            double side = (i % 2 == 0 ? -1.5 : 1.5);
            double x = p.getX() + fx * along + sx * side;
            double z = p.getZ() + fz * along + sz * side;
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
            double y = Math.abs(top.getY() - p.getY()) < 10 ? top.getY() : p.getY();

            AbstractHorseEntity horse = spawnHorse(p, w, x, y, z, d);
            VillagerEntity thief = EntityType.VILLAGER.create(w);
            if (horse == null || thief == null) continue;
            thief.refreshPositionAndAngles(x, y, z, 0f, 0f);
            thief.setCustomName(Text.literal("§cВор"));
            thief.setCustomNameVisible(true);
            thief.setPersistent();
            thief.addCommandTag(THIEF_TAG);
            w.spawnEntity(thief);
            EffectUtil.reg(d, thief);
            thief.startRiding(horse, true);
        }
        p.sendMessage(Text.literal("§6Догони воров и перестреляй их всех! ПКМ револьвером — выстрел."), false);
    }

    private AbstractHorseEntity spawnHorse(ServerPlayerEntity p, ServerWorld w, double x, double y, double z, PlayerData d) {
        Entity e = EntityType.HORSE.create(w);
        if (!(e instanceof AbstractHorseEntity horse)) return null;
        horse.refreshPositionAndAngles(x, y, z, 0f, 0f);
        horse.setTame(true);
        horse.setPersistent();
        horse.getStackReference(400).set(new ItemStack(Items.SADDLE)); // седло
        w.spawnEntity(horse);
        EffectUtil.reg(d, horse);
        return horse;
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);

        // перезарядка
        if (d.reload > 0 && --d.reload == 0) {
            d.ammo = 6;
            EffectUtil.bar(p, "§aПерезаряжено!");
        }

        // конвой едет в одном направлении; курс слегка меняется
        if (elapsed % 120 == 0) d.heading += (RNG.nextDouble() - 0.5) * 0.9;
        double fx = Math.cos(d.heading) * CONVOY_SPEED;
        double fz = Math.sin(d.heading) * CONVOY_SPEED;
        float yaw = (float) (Math.toDegrees(Math.atan2(fz, fx)) - 90.0);

        List<UUID> ids = new ArrayList<>(d.spawned);
        for (UUID id : ids) {
            Entity e = w.getEntity(id);
            if (!(e instanceof AbstractHorseEntity horse) || id.equals(d.horseId)) continue;
            Entity rider = horse.getFirstPassenger();
            if (rider == null || !rider.isAlive()) { // вор убит — лошадь исчезает
                horse.discard();
                continue;
            }
            double vy = (horse.horizontalCollision && horse.isOnGround()) ? 0.5 : horse.getVelocity().y;
            horse.setVelocity(fx, vy, fz);
            horse.setYaw(yaw);
        }

        // воры: арбалет (долгая зарядка, потом выстрел обычной стрелой) + ближайший вор для стрелки на экране
        int idx = 0;
        LivingEntity nearest = null;
        double nd = Double.MAX_VALUE;
        for (UUID id : ids) {
            Entity e = w.getEntity(id);
            if (!(e instanceof VillagerEntity thief) || !thief.isAlive() || !thief.getCommandTags().contains(THIEF_TAG)) continue;
            double dist2 = thief.squaredDistanceTo(p);
            if (dist2 < nd) { nd = dist2; nearest = thief; }

            int phase = (elapsed + idx * 23) % CROSSBOW_CYCLE;
            if (phase == CROSSBOW_CYCLE - 12 && dist2 < CROSSBOW_RANGE * CROSSBOW_RANGE) {
                // предупреждение: заряжен
                w.spawnParticles(ParticleTypes.CRIT, thief.getX(), thief.getY() + 2.4, thief.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                w.playSound(null, thief.getX(), thief.getY(), thief.getZ(), SoundEvents.ENTITY_ARROW_HIT, SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
            if (phase == 0 && elapsed > 40 && dist2 < CROSSBOW_RANGE * CROSSBOW_RANGE && thief.canSee(p)) {
                shootArrow(w, thief, p);
            }
            idx++;
        }
        if (elapsed % 5 == 0 && p.networkHandler != null) {
            ServerPlayNetworking.send(p, nearest == null
                    ? new CompassPayload(false, 0, 0)
                    : new CompassPayload(true, nearest.getX(), nearest.getZ()));
        }

        // если упал с лошади — садимся обратно
        Entity mine = d.horseId == null ? null : w.getEntity(d.horseId);
        if (mine != null && !p.hasVehicle() && elapsed % 20 == 0 && p.distanceTo(mine) < 4) {
            p.startRiding(mine, true);
        }

        if (elapsed % 10 == 0) {
            String ammo = "§e" + "●".repeat(Math.max(0, d.ammo)) + "§8" + "○".repeat(Math.max(0, 6 - d.ammo));
            EffectUtil.bar(p, "§fРевольвер: " + ammo + (d.reload > 0 ? " §cперезарядка" : "") + " §7| §cВоры: §f" + d.kills + "/" + THIEVES);
        }
    }

    private static void shootArrow(ServerWorld w, VillagerEntity thief, ServerPlayerEntity target) {
        Entity ent = EntityType.ARROW.create(w);
        if (!(ent instanceof PersistentProjectileEntity arrow)) return;
        Vec3d from = thief.getEyePos();
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0);
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.refreshPositionAndAngles(from.x, from.y, from.z, 0f, 0f);
        arrow.setOwner(thief);
        arrow.setVelocity(dx, dy + flat * 0.06, dz, 2.2f, 5.0f); // небольшая поправка на падение
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        w.spawnEntity(arrow);
        w.playSound(null, thief.getX(), thief.getY(), thief.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    /** Выстрел из револьвера (можно и вне трека — тогда патроны бесконечные). */
    public static void shoot(ServerPlayerEntity p) {
        PlayerData d = ChallengeManager.data(p);
        boolean inTrack = d.current == Track.COWBOY;
        if (inTrack) {
            if (d.reload > 0) return;
            if (d.ammo <= 0) {
                d.reload = 40;
                return;
            }
        }

        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(60));
        BlockHitResult bh = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, p));
        Vec3d stop = bh.getType() == HitResult.Type.MISS ? end : bh.getPos();

        EntityHitResult eh = ProjectileUtil.raycast(p, eye, stop,
                p.getBoundingBox().stretch(look.multiply(60)).expand(1.0),
                e -> !e.isSpectator() && e.isAttackable() && e != p && e != p.getVehicle(),
                60.0 * 60.0);

        Vec3d endPoint = stop;
        if (eh != null) {
            endPoint = eh.getPos();
            Entity hit = eh.getEntity();
            if (hit instanceof LivingEntity le) {
                le.damage(p.getDamageSources().playerAttack(p), 9.0f);
            }
        }

        double len = eye.distanceTo(endPoint);
        int steps = (int) Math.min(40, len * 2);
        for (int i = 1; i <= steps; i++) {
            Vec3d pt = eye.lerp(endPoint, i / (double) steps);
            w.spawnParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.0f, 1.6f);

        p.getItemCooldownManager().set(ModItems.REVOLVER, 8);
        if (inTrack) {
            d.ammo--;
            if (d.ammo <= 0) {
                d.reload = 40;
                p.getItemCooldownManager().set(ModItems.REVOLVER, 40);
                EffectUtil.bar(p, "§cПерезарядка...");
            }
        }
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.COWBOY) return;
        d.kills++;
        if (d.kills >= THIEVES) p.sendMessage(Text.literal("§a§lВсе воры пойманы!"), false);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.stopRiding();
        EffectUtil.removeTagged(p); // забираем револьвер
        if (d.kills < THIEVES) {
            p.sendMessage(Text.literal("§7Часть воров ушла (" + d.kills + "/" + THIEVES + ")."), false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30 * 20, 0));
        } else {
            p.getInventory().offerOrDrop(new ItemStack(Items.EMERALD, 8));
            p.sendMessage(Text.literal("§aНаграда за поимку: 8 изумрудов."), false);
        }
    }
}
