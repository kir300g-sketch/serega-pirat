package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/** Серый мир и волосы на экране — на клиенте. Здесь: каждые 20 сек полная чернота (слепота на 2 сек). */
public class CroweEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.bar(p, "§7Ты дед инсайд...");
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        if (elapsed > 0 && elapsed % 400 == 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.removeStatusEffect(StatusEffects.BLINDNESS);
    }
}
