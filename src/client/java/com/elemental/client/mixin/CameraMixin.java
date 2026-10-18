package com.elemental.client.mixin;

import com.elemental.client.animation.StormcallerHammerAnimationHandler;
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

    @Shadow private float pitch;

    @Inject(method = "update", at = @At("TAIL"))
    private void elemental$applyStormcallerSmashCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        // Only apply in first person for the player during ground smash
        if (!thirdPerson && focusedEntity instanceof PlayerEntity) {
            float pitchOffset = StormcallerHammerAnimationHandler.getCameraPitchOffset(tickDelta);
            this.pitch += pitchOffset;
        }
    }
}
