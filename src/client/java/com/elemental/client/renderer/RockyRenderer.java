package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.client.model.RockyModel;
import com.elemental.entity.RockyEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RockyRenderer extends GeoEntityRenderer<RockyEntity> {

    public RockyRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new RockyModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    public Identifier getTextureLocation(RockyEntity animatable) {
        return Elemental.id("textures/entity/rocky.png");
    }

    @Override
    public void render(RockyEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
