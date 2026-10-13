package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.entity.ChronicleGuideEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public class ChronicleGuideRenderer extends BipedEntityRenderer<ChronicleGuideEntity, PlayerEntityModel<ChronicleGuideEntity>> {
    public static final Identifier TEXTURE = Elemental.id("textures/entity/chronicle_guide.png");

    public ChronicleGuideRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(ChronicleGuideEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ChronicleGuideEntity entity, float f, float g, net.minecraft.client.util.math.MatrixStack matrixStack, net.minecraft.client.render.VertexConsumerProvider vertexConsumerProvider, int i) {
        PlayerEntityModel<ChronicleGuideEntity> model = this.getModel();
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
        super.render(entity, f, g, matrixStack, vertexConsumerProvider, i);
    }
}
