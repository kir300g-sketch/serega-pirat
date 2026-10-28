package com.piratechallenge.effect;

import com.piratechallenge.PlayerData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Мобы танцуют, хотбар перемешивается раз в секунду, стоять нельзя. Дискотека — на клиенте. */
public class VibemanEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.lastPos = p.getPos();
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        ServerWorld w = p.getServerWorld();

        if (elapsed % 2 == 0) {
            for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(14), Entity::isAlive)) {
                m.setTarget(null);
                m.getNavigation().stop();
                float yaw = m.getYaw() + 45f;
                m.setYaw(yaw);
                m.setBodyYaw(yaw);
                m.setHeadYaw(yaw);
                if (elapsed % 10 == 0 && m.isOnGround()) m.addVelocity(0, 0.35, 0);
            }
        }

        if (elapsed % 20 == 0 && elapsed > 0) {
            // перемешиваем хотбар
            PlayerInventory inv = p.getInventory();
            List<ItemStack> hotbar = new ArrayList<>();
            for (int i = 0; i < 9; i++) hotbar.add(inv.getStack(i));
            Collections.shuffle(hotbar);
            for (int i = 0; i < 9; i++) inv.setStack(i, hotbar.get(i));

            // стоять нельзя: меньше блока за секунду = урон
            if (p.getPos().squaredDistanceTo(d.lastPos) < 1.0) {
                p.damage(p.getDamageSources().magic(), 2.0f);
                EffectUtil.bar(p, "§cДВИГАЙСЯ! На танцполе нельзя стоять!");
            }
            d.lastPos = p.getPos();
        }
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {}
}
