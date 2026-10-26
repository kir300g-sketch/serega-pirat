package com.piratechallenge;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/**
 * Навязчивые мысли и слуховые галлюцинации. Чем больше прослушано треков, тем сильнее персонаж сходит с ума:
 *  10+ — мысли о музыке и усталость; 20+ — путаница и слуховые галлюцинации; 25+ — паранойя (чаще и страннее).
 * Добавляй свои фразы в списки.
 */
public final class Thoughts {
    private static final Random RNG = new Random();

    private static final List<String> TIER1 = List.of(
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
            "может, просто выключить звук? ...нет, нельзя.");

    private static final List<String> TIER2 = List.of(
            "я слышу бит, даже когда он выключен.",
            "стены подпевают. они знают слова.",
            "треки идут по кругу... или это я хожу по кругу?",
            "кажется, это уже не музыка играет во мне, а я играю в ней.",
            "кто-то только что сказал моё имя. нет, никого.",
            "если долго слушать, начинаешь различать, что шепчет бит.",
            "я больше не уверен, какой сейчас трек.",
            "почему все мобы смотрят на меня в такт?");

    private static final List<String> TIER3 = List.of(
            "за спиной кто-то стоит. я оборачиваюсь — никого. но он стоит.",
            "они все в сговоре. каждый моб знает, какой трек следующий.",
            "не оглядывайся. не оглядывайся. не оглядывайся.",
            "это не я придумал эти мысли. кто-то вкладывает их мне в голову.",
            "здесь слишком тихо между треками. это тишина, которая подслушивает.");

    private static final List<SoundEvent> SOUNDS = List.of(
            SoundEvents.ENTITY_CREEPER_PRIMED, SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundEvents.ENTITY_ENDERMAN_STARE,
            SoundEvents.ENTITY_SKELETON_AMBIENT, SoundEvents.ENTITY_SPIDER_AMBIENT, SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
            SoundEvents.ENTITY_GHAST_AMBIENT, SoundEvents.ENTITY_PHANTOM_AMBIENT);

    public static void send(ServerPlayerEntity p, int listened) {
        List<String> pool;
        if (listened >= 25 && RNG.nextBoolean()) pool = TIER3;
        else if (listened >= 20 && RNG.nextBoolean()) pool = TIER2;
        else pool = TIER1;
        String line = pool.get(RNG.nextInt(pool.size()));
        p.sendMessage(Text.literal("[Мысли] " + line).formatted(Formatting.GRAY, Formatting.ITALIC), false);
    }

    /** Слуховая галлюцинация: звук, которого нет в мире (слышит только игрок). Безумие 20+. */
    public static void hallucinate(ServerPlayerEntity p) {
        SoundEvent s = SOUNDS.get(RNG.nextInt(SOUNDS.size()));
        p.playSoundToPlayer(s, SoundCategory.HOSTILE, 0.8f, 0.6f + RNG.nextFloat() * 0.8f);
    }

    public static int nextDelayTicks(int listened) {
        int base = listened >= 25 ? 20 : 30;
        return (base + RNG.nextInt(60)) * 20;
    }

    public static int nextHallDelayTicks(int listened) {
        int base = listened >= 25 ? 25 : 45;
        return (base + RNG.nextInt(60)) * 20;
    }

    private Thoughts() {}
}
