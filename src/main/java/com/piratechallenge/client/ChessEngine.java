package com.piratechallenge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Компактный шахматный движок: полные правила (рокировка, взятие на проходе, превращение, шах/мат/пат)
 * и перебор alpha-beta. Клетки: индекс = ряд*8 + колонка, ряд 0 — восьмая горизонталь (сверху), колонка 0 — линия «a».
 * Фигуры: 1 пешка, 2 конь, 3 слон, 4 ладья, 5 ферзь, 6 король; белые > 0, чёрные < 0.
 */
public final class ChessEngine {
    /** Глубина поиска бота. 3 — играет прилично, но обыграть можно; 4 — заметно сильнее и дольше думает. */
    public static final int DEPTH = 3;

    public record Move(int from, int to, int promo) {}

    public static final class Position {
        public final int[] b = new int[64];
        public boolean white = true;
        boolean wK = true, wQ = true, bK = true, bQ = true;
        int ep = -1;
        int half = 0;

        Position copy() {
            Position p = new Position();
            System.arraycopy(b, 0, p.b, 0, 64);
            p.white = white; p.wK = wK; p.wQ = wQ; p.bK = bK; p.bQ = bQ; p.ep = ep; p.half = half;
            return p;
        }

        public static Position start() {
            Position p = new Position();
            int[] back = {4, 2, 3, 5, 6, 3, 2, 4};
            for (int c = 0; c < 8; c++) {
                p.b[c] = -back[c];
                p.b[8 + c] = -1;
                p.b[48 + c] = 1;
                p.b[56 + c] = back[c];
            }
            return p;
        }
    }

    private static final int[] VAL = {0, 100, 320, 330, 500, 900, 20000};
    private static final int[][] KN = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    private static final int[][] KG = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    private static final Random RNG = new Random();

    // ---------------------------------------------------------------- атаки и шах

    static boolean attacked(Position p, int sq, boolean byWhite) {
        int r = sq / 8, c = sq % 8;
        int sign = byWhite ? 1 : -1;
        // пешки
        int pr = r + (byWhite ? 1 : -1);
        for (int dc = -1; dc <= 1; dc += 2) {
            int pc = c + dc;
            if (pr >= 0 && pr < 8 && pc >= 0 && pc < 8 && p.b[pr * 8 + pc] == sign) return true;
        }
        for (int[] d : KN) {
            int rr = r + d[0], cc = c + d[1];
            if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && p.b[rr * 8 + cc] == 2 * sign) return true;
        }
        for (int[] d : KG) {
            int rr = r + d[0], cc = c + d[1];
            if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && p.b[rr * 8 + cc] == 6 * sign) return true;
        }
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                boolean diag = dr != 0 && dc != 0;
                int rr = r + dr, cc = c + dc;
                while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                    int v = p.b[rr * 8 + cc];
                    if (v != 0) {
                        if (v * sign > 0) {
                            int t = Math.abs(v);
                            if (t == 5 || (diag && t == 3) || (!diag && t == 4)) return true;
                        }
                        break;
                    }
                    rr += dr; cc += dc;
                }
            }
        }
        return false;
    }

    public static boolean inCheck(Position p, boolean white) {
        int k = white ? 6 : -6;
        for (int i = 0; i < 64; i++) if (p.b[i] == k) return attacked(p, i, !white);
        return false;
    }

    // ---------------------------------------------------------------- генерация ходов

    private static void add(List<Move> out, Position p, int from, int to) {
        int piece = p.b[from];
        int row = to / 8;
        if (Math.abs(piece) == 1 && (row == 0 || row == 7)) {
            for (int promo : new int[]{5, 4, 3, 2}) out.add(new Move(from, to, promo));
        } else {
            out.add(new Move(from, to, 0));
        }
    }

    private static List<Move> pseudo(Position p) {
        List<Move> out = new ArrayList<>();
        int sign = p.white ? 1 : -1;
        for (int i = 0; i < 64; i++) {
            int v = p.b[i];
            if (v * sign <= 0) continue;
            int t = Math.abs(v), r = i / 8, c = i % 8;
            switch (t) {
                case 1 -> {
                    int dir = p.white ? -1 : 1;
                    int startRow = p.white ? 6 : 1;
                    int r1 = r + dir;
                    if (r1 >= 0 && r1 < 8 && p.b[r1 * 8 + c] == 0) {
                        add(out, p, i, r1 * 8 + c);
                        int r2 = r + 2 * dir;
                        if (r == startRow && p.b[r2 * 8 + c] == 0) out.add(new Move(i, r2 * 8 + c, 0));
                    }
                    for (int dc = -1; dc <= 1; dc += 2) {
                        int cc = c + dc;
                        if (r1 < 0 || r1 > 7 || cc < 0 || cc > 7) continue;
                        int to = r1 * 8 + cc;
                        if (p.b[to] * sign < 0 || to == p.ep) add(out, p, i, to);
                    }
                }
                case 2 -> {
                    for (int[] d : KN) {
                        int rr = r + d[0], cc = c + d[1];
                        if (rr < 0 || rr > 7 || cc < 0 || cc > 7) continue;
                        if (p.b[rr * 8 + cc] * sign <= 0) out.add(new Move(i, rr * 8 + cc, 0));
                    }
                }
                case 3, 4, 5 -> {
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            if (dr == 0 && dc == 0) continue;
                            boolean diag = dr != 0 && dc != 0;
                            if (t == 3 && !diag) continue;
                            if (t == 4 && diag) continue;
                            int rr = r + dr, cc = c + dc;
                            while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                                int tv = p.b[rr * 8 + cc];
                                if (tv * sign > 0) break;
                                out.add(new Move(i, rr * 8 + cc, 0));
                                if (tv != 0) break;
                                rr += dr; cc += dc;
                            }
                        }
                    }
                }
                case 6 -> {
                    for (int[] d : KG) {
                        int rr = r + d[0], cc = c + d[1];
                        if (rr < 0 || rr > 7 || cc < 0 || cc > 7) continue;
                        if (p.b[rr * 8 + cc] * sign <= 0) out.add(new Move(i, rr * 8 + cc, 0));
                    }
                    // рокировка
                    int home = p.white ? 60 : 4;
                    if (i == home && !attacked(p, home, !p.white)) {
                        boolean ks = p.white ? p.wK : p.bK;
                        boolean qs = p.white ? p.wQ : p.bQ;
                        if (ks && p.b[home + 1] == 0 && p.b[home + 2] == 0 && p.b[home + 3] == 4 * sign
                                && !attacked(p, home + 1, !p.white) && !attacked(p, home + 2, !p.white)) {
                            out.add(new Move(home, home + 2, 0));
                        }
                        if (qs && p.b[home - 1] == 0 && p.b[home - 2] == 0 && p.b[home - 3] == 0 && p.b[home - 4] == 4 * sign
                                && !attacked(p, home - 1, !p.white) && !attacked(p, home - 2, !p.white)) {
                            out.add(new Move(home, home - 2, 0));
                        }
                    }
                }
                default -> {}
            }
        }
        return out;
    }

    public static Position apply(Position p, Move m) {
        Position n = p.copy();
        int piece = n.b[m.from()];
        int captured = n.b[m.to()];
        int t = Math.abs(piece);
        int sign = piece > 0 ? 1 : -1;

        // взятие на проходе
        if (t == 1 && m.to() == p.ep && m.from() % 8 != m.to() % 8 && captured == 0) {
            n.b[m.to() + (piece > 0 ? 8 : -8)] = 0;
            captured = -sign;
        }
        n.b[m.to()] = piece;
        n.b[m.from()] = 0;
        if (m.promo() != 0) n.b[m.to()] = m.promo() * sign;

        // рокировка: переносим ладью
        if (t == 6 && Math.abs(m.to() - m.from()) == 2) {
            if (m.to() > m.from()) { n.b[m.from() + 1] = n.b[m.from() + 3]; n.b[m.from() + 3] = 0; }
            else { n.b[m.from() - 1] = n.b[m.from() - 4]; n.b[m.from() - 4] = 0; }
        }
        // права на рокировку
        if (t == 6) { if (piece > 0) { n.wK = false; n.wQ = false; } else { n.bK = false; n.bQ = false; } }
        for (int sq : new int[]{m.from(), m.to()}) {
            if (sq == 63) n.wK = false;
            if (sq == 56) n.wQ = false;
            if (sq == 7) n.bK = false;
            if (sq == 0) n.bQ = false;
        }
        // проходная пешка
        n.ep = (t == 1 && Math.abs(m.to() - m.from()) == 16) ? (m.from() + m.to()) / 2 : -1;
        n.half = (t == 1 || captured != 0) ? 0 : p.half + 1;
        n.white = !p.white;
        return n;
    }

    public static List<Move> legal(Position p) {
        List<Move> out = new ArrayList<>();
        for (Move m : pseudo(p)) {
            Position n = apply(p, m);
            if (!inCheck(n, p.white)) out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- оценка и поиск

    private static int eval(Position p) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int v = p.b[i];
            if (v == 0) continue;
            int t = Math.abs(v), r = i / 8, c = i % 8;
            int s = VAL[t];
            double center = 3.5 - Math.max(Math.abs(r - 3.5), Math.abs(c - 3.5)); // 0..3
            if (t == 1) s += (v > 0 ? (6 - r) : (r - 1)) * 6 + (int) (center * 3);
            else if (t == 2 || t == 3) s += (int) (center * 9);
            else if (t == 5) s += (int) (center * 2);
            score += v > 0 ? s : -s;
        }
        return score;
    }

    private static int order(Position p, Move m) {
        int cap = Math.abs(p.b[m.to()]);
        return cap * 10 - Math.abs(p.b[m.from()]) + (m.promo() != 0 ? 8 : 0);
    }

    private static int search(Position p, int depth, int alpha, int beta) {
        List<Move> moves = legal(p);
        if (moves.isEmpty()) return inCheck(p, p.white) ? -100000 - depth : 0;
        if (p.half >= 100) return 0;
        if (depth == 0) return eval(p) * (p.white ? 1 : -1);
        moves.sort((a, b) -> order(p, b) - order(p, a));
        int best = Integer.MIN_VALUE + 1;
        for (Move m : moves) {
            int sc = -search(apply(p, m), depth - 1, -beta, -alpha);
            if (sc > best) best = sc;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    /** Лучший ход для стороны, чей ход (вызывать в фоновом потоке). */
    public static Move bestMove(Position p) {
        List<Move> moves = legal(p);
        if (moves.isEmpty()) return null;
        moves.sort((a, b) -> order(p, b) - order(p, a));
        List<Move> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        for (Move m : moves) {
            int sc = -search(apply(p, m), DEPTH - 1, -1000000, 1000000);
            if (sc > bestScore) { bestScore = sc; best.clear(); best.add(m); }
            else if (sc == bestScore) best.add(m);
        }
        return best.get(RNG.nextInt(best.size()));
    }

    private ChessEngine() {}
}
