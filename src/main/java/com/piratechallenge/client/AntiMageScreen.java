package com.piratechallenge.client;

import com.piratechallenge.MovePayload;
import com.piratechallenge.SkillPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

/**
 * ФП АМ: прозрачный экран, который освобождает курсор. ПКМ/ЛКМ — идти в точку под курсором,
 * Q W E R — навыки (Mana Break / Blink / Counterspell / Mana Void) в точку курсора.
 * ESC закрывает экран на 2.5 секунды (чтобы можно было открыть меню паузы), потом он вернётся.
 */
public class AntiMageScreen extends Screen {
    public static long reopenAtMs = 0;

    public AntiMageScreen() {
        super(Text.literal("Anti-Mage"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // прозрачный: ничего не рисуем
    }

    @Override
    public void close() {
        reopenAtMs = Util.getMeasuringTimeMs() + 2500;
        super.close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Vec3d p = cursorWorld(mouseX, mouseY);
        if (p != null) ClientPlayNetworking.send(new MovePayload(p.x, p.y, p.z));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int skill = switch (keyCode) {
            case GLFW.GLFW_KEY_Q -> 0;
            case GLFW.GLFW_KEY_W -> 1;
            case GLFW.GLFW_KEY_E -> 2;
            case GLFW.GLFW_KEY_R -> 3;
            default -> -1;
        };
        if (skill >= 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            double mx = mc.mouse.getX() * this.width / (double) mc.getWindow().getWidth();
            double my = mc.mouse.getY() * this.height / (double) mc.getWindow().getHeight();
            Vec3d p = cursorWorld(mx, my);
            if (p == null && mc.player != null) p = mc.player.getPos();
            if (p != null) ClientPlayNetworking.send(new SkillPayload(skill, p.x, p.y, p.z));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Луч из камеры через курсор -> точка на земле. */
    private Vec3d cursorWorld(double mx, double my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d pos = cam.getPos();

        double ndcX = (mx / this.width) * 2.0 - 1.0;
        double ndcY = 1.0 - (my / this.height) * 2.0;
        double tanH = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) this.width / this.height;

        Vec3d fwd = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d right = fwd.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(fwd).normalize();
        Vec3d dir = fwd.add(right.multiply(ndcX * tanH * aspect)).add(up.multiply(ndcY * tanH)).normalize();

        Vec3d end = pos.add(dir.multiply(90));
        BlockHitResult r = mc.world.raycast(new RaycastContext(pos, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (r.getType() != HitResult.Type.MISS) return r.getPos();

        // нет блока — пересечение с плоскостью на уровне игрока
        if (Math.abs(dir.y) < 1e-4) return null;
        double t = (mc.player.getY() - pos.y) / dir.y;
        if (t <= 0) return null;
        return pos.add(dir.multiply(t));
    }
}
