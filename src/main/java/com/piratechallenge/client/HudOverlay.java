package com.piratechallenge.client;

import com.piratechallenge.GlobalDebuffs;
import com.piratechallenge.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** Все экранные эффекты, заголовок трека, описание и счётчик прослушанных треков. */
public final class HudOverlay {
    private static final int BIG_TITLE_TICKS = 100; // 5 секунд большой заголовок в центре

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float time = Util.getMeasuringTimeMs() / 1000f;
        Track t = ClientState.track();

        if (t != null) {
            overlay(ctx, t, w, h, time);
            title(ctx, mc.textRenderer, t, w, h);
        }
        counter(ctx, mc.textRenderer, w);
    }

    // ------------------------------------------------------------ оверлеи треков

    private static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int alpha, int rgb) {
        ctx.fill(x1, y1, x2, y2, (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF));
    }

    private static int hue(float h) {
        h = h - (float) Math.floor(h);
        return MathHelper.hsvToRgb(h, 1f, 1f);
    }

    private static void overlay(DrawContext ctx, Track t, int w, int h, float time) {
        switch (t) {
            case VIBEMAN -> {
                fill(ctx, 0, 0, w, h, 70, hue(time * 2.5f));
                int bars = 8;
                int shift = ((int) (time * 6)) % bars;
                for (int i = 0; i < bars; i++) {
                    fill(ctx, i * w / bars, 0, (i + 1) * w / bars, h, 55, hue(time * 3f + (i + shift) / (float) bars));
                }
            }
            case TP_NA_AME -> fill(ctx, 0, 0, w, h, 28 + (int) (10 * Math.sin(time * 3)), 0xFFD700);
            case MOY_TOPOR -> {
                int a = 85 + (int) (30 * Math.sin(time * 6));
                fill(ctx, 0, 0, w, h, a, 0xB00000);
                ctx.fillGradient(0, 0, w, h / 4, 0xAA000000, 0x00000000);
                ctx.fillGradient(0, h * 3 / 4, w, h, 0x00000000, 0xAA000000);
            }
            case KACHALKA -> fill(ctx, 0, 0, w, h, 18, 0xFF8800);
            case V_ETOY_TRAVE -> {
                int strips = 18;
                for (int i = 0; i < strips; i++) {
                    float wob = (float) Math.sin(time * 2 + i) * 0.1f;
                    fill(ctx, 0, i * h / strips, w, (i + 1) * h / strips, 65, hue(time * 0.3f + i * 0.07f + wob));
                }
                int dark = (int) (Math.max(0, Math.sin(time * 0.9)) * 170);
                fill(ctx, 0, 0, w, h, dark, 0x000000);
            }
            case YA_VZLETAYU_VVERH -> {
                fill(ctx, 0, 0, w, h, 70 + (int) (25 * Math.sin(time * 2)), 0xFFC800);
                for (int i = 0; i < 30; i++) {
                    float px = ((i * 7919) % 1000) / 1000f;
                    float py = (((i * 104729) % 1000) / 1000f + time * 0.4f) % 1f;
                    int x = (int) (px * w), y = (int) ((1 - py) * h);
                    fill(ctx, x, y, x + 3, y + 3, 200, 0xFFF2A0);
                }
            }
            case RUSSELL_CROWE -> {
                fill(ctx, 0, 0, w, h, 150, 0x707070);
                // длинные волосы по бокам
                for (int i = 0; i < 44; i++) {
                    float r = ((i * 7919) % 100) / 100f;
                    boolean left = i % 2 == 0;
                    int sway = (int) (Math.sin(time * 1.3 + i) * 4);
                    int x = left ? (int) (r * w * 0.2f) + sway : w - (int) (r * w * 0.2f) + sway;
                    int len = (int) (h * (0.55f + 0.45f * (((i * 31) % 100) / 100f)));
                    fill(ctx, x, 0, x + 2, len, 215, 0x1A120B);
                }
                ctx.fillGradient(0, 0, w, h / 3, 0xAA000000, 0x00000000);
            }
            case ZOMBIE_APOCALYPSE -> {
                fill(ctx, 0, 0, w, h, 55, 0x1A3A10);
                int elapsed = ClientState.total - ClientState.ticksLeft;
                int wave = Math.min(2, elapsed * 3 / Math.max(1, ClientState.total));
                int a = 40 + wave * 35 + (int) (20 * Math.sin(time * (2 + wave * 2)));
                ctx.fillGradient(0, h * 2 / 3, w, h, 0x00000000, (MathHelper.clamp(a, 0, 255) << 24) | 0x600000);
            }
            case THIEF -> balaclava(ctx, w, h);
            case ORANGE -> {
                fill(ctx, 0, 0, w, h, 100, 0xFF7A00);
                ctx.fillGradient(0, 0, w, h / 3, 0x55FFA000, 0x00FF7A00);
            }
            case NATALIA -> {
                fill(ctx, 0, 0, w, h, 40 + (int) (15 * Math.sin(time * 4)), 0x8B0000);
                ctx.fillGradient(0, h * 2 / 3, w, h, 0x00000000, 0xAA400000);
            }
        }
    }

    /** Балаклава: чёрная маска с узкой прорезью для глаз; нижние 80px оставлены, чтобы был виден хотбар. */
    private static void balaclava(DrawContext ctx, int w, int h) {
        int slitTop = (int) (h * 0.43f);
        int slitBottom = (int) (h * 0.55f);
        int slitLeft = (int) (w * 0.14f);
        int slitRight = (int) (w * 0.86f);
        int bottomEnd = Math.max(slitBottom, h - 80);
        int black = 0xFF050505;
        ctx.fill(0, 0, w, slitTop, black);
        ctx.fill(0, slitBottom, w, bottomEnd, black);
        ctx.fill(0, slitTop, slitLeft, slitBottom, black);
        ctx.fill(slitRight, slitTop, w, slitBottom, black);
        // «вязаная» текстура
        for (int y = 0; y < slitTop; y += 6) ctx.fill(0, y, w, y + 1, 0x22FFFFFF);
        for (int y = slitBottom; y < bottomEnd; y += 6) ctx.fill(0, y, w, y + 1, 0x22FFFFFF);
        // мягкие края прорези
        ctx.fillGradient(slitLeft, slitTop, slitRight, slitTop + 8, 0xCC000000, 0x00000000);
        ctx.fillGradient(slitLeft, slitBottom - 8, slitRight, slitBottom, 0x00000000, 0xCC000000);
    }

    // ------------------------------------------------------------ заголовок и описание

    private static void title(DrawContext ctx, TextRenderer tr, Track t, int w, int h) {
        boolean big = ClientState.age < BIG_TITLE_TICKS;
        int rgb = t.color;

        if (big) {
            int fade = BIG_TITLE_TICKS - ClientState.age;
            int alpha = fade < 20 ? (int) (255 * fade / 20f) : 255;
            if (alpha > 8) {
                MatrixStack m = ctx.getMatrices();
                m.push();
                m.translate(w / 2f, h / 3f, 0);
                m.scale(4f, 4f, 1f);
                ctx.drawCenteredTextWithShadow(tr, t.title, 0, -4, (alpha << 24) | rgb);
                m.pop();
            }
        }

        // описание (всегда), под большим заголовком или сверху мелким
        int top = big ? h / 3 + 30 : 22;
        if (!big) ctx.drawCenteredTextWithShadow(tr, t.title, w / 2, 8, 0xFF000000 | rgb);
        List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(t.description), Math.min(w - 40, 320));
        int y = top;
        for (OrderedText line : lines) {
            ctx.drawCenteredTextWithShadow(tr, line, w / 2, y, 0xFFFFFFFF);
            y += 10;
        }
    }

    // ------------------------------------------------------------ счётчик

    private static void counter(DrawContext ctx, TextRenderer tr, int w) {
        // от зелёного к красному по мере прослушивания
        float f = MathHelper.clamp(ClientState.listened / (float) (Track.values().length * 2), 0f, 1f);
        int r = (int) (255 * f);
        int g = (int) (255 * (1 - f));
        int color = 0xFF000000 | (r << 16) | (g << 8) | 0x20;

        String text = "Треков: " + ClientState.listened;
        ctx.drawTextWithShadow(tr, text, w - tr.getWidth(text) - 6, 6, color);

        int sec = ClientState.ticksLeft / 20;
        String timer = (ClientState.trackId < 0 ? "Передышка: " : "Трек: ") + (sec / 60) + ":" + String.format("%02d", sec % 60);
        ctx.drawTextWithShadow(tr, timer, w - tr.getWidth(timer) - 6, 17, 0xFFCCCCCC);

        int y = 30;
        if (ClientState.track() == Track.ZOMBIE_APOCALYPSE && ClientState.total > 0) {
            int el = ClientState.total - ClientState.ticksLeft;
            int wave = Math.min(2, el * 3 / ClientState.total) + 1;
            String ws = "Волна " + wave + " / 3";
            ctx.drawTextWithShadow(tr, ws, w - tr.getWidth(ws) - 6, y, 0xFFFF5555);
            y += 11;
        }
        for (GlobalDebuffs.Debuff d : GlobalDebuffs.ALL) {
            if (ClientState.listened >= d.threshold()) {
                String s = "☠ " + d.name();
                ctx.drawTextWithShadow(tr, s, w - tr.getWidth(s) - 6, y, 0xFFFF4444);
                y += 11;
            }
        }
    }

    private HudOverlay() {}
}
