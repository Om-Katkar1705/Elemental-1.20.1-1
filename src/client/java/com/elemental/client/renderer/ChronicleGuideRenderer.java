package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.entity.ChronicleGuideEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class ChronicleGuideRenderer extends BipedEntityRenderer<ChronicleGuideEntity, BipedEntityModel<ChronicleGuideEntity>> {
    public static final Identifier TEXTURE = Elemental.id("textures/entity/chronicle_guide.png");

    public ChronicleGuideRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(ChronicleGuideEntity entity) {
        return TEXTURE;
    }
}
