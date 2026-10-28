package com.piratechallenge.client;

import com.piratechallenge.ModeChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Плашка выбора режима после разговора с Серёгой: обычный (трек раз в 1,5 мин) или хард (раз в 30 сек). */
public class ModeScreen extends Screen {
    public ModeScreen() {
        super(Text.literal("Режим"));
    }

    @Override
    protected void init() {
        int bw = 220, bh = 26, x = (this.width - bw) / 2, y = this.height / 2 - 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§aОбычный — трек раз в 1,5 минуты"), b -> choose(false))
                .dimensions(x, y, bw, bh).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§cХард — трек раз в 30 секунд"), b -> choose(true))
                .dimensions(x, y + 34, bw, bh).build());
    }

    private void choose(boolean hard) {
        ClientPlayNetworking.send(new ModeChoicePayload(hard));
        close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // нужно выбрать
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = this.width, h = this.height;
        int pw = 280, ph = 130, px = (w - pw) / 2, py = h / 2 - 60;
        // плашка под кнопками (кнопки уже нарисованы super.render, поэтому рамку рисуем вокруг)
        ctx.fill(px - 2, py - 2, px + pw + 2, py + 18, 0xFFFFCC33);
        ctx.fill(px, py, px + pw, py + 16, 0xFF222233);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "ВЫБЕРИ РЕЖИМ", w / 2, py + 4, 0xFFFFDD55);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Как часто будут играть треки Сереги Пирата?", w / 2, py + 26, 0xFFFFFFFF);
    }
}
