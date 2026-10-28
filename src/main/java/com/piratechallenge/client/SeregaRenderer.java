package com.piratechallenge.client;

import com.piratechallenge.PirateChallenge;
import com.piratechallenge.SeregaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Рендер NPC Серёги: модель игрока (широкие руки) + скин assets/pirate_challenge/textures/entity/serega.png */
public class SeregaRenderer extends MobEntityRenderer<SeregaEntity, PlayerEntityModel<SeregaEntity>> {
    private static final Identifier TEXTURE = Identifier.of(PirateChallenge.MOD_ID, "textures/entity/serega.png");

    public SeregaRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(SeregaEntity entity) {
        return TEXTURE;
    }
}
