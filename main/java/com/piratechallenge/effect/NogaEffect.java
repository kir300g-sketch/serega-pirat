package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.QtePayload;
import com.piratechallenge.Track;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Random;

/**
 * «Ну где моя нога?»: игрок ползёт (маленький рост, ходьба очень медленная, прыжок отключён).
 * Каждые 5–10 секунд — кровотечение: нужно быстро набрать комбинацию букв (экран QteScreen на клиенте).
 * Не успел или ошибся до конца времени — потеря крови (урон).
 */
public class NogaEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final String LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM";
    private static final int QTE_TICKS = 80;   // 4 секунды на ввод
    private static final float BLEED_DAMAGE = 5.0f;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.qteActive = false;
        d.nextQte = 100 + RNG.nextInt(100);
        applyLeg(p);
        p.sendMessage(Text.literal("§cТы потерял ногу! Ползи и останавливай кровь."), false);
    }

    private static void applyLeg(ServerPlayerEntity p) {
        EffectUtil.ensure(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("noga_scale"), -0.5, Operation.ADD_MULTIPLIED_BASE);
        EffectUtil.ensure(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, EffectUtil.id("noga_speed"), -0.7, Operation.ADD_MULTIPLIED_TOTAL);
        EffectUtil.ensure(p, EntityAttributes.GENERIC_JUMP_STRENGTH, EffectUtil.id("noga_jump"), -0.42, Operation.ADD_VALUE);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        if (elapsed % 20 == 0) applyLeg(p);

        if (!d.qteActive) {
            if (elapsed % 10 == 0) {
                p.getServerWorld().spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 0.3, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
            if (--d.nextQte <= 0) startQte(p, d, elapsed, total);
        } else if (--d.qteLeft <= 0) {
            bleed(p, d);
        }
    }

    private void startQte(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        int len = 4 + Math.min(2, elapsed * 3 / Math.max(1, total)); // 4..6 букв
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(LETTERS.charAt(RNG.nextInt(LETTERS.length())));
        d.qteActive = true;
        d.qteLeft = QTE_TICKS + 10; // небольшой запас на задержку сети
        ServerPlayNetworking.send(p, new QtePayload(sb.toString(), QTE_TICKS));
        p.sendMessage(Text.literal("§c§lКРОВЬ! Быстро набери комбинацию!"), true);
    }

    private void bleed(ServerPlayerEntity p, PlayerData d) {
        d.qteActive = false;
        d.nextQte = 100 + RNG.nextInt(100);
        ServerPlayNetworking.send(p, new QtePayload("", 0));
        p.damage(p.getDamageSources().magic(), BLEED_DAMAGE);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, false, false, false));
        p.sendMessage(Text.literal("§4Ты потерял много крови!"), true);
    }

    public static void onResult(ServerPlayerEntity p, boolean success) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.NOGA || !d.qteActive) return;
        if (success) {
            d.qteActive = false;
            d.nextQte = 100 + RNG.nextInt(100);
            p.sendMessage(Text.literal("§aКровь остановлена."), true);
        } else {
            new NogaEffect().bleed(p, d);
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        d.qteActive = false;
        EffectUtil.remove(p, EntityAttributes.GENERIC_SCALE, EffectUtil.id("noga_scale"));
        EffectUtil.remove(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, EffectUtil.id("noga_speed"));
        EffectUtil.remove(p, EntityAttributes.GENERIC_JUMP_STRENGTH, EffectUtil.id("noga_jump"));
        if (p.networkHandler != null && p.networkHandler.isConnectionOpen()) {
            ServerPlayNetworking.send(p, new QtePayload("", 0));
        }
    }
}
