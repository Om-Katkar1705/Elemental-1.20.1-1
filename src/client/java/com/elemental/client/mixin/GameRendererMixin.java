package com.elemental.client.mixin;

import com.elemental.util.WeaponReachHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "updateTargetedEntity", at = @At("HEAD"), cancellable = true)
    private void elemental$updateTargetedEntityForCustomWeapons(float tickDelta, CallbackInfo ci) {
        if (this.client.player == null) return;
        if (!WeaponReachHelper.isCustomWeapon(this.client.player.getMainHandStack())) return;

        Entity cameraEntity = this.client.getCameraEntity();
        if (cameraEntity != null && this.client.world != null) {
            this.client.getProfiler().push("pick");
            this.client.targetedEntity = null;

            double reach = WeaponReachHelper.EXTENDED_ATTACK_RANGE;
            double blockReach = Math.max((double) this.client.interactionManager.getReachDistance(), reach);

            this.client.crosshairTarget = cameraEntity.raycast(blockReach, tickDelta, false);
            Vec3d cameraPos = cameraEntity.getCameraPosVec(tickDelta);

            double maxEntityDistanceSq = reach * reach;
            if (this.client.crosshairTarget != null) {
                maxEntityDistanceSq = Math.min(maxEntityDistanceSq, this.client.crosshairTarget.getPos().squaredDistanceTo(cameraPos));
            }

            Vec3d lookVec = cameraEntity.getRotationVec(1.0F);
            Vec3d endVec = cameraPos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
            Box box = cameraEntity.getBoundingBox().stretch(lookVec.multiply(reach)).expand(1.0, 1.0, 1.0);

            EntityHitResult entityHitResult = ProjectileUtil.raycast(
                    cameraEntity,
                    cameraPos,
                    endVec,
                    box,
                    e -> !e.isSpectator() && e.canHit(),
                    maxEntityDistanceSq
            );

            if (entityHitResult != null) {
                Vec3d hitPos = entityHitResult.getPos();
                double distSq = cameraPos.squaredDistanceTo(hitPos);
                if (distSq <= maxEntityDistanceSq || this.client.crosshairTarget == null) {
                    this.client.crosshairTarget = entityHitResult;
                    if (entityHitResult.getEntity() instanceof LivingEntity || entityHitResult.getEntity() instanceof ItemFrameEntity) {
                        this.client.targetedEntity = entityHitResult.getEntity();
                    }
                }
            }

            this.client.getProfiler().pop();
            ci.cancel();
        }
    }
}
