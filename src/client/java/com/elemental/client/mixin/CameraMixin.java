package com.elemental.client.mixin;

import com.elemental.client.animation.StormcallerHammerAnimationHandler;
import com.elemental.client.util.CameraShakeManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private float pitch;
    @Shadow private float yaw;

    @Inject(method = "update", at = @At("TAIL"))
    private void elemental$applyCameraEffects(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (focusedEntity instanceof PlayerEntity) {
            float shakePitch = CameraShakeManager.getPitchOffset(tickDelta);
            float shakeYaw = CameraShakeManager.getYawOffset(tickDelta);
            float hammerPitch = (!thirdPerson) ? StormcallerHammerAnimationHandler.getCameraPitchOffset(tickDelta) : 0.0f;

            if (shakePitch != 0.0f || shakeYaw != 0.0f || hammerPitch != 0.0f) {
                this.setRotation(this.yaw + shakeYaw, this.pitch + shakePitch + hammerPitch);
            }
        }
    }
}
