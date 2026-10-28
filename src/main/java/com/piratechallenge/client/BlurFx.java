package com.piratechallenge.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Собственное размытие экрана (НЕ эффект Майнкрафта): кадр копируется в отдельный буфер и несколько раз
 * рисуется поверх со смещениями и прозрачностью — получается «мыльное» размытие/двоение.
 * Если на этой системе что-то пойдёт не так — эффект отключится сам и вернётся простая «дымка».
 */
public final class BlurFx {
    private static Framebuffer copy;
    private static boolean broken;
    private static long nextPulseMs;
    private static long pulseStartMs = -1;
    private static final long PULSE_LEN = 2600;

    /** Интенсивность 0..1: периодические импульсы размытия (раз в 6–12 секунд, по 2.6 сек). */
    public static float intensity() {
        long now = Util.getMeasuringTimeMs();
        if (nextPulseMs == 0) nextPulseMs = now + 3000;
        if (pulseStartMs < 0 && now >= nextPulseMs) pulseStartMs = now;
        if (pulseStartMs >= 0) {
            float f = (now - pulseStartMs) / (float) PULSE_LEN;
            if (f >= 1f) {
                pulseStartMs = -1;
                nextPulseMs = now + 6000 + (long) (Math.random() * 6000);
                return 0f;
            }
            return (float) Math.sin(Math.PI * f);
        }
        return 0f;
    }

    public static void reset() {
        nextPulseMs = 0;
        pulseStartMs = -1;
    }

    public static void render(DrawContext ctx, float k) {
        if (k <= 0.02f) return;
        if (broken) { fallback(ctx, k); return; }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Framebuffer main = mc.getFramebuffer();
            int fw = main.textureWidth, fh = main.textureHeight;
            if (copy == null || copy.textureWidth != fw || copy.textureHeight != fh) {
                if (copy != null) copy.delete();
                copy = new SimpleFramebuffer(fw, fh, false, MinecraftClient.IS_SYSTEM_MAC);
            }
            // main -> copy
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
            GL30.glBlitFramebuffer(0, 0, fw, fh, 0, 0, fw, fh, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.beginWrite(false);

            float w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();

            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, copy.getColorAttachment());

            float r = 5.5f * k;
            float[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7f, 0.7f}, {-0.7f, -0.7f}, {0.7f, -0.7f}, {-0.7f, 0.7f}};
            float alpha = 0.16f + 0.14f * k;
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (float[] o : offs) {
                float dx = o[0] * r, dy = o[1] * r;
                bb.vertex(m, dx, dy, 0f).texture(0f, 1f).color(1f, 1f, 1f, alpha);
                bb.vertex(m, dx, h + dy, 0f).texture(0f, 0f).color(1f, 1f, 1f, alpha);
                bb.vertex(m, w + dx, h + dy, 0f).texture(1f, 0f).color(1f, 1f, 1f, alpha);
                bb.vertex(m, w + dx, dy, 0f).texture(1f, 1f).color(1f, 1f, 1f, alpha);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());

            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
        } catch (Throwable t) {
            broken = true;
            System.err.println("[PirateChallenge] BlurFx отключён: " + t);
            main(ctx);
            fallback(ctx, k);
        }
    }

    private static void main(DrawContext ctx) {
        try {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        } catch (Throwable ignored) {}
    }

    /** Запасной вариант: молочная дымка + затемнение краёв. */
    private static void fallback(DrawContext ctx, float k) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        ctx.fill(0, 0, w, h, ((int) (90 * k) << 24) | 0xCCCCDD);
        ctx.fillGradient(0, 0, w, h / 3, ((int) (110 * k) << 24), 0x00000000);
        ctx.fillGradient(0, h * 2 / 3, w, h, 0x00000000, ((int) (110 * k) << 24));
    }

    private BlurFx() {}
}
