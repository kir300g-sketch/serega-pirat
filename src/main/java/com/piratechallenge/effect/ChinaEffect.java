package com.piratechallenge.effect;

import com.piratechallenge.ChallengeManager;
import com.piratechallenge.PlayerData;
import com.piratechallenge.Track;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * «Песня в поддержку китайцев»: бамбуковый туман (обзор почти пропал — туннельное зрение на клиенте),
 * игроку выдаётся катана; со всех сторон идут налётчики (волны растут). Бой до конца трека.
 */
public class ChinaEffect implements TrackEffect {
    public static final String TAG = "pc_raider";
    private static final Random RNG = new Random();

    private static final List<EntityType<?>> RAIDERS = List.of(
            EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.ZOMBIE, EntityType.SKELETON);

    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        d.kills = 0;
        ItemStack katana = EffectUtil.tag(new ItemStack(Items.NETHERITE_SWORD));
        katana.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Катана"));
        ench(w, katana, Enchantments.SHARPNESS, 4);
        ench(w, katana, Enchantments.SWEEPING_EDGE, 3);
        ench(w, katana, Enchantments.UNBREAKING, 3);
        p.getInventory().offerOrDrop(katana);
        for (int i = 0; i < 3; i++) spawnRaider(p, d);
        p.sendMessage(Text.literal("§cТуман окутал поле боя. Тебе выдана катана — защищайся!"), false);
    }

    private void ench(ServerWorld w, ItemStack s, RegistryKey<Enchantment> key, int lvl) {
        s.addEnchantment(w.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key), lvl);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();
        EffectUtil.prune(w, d);
        double progress = elapsed / (double) Math.max(1, total);

        int cap = 4 + (int) (progress * 10);
        if (elapsed % Math.max(25, 70 - (int) (progress * 45)) == 0 && d.spawned.size() < cap) {
            spawnRaider(p, d);
            if (progress > 0.5) spawnRaider(p, d);
        }
        if (elapsed % 40 == 0) {
            for (UUID id : d.spawned) {
                Entity e = w.getEntity(id);
                if (e instanceof MobEntity m) m.setTarget(p);
            }
        }
    }

    private void spawnRaider(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        Entity e = RAIDERS.get(RNG.nextInt(RAIDERS.size())).create(w);
        if (!(e instanceof MobEntity m)) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 12 + RNG.nextDouble() * 8;
        double x = p.getX() + Math.cos(ang) * dist;
        double z = p.getZ() + Math.sin(ang) * dist;
        BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
        double y = Math.abs(top.getY() - p.getY()) < 8 ? top.getY() : p.getY();
        m.refreshPositionAndAngles(x, y, z, 0f, 0f);
        EffectUtil.randomArmor(m, RNG);
        m.setCustomName(Text.literal("Налётчик"));
        m.setCustomNameVisible(true);
        m.setPersistent();
        m.addCommandTag(TAG);
        m.setTarget(p);
        if (m.isOnFire()) m.extinguish();
        w.spawnEntity(m);
        EffectUtil.reg(d, m);
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity victim) {
        PlayerData d = ChallengeManager.data(p);
        if (d.current != Track.CHINA) return;
        d.kills++;
        p.heal(1.5f);
        EffectUtil.bar(p, "§cНалётчиков повержено: " + d.kills);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        EffectUtil.removeTagged(p); // катана остаётся только на время трека
        p.sendMessage(Text.literal("§7Налётчиков повержено: " + d.kills), false);
    }
}
