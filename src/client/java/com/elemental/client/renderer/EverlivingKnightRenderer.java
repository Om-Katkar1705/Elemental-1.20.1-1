package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.client.model.EverlivingKnightModel;
import com.elemental.entity.EverlivingKnightEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class EverlivingKnightRenderer extends GeoEntityRenderer<EverlivingKnightEntity> {

    public EverlivingKnightRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new EverlivingKnightModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public Identifier getTextureLocation(EverlivingKnightEntity animatable) {
        return Elemental.id("textures/entity/everliving_knight.png");
    }

    @Override
    protected void applyRotations(EverlivingKnightEntity animatable, MatrixStack poseStack, float ageInTicks,
            float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        poseStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(90f));
    }

    @Override
    public void render(EverlivingKnightEntity entity, float entityYaw, float partialTick,
            MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        // Scale 1.3x for large armored knight presence
        poseStack.scale(1.3f, 1.3f, 1.3f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
