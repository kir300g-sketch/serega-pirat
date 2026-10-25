package com.piratechallenge;

import com.piratechallenge.effect.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Главный цикл: трек -> 30 сек передышки -> следующий трек (случайный, без повторов). Идёт, пока игрок проклят. */
public final class ChallengeManager {
    public static final int REST_TICKS = 30 * 20;

    private static final Map<Track, TrackEffect> EFFECTS = new EnumMap<>(Track.class);

    static {
        EFFECTS.put(Track.VIBEMAN, new VibemanEffect());
        EFFECTS.put(Track.TP_NA_AME, new RadianceEffect());
        EFFECTS.put(Track.MOY_TOPOR, new AxeEffect());
        EFFECTS.put(Track.KACHALKA, new GymEffect());
        EFFECTS.put(Track.V_ETOY_TRAVE, new GrassEffect());
        EFFECTS.put(Track.YA_VZLETAYU_VVERH, new FlyEffect());
        EFFECTS.put(Track.RUSSELL_CROWE, new CroweEffect());
        EFFECTS.put(Track.NATALIA, new NataliaEffect());
        EFFECTS.put(Track.ZOMBIE_APOCALYPSE, new ZombieEffect());
        EFFECTS.put(Track.THIEF, new ThiefEffect());
        EFFECTS.put(Track.ORANGE, new OrangeEffect());
        EFFECTS.put(Track.FP_AM, new AntiMageEffect());
        EFFECTS.put(Track.COWBOY, new CowboyEffect());
        EFFECTS.put(Track.NOGA, new NogaEffect());
        EFFECTS.put(Track.LASTPICK, new LastPickEffect());
        EFFECTS.put(Track.N911, new EmergencyEffect());
        EFFECTS.put(Track.FANAT, new FanatEffect());
    }

    public static PlayerData data(ServerPlayerEntity p) {
        return ChallengeSave.get(p.getServer()).get(p.getUuid());
    }

    public static Track current(ServerPlayerEntity p) {
        return data(p).current;
    }

    public static boolean anyActive(MinecraftServer server) {
        ChallengeSave save = ChallengeSave.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (save.get(p.getUuid()).current != null) return true;
        }
        return false;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ChallengeManager::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            PlayerData d = ChallengeSave.get(server).get(p.getUuid());
            d.current = null;
            d.restLeft = REST_TICKS;
            d.paused = false;
            d.dialogStage = 0;
            d.seregaId = null;
            d.dialogTimer = d.dialogDone ? 0 : 100;
            if (!anyActive(server)) EffectUtil.sweep(server); // убираем остатки прошлой сессии
            sync(p, d);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.player;
            PlayerData d = ChallengeSave.get(server).get(p.getUuid());
            if (d.current != null) finish(p, d, false);
        });

        // Смерть прерывает трек (он не засчитывается)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                PlayerData d = data(p);
                if (d.current != null) finish(p, d, false);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        ChallengeSave save = ChallengeSave.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData d = save.get(p.getUuid());
            GlobalDebuffs.tick(p, d.listened);
            DialogueManager.tick(p, d);

            // проклятие не должно пропадать (смерть, молоко)
            if (d.cursed && p.age % 40 == 0 && !p.hasStatusEffect(ModEffects.CURSE)) {
                p.addStatusEffect(new StatusEffectInstance(ModEffects.CURSE, StatusEffectInstance.INFINITE, 0, false, true, true));
            }
            if (!p.isAlive()) continue;

            if (d.cursed && d.listened >= 10 && --d.thoughtTimer <= 0) {
                d.thoughtTimer = Thoughts.nextDelayTicks();
                Thoughts.send(p);
            }

            if (d.paused) {
                if (p.age % 20 == 0) sync(p, d);
                continue;
            }

            if (d.current == null) {
                if (!d.cursed || !d.auto) continue;
                if (--d.restLeft <= 0) startTrack(p, d, null);
                else if (d.restLeft % 20 == 0) sync(p, d);
            } else {
                int elapsed = d.totalTicks - d.ticksLeft;
                if (d.listened >= 15 && p.age % 5 == 0) WallCover.check(p, d);
                EFFECTS.get(d.current).tick(p, d, elapsed, d.totalTicks);
                if (d.current == null) continue; // эффект мог сам завершить игру (смерть)
                if (--d.ticksLeft <= 0) finish(p, d, true);
                else if (d.ticksLeft % 20 == 0) sync(p, d);
            }
        }
    }

    private static void startTrack(ServerPlayerEntity p, PlayerData d, Track forced) {
        Track t = forced;
        if (t == null) {
            List<Track> pool = new ArrayList<>();
            for (Track x : Track.values()) if (!d.played.contains(x.ordinal())) pool.add(x);
            if (pool.isEmpty()) { // все треки отыграли — новый круг
                d.played.clear();
                pool.addAll(List.of(Track.values()));
            }
            t = pool.get(p.getRandom().nextInt(pool.size()));
        }

        d.resetRuntime();
        d.current = t;
        d.totalTicks = t.seconds * 20;
        d.ticksLeft = d.totalTicks;

        if (d.hangover) {
            d.hangover = false;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, d.totalTicks, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, d.totalTicks, 1));
            p.sendMessage(Text.literal("Похмелье: слабость и медлительность на весь трек.").formatted(Formatting.GRAY), false);
        }

        EFFECTS.get(t).start(p, d);
        sync(p, d);
    }

    private static void finish(ServerPlayerEntity p, PlayerData d, boolean completed) {
        Track t = d.current;
        if (t == null) return;
        EFFECTS.get(t).end(p, d);
        EffectUtil.discardSpawned(p, d);
        EffectUtil.clearTaggedDrops(p); // выброшенные/выпавшие выданные предметы
        d.intro = false;
        d.coverEars = false;
        d.current = null;
        d.restLeft = REST_TICKS;

        if (completed) {
            d.played.add(t.ordinal());
            d.listened++;
            for (GlobalDebuffs.Debuff debuff : GlobalDebuffs.ALL) {
                if (d.listened == debuff.threshold()) {
                    p.sendMessage(Text.literal("[" + debuff.name() + "] " + debuff.description()).formatted(Formatting.RED), false);
                }
            }
        }
        MinecraftServer server = p.getServer();
        ChallengeSave.get(server).markDirty();
        if (!anyActive(server)) EffectUtil.sweep(server);
        sync(p, d);
    }

    public static void sync(ServerPlayerEntity p, PlayerData d) {
        if (p.networkHandler == null || !p.networkHandler.isConnectionOpen()) return;
        int id = d.current == null ? -1 : d.current.ordinal();
        int left = d.current == null ? d.restLeft : d.ticksLeft;
        int total = d.current == null ? REST_TICKS : d.totalTicks;
        int flags = (d.paused ? 1 : 0) | (d.intro ? 2 : 0) | (d.coverEars ? 4 : 0);
        ServerPlayNetworking.send(p, new StatePayload(id, left, total, d.listened, flags));
    }

    // ------------------------------------------------------------ админ-функции (Плеер Серёги)

    public static void adminPlay(ServerPlayerEntity p, Track t) {
        PlayerData d = data(p);
        d.paused = false;
        if (d.current != null) finish(p, d, false);
        startTrack(p, d, t);
    }

    public static void adminStop(ServerPlayerEntity p) {
        PlayerData d = data(p);
        d.paused = false;
        if (d.current != null) finish(p, d, false);
        else sync(p, d);
    }

    public static void adminTogglePause(ServerPlayerEntity p) {
        PlayerData d = data(p);
        d.paused = !d.paused;
        sync(p, d);
    }

    public static void adminToggleAuto(ServerPlayerEntity p) {
        PlayerData d = data(p);
        d.auto = !d.auto;
    }

    public static void adminSkip(ServerPlayerEntity p) {
        PlayerData d = data(p);
        d.paused = false;
        if (d.current != null) finish(p, d, false);
        startTrack(p, d, null);
    }

    private ChallengeManager() {}
}
