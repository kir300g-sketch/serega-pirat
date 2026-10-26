package com.piratechallenge.client;

import com.piratechallenge.AmStatePayload;
import com.piratechallenge.CompassPayload;
import com.piratechallenge.SingPayload;
import com.piratechallenge.ModEntities;
import com.piratechallenge.QtePayload;
import com.piratechallenge.StatePayload;
import com.piratechallenge.Track;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;

import java.util.concurrent.ThreadLocalRandom;

public class PirateChallengeClient implements ClientModInitializer {
    private static KeyBinding held;
    private static int heldTicks;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SEREGA, SeregaRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientState.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AmStatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientState.updateAm(payload)));
        ClientPlayNetworking.registerGlobalReceiver(QtePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    ClientState.qteLetters = payload.letters();
                    ClientState.qteTicks = payload.ticks();
                }));

        ClientPlayNetworking.registerGlobalReceiver(CompassPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientState.updateCompass(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            MicMonitor.stop();
        });

        HudRenderCallback.EVENT.register(HudOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientState.tick();
            randomKeys(mc);
            screens(mc);
            mic(mc);
        });
    }

    /** Открываем/закрываем служебные экраны и фиксируем вид камеры в зависимости от трека. */
    private static void screens(MinecraftClient mc) {
        if (mc.player == null) return;
        Track t = ClientState.track();

        // «Ну и что, что я вор?»: только вид от первого лица
        if (t == Track.THIEF && mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }

        // ФП АМ: вид от третьего лица (камеру поднимает CameraMixin) + экран управления мышью
        if (t == Track.FP_AM && !ClientState.paused) {
            if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            if (mc.currentScreen == null && Util.getMeasuringTimeMs() >= AntiMageScreen.reopenAtMs) {
                mc.setScreen(new AntiMageScreen());
            }
        } else if (mc.currentScreen instanceof AntiMageScreen) {
            mc.setScreen(null);
        }

        // Шахматы (фанат): доска на весь экран, пока идёт трек и партия не закончена
        if (t == Track.FANAT && !ClientState.paused && !ChessScreen.finished) {
            if (mc.currentScreen == null && Util.getMeasuringTimeMs() >= ChessScreen.reopenAtMs) {
                mc.setScreen(new ChessScreen());
            }
        } else if (mc.currentScreen instanceof ChessScreen && t != Track.FANAT) {
            mc.setScreen(null);
        }

        // Нога: комбинация букв
        if (ClientState.qteTicks > 0 && !ClientState.qteLetters.isEmpty() && !(mc.currentScreen instanceof QteScreen)) {
            mc.setScreen(new QteScreen());
        }
    }

    private static int micTick;

    /** «Ластпик сапорт»: микрофон включён только на время этого трека; на сервер уходят лишь флаги. */
    private static void mic(MinecraftClient mc) {
        boolean need = mc.player != null && ClientState.track() == Track.LASTPICK && !ClientState.paused;
        if (need) {
            if (!MicMonitor.isRunning()) MicMonitor.start();
            if (++micTick % 10 == 0) {
                // пока микрофон ещё пробуется открыть, не сообщаем «нет микрофона»
                boolean ok = MicMonitor.isOk();
                if (ok || MicMonitor.tried()) {
                    ClientPlayNetworking.send(new SingPayload(MicMonitor.singing(), ok));
                }
            }
        } else if (MicMonitor.isRunning()) {
            MicMonitor.stop();
            micTick = 0;
        }
    }

    /** «Мой топор»: в ярости жмутся случайные кнопки. */
    private static void randomKeys(MinecraftClient mc) {
        if (held != null && --heldTicks <= 0) {
            held.setPressed(false);
            held = null;
        }
        if (mc.player == null || mc.currentScreen != null || held != null) return;
        if (ClientState.track() != Track.MOY_TOPOR) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextInt(25) != 0) return;

        KeyBinding[] pool = {
                mc.options.attackKey, mc.options.useKey, mc.options.jumpKey,
                mc.options.sneakKey, mc.options.sprintKey,
                mc.options.hotbarKeys[r.nextInt(9)]
        };
        KeyBinding k = pool[r.nextInt(pool.length)];
        InputUtil.Key key = InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey());
        k.setPressed(true);
        KeyBinding.onKeyPressed(key);
        held = k;
        heldTicks = 4;
    }
}
