package com.piratechallenge;

import com.piratechallenge.effect.EffectUtil;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

/** «Санкции» (после трека «Гей»): пока sanctionTracks > 0 — максимум здоровья −2 сердца и копание медленнее. */
public final class Sanctions {
    public static void apply(ServerPlayerEntity p, PlayerData d) {
        if (p.age % 20 != 0) return;
        if (d.sanctionTracks > 0) {
            EffectUtil.ensure(p, EntityAttributes.GENERIC_MAX_HEALTH, EffectUtil.id("sanction_health"), -4.0, Operation.ADD_VALUE);
            EffectUtil.ensure(p, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, EffectUtil.id("sanction_dig"), -0.25, Operation.ADD_MULTIPLIED_TOTAL);
        } else {
            EffectUtil.remove(p, EntityAttributes.GENERIC_MAX_HEALTH, EffectUtil.id("sanction_health"));
            EffectUtil.remove(p, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, EffectUtil.id("sanction_dig"));
        }
    }

    private Sanctions() {}
}
