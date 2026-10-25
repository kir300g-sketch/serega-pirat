package com.piratechallenge;

import com.piratechallenge.effect.GymEffect;
import com.piratechallenge.effect.NataliaEffect;
import com.piratechallenge.effect.ThiefEffect;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

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
                    && ChallengeManager.current(p) == Track.TP_NA_AME
                    && p.getRandom().nextFloat() < 0.25f) {
                return false;
            }
            return true;
        });

        // Наталия: подсчёт убитых жителей
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity p && entity.getCommandTags().contains(NataliaEffect.TAG)) {
                NataliaEffect.onKill(p, entity);
                ThiefEffect.onKill(p, entity);
            }
        });

        // Кража у жителей (ПКМ) в треке «Ну и что, что я вор?»
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            return ThiefEffect.onUse(sp, entity) ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
}
