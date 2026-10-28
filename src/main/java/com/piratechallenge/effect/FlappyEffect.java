package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * «Я не боюсь ошибаться»: Флаппи Бёрд на весь экран до конца трека (экран — на клиенте).
 * Каждая смерть птицы = −2 HP (сердце). Мобы игрока в это время не трогают (см. PirateChallenge / ChallengeManager).
 */
public class FlappyEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.flappyCool = 0;
        p.sendMessage(Text.literal("§eСерёга не боялся ошибаться — и ты не бойся! Пробел / ЛКМ — взмах. Смерть птицы = −1 сердце."), false);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        if (d.flappyCool > 0) d.flappyCool--;
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {}

    public static void onDeath(ServerPlayerEntity p, int score) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FLAPPY || d.flappyCool > 0) return;
        d.flappyCool = 15; // защита от спама пакетами
        p.damage(p.getDamageSources().magic(), 2.0f);
        EffectUtil.bar(p, "§cПтица разбилась! −1 сердце (счёт: " + score + ")");
    }
}
