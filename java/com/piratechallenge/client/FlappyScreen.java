package com.piratechallenge.client;

import com.piratechallenge.FlappyDeathPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * «Я не боюсь ошибаться»: Флаппи Бёрд на весь экран до конца трека.
 * Пробел / W / стрелка вверх / ЛКМ — взмах. Каждая смерть птицы отправляет пакет на сервер (−1 сердце).
 * ESC закрывает игру на 2.5 секунды, потом она возвращается.
 */
public class FlappyScreen extends Screen {
    public static long reopenAtMs = 0;

    private static class Pipe {
        float x;
        float gapY;
        boolean passed;
    }

    private final Random rng = new Random();
    private final List<Pipe> pipes = new ArrayList<>();
    private float birdY;
    private float vel;
    private int score;
    private int deadTicks;      // >0 — птица разбилась, ждём перезапуск
    private int ticks;
    private float spawnTimer;

    public FlappyScreen() {
        super(Text.literal("Flappy"));
        restart();
    }

    private void restart() {
        pipes.clear();
        birdY = 0.5f;
        vel = 0;
        spawnTimer = 0;
        deadTicks = 0;
        score = 0;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        reopenAtMs = Util.getMeasuringTimeMs() + 2500;
        super.close();
    }

    // Единицы: высота экрана = 1.0. Скорость и физика заданы в долях высоты за тик.
    private float u() { return this.height; }

    private float birdX() { return this.width * 0.25f; }

    private float birdR() { return u() * 0.035f; }

    private float pipeW() { return u() * 0.11f; }

    private float gap() { return u() * (0.30f - Math.min(0.06f, ticks / 4000f)); } // со временем уже

    private float speed() { return u() * (0.0075f + Math.min(0.004f, ticks / 20000f)); }

    private void flap() {
        if (deadTicks > 0) return;
        vel = -0.0165f;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        flap();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_UP) {
            flap();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (ClientState.track() != com.piratechallenge.Track.FLAPPY) {
            close();
            return;
        }
        if (ClientState.paused) return;
        ticks++;

        if (deadTicks > 0) {
            if (--deadTicks == 0) restart();
            return;
        }

        vel += 0.00085f;   // гравитация (доли высоты за тик)
        birdY += vel;

        spawnTimer -= speed();
        if (spawnTimer <= 0) {
            Pipe p = new Pipe();
            p.x = this.width + pipeW();
            float margin = 0.12f + gap() / u() / 2f;
            p.gapY = margin + rng.nextFloat() * (1f - 2 * margin);
            pipes.add(p);
            spawnTimer = this.width * 0.48f;
        }
        for (Pipe p : pipes) {
            p.x -= speed();
            if (!p.passed && p.x + pipeW() < birdX() - birdR()) {
                p.passed = true;
                score++;
            }
        }
        pipes.removeIf(p -> p.x < -pipeW() * 2);

        // столкновения
        float by = birdY * u();
        boolean hit = by < birdR() || by > u() - birdR();
        for (Pipe p : pipes) {
            float gy = p.gapY * u();
            boolean overlapX = birdX() + birdR() > p.x && birdX() - birdR() < p.x + pipeW();
            if (overlapX && (by - birdR() < gy - gap() / 2 || by + birdR() > gy + gap() / 2)) hit = true;
        }
        if (hit) {
            deadTicks = 30;
            ClientPlayNetworking.send(new FlappyDeathPayload(score));
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, 0xFF66CCFF, 0xFFBFEFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        int w = this.width, h = this.height;

        // земля
        ctx.fill(0, h - 6, w, h, 0xFF8B5A2B);
        ctx.fill(0, h - 8, w, h - 6, 0xFF7ED957);

        // трубы
        for (Pipe p : pipes) {
            int x1 = (int) p.x, x2 = (int) (p.x + pipeW());
            int top = (int) (p.gapY * h - gap() / 2);
            int bot = (int) (p.gapY * h + gap() / 2);
            ctx.fill(x1, 0, x2, top, 0xFF3DAA3D);
            ctx.fill(x1 - 3, top - 10, x2 + 3, top, 0xFF52C752);
            ctx.fill(x1, bot, x2, h - 8, 0xFF3DAA3D);
            ctx.fill(x1 - 3, bot, x2 + 3, bot + 10, 0xFF52C752);
            ctx.fill(x1 + 3, 0, x1 + 6, top, 0xFF7BE07B);
            ctx.fill(x1 + 3, bot + 10, x1 + 6, h - 8, 0xFF7BE07B);
        }

        // птица
        int bx = (int) birdX(), by = (int) (birdY * h), r = (int) birdR();
        int body = deadTicks > 0 ? 0xFFFF5555 : 0xFFFFDD33;
        ctx.fill(bx - r, by - r, bx + r, by + r, 0xFF000000);
        ctx.fill(bx - r + 2, by - r + 2, bx + r - 2, by + r - 2, body);
        ctx.fill(bx + r / 3, by - r / 2, bx + r - 3, by - r / 6, 0xFFFFFFFF); // глаз
        ctx.fill(bx + r - 4, by, bx + r + 5, by + r / 3, 0xFFFF8800);         // клюв
        ctx.fill(bx - r, by + 1, bx - 3, by + r / 2, 0xFFE8B800);             // крыло

        // счёт и подсказки
        var tr = this.textRenderer;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2f, 22, 0);
        ctx.getMatrices().scale(3f, 3f, 1f);
        ctx.drawCenteredTextWithShadow(tr, String.valueOf(score), 0, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(tr, "Пробел / ЛКМ — взмах. Смерть = −1 сердце", w / 2, h - 22, 0xFF224422);
        if (deadTicks > 0) {
            ctx.drawCenteredTextWithShadow(tr, "Разбился! −1 сердце", w / 2, h / 2 - 20, 0xFFFF2222);
        }
    }
}
