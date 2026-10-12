package com.elemental.mixin;

import com.elemental.network.QuestNetworking;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void elemental$copyJetpackSlot(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity newPlayer = (ServerPlayerEntity) (Object) this;
        if (alive || oldPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            ItemStack stack = ((JetpackSlotHolder) oldPlayer).elemental$getJetpackSlot();
            ((JetpackSlotHolder) newPlayer).elemental$setJetpackSlot(stack);
        }
        QuestNetworking.sendExtraSlotSync(newPlayer);
    }
}
