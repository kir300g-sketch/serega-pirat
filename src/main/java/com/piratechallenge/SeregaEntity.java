package com.piratechallenge;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

/** NPC Серёга Пират: стоит, неуязвим, не двигается (диалог ведёт DialogueManager). */
public class SeregaEntity extends MobEntity {
    public SeregaEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
