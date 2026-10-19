package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.client.model.ColossusMiniModel;
import com.elemental.entity.ColossusMiniEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ColossusMiniRenderer extends GeoEntityRenderer<ColossusMiniEntity> {

    public ColossusMiniRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new ColossusMiniModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public Identifier getTextureLocation(ColossusMiniEntity animatable) {
        return Elemental.id("textures/entity/colossus_mini.png");
    }

    @Override
    public void render(ColossusMiniEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        // Scale 0.55x so it is smaller than a player
        poseStack.scale(0.55f, 0.55f, 0.55f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
