package com.piratechallenge;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static EntityType<SeregaEntity> SEREGA;

    public static void register() {
        SEREGA = Registry.register(Registries.ENTITY_TYPE, Identifier.of(PirateChallenge.MOD_ID, "serega"),
                EntityType.Builder.create(SeregaEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).build("serega"));
        FabricDefaultAttributeRegistry.register(SEREGA, MobEntity.createMobAttributes());
    }

    private ModEntities() {}
}
