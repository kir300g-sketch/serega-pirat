package com.piratechallenge.mixin;

import com.piratechallenge.Track;
import com.piratechallenge.client.ClientState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** «Мой топор»: каждые 3 секунды меняется схема путаницы управления. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void pc$confuse(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (ClientState.track() != Track.MOY_TOPOR) return;
        int mode = (ClientState.age / 60) % 3;
        float f = this.movementForward;
        float s = this.movementSideways;
        switch (mode) {
            case 0 -> { this.movementForward = -f; this.movementSideways = -s; } // всё наоборот
            case 1 -> { this.movementForward = s; this.movementSideways = f; }   // оси поменялись
            default -> this.movementForward = -f;                                 // только вперёд/назад
        }
    }
}
