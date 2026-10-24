package com.piratechallenge;

import com.piratechallenge.effect.GymEffect;
import com.piratechallenge.effect.NataliaEffect;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class PirateChallenge implements ModInitializer {
    public static final String MOD_ID = "pirate_challenge";

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModItems.register();
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        ChallengeManager.init();

        // Качалка: ломаем 10x10
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
                GymEffect.onBreak(sw, sp, pos);
            }
        });

        // ТП на аме: 25% уклонение, пока Radiance в руках
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p
                    && p.getMainHandStack().isOf(ModItems.RADIANCE)
                    && p.getRandom().nextFloat() < 0.25f) {
                return false;
            }
            return true;
        });

        // Наталия: подсчёт убитых жителей
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity p && entity.getCommandTags().contains(NataliaEffect.TAG)) {
                NataliaEffect.onKill(p, entity);
            }
        });
    }
}
