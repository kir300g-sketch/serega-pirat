package com.piratechallenge.client;

import com.piratechallenge.StatePayload;
import com.piratechallenge.Track;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;

import java.util.concurrent.ThreadLocalRandom;

public class PirateChallengeClient implements ClientModInitializer {
    private static KeyBinding held;
    private static int heldTicks;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientState.update(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

        HudRenderCallback.EVENT.register(HudOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientState.tick();
            randomKeys(mc);
            // «Ну и что, что я вор?»: смотреть с F5 нельзя
            if (mc.player != null && ClientState.track() == Track.THIEF
                    && mc.options.getPerspective() != Perspective.FIRST_PERSON) {
                mc.options.setPerspective(Perspective.FIRST_PERSON);
            }
        });
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
