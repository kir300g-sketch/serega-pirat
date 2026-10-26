package com.piratechallenge.client;

import com.piratechallenge.ChessResultPayload;
import com.piratechallenge.client.ChessEngine.Move;
import com.piratechallenge.client.ChessEngine.Position;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * «Почему ты ещё не фанат?» — шахматная партия на весь экран против бота «Хикару Накамура».
 * Игрок — белые. Клик по фигуре, затем по клетке. Пешка при превращении автоматически становится ферзём.
 * ESC закрывает доску на 2.5 сек (чтобы можно было выйти в меню), потом она возвращается.
 */
public class ChessScreen extends Screen {
    public static long reopenAtMs = 0;
    public static boolean finished = false;   // партия закончена в этом треке — экран больше не открываем
    private static Position savedPos;          // чтобы партия не сбрасывалась при закрытии экрана
    private static boolean savedStarted;

    private static final String[] GLYPH = {"", "♟", "♞", "♝", "♜", "♛", "♚"};

    private Position pos;
    private int sel = -1;
    private List<Move> targets = new ArrayList<>();
    private volatile Move pending;
    private volatile boolean thinking;
    private String status = "Твой ход (белые)";
    private int lastFrom = -1, lastTo = -1;

    public ChessScreen() {
        super(Text.literal("Шахматы"));
        if (!savedStarted || savedPos == null) {
            savedPos = Position.start();
            savedStarted = true;
            finished = false;
        }
        this.pos = savedPos;
    }

    /** Сброс на новый трек. */
    public static void resetGame() {
        savedPos = null;
        savedStarted = false;
        finished = false;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сдаться"), b -> resign())
                .dimensions(this.width - 90, this.height - 30, 80, 20).build());
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

    // ---------------------------------------------------------------- геометрия

    private int cell() {
        return Math.max(16, (int) (Math.min(this.width, this.height) * 0.86f / 8));
    }

    private int boardX() { return (this.width - 8 * cell()) / 2; }

    private int boardY() { return (this.height - 8 * cell()) / 2; }

    private int squareAt(double mx, double my) {
        int c = (int) Math.floor((mx - boardX()) / cell());
        int r = (int) Math.floor((my - boardY()) / cell());
        if (c < 0 || c > 7 || r < 0 || r > 7) return -1;
        return r * 8 + c;
    }

    // ---------------------------------------------------------------- логика

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (finished || thinking || !pos.white) return true;
        int sq = squareAt(mx, my);
        if (sq < 0) return true;

        // выбранный ход?
        if (sel >= 0) {
            for (Move m : targets) {
                if (m.to() == sq && (m.promo() == 0 || m.promo() == 5)) {
                    play(m);
                    return true;
                }
            }
        }
        // выбор своей фигуры
        if (pos.b[sq] > 0) {
            sel = sq;
            targets = new ArrayList<>();
            for (Move m : ChessEngine.legal(pos)) if (m.from() == sq) targets.add(m);
        } else {
            sel = -1;
            targets.clear();
        }
        return true;
    }

    private void play(Move m) {
        pos = ChessEngine.apply(pos, m);
        savedPos = pos;
        lastFrom = m.from();
        lastTo = m.to();
        sel = -1;
        targets.clear();
        if (checkEnd()) return;
        status = "Хикару думает...";
        thinking = true;
        final Position snapshot = pos;
        Thread t = new Thread(() -> pending = ChessEngine.bestMove(snapshot), "pirate-chess-ai");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void tick() {
        Move m = pending;
        if (m != null) {
            pending = null;
            thinking = false;
            pos = ChessEngine.apply(pos, m);
            savedPos = pos;
            lastFrom = m.from();
            lastTo = m.to();
            if (!checkEnd()) status = ChessEngine.inCheck(pos, true) ? "Тебе шах! Твой ход" : "Твой ход (белые)";
        }
        if (ClientState.track() != com.piratechallenge.Track.FANAT) close();
    }

    private boolean checkEnd() {
        List<Move> legal = ChessEngine.legal(pos);
        if (legal.isEmpty()) {
            if (ChessEngine.inCheck(pos, pos.white)) {
                // мат: проиграл тот, чей ход
                if (pos.white) finish(-1, "Мат. Хикару победил.");
                else finish(1, "Мат! Ты победил Хикару!");
            } else {
                finish(0, "Пат. Ничья.");
            }
            return true;
        }
        if (pos.half >= 100) {
            finish(0, "Ничья (правило 50 ходов).");
            return true;
        }
        return false;
    }

    private void resign() {
        if (!finished) finish(-1, "Ты сдался.");
    }

    private void finish(int result, String text) {
        finished = true;
        status = text;
        ClientPlayNetworking.send(new ChessResultPayload(result));
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xEE1B1B1F);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        int cs = cell(), bx = boardX(), by = boardY();
        var tr = this.textRenderer;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int sq = r * 8 + c;
                boolean light = (r + c) % 2 == 0;
                int col = light ? 0xFFEEEED2 : 0xFF769656;
                if (sq == lastFrom || sq == lastTo) col = light ? 0xFFF7EC74 : 0xFFD7C94A;
                if (sq == sel) col = 0xFFFFE066;
                ctx.fill(bx + c * cs, by + r * cs, bx + (c + 1) * cs, by + (r + 1) * cs, col);
            }
        }
        // подсказки ходов
        for (Move m : targets) {
            int r = m.to() / 8, c = m.to() % 8;
            int cx = bx + c * cs + cs / 2, cy = by + r * cs + cs / 2, rad = Math.max(3, cs / 7);
            ctx.fill(cx - rad, cy - rad, cx + rad, cy + rad, 0x88000000);
        }
        // фигуры
        MatrixStack ms = ctx.getMatrices();
        float scale = cs / 9.5f;
        for (int i = 0; i < 64; i++) {
            int v = this.pos.b[i];
            if (v == 0) continue;
            String g = GLYPH[Math.abs(v)];
            int r = i / 8, c = i % 8;
            ms.push();
            ms.translate(bx + c * cs + cs / 2f - 4f * scale, by + r * cs + cs / 2f - 4.5f * scale, 0);
            ms.scale(scale, scale, 1f);
            if (v > 0) {
                ctx.drawText(tr, g, 1, 1, 0xFF000000, false);   // белые: светлая фигура с тёмной тенью
                ctx.drawText(tr, g, 0, 0, 0xFFFFFFFF, false);
            } else {
                ctx.drawText(tr, g, 0, 0, 0xFF111111, false);
            }
            ms.pop();
        }
        // подписи сторон
        ctx.drawCenteredTextWithShadow(tr, "Хикару Накамура (бот)", this.width / 2, Math.max(4, by - 12), 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(tr, "Ты (белые)", this.width / 2, by + 8 * cs + 4, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, status, 10, 10, finished ? 0xFFFFDD55 : 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "Трек идёт — партия тоже", 10, 24, 0xFF999999);

        super.render(ctx, mouseX, mouseY, delta);
    }
}
