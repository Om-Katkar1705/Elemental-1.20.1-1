package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.client.model.SeismicColossusModel;
import com.elemental.entity.SeismicColossusEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
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
    public void preRender(MatrixStack poseStack, SeismicColossusEntity animatable, BakedGeoModel bakedModel,
                          VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        int phase = animatable.getPhase();
        if (phase <= 1) {
            // Phase 1 (Stone Guardian): Both arms and all purple crystals fully intact
            bakedModel.getBone("LeftArm").ifPresent(b -> b.setHidden(false));
            bakedModel.getBone("LowerLeftArm").ifPresent(b -> b.setHidden(false));
            bakedModel.getBone("Cystal3").ifPresent(b -> b.setHidden(false));
            bakedModel.getBone("Crystal2").ifPresent(b -> b.setHidden(false));
        } else if (phase == 2) {
            // Phase 2 (Living Quarry): Purple hand / lower arm breaks off, crystals shattered
            bakedModel.getBone("LeftArm").ifPresent(b -> b.setHidden(false));
            bakedModel.getBone("LowerLeftArm").ifPresent(b -> b.setHidden(true));
            bakedModel.getBone("Cystal3").ifPresent(b -> b.setHidden(true));
            bakedModel.getBone("Crystal2").ifPresent(b -> b.setHidden(true));
        } else {
            // Phase 3 (Seismic Rage): Entire purple arm broken off completely
            bakedModel.getBone("LeftArm").ifPresent(b -> b.setHidden(true));
            bakedModel.getBone("LowerLeftArm").ifPresent(b -> b.setHidden(true));
            bakedModel.getBone("Cystal3").ifPresent(b -> b.setHidden(true));
            bakedModel.getBone("Crystal2").ifPresent(b -> b.setHidden(true));
        }

        super.preRender(poseStack, animatable, bakedModel, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
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
