package com.piratechallenge.mixin;

import com.piratechallenge.Track;
import com.piratechallenge.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Вайбмен»: сердечки и голод прыгают и переливаются радугой. require = 0 — если сигнатура не совпадёт, мод не упадёт. */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    private static boolean pc$busy;

    @Inject(method = "drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pc$vibe(Identifier texture, int x, int y, int width, int height, CallbackInfo ci) {
        if (pc$busy || ClientState.track() != Track.VIBEMAN) return;
        String path = texture.getPath();
        if (!(path.startsWith("hud/heart/") || path.startsWith("hud/food_"))) return;

        pc$busy = true;
        try {
            DrawContext self = (DrawContext) (Object) this;
            float t = Util.getMeasuringTimeMs() / 1000f;
            float hue = (t * 0.8f + x / 180f);
            hue = hue - (float) Math.floor(hue);
            int rgb = MathHelper.hsvToRgb(hue, 0.85f, 1f);
            self.setShaderColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            int jump = (int) Math.round(Math.sin(t * 9 + x * 0.25) * 3);
            self.drawGuiTexture(texture, x, y + jump, width, height);
            self.setShaderColor(1f, 1f, 1f, 1f);
            ci.cancel();
        } finally {
            pc$busy = false;
        }
    }
}
