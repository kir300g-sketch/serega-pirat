package com.piratechallenge;

import com.piratechallenge.effect.EffectUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * Интро: Серёга Пират появляется рядом, спрашивает про альбом, злится, бьют молнии,
 * одна попадает в игрока -> «Проклятие Сереги Пирата». Челлендж идёт, пока игрок проклят.
 */
public final class DialogueManager {
    private static final Random RNG = new Random();

    private static void say(ServerPlayerEntity p, String line) {
        p.sendMessage(Text.literal("§6[Серёга Пират] §f" + line), false);
    }

    public static void restart(ServerPlayerEntity p, PlayerData d) {
        removeNpc(p, d);
        d.dialogDone = false;
        d.dialogStage = 0;
        d.dialogTimer = 60;
        d.cursed = false;
        p.removeStatusEffect(ModEffects.CURSE);
        ChallengeSave.get(p.getServer()).markDirty();
    }

    public static void tick(ServerPlayerEntity p, PlayerData d) {
        if (d.dialogDone) return;
        ServerWorld w = p.getServerWorld();

        Entity npc = d.seregaId == null ? null : w.getEntity(d.seregaId);
        if (npc != null) faceTo(npc, p);

        switch (d.dialogStage) {
            case 0 -> {
                if (d.dialogTimer > 0 && --d.dialogTimer == 0) {
                    spawnNpc(p, d);
                    say(p, "О, здарова! Это я, Серёга Пират!");
                    d.dialogStage = 1;
                    d.dialogTimer = 60;
                }
            }
            case 1 -> {
                if (--d.dialogTimer <= 0) {
                    say(p, "Я тут новый альбом выпустил. Ну как тебе?");
                    MutableText opts = Text.literal("");
                    opts.append(option(1, "Круто... если слушать без звука."));
                    opts.append(Text.literal("\n"));
                    opts.append(option(2, "Ну, честно говоря, не очень."));
                    p.sendMessage(opts, false);
                    d.dialogStage = 2;
                }
            }
            case 2 -> { /* ждём выбора игрока */ }
            case 3 -> timeline(p, d, w);
            default -> {}
        }
    }

    private static Text option(int n, String text) {
        return Text.literal("   §a[" + n + "] §f" + text).styled(s ->
                s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pcdialog " + n)));
    }

    public static void choose(ServerPlayerEntity p, int n) {
        PlayerData d = ChallengeManager.data(p);
        if (d.dialogStage != 2) return;
        d.dialogChoice = n;
        d.dialogStage = 3;
        d.dialogTimer = 0;
        p.sendMessage(Text.literal(n == 1
                ? "§b[Ты] §fКруто... если слушать без звука."
                : "§b[Ты] §fНу, честно говоря, не очень."), false);
    }

    private static void timeline(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        int t = d.dialogTimer++;
        if (t == 20) {
            say(p, d.dialogChoice == 1
                    ? "Чего?.. Без звука?! Ты сейчас серьёзно?!"
                    : "Не очень?! Да ты ничего не понимаешь в искусстве!");
        }
        if (t == 60) {
            say(p, "Ах так?! Ну тогда ты у меня ещё послушаешь!!!");
        }
        if (t >= 80 && t < 150 && (t - 80) % 14 == 0) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 4 + RNG.nextDouble() * 4;
            strike(w, p.getX() + Math.cos(ang) * dist, p.getY(), p.getZ() + Math.sin(ang) * dist);
        }
        if (t == 160) {
            strike(w, p.getX(), p.getY(), p.getZ()); // прямое попадание
            d.cursed = true;
            p.addStatusEffect(new StatusEffectInstance(ModEffects.CURSE, StatusEffectInstance.INFINITE, 0, false, true, true));
            p.sendMessage(Text.literal("§4§lТЫ ПРОКЛЯТ: «Проклятие Сереги Пирата»"), false);
        }
        if (t == 190) {
            say(p, "Теперь слушай. Это проклятие не снять, пока не пройдёшь Майнкрафт!");
        }
        if (t == 240) {
            removeNpc(p, d);
            d.dialogDone = true;
            d.dialogStage = 4;
            d.restLeft = ChallengeManager.REST_TICKS;
            p.sendMessage(Text.literal("§7Первый трек начнётся через 30 секунд..."), false);
            ChallengeSave.get(p.getServer()).markDirty();
        }
    }

    private static void strike(ServerWorld w, double x, double y, double z) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(x, y, z);
        bolt.setCosmetic(true); // без урона и пожаров
        w.spawnEntity(bolt);
    }

    private static void spawnNpc(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        SeregaEntity s = ModEntities.SEREGA.create(w);
        if (s == null) return;
        Vec3d look = p.getRotationVec(1f);
        double x = p.getX() + look.x * 3.5;
        double z = p.getZ() + look.z * 3.5;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 6 ? top.getY() : p.getY();
        s.refreshPositionAndAngles(x, y, z, 0f, 0f);
        s.setCustomName(Text.literal("Серёга Пират"));
        s.setCustomNameVisible(true);
        s.setPersistent();
        s.addCommandTag("pc_serega");
        w.spawnEntity(s);
        d.seregaId = s.getUuid();
        w.spawnParticles(ParticleTypes.POOF, x, y + 1, z, 20, 0.4, 0.8, 0.4, 0.02);
    }

    private static void removeNpc(ServerPlayerEntity p, PlayerData d) {
        if (d.seregaId != null) {
            for (ServerWorld w : p.getServer().getWorlds()) {
                Entity e = w.getEntity(d.seregaId);
                if (e != null) {
                    w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
                    e.discard();
                    break;
                }
            }
            d.seregaId = null;
        }
    }

    private static void faceTo(Entity npc, ServerPlayerEntity p) {
        double dx = p.getX() - npc.getX();
        double dz = p.getZ() - npc.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        npc.setYaw(yaw);
        npc.setHeadYaw(yaw);
        if (npc instanceof net.minecraft.entity.LivingEntity le) le.setBodyYaw(yaw);
    }

    /** Проклятие снято (убит Дракон Края). */
    public static void lift(ServerPlayerEntity p) {
        PlayerData d = ChallengeManager.data(p);
        if (!d.cursed) return;
        d.cursed = false;
        p.removeStatusEffect(ModEffects.CURSE);
        ChallengeManager.adminStop(p);
        ChallengeSave.get(p.getServer()).markDirty();
        p.sendMessage(Text.literal("§6§lТы прошёл Майнкрафт! Проклятие Сереги Пирата снято."), false);
    }

    private DialogueManager() {}
}
