package com.piratechallenge.mixin;

import com.piratechallenge.Track;
import com.piratechallenge.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ФП АМ: вместо хотбара рисуются навыки (см. HudOverlay), ванильный хотбар скрыт. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void pc$noHotbar(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientState.track() == Track.FP_AM) ci.cancel();
    }
}
