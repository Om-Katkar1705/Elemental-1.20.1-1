package com.elemental.client.mixin;

import com.elemental.item.ElementalJetpackItem;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {

    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void elemental$ironManFlightPose(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        ItemStack jetpackStack = JetpackSlotHolder.getJetpackStack(player);
        if (jetpackStack.isEmpty() || !(jetpackStack.getItem() instanceof ElementalJetpackItem)) {
            ItemStack cached = com.elemental.client.util.ClientJetpackSync.getStack(player.getId());
            if (cached.getItem() instanceof ElementalJetpackItem) {
                jetpackStack = cached;
            } else {
                return;
            }
        }

        // Only apply flight pose when jetpack is toggled on
        if (!JetpackSlotHolder.isJetpackEnabled(player) && !com.elemental.client.util.ClientJetpackSync.isEnabled(player.getId())) {
            return;
        }

        if (player.isOnGround() || player.isTouchingWater() || player.isSwimming() || player.isSleeping() || player.hasVehicle() || player.isFallFlying()) {
            return;
        }

        Vec3d vel = player.getVelocity();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float speedFactor = (float) MathHelper.clamp(horizSpeed / 1.0, 0.0, 1.0);

        // Completely eliminate crouching/hunching while airborne with jetpack
        this.sneaking = false;
        this.body.pitch = 0.0f;
        this.body.pivotY = 0.0f;
        this.body.pivotZ = 0.0f;
        this.head.pivotY = 0.0f;
        this.head.pivotZ = 0.0f;
        this.rightArm.pivotY = 2.0f;
        this.leftArm.pivotY = 2.0f;
        this.rightArm.pivotZ = 0.0f;
        this.leftArm.pivotZ = 0.0f;
        this.rightLeg.pivotY = 12.0f;
        this.leftLeg.pivotY = 12.0f;
        this.rightLeg.pivotZ = 0.1f;
        this.leftLeg.pivotZ = 0.1f;

        // 1. Legs: Straightened and trailing back like rocket boots (Iron Man flight pose)
        this.rightLeg.pitch = MathHelper.lerp(speedFactor, this.rightLeg.pitch, 0.03f);
        this.leftLeg.pitch = MathHelper.lerp(speedFactor, this.leftLeg.pitch, 0.03f);
        this.rightLeg.yaw = MathHelper.lerp(speedFactor, this.rightLeg.yaw, 0.0f);
        this.leftLeg.yaw = MathHelper.lerp(speedFactor, this.leftLeg.yaw, 0.0f);
        this.rightLeg.roll = MathHelper.lerp(speedFactor, this.rightLeg.roll, 0.04f);
        this.leftLeg.roll = MathHelper.lerp(speedFactor, this.leftLeg.roll, -0.04f);

        // 2. Arms: Classic Iron Man flight pose (angled back with palm stabilizers)
        // Only if player is not actively swinging/attacking or using an item
        if (this.handSwingProgress <= 0.0f && !player.isUsingItem()) {
            float targetArmPitch = MathHelper.lerp(speedFactor, 0.22f, 0.45f);
            this.rightArm.pitch = MathHelper.lerp(speedFactor, this.rightArm.pitch, targetArmPitch);
            this.leftArm.pitch = MathHelper.lerp(speedFactor, this.leftArm.pitch, targetArmPitch);

            this.rightArm.roll = MathHelper.lerp(speedFactor, this.rightArm.roll, 0.15f);
            this.leftArm.roll = MathHelper.lerp(speedFactor, this.leftArm.roll, -0.15f);

            this.rightArm.yaw = MathHelper.lerp(speedFactor, this.rightArm.yaw, -0.08f);
            this.leftArm.yaw = MathHelper.lerp(speedFactor, this.leftArm.yaw, 0.08f);
        }

        // Sync second layer parts (sleeves, pants, jacket) with primary limbs
        this.rightPants.copyTransform(this.rightLeg);
        this.leftPants.copyTransform(this.leftLeg);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftSleeve.copyTransform(this.leftArm);
        this.jacket.copyTransform(this.body);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void elemental$fistBumpPose(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        if (com.elemental.client.util.ClientFistBumpSync.isAnimating(player.getId())) {
            float tickDelta = net.minecraft.client.MinecraftClient.getInstance().getTickDelta();
            float currentTick = com.elemental.client.util.ClientFistBumpSync.getAnimationTick(player.getId(), tickDelta);
            com.elemental.client.animation.PlayerFistBumpAnimation.applyPose((PlayerEntityModel<?>) (Object) this, currentTick);
        }
    }
}
