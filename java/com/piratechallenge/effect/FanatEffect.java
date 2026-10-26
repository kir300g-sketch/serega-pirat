package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** «Почему ты ещё не фанат?»: шахматная партия (экран и ИИ — на клиенте), здесь только награда/штраф по итогу. */
public class FanatEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        p.sendMessage(Text.literal("§fХикару Накамура вызывает тебя на партию. Ты играешь белыми."), false);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {}

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {}

    public static void onResult(ServerPlayerEntity p, int result) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.FANAT) return;
        if (result > 0) {
            p.sendMessage(Text.literal("§a§lТы победил Хикару! Награда: 3 золотых яблока."), false);
            p.getInventory().offerOrDrop(new ItemStack(Items.GOLDEN_APPLE, 3));
        } else if (result < 0) {
            p.sendMessage(Text.literal("§cХикару победил. Ты ослаблен на 30 секунд."), false);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 30 * 20, 0));
        } else {
            p.sendMessage(Text.literal("§7Ничья. Хикару одобрительно кивает."), false);
        }
    }
}
