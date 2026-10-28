package com.piratechallenge.client;

import com.piratechallenge.MinerResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.Random;

/**
 * «Минёр»: сапёр 9×9 с 10 минами. ЛКМ — открыть, ПКМ — флажок. Первый клик всегда безопасный.
 * Подорвался — экран закрывается на ~6 секунд (на сервере в это время идёт дождь из TNT), потом новая игра.
 * Прошёл поле — награда (сердца) и новая игра. ESC закрывает на 2.5 сек (можно выйти в меню).
 */
public class MinesweeperScreen extends Screen {
    public static long reopenAtMs = 0;

    private static final int N = 9, MINES = 10;
    private static final Random RNG = new Random();

    private final boolean[][] mine = new boolean[N][N];
    private final boolean[][] open = new boolean[N][N];
    private final boolean[][] flag = new boolean[N][N];
    private final int[][] near = new int[N][N];
    private boolean placed;
    private boolean lost, won;
    private int endTicks;

    public MinesweeperScreen() {
        super(Text.literal("Сапёр"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        reopenAtMs = Util.getMeasuringTimeMs() + 2500;
        super.close();
    }

    private int cell() { return Math.max(14, (int) (Math.min(width, height) * 0.8f / N)); }

    private int bx() { return (width - N * cell()) / 2; }

    private int by() { return (height - N * cell()) / 2; }

    private void placeMines(int sx, int sy) {
        int put = 0;
        while (put < MINES) {
            int x = RNG.nextInt(N), y = RNG.nextInt(N);
            if (mine[x][y] || (Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1)) continue; // вокруг первого клика мин нет
            mine[x][y] = true;
            put++;
        }
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                int c = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < N && ny < N && mine[nx][ny]) c++;
                }
                near[x][y] = c;
            }
        }
        placed = true;
    }

    private void reveal(int x, int y) {
        if (x < 0 || y < 0 || x >= N || y >= N || open[x][y] || flag[x][y]) return;
        open[x][y] = true;
        if (near[x][y] == 0 && !mine[x][y]) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) reveal(x + dx, y + dy);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (lost || won) return true;
        int x = (int) Math.floor((mx - bx()) / cell()), y = (int) Math.floor((my - by()) / cell());
        if (x < 0 || y < 0 || x >= N || y >= N) return true;

        if (button == 1) {                        // ПКМ — флажок
            if (!open[x][y]) flag[x][y] = !flag[x][y];
            return true;
        }
        if (flag[x][y]) return true;
        if (!placed) placeMines(x, y);
        if (mine[x][y]) {                         // подорвался
            lost = true;
            endTicks = 30;
            for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) if (mine[i][j]) open[i][j] = true;
            return true;
        }
        reveal(x, y);
        // победа: открыты все клетки без мин
        int closedSafe = 0;
        for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) if (!mine[i][j] && !open[i][j]) closedSafe++;
        if (closedSafe == 0) {
            won = true;
            endTicks = 30;
        }
        return true;
    }

    @Override
    public void tick() {
        if (ClientState.track() != com.piratechallenge.Track.MINER) { close(); return; }
        if (lost || won) {
            if (--endTicks <= 0) {
                ClientPlayNetworking.send(new MinerResultPayload(won));
                if (lost) ClientState.minerWait = 120; // 6 секунд без сапёра — идёт дождь TNT
                reopenAtMs = 0;
                super.close();                          // без задержки в 2.5 сек
            }
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xFF33414F, 0xFF16202A);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // фон рисует super.render — поле поверх
        var tr = this.textRenderer;
        int cs = cell(), bx = bx(), by = by();

        ctx.fill(bx - 5, by - 5, bx + N * cs + 5, by + N * cs + 5, 0xFF222222);
        int[] numCol = {0, 0xFF3366FF, 0xFF22AA22, 0xFFEE3333, 0xFF1111AA, 0xFFAA2222, 0xFF11AAAA, 0xFF111111, 0xFF777777};
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                int x1 = bx + x * cs, y1 = by + y * cs;
                boolean o = open[x][y];
                ctx.fill(x1, y1, x1 + cs - 1, y1 + cs - 1, o ? (mine[x][y] ? 0xFFEE5555 : 0xFFDDDDDD) : 0xFFAAB4BF);
                if (!o) {
                    ctx.fill(x1, y1, x1 + cs - 1, y1 + 2, 0xFFFFFFFF);
                    ctx.fill(x1, y1, x1 + 2, y1 + cs - 1, 0xFFFFFFFF);
                }
                String s = null;
                int col = 0xFF000000;
                if (o && mine[x][y]) { s = "✹"; col = 0xFF000000; }
                else if (o && near[x][y] > 0) { s = String.valueOf(near[x][y]); col = numCol[near[x][y]]; }
                else if (!o && flag[x][y]) { s = "⚑"; col = 0xFFCC0000; }
                if (s != null) ctx.drawCenteredTextWithShadow(tr, s, x1 + cs / 2, y1 + cs / 2 - 4, col);
            }
        }
        ctx.drawCenteredTextWithShadow(tr, "Минёр — ЛКМ: открыть, ПКМ: флажок. Мин: " + MINES, width / 2, Math.max(6, by - 16), 0xFFFFFFFF);
        if (lost) ctx.drawCenteredTextWithShadow(tr, "БАХ! Сейчас польётся TNT...", width / 2, by + N * cs + 10, 0xFFFF5555);
        if (won) ctx.drawCenteredTextWithShadow(tr, "Поле разминировано!", width / 2, by + N * cs + 10, 0xFF55FF55);
    }
}
