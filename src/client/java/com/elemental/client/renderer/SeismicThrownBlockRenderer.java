package com.elemental.client.renderer;

import com.elemental.entity.SeismicThrownBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public class SeismicThrownBlockRenderer extends EntityRenderer<SeismicThrownBlockEntity> {

    private final BlockRenderManager blockRenderManager;

    public SeismicThrownBlockRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
        this.blockRenderManager = context.getBlockRenderManager();
    }

    @Override
    public void render(SeismicThrownBlockEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        BlockState state = entity.getBlockState();
        if (state.getRenderType() == BlockRenderType.MODEL) {
            matrices.push();
            matrices.translate(-0.5, 0.0, -0.5);

            float age = entity.age + tickDelta;
            matrices.translate(0.5, 0.5, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(age * 12.0f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 8.0f));
            matrices.translate(-0.5, -0.5, -0.5);

            this.blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }

    @Override
    public Identifier getTexture(SeismicThrownBlockEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
