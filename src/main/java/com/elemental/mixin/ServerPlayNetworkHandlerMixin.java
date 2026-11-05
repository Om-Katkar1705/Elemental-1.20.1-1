package com.elemental.mixin;

import com.elemental.item.ElementalJetpackItem;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ServerPlayNetworkHandler to prevent survival players using the Elemental Jetpack
 * from being rubberbanded or kicked with "Flying is not enabled on this server" (allow-flight=false).
 */
@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    private int floatingTicks;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void elemental$resetFloatingTicksHead(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (this.player != null) {
            ItemStack stack = JetpackSlotHolder.getJetpackStack(this.player);
            if (stack.getItem() instanceof ElementalJetpackItem && JetpackSlotHolder.isJetpackEnabled(this.player)) {
                this.floatingTicks = 0;
            }
        }
    }

    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void elemental$resetFloatingTicksReturn(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (this.player != null) {
            ItemStack stack = JetpackSlotHolder.getJetpackStack(this.player);
            if (stack.getItem() instanceof ElementalJetpackItem && JetpackSlotHolder.isJetpackEnabled(this.player)) {
                this.floatingTicks = 0;
            }
        }
    }
}

