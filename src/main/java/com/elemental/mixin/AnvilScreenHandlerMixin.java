package com.elemental.mixin;

import com.elemental.block.ModBlocks;
import com.elemental.enchantment.ModEnchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {

    @Shadow @Final private Property levelCost;

    protected AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void elemental$restrictNormalAnvil(CallbackInfo ci) {
        ItemStack result = this.output.getStack(0);
        ItemStack sacrifice = this.input.getStack(1);

        if (!ModEnchantments.hasCustomEnchantment(result) && !ModEnchantments.hasCustomEnchantment(sacrifice)) {
            return;
        }

        boolean isElementalAnvil = this.context.get((world, pos) -> world.getBlockState(pos).isOf(ModBlocks.ELEMENTAL_ANVIL)).orElse(false);

        if (!isElementalAnvil) {
            // Normal anvil is restricted from applying or combining custom enchantments!
            this.output.setStack(0, ItemStack.EMPTY);
            this.levelCost.set(0);
            this.sendContentUpdates();
        }
    }

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void elemental$canTakeOutputCheck(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        ItemStack result = this.output.getStack(0);
        ItemStack sacrifice = this.input.getStack(1);

        if (ModEnchantments.hasCustomEnchantment(result) || ModEnchantments.hasCustomEnchantment(sacrifice)) {
            boolean isElementalAnvil = this.context.get((world, pos) -> world.getBlockState(pos).isOf(ModBlocks.ELEMENTAL_ANVIL)).orElse(false);
            if (!isElementalAnvil) {
                cir.setReturnValue(false);
            }
        }
    }
}
