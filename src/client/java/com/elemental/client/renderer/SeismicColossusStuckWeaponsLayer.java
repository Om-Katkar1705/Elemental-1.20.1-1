package com.elemental.client.renderer;

import com.elemental.entity.SeismicColossusEntity;
import com.elemental.item.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.List;

public class SeismicColossusStuckWeaponsLayer extends GeoRenderLayer<SeismicColossusEntity> {

    public SeismicColossusStuckWeaponsLayer(GeoRenderer<SeismicColossusEntity> entityRendererIn) {
        super(entityRendererIn);
    }

    @Override
    public void render(MatrixStack poseStack, SeismicColossusEntity animatable, BakedGeoModel bakedModel,
                       RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        List<SeismicColossusEntity.StuckWeapon> weapons = animatable.getStuckWeapons();

        for (SeismicColossusEntity.StuckWeapon sw : weapons) {
            if (sw.stack.isEmpty()) continue;
            bakedModel.getBone(sw.boneName).ifPresent(bone -> {
                if (bone.isHidden()) return;
                poseStack.push();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                poseStack.translate(sw.offsetX, sw.offsetY, sw.offsetZ);
                poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(70f));
                poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25f));
                poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45f));
                poseStack.scale(0.85f, 0.85f, 0.85f);
                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        sw.stack,
                        ModelTransformationMode.FIXED,
                        packedLight,
                        OverlayTexture.DEFAULT_UV,
                        poseStack,
                        bufferSource,
                        animatable.getWorld(),
                        0
                );
                poseStack.pop();
            });
        }

        if (animatable.isTridentStuck()) {
            bakedModel.getBone("Body").ifPresent(bone -> {
                if (bone.isHidden()) return;
                poseStack.push();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                poseStack.translate(0.0, 2.7, -0.22);
                poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(75f));
                poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(15f));
                poseStack.scale(1.1f, 1.1f, 1.1f);
                MinecraftClient.getInstance().getItemRenderer().renderItem(
                        new ItemStack(ModItems.TIDEBREAKER_TRIDENT),
                        ModelTransformationMode.FIXED,
                        packedLight,
                        OverlayTexture.DEFAULT_UV,
                        poseStack,
                        bufferSource,
                        animatable.getWorld(),
                        0
                );
                poseStack.pop();
            });
        }
    }
}
