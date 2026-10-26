package com.piratechallenge;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/** Навязчивые мысли (депрессия после 10 прослушанных треков). Добавляй свои строки в список. */
public final class Thoughts {
    private static final Random RNG = new Random();

    private static final List<String> LINES = List.of(
            "как же достала эта музыка...",
            "а может сломать колонки?",
            "а может уйти жить в пещеру, где тихо?",
            "опять этот бит... опять этот голос...",
            "почему я вообще это слушаю?",
            "хочу тишины. просто тишины.",
            "а может улететь на Луну? Там звука нет.",
            "снова трек... сколько можно.",
            "а может заткнуть уши блоками земли?",
            "никто не спрашивал, хочу ли я это слушать.",
            "всё серое. и это ещё не Рассел Кроу.",
            "может, просто выключить звук? ...нет, нельзя."
    );

    public static void send(ServerPlayerEntity p) {
        String line = LINES.get(RNG.nextInt(LINES.size()));
        p.sendMessage(Text.literal("[Мысли] " + line).formatted(Formatting.GRAY, Formatting.ITALIC), false);
    }

    public static int nextDelayTicks() {
        return (30 + RNG.nextInt(60)) * 20;
    }

    private Thoughts() {}
}
