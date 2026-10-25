package com.piratechallenge.client;

import com.piratechallenge.AmStatePayload;
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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

        HudRenderCallback.EVENT.register(HudOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientState.tick();
            randomKeys(mc);
            screens(mc);
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

        // Нога: комбинация букв
        if (ClientState.qteTicks > 0 && !ClientState.qteLetters.isEmpty() && !(mc.currentScreen instanceof QteScreen)) {
            mc.setScreen(new QteScreen());
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
