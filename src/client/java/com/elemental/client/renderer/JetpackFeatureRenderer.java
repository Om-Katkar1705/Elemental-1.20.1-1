package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.item.ElementalJetpackItem;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class JetpackFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public static final Identifier TEXTURE = Elemental.id("textures/entity/elemental_jetpack.png");
    public static final EntityModelLayer JETPACK_LAYER = new EntityModelLayer(Elemental.id("elemental_jetpack"), "main");

    private final JetpackModel model;

    public JetpackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context, EntityModelLoader loader) {
        super(context);
        this.model = new JetpackModel(loader.getModelPart(JETPACK_LAYER));
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        ItemStack jetpackStack = JetpackSlotHolder.getJetpackStack(player);
        if (jetpackStack.isEmpty() || !(jetpackStack.getItem() instanceof ElementalJetpackItem)) {
            // Check replicated client cache if holder hasn't populated yet
            ItemStack cached = com.elemental.client.util.ClientJetpackSync.getStack(player.getId());
            if (cached.getItem() instanceof ElementalJetpackItem) {
                if (player instanceof JetpackSlotHolder holder) {
                    holder.elemental$setJetpackSlot(cached);
                    holder.elemental$setJetpackEnabled(com.elemental.client.util.ClientJetpackSync.isEnabled(player.getId()));
                }
                jetpackStack = cached;
            } else {
                return;
            }
        }

        if (player.isInvisible() || player.isSpectator()) {
            return;
        }

        matrices.push();

        // 1. Follow player body rotation
        this.getContextModel().body.rotate(matrices);

        // 2. Dynamic tilt in direction of movement
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float yawRad = (float) Math.toRadians(bodyYaw);

        double vx = player.getVelocity().x;
        double vz = player.getVelocity().z;
        double vy = player.getVelocity().y;

        // Fallback to position delta if velocity is near zero (e.g. for remote multiplayer entities)
        if (Math.abs(vx) < 0.001 && Math.abs(vz) < 0.001) {
            vx = (player.getX() - player.prevX);
            vz = (player.getZ() - player.prevZ);
        }

        // Project velocity into player's local horizontal coordinate space
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double localForward = -vx * sinYaw + vz * cosYaw;
        double localRight = -vx * cosYaw - vz * sinYaw;

        // Subtle thruster gimbal articulation on the backplate
        float pitchAngle = (float) MathHelper.clamp(localForward * 0.12, -0.08, 0.15);
        float rollAngle = (float) MathHelper.clamp(-localRight * 0.12, -0.12, 0.12);

        // Smooth vertical ascent adjustment
        if (vy > 0.25) {
            pitchAngle *= 0.5f;
        }

        // 3. Pivot around jetpack's mount center on torso: (0, 6/16, 3.3/16)
        matrices.translate(0.0f, 6.0f / 16.0f, 3.3f / 16.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitchAngle));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rollAngle));
        matrices.translate(0.0f, -6.0f / 16.0f, -3.3f / 16.0f);

        // 4. Render 3D jetpack model
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        this.model.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);

        matrices.pop();
    }
}
