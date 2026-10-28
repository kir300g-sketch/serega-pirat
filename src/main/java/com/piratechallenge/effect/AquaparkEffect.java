package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «В аквапарке»: ты на территории аквапарка (бассейн, лежаки, толпа отдыхающих). Спасатели следят за порядком:
 * каждые ~12 секунд объявляется ПРАВИЛО («Не бегать!», «Не прыгать!», «Не стоять на месте!», «Выйти из воды!», «Все в воду!»).
 * 3 секунды на подготовку, потом 8 секунд оно действует — нарушил, получи штраф. Плюс волны в бассейне толкают тебя.
 */
public class AquaparkEffect implements TrackEffect {
    private static final int HALF = 12;
    private static final int POOL = 6; // бассейн (2*POOL+1)^2, глубина 3
    private static final int CYCLE = 12 * 20;
    private static final int GRACE = 3 * 20;
    private static final int ACTIVE = 8 * 20;
    private static final Random RNG = new Random();

    private static final String[] RULES = {
            "Не бегать!", "Не прыгать!", "Не стоять на месте!", "Выйти из воды!", "Все в воду!"
    };

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.returnPos = p.getPos();
        d.arenaHalf = HALF;
        d.arenaH = 4;
        d.arenaCenter = SkyArena.build(w, p.getBlockPos(), HALF, 4,
                Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
        BlockPos c = d.arenaCenter;

        // бассейн: дно на 3 ниже, вода по уровень палубы
        for (int dx = -POOL; dx <= POOL; dx++) {
            for (int dz = -POOL; dz <= POOL; dz++) {
                w.setBlockState(c.add(dx, -3, dz), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int dy = -2; dy <= 0; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        // бортики бассейна (стенки под палубой) — чтобы вода не утекала в стороны
        for (int dx = -POOL - 1; dx <= POOL + 1; dx++) {
            for (int dz = -POOL - 1; dz <= POOL + 1; dz++) {
                boolean edge = Math.abs(dx) == POOL + 1 || Math.abs(dz) == POOL + 1;
                if (!edge) continue;
                for (int dy = -3; dy <= 0; dy++) {
                    w.setBlockState(c.add(dx, dy, dz), Blocks.WHITE_CONCRETE.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        // «лежаки» и свет
        for (int dx = -HALF + 2; dx <= HALF - 2; dx += 4) {
            w.setBlockState(c.add(dx, 1, -HALF + 2), Blocks.YELLOW_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
            w.setBlockState(c.add(dx, 1, HALF - 2), Blocks.ORANGE_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        for (int[] cr : new int[][]{{-HALF + 1, -HALF + 1}, {HALF - 1, -HALF + 1}, {-HALF + 1, HALF - 1}, {HALF - 1, HALF - 1}}) {
            w.setBlockState(c.add(cr[0], 1, cr[1]), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - HALF + 3.5);

        // отдыхающие (стоят и прыгают) и спасатели (не нападают)
        EntityType<?>[] folks = {EntityType.VILLAGER, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP, EntityType.COW};
        for (int i = 0; i < 18; i++) {
            Entity e = folks[RNG.nextInt(folks.length)].create(w);
            if (e instanceof MobEntity m) place(m, w, c, d, false);
        }
        for (int i = 0; i < 3; i++) {
            Entity e = EntityType.IRON_GOLEM.create(w);
            if (e instanceof MobEntity m) {
                m.setCustomName(Text.literal("§bСпасатель"));
                m.setCustomNameVisible(true);
                place(m, w, c, d, true);
            }
        }

        d.ruleTick = 0;
        d.ruleType = -1;
        d.waveTimer = 600;
        p.sendMessage(Text.literal("§bДобро пожаловать в аквапарк! Слушай спасателей — нарушителей штрафуют."), false);
    }

    private void place(MobEntity m, ServerWorld w, BlockPos c, PlayerData d, boolean guard) {
        double x, z;
        int tries = 0;
        do {
            x = c.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 2 * (HALF - 2);
            z = c.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 2 * (HALF - 2);
        } while (Math.abs(x - c.getX()) < POOL + 1.5 && Math.abs(z - c.getZ()) < POOL + 1.5 && tries++ < 20); // на палубе, не в бассейне
        m.refreshPositionAndAngles(x, c.getY() + 1, z, RNG.nextFloat() * 360f, 0f);
        m.setAiDisabled(true);
        m.setPersistent();
        m.setSilent(!guard);
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = d.arenaCenter;
        if (c == null) return;
        EffectUtil.prune(w, d);

        // не выпадаем с территории
        if (p.getY() < c.getY() - 5 || Math.abs(p.getX() - c.getX()) > HALF || Math.abs(p.getZ() - c.getZ()) > HALF) {
            p.requestTeleport(c.getX() + 0.5, c.getY() + 1.0, c.getZ() - HALF + 3.5);
        }

        // отдыхающие подпрыгивают
        if (elapsed % 10 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m && m.isOnGround() && RNG.nextInt(4) == 0) m.setVelocity(0, 0.3, 0);
            }
        }

        // цикл правил
        int phase = elapsed % CYCLE;
        if (phase == 0) {
            d.ruleType = RNG.nextInt(RULES.length);
            d.lastPos = p.getPos();
            p.sendMessage(Text.literal("§b[Спасатель] §fСкоро правило: §e§l" + RULES[d.ruleType]), false);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        }
        if (d.violationCd > 0) d.violationCd--;

        if (phase >= GRACE && phase < GRACE + ACTIVE && d.ruleType >= 0) {
            if (phase == GRACE) EffectUtil.bar(p, "§c§lПРАВИЛО ДЕЙСТВУЕТ: " + RULES[d.ruleType]);
            boolean bad = false;
            if (d.ruleType == 2) {
                if (elapsed % 20 == 0) {
                    bad = p.getPos().squaredDistanceTo(d.lastPos) < 0.6;
                    d.lastPos = p.getPos();
                }
            } else {
                bad = violates(p, d);
            }
            if (bad && d.violationCd <= 0) {
                d.violationCd = 40;
                p.damage(p.getDamageSources().magic(), 3.0f);
                p.sendMessage(Text.literal("§b[Спасатель] §c" + RULES[d.ruleType] + " Штраф!"), false);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.2f, 0.8f);
            }
        } else if (phase == GRACE + ACTIVE) {
            EffectUtil.bar(p, "§aПравило снято");
        }

        // волны в бассейне (раз в 30 секунд толкают всех, кто в воде)
        if (--d.waveTimer <= 0) {
            d.waveTimer = 600;
            p.sendMessage(Text.literal("§9§lВОЛНА!"), true);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.5f, 0.6f);
            d.tntRain = 60; // переиспользуем счётчик как «длительность волны» (в этом треке TNT нет)
        }
        if (d.tntRain > 0) {
            d.tntRain--;
            if (p.isTouchingWater()) {
                double push = Math.sin(d.tntRain * 0.35) * 0.18;
                p.addVelocity(push, 0.02, 0);
                p.velocityModified = true;
            }
            if (d.tntRain % 3 == 0) {
                for (int i = -POOL; i <= POOL; i += 3) {
                    w.spawnParticles(ParticleTypes.SPLASH, c.getX() + i, c.getY() + 0.8, c.getZ() + (d.tntRain % 12 - 6), 8, 0.5, 0.3, 0.5, 0.1);
                }
            }
        }
    }

    private boolean violates(ServerPlayerEntity p, PlayerData d) {
        return switch (d.ruleType) {
            case 0 -> p.isSprinting();                                                        // не бегать
            case 1 -> !p.isOnGround() && p.getVelocity().y > 0.1 && !p.isTouchingWater();    // не прыгать
            case 3 -> p.isTouchingWater();                                                     // выйти из воды
            case 4 -> !p.isTouchingWater();                                                    // все в воду
            default -> false;
        };
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.tntRain = 0;
        if (d.arenaCenter != null) SkyArena.clear(p.getServerWorld(), d.arenaCenter, d.arenaHalf, d.arenaH + 4);
        if (p.isAlive() && d.returnPos != null) {
            Vec3d r = d.returnPos;
            p.requestTeleport(r.x, r.y, r.z);
        }
    }
}
