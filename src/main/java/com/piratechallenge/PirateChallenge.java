package com.piratechallenge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.piratechallenge.effect.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class PirateChallenge implements ModInitializer {
    public static final String MOD_ID = "pirate_challenge";

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModItems.register();
        ModEffects.register();
        ModEntities.register();

        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QtePayload.ID, QtePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AmStatePayload.ID, AmStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QteResultPayload.ID, QteResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MovePayload.ID, MovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkillPayload.ID, SkillPayload.CODEC);

        ChallengeManager.init();

        // ---- сеть: клиент -> сервер
        ServerPlayNetworking.registerGlobalReceiver(QteResultPayload.ID,
                (payload, ctx) -> NogaEffect.onResult(ctx.player(), payload.success()));
        ServerPlayNetworking.registerGlobalReceiver(MovePayload.ID,
                (payload, ctx) -> AntiMageEffect.onMove(ctx.player(), new Vec3d(payload.x(), payload.y(), payload.z())));
        ServerPlayNetworking.registerGlobalReceiver(SkillPayload.ID,
                (payload, ctx) -> AntiMageEffect.onSkill(ctx.player(), payload.skill(), new Vec3d(payload.x(), payload.y(), payload.z())));

        // ---- команда для кликов в чате (диалог)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("pcdialog")
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(1, 2))
                                .executes(c -> {
                                    ServerPlayerEntity p = c.getSource().getPlayer();
                                    if (p != null) DialogueManager.choose(p, IntegerArgumentType.getInteger(c, "n"));
                                    return 1;
                                }))));

        // ---- Качалка: ломаем 10x10
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
                GymEffect.onBreak(sw, sp, pos);
            }
        });

        // ---- урон: уклонение Радика и Counterspell
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p) {
                Track cur = ChallengeManager.current(p);
                if (cur == Track.TP_NA_AME && p.getMainHandStack().isOf(ModItems.RADIANCE)
                        && p.getRandom().nextFloat() < 0.25f) {
                    return false;
                }
                if (cur == Track.FP_AM && AntiMageEffect.blocks(p, source, amount)) {
                    return false;
                }
            }
            return true;
        });

        // ---- смерти: счёт душ, воры, хейтеры, Дракон Края снимает проклятие
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity p) {
                var tags = entity.getCommandTags();
                if (tags.contains(NataliaEffect.TAG)) {
                    NataliaEffect.onKill(p, entity);
                    ThiefEffect.onKill(p, entity);
                }
                if (tags.contains(AntiMageEffect.TAG)) AntiMageEffect.onKill(p, entity);
                if (tags.contains(CowboyEffect.THIEF_TAG)) CowboyEffect.onKill(p, entity);
            }
            if (entity instanceof EnderDragonEntity && entity.getWorld() instanceof ServerWorld sw) {
                for (ServerPlayerEntity pl : sw.getPlayers()) DialogueManager.lift(pl);
            }
        });

        // ---- кража у жителей
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            return ThiefEffect.onUse(sp, entity) ? ActionResult.SUCCESS : ActionResult.PASS;
        });

        // ---- остатки существ мода (после вылета игры) удаляем при загрузке чанка
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(EffectUtil.SPAWN_TAG) && !ChallengeManager.anyActive(world.getServer())) {
                world.getServer().execute(entity::discard);
            }
        });
    }
}
