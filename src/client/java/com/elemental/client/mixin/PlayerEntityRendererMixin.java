package com.elemental.client.mixin;

import com.elemental.item.ElementalJetpackItem;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    public PlayerEntityRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("TAIL"))
    private void elemental$ironManFlightTransform(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, CallbackInfo ci) {
        ItemStack jetpackStack = JetpackSlotHolder.getJetpackStack(player);
        if (jetpackStack.isEmpty() || !(jetpackStack.getItem() instanceof ElementalJetpackItem)) {
            ItemStack cached = com.elemental.client.util.ClientJetpackSync.getStack(player.getId());
            if (cached.getItem() instanceof ElementalJetpackItem) {
                jetpackStack = cached;
            } else {
                return;
            }
        }

        // Only apply flight tilt transform when jetpack is toggled on
        if (!JetpackSlotHolder.isJetpackEnabled(player) && !com.elemental.client.util.ClientJetpackSync.isEnabled(player.getId())) {
            return;
        }

        // Only apply in-air superhero flight transform
        if (player.isOnGround() || player.isTouchingWater() || player.isSwimming() || player.isSleeping() || player.hasVehicle() || player.isFallFlying()) {
            return;
        }

        Vec3d vel = player.getVelocity();
        double vx = vel.x;
        double vy = vel.y;
        double vz = vel.z;

        if (Math.abs(vx) < 0.001 && Math.abs(vz) < 0.001) {
            vx = player.getX() - player.prevX;
            vz = player.getZ() - player.prevZ;
        }

        double horizSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizSpeed < 0.02 && Math.abs(vy) < 0.05) {
            return;
        }

        // Project velocity onto body yaw
        float interpolatedBodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float bodyYawRad = (float) Math.toRadians(interpolatedBodyYaw);

        double sinYaw = Math.sin(bodyYawRad);
        double cosYaw = Math.cos(bodyYawRad);
        double localForward = -vx * sinYaw + vz * cosYaw;
        double localRight = -vx * cosYaw - vz * sinYaw;

        // Speed factor: 0.0 (stationary hover) to 1.0 (Elytra speed ~1.2 blocks/tick)
        float speedFactor = (float) MathHelper.clamp(localForward / 1.1, 0.0, 1.0);

        // Pitch angle: tilts forward horizontally into flight direction (like Iron Man supersonic flight)
        float cameraPitch = player.getPitch(tickDelta);
        float targetPitch = -speedFactor * (72.0f + cameraPitch * 0.35f);

        // For backward movement
        if (localForward < -0.1) {
            targetPitch = (float) MathHelper.clamp(-localForward * 25.0, 0.0, 25.0);
        }

        // Banking roll angle: banks and rolls when turning into curves or strafing
        float yawDelta = MathHelper.wrapDegrees(player.getYaw(tickDelta) - player.prevYaw);
        float bankRoll = (float) (-localRight * 38.0 - yawDelta * 4.2) * (speedFactor + 0.15f);
        bankRoll = MathHelper.clamp(bankRoll, -45.0f, 45.0f);

        // Pivot around center of mass (mid-torso ~ y = 1.0)
        matrices.translate(0.0f, 1.0f, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bankRoll));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(targetPitch));
        matrices.translate(0.0f, -1.0f, 0.0f);
    }
}
