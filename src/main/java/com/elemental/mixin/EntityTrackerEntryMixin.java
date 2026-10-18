package com.elemental.mixin;

import com.elemental.network.QuestNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into EntityTrackerEntry to replicate equipped jetpack state to remote players
 * as soon as they start tracking another player (when entering render range or joining).
 */
@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    @Shadow @Final private Entity entity;

    @Inject(method = "startTracking", at = @At("TAIL"))
    private void elemental$syncJetpackOnStartTracking(ServerPlayerEntity player, CallbackInfo ci) {
        if (this.entity instanceof ServerPlayerEntity trackedPlayer) {
            QuestNetworking.sendExtraSlotSyncTo(trackedPlayer, player);
            if (com.elemental.combat.StormcallerServerCombatHandler.isAiming(trackedPlayer)) {
                com.elemental.network.StormcallerNetworking.sendPlayAnimationTo(player, trackedPlayer, "animation.stormcaller_hammer.lightning_aim_idle");
            }
        }
    }
}
