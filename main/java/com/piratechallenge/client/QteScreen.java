package com.piratechallenge.client;

import com.piratechallenge.QteResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * «Ну где моя нога?»: нужно быстро набрать буквы по порядку. Экран перехватывает клавиши,
 * чтобы Q/E/T и т.п. не выбрасывали предметы и не открывали инвентарь/чат.
 */
public class QteScreen extends Screen {
    private final String letters;
    private int idx;
    private int ticksLeft;
    private final int ticksTotal;
    private int flash;

    public QteScreen() {
        super(Text.literal("QTE"));
        this.letters = ClientState.qteLetters;
        this.ticksTotal = Math.max(1, ClientState.qteTicks);
        this.ticksLeft = ClientState.qteTicks;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // без размытия: игру должно быть видно
    }

    @Override
    public void tick() {
        if (letters.isEmpty() || ClientState.qteTicks <= 0) {
            close();
            return;
        }
        if (--ticksLeft <= 0) {
            ClientState.qteTicks = 0;
            close();
        }
        if (flash > 0) flash--;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            char c = (char) ('A' + (keyCode - GLFW.GLFW_KEY_A));
            if (c == letters.charAt(idx)) {
                idx++;
                if (idx >= letters.length()) {
                    ClientPlayNetworking.send(new QteResultPayload(true));
                    ClientState.qteTicks = 0;
                    close();
                }
            } else {
                idx = 0; // ошибка — начинаем заново
                flash = 6;
            }
        }
        return true; // поглощаем все клавиши
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int w = this.width, h = this.height;
        ctx.fill(0, 0, w, h, 0x44550000); // красноватый оттенок

        var tr = this.textRenderer;
        ctx.drawCenteredTextWithShadow(tr, "КРОВОТЕЧЕНИЕ! Набери буквы:", w / 2, h / 2 - 50, 0xFFFF5555);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(w / 2f, h / 2f - 20, 0);
        m.scale(3f, 3f, 1f);
        int total = letters.length() * 14;
        int x = -total / 2;
        for (int i = 0; i < letters.length(); i++) {
            int color = i < idx ? 0xFF55FF55 : (i == idx ? (flash > 0 ? 0xFFFF2222 : 0xFFFFFF55) : 0xFFAAAAAA);
            ctx.drawTextWithShadow(tr, String.valueOf(letters.charAt(i)), x + i * 14, 0, color);
        }
        m.pop();

        // полоса времени
        int barW = 160;
        int filled = (int) (barW * (ticksLeft / (float) ticksTotal));
        ctx.fill(w / 2 - barW / 2, h / 2 + 30, w / 2 + barW / 2, h / 2 + 38, 0xFF333333);
        ctx.fill(w / 2 - barW / 2, h / 2 + 30, w / 2 - barW / 2 + filled, h / 2 + 38, 0xFFCC2222);
    }
}
