package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.client.model.SeismicColossusModel;
import com.elemental.entity.SeismicColossusEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SeismicColossusRenderer extends GeoEntityRenderer<SeismicColossusEntity> {

    public SeismicColossusRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new SeismicColossusModel());
        this.shadowRadius = 1.6f;
        addRenderLayer(new SeismicColossusStuckWeaponsLayer(this));
    }

    @Override
    public Identifier getTextureLocation(SeismicColossusEntity animatable) {
        return Elemental.id("textures/entity/seismic_colossus.png");
    }

    @Override
    public void render(SeismicColossusEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        // Scale 1.25x for towering ancient titan presence
        poseStack.scale(1.25f, 1.25f, 1.25f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
