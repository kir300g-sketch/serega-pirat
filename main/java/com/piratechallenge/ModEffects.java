package com.piratechallenge;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public final class ModEffects {
    public static RegistryEntry<StatusEffect> CURSE;

    public static void register() {
        CURSE = Registry.registerReference(Registries.STATUS_EFFECT,
                Identifier.of(PirateChallenge.MOD_ID, "serega_curse"), new CurseEffect());
    }

    /** «Проклятие Сереги Пирата»: бесконечное, снимается только прохождением Майнкрафта (убийство Дракона Края). */
    private static class CurseEffect extends StatusEffect {
        protected CurseEffect() {
            super(StatusEffectCategory.HARMFUL, 0x8B0000);
        }
    }

    private ModEffects() {}
}
