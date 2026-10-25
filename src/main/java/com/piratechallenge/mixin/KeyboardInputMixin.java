package com.piratechallenge.mixin;

import com.piratechallenge.Track;
import com.piratechallenge.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «Мой топор»: каждые 3 секунды меняется схема путаницы управления.
 * «Апельсин»: обычная ходьба отключена, движение только рывками (прыжок + WASD = примерно 1 блок).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void pc$input(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Track t = ClientState.track();
        if (t == Track.MOY_TOPOR) {
            int mode = (ClientState.age / 60) % 3;
            float f = this.movementForward;
            float s = this.movementSideways;
            switch (mode) {
                case 0 -> { this.movementForward = -f; this.movementSideways = -s; } // всё наоборот
                case 1 -> { this.movementForward = s; this.movementSideways = f; }   // оси поменялись
                default -> this.movementForward = -f;                                 // только вперёд/назад
            }
        } else if (t == Track.ORANGE) {
            float f = this.movementForward;
            float s = this.movementSideways;
            boolean jump = this.jumping;
            this.movementForward = 0;
            this.movementSideways = 0; // ходить нельзя
            ClientPlayerEntity pl = MinecraftClient.getInstance().player;
            if (pl != null && jump && pl.isOnGround() && (f != 0 || s != 0)) {
                double len = Math.sqrt(f * f + s * s);
                double vx = s / len, vz = f / len;
                double yaw = Math.toRadians(pl.getYaw());
                double sin = Math.sin(yaw), cos = Math.cos(yaw);
                double dx = vx * cos - vz * sin;
                double dz = vz * cos + vx * sin;
                double speed = 0.15; // подбирай: больше = дальше прыжок
                pl.setVelocity(dx * speed, pl.getVelocity().y, dz * speed);
            }
        }
    }
}
