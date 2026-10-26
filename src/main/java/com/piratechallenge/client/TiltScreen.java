package com.piratechallenge.client;

import com.piratechallenge.TiltAttemptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * «Тильт»: экран-перехватчик. Персонаж не слушается — любое нажатие/клик считается «попыткой расшевелить»
 * и отправляется на сервер (там персонаж отвечает фразами, а после нескольких попыток начинает играть сам).
 * ESC закрывает экран на 2.5 секунды (можно выйти в меню).
 */
public class TiltScreen extends Screen {
    public static long reopenAtMs = 0;

    public TiltScreen() {
        super(Text.literal("Тильт"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        reopenAtMs = Util.getMeasuringTimeMs() + 2500;
        super.close();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // не затемняем: видно, что персонаж творит
    }

    @Override
    public void tick() {
        if (ClientState.track() != com.piratechallenge.Track.TILT) close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers); // ESC
        ClientPlayNetworking.send(new TiltAttemptPayload(1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClientPlayNetworking.send(new TiltAttemptPayload(1));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Персонаж в тильте и не слушается...", this.width / 2, 60, 0xFFCCCCCC);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "(попробуй его расшевелить — нажми что-нибудь)", this.width / 2, 74, 0xFF888888);
    }
}
