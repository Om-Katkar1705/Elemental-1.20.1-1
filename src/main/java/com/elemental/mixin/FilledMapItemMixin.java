package com.elemental.mixin;

import com.elemental.item.BlueprintMapStateAccessor;
import com.elemental.item.BlueprintMaps;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FilledMapItem.class)
public class FilledMapItemMixin {

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void elemental$onBlueprintInventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (!world.isClient() && BlueprintMaps.isBlueprint(stack)) {
            BlueprintMaps.ensureBlueprintMapInitialized(stack, world);
        }
    }

    @Inject(method = "updateColors", at = @At("HEAD"), cancellable = true)
    private void elemental$preventBlueprintMapScan(World world, Entity entity, MapState state, CallbackInfo ci) {
        if (state instanceof BlueprintMapStateAccessor accessor && accessor.elemental$getBlueprintType() != null) {
            ci.cancel();
        }
    }
}
