package com.piratechallenge.client;

import com.piratechallenge.BambooResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Разрезание бамбука в стиле Ghost of Tsushima. Для каждого удара появляется направление (W/A/S/D или стрелки)
 * и сужающееся кольцо. Нужно нажать нужное направление, когда кольцо сойдётся с белой рамкой (окно ±6 тиков).
 * Попали во все удары — идеальный разрез, хотя бы в половину — срез, иначе потеря концентрации.
 */
public class BambooScreen extends Screen {
    private static final int WINDOW = 6;
    private final String seq;
    private final int beat;
    private final boolean[] resolved;
    private final boolean[] hit;
    private final int[] flash;
    private int ticks;
    private int sent;
    private int cutAnim;

    public BambooScreen() {
        super(Text.literal("Бамбук"));
        this.seq = ClientState.bambooSeq;
        this.beat = Math.max(10, ClientState.bambooBeat);
        this.resolved = new boolean[seq.length()];
        this.hit = new boolean[seq.length()];
        this.flash = new int[seq.length()];
    }

    private int start(int i) { return 15 + i * beat; }          // когда появляется кольцо
    private int target(int i) { return start(i) + beat; }       // когда оно сходится

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }

    @Override
    public void tick() {
        if (seq.isEmpty() || ClientState.bambooSeq.isEmpty()) { close(); return; }
        ticks++;
        for (int i = 0; i < seq.length(); i++) {
            if (!resolved[i] && ticks > target(i) + WINDOW) resolved[i] = true; // пропустил
            if (flash[i] > 0) flash[i]--;
        }
        if (cutAnim > 0) cutAnim--;

        int last = target(seq.length() - 1) + WINDOW + 12;
        if (sent == 0 && ticks > last) {
            int hits = 0;
            for (boolean h : hit) if (h) hits++;
            ClientPlayNetworking.send(new BambooResultPayload(hits, seq.length()));
            sent = 1;
            ClientState.bambooSeq = "";
            close();
        }
    }

    private static char keyToDir(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> 'W';
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> 'A';
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> 'S';
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> 'D';
            default -> '?';
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        char dir = keyToDir(keyCode);
        if (dir == '?') return true;
        // ближайший по времени неразрешённый удар
        int best = -1, bd = Integer.MAX_VALUE;
        for (int i = 0; i < seq.length(); i++) {
            if (resolved[i] || ticks < start(i)) continue;
            int dt = Math.abs(ticks - target(i));
            if (dt < bd) { bd = dt; best = i; }
        }
        if (best < 0) return true;
        resolved[best] = true;
        if (seq.charAt(best) == dir && bd <= WINDOW) {
            hit[best] = true;
            flash[best] = 10;
            cutAnim = 10;
        } else {
            flash[best] = -10; // промах
        }
        return true;
    }

    private static String glyph(char d) {
        return switch (d) { case 'W' -> "↑"; case 'A' -> "←"; case 'S' -> "↓"; default -> "→"; };
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int w = this.width, h = this.height;
        var tr = this.textRenderer;

        // лёгкая зелёная дымка и затемнение краёв
        ctx.fill(0, 0, w, h, 0x2288CC88);
        ctx.fillGradient(0, 0, w, h / 5, 0x88000000, 0x00000000);
        ctx.fillGradient(0, h * 4 / 5, w, h, 0x00000000, 0x88000000);

        // стебель бамбука по центру
        int cx = w / 2, sw = Math.max(14, w / 28);
        int hits = 0;
        for (boolean b : hit) if (b) hits++;
        int cutOffset = hits * 4 + (cutAnim > 0 ? 6 : 0);
        drawStalk(ctx, cx, sw, h, hits > 0 ? cutOffset : 0, hits > 0);

        // текущие кольца/направления
        for (int i = 0; i < seq.length(); i++) {
            if (ticks < start(i) || ticks > target(i) + WINDOW + 10) continue;
            float f = MathHelperClamp((ticks - start(i)) / (float) beat);
            int sizeOuter = (int) (150 - 100 * f);
            int sizeTarget = 50;
            int ringColor = Math.abs(ticks - target(i)) <= WINDOW ? 0xFF66FF66 : 0xFFFFDD55;
            if (resolved[i]) ringColor = hit[i] ? 0xFF55FF55 : 0xFFFF5555;

            int px = cx + (i % 2 == 0 ? -(w / 6) : (w / 6));
            int py = h / 2 - 10;
            // рамка-цель
            outline(ctx, px, py, sizeTarget, 2, 0xFFFFFFFF);
            // сужающееся кольцо
            if (!resolved[i]) outline(ctx, px, py, sizeOuter, 3, ringColor);
            // направление
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate(px, py - 9, 0);
            m.scale(3f, 3f, 1f);
            ctx.drawCenteredTextWithShadow(tr, glyph(seq.charAt(i)), 0, 0, resolved[i] ? ringColor : 0xFFFFFFFF);
            m.pop();
            ctx.drawCenteredTextWithShadow(tr, String.valueOf(seq.charAt(i)), px, py + 18, 0xFFCCCCCC);
            if (flash[i] > 0) ctx.drawCenteredTextWithShadow(tr, "РАЗРЕЗ!", px, py - 40, 0xFF99FF99);
            if (flash[i] < 0) ctx.drawCenteredTextWithShadow(tr, "МИМО", px, py - 40, 0xFFFF7777);
        }

        ctx.drawCenteredTextWithShadow(tr, "Жми направление, когда кольцо сойдётся с рамкой (WASD / стрелки)", cx, 14, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, "Попаданий: " + hits + " / " + seq.length(), cx, h - 20, 0xFFAAFFAA);
    }

    private static float MathHelperClamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void outline(DrawContext ctx, int cx, int cy, int size, int th, int color) {
        int x1 = cx - size / 2, y1 = cy - size / 2, x2 = cx + size / 2, y2 = cy + size / 2;
        ctx.fill(x1, y1, x2, y1 + th, color);
        ctx.fill(x1, y2 - th, x2, y2, color);
        ctx.fill(x1, y1, x1 + th, y2, color);
        ctx.fill(x2 - th, y1, x2, y2, color);
    }

    private void drawStalk(DrawContext ctx, int cx, int sw, int h, int offset, boolean cut) {
        int green = 0xFF4CAF50, dark = 0xFF2E7D32, light = 0xFF8BE08F;
        int cutY = h / 2;
        // нижняя часть
        ctx.fill(cx - sw / 2, cutY, cx + sw / 2, h, green);
        ctx.fill(cx - sw / 2, cutY, cx - sw / 2 + 3, h, light);
        ctx.fill(cx + sw / 2 - 3, cutY, cx + sw / 2, h, dark);
        for (int y = cutY + 20; y < h; y += 46) ctx.fill(cx - sw / 2 - 2, y, cx + sw / 2 + 2, y + 4, dark);
        // верхняя часть (после ударов сдвигается вверх-вправо)
        int ox = cut ? offset : 0, oy = cut ? -offset : 0;
        ctx.fill(cx - sw / 2 + ox, 0 + oy, cx + sw / 2 + ox, cutY + oy, green);
        ctx.fill(cx - sw / 2 + ox, 0 + oy, cx - sw / 2 + 3 + ox, cutY + oy, light);
        ctx.fill(cx + sw / 2 - 3 + ox, 0 + oy, cx + sw / 2 + ox, cutY + oy, dark);
        for (int y = 20; y < cutY; y += 46) ctx.fill(cx - sw / 2 - 2 + ox, y + oy, cx + sw / 2 + 2 + ox, y + 4 + oy, dark);
        // белая линия среза
        if (cut) {
            for (int i = -sw; i <= sw; i++) ctx.fill(cx + i, cutY - i / 3, cx + i + 1, cutY - i / 3 + 2, 0xFFFFFFFF);
        }
    }
}
