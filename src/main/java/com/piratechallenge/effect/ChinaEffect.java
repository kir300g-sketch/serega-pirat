package com.piratechallenge.effect;

import com.piratechallenge.BambooPayload;
import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * «Песня в поддержку китайцев» — разрезание бамбука в стиле Ghost of Tsushima.
 * Вокруг игрока вырастает бамбуковая роща. Раз в 7–11 секунд сервер выбирает стебель и присылает последовательность
 * направлений; клиент (BambooScreen) показывает сужающиеся кольца — нужно нажать нужное направление (WASD / стрелки)
 * в момент, когда кольцо сойдётся. Все удары точные — идеальный разрез (награда), хотя бы половина — стебель срезан,
 * иначе игрок теряет концентрацию (урон). После трека роща исчезает, земля возвращается.
 */
public class ChinaEffect implements TrackEffect {
    private static final Random RNG = new Random();
    private static final int STALKS = 12;

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.bambooCuts = 0;
        d.bambooBusy = false;
        d.bambooTimer = 80; // 4 секунды до первого стебля

        ItemStack katana = EffectUtil.tag(new ItemStack(Items.NETHERITE_SWORD));
        katana.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Катана"));
        katana.addEnchantment(w.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SHARPNESS), 4);
        p.getInventory().offerOrDrop(katana);

        growGrove(p, d, w);
        p.sendMessage(Text.literal("§aБамбуковая роща. Когда появится приказ — жми показанные направления (WASD/стрелки) в такт кольцам."), false);
    }

    private void growGrove(ServerPlayerEntity p, PlayerData d, ServerWorld w) {
        int placed = 0, tries = 0;
        while (placed < STALKS && tries++ < 200) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double r = 4 + RNG.nextDouble() * 6;
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * r);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * r);
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - p.getY()) > 4) continue;

            boolean tooClose = false;
            for (int[] s : d.bambooStalks) {
                if ((s[0] - x) * (s[0] - x) + (s[2] - z) * (s[2] - z) < 6) { tooClose = true; break; }
            }
            if (tooClose) continue;

            int base = top.getY();
            int h = 7 + RNG.nextInt(4);
            BlockPos ground = new BlockPos(x, base - 1, z);
            d.bambooRestore.add(new Object[]{ground, w.getBlockState(ground)});
            w.setBlockState(ground, Blocks.PODZOL.getDefaultState(), Block.NOTIFY_LISTENERS);
            for (int i = 0; i < h; i++) {
                BlockState st = Blocks.BAMBOO.getDefaultState();
                if (i == h - 1) st = st.with(BambooBlock.LEAVES, BambooLeaves.LARGE);
                else if (i == h - 2) st = st.with(BambooBlock.LEAVES, BambooLeaves.SMALL);
                w.setBlockState(new BlockPos(x, base + i, z), st, Block.NOTIFY_LISTENERS);
            }
            d.bambooStalks.add(new int[]{x, base, z, h, 0});
            placed++;
        }
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        double progress = elapsed / (double) Math.max(1, total);

        // идущий разрез: если клиент не ответил в срок — считаем промахом
        if (d.bambooBusy) {
            if (elapsed > d.bambooDeadline) applyResult(p, d, w, 0, 1);
            return;
        }
        if (--d.bambooTimer > 0) return;

        // выбираем ближайший несрезанный стебель
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < d.bambooStalks.size(); i++) {
            int[] s = d.bambooStalks.get(i);
            if (s[4] != 0) continue;
            double dd = p.squaredDistanceTo(s[0] + 0.5, p.getY(), s[2] + 0.5);
            if (dd < bd) { bd = dd; best = i; }
        }
        if (best < 0) { // все срезаны — выращиваем новую рощу
            clearGrove(w, d);
            growGrove(p, d, w);
            d.bambooTimer = 40;
            return;
        }

        int len = 3 + (int) (progress * 3);           // 3..5 ударов
        int beat = 26 - (int) (progress * 8);         // 26..18 тиков между ударами
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append("WASD".charAt(RNG.nextInt(4)));
        d.bambooBusy = true;
        d.bambooTarget = best;
        d.bambooDeadline = elapsed + 15 + len * beat + 70;
        ServerPlayNetworking.send(p, new BambooPayload(sb.toString(), beat));
    }

    /** Результат от клиента: hits из total ударов попали в такт. */
    public static void onResult(ServerPlayerEntity p, int hits, int total) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.CHINA || !d.bambooBusy) return;
        new ChinaEffect().applyResult(p, d, p.getServerWorld(), hits, Math.max(1, total));
    }

    private void applyResult(ServerPlayerEntity p, PlayerData d, ServerWorld w, int hits, int total) {
        d.bambooBusy = false;
        d.bambooTimer = 140 + RNG.nextInt(80);
        int[] s = d.bambooStalks.get(Math.min(d.bambooTarget, d.bambooStalks.size() - 1));

        if (hits >= total) {
            cut(p, w, s);
            d.bambooCuts++;
            p.heal(4.0f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 1));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 240, 0));
            EffectUtil.bar(p, "§a§lИДЕАЛЬНЫЙ РАЗРЕЗ! §fСрезано бамбука: " + d.bambooCuts);
        } else if (hits * 2 >= total) {
            cut(p, w, s);
            d.bambooCuts++;
            p.heal(1.0f);
            EffectUtil.bar(p, "§eБамбук срезан (" + hits + "/" + total + "). Срезано: " + d.bambooCuts);
        } else {
            p.damage(p.getDamageSources().magic(), 2.0f);
            EffectUtil.bar(p, "§cТы потерял концентрацию! (" + hits + "/" + total + ")");
        }
    }

    /** Срез: от середины стебля вверх бамбук ломается (с дропом), вспышка удара. */
    private void cut(ServerPlayerEntity p, ServerWorld w, int[] s) {
        s[4] = 1;
        int cutY = s[1] + s[3] / 2;
        for (int y = cutY; y < s[1] + s[3]; y++) {
            w.breakBlock(new BlockPos(s[0], y, s[2]), true, p);
        }
        w.spawnParticles(ParticleTypes.SWEEP_ATTACK, s[0] + 0.5, cutY + 0.5, s[2] + 0.5, 3, 0.4, 0.1, 0.4, 0);
        w.spawnParticles(ParticleTypes.CRIT, s[0] + 0.5, cutY + 0.5, s[2] + 0.5, 25, 0.3, 0.3, 0.3, 0.3);
        w.playSound(null, s[0] + 0.5, cutY, s[2] + 0.5, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 1.3f);
    }

    private void clearGrove(ServerWorld w, PlayerData d) {
        for (int[] s : d.bambooStalks) {
            for (int i = 0; i < s[3]; i++) {
                BlockPos pos = new BlockPos(s[0], s[1] + i, s[2]);
                if (w.getBlockState(pos).isOf(Blocks.BAMBOO)) w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (Object[] r : d.bambooRestore) {
            w.setBlockState((BlockPos) r[0], (BlockState) r[1], Block.NOTIFY_LISTENERS);
        }
        d.bambooStalks.clear();
        d.bambooRestore.clear();
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        clearGrove(p.getServerWorld(), d);
        d.bambooBusy = false;
        EffectUtil.removeTagged(p); // катана — только на время трека
        if (p.networkHandler != null && p.networkHandler.isConnectionOpen()) {
            ServerPlayNetworking.send(p, new BambooPayload("", 0));
        }
        if (p.isAlive()) p.sendMessage(Text.literal("§7Срезано бамбука: " + d.bambooCuts), false);
    }
}
