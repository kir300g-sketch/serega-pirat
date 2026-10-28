package com.piratechallenge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.piratechallenge.Track;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Flower Man: собственный радужный след-«шлейф» за игроком (рисуется в мире нашим рендером, НЕ частицами Майнкрафта).
 * Как у Флауи на арте: вертикальная радужная «лента» за спиной, которая тянется по пройденному пути и растворяется.
 * Лента из вертикальных полос по пути + поперечные «перегородки» (видно и сзади), радуга идёт сверху вниз со сдвигом.
 */
public final class RainbowTrail {
    private static final int MAX = 40;
    private static final ArrayDeque<Vec3d> points = new ArrayDeque<>();

    public static void sample(MinecraftClient mc) {
        if (mc.player == null || ClientState.track() != Track.FLOWER) {
            points.clear();
            return;
        }
        Vec3d pos = mc.player.getPos();
        if (points.isEmpty() || points.peekLast().squaredDistanceTo(pos) > 0.02) {
            points.addLast(pos);
            while (points.size() > MAX) points.pollFirst();
        } else if (points.size() > 1 && mc.player.age % 2 == 0) {
            points.pollFirst(); // стоим на месте — шлейф понемногу рассасывается
        }
    }

    public static void render(WorldRenderContext ctx) {
        if (ClientState.track() != Track.FLOWER || points.size() < 2) return;
        Vec3d cam = ctx.camera().getPos();
        var ms = ctx.matrixStack();
        if (ms == null) return;
        Matrix4f m = ms.peek().getPositionMatrix();

        List<Vec3d> pts = new ArrayList<>(points);
        boolean omega = ClientState.flowerOmega > 0;
        float t = Util.getMeasuringTimeMs() / 1000f;
        float height = 1.9f;
        int bands = 14;
        float halfW = omega ? 0.55f : 0.38f;
        int n = pts.size();

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int quads = 0;
        for (int i = 0; i < n - 1; i++) {
            Vec3d a = pts.get(i), b = pts.get(i + 1);
            float ageA = (i) / (float) (n - 1);          // 0 — старое, 1 — новое
            float ageB = (i + 1) / (float) (n - 1);
            float alphaA = (omega ? 0.80f : 0.60f) * ageA * ageA;
            float alphaB = (omega ? 0.80f : 0.60f) * ageB * ageB;

            Vec3d dir = b.subtract(a);
            double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (len < 1e-4) continue;
            double px = -dir.z / len, pz = dir.x / len; // перпендикуляр на горизонтали

            for (int k = 0; k < bands; k++) {
                float y0 = k * height / bands, y1 = (k + 1) * height / bands;
                float hue = (k / (float) bands + t * 0.35f + i * 0.015f) % 1f;
                int rgb = MathHelper.hsvToRgb(hue, 0.95f, 1f);
                float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, bl = (rgb & 255) / 255f;

                // вертикальная полоса по пути (две стороны смещены на ±halfW*0.35: «толщина»)
                for (int side = -1; side <= 1; side += 2) {
                    double ox = px * halfW * 0.35 * side, oz = pz * halfW * 0.35 * side;
                    quad(bb, m, cam, a.x + ox, a.y + y0, a.z + oz, b.x + ox, b.y + y0, b.z + oz,
                            b.x + ox, b.y + y1, b.z + oz, a.x + ox, a.y + y1, a.z + oz, r, g, bl, alphaA, alphaB);
                    quads++;
                }
                // поперечная «перегородка» в точке a: видна и сзади
                if (i % 2 == 0) {
                    quad(bb, m, cam, a.x - px * halfW, a.y + y0, a.z - pz * halfW, a.x + px * halfW, a.y + y0, a.z + pz * halfW,
                            a.x + px * halfW, a.y + y1, a.z + pz * halfW, a.x - px * halfW, a.y + y1, a.z - pz * halfW,
                            r, g, bl, alphaA, alphaA);
                    quads++;
                }
            }
        }
        if (quads == 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder bb, Matrix4f m, Vec3d cam,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4,
                             float r, float g, float b, float aA, float aB) {
        bb.vertex(m, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).color(r, g, b, aA);
        bb.vertex(m, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).color(r, g, b, aB);
        bb.vertex(m, (float) (x3 - cam.x), (float) (y3 - cam.y), (float) (z3 - cam.z)).color(r, g, b, aB);
        bb.vertex(m, (float) (x4 - cam.x), (float) (y4 - cam.y), (float) (z4 - cam.z)).color(r, g, b, aA);
    }

    private RainbowTrail() {}
}
