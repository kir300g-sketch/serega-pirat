package com.piratechallenge.mixin;

import com.piratechallenge.Track;
import com.piratechallenge.client.ClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ФП АМ: камера сверху (Dota-style): высоко над игроком и чуть позади, смотрит вниз-на север. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void pc$topDown(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (ClientState.track() != Track.FP_AM || focused == null) return;
        double x = MathHelper.lerp(tickDelta, focused.prevX, focused.getX());
        double y = MathHelper.lerp(tickDelta, focused.prevY, focused.getY());
        double z = MathHelper.lerp(tickDelta, focused.prevZ, focused.getZ());
        this.setRotation(180f, 62f);
        this.setPos(x, y + 16.5, z + 8.5);
    }
}
