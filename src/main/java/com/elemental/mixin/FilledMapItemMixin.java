package com.elemental.mixin;

import com.elemental.item.BlueprintMapStateAccessor;
import com.elemental.item.BlueprintMaps;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;

@Mixin(FilledMapItem.class)
public class FilledMapItemMixin {

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void elemental$onBlueprintInventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (!world.isClient() && BlueprintMaps.isBlueprint(stack)) {
            BlueprintMaps.ensureBlueprintMapInitialized(stack, world);
            if (entity instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
                Integer mapId = FilledMapItem.getMapId(stack);
                if (mapId != null) {
                    MapState mapState = serverWorld.getMapState(FilledMapItem.getMapName(mapId));
                    if (mapState != null) {
                        boolean isHeld = (serverPlayer.getMainHandStack() == stack || serverPlayer.getOffHandStack() == stack);
                        if (isHeld || selected || (serverPlayer.age % 20 == 0)) {
                            MapState.UpdateData updateData = new MapState.UpdateData(0, 0, 128, 128, mapState.colors);
                            serverPlayer.networkHandler.sendPacket(new MapUpdateS2CPacket(mapId, mapState.scale, mapState.locked, Collections.emptyList(), updateData));
                        }
                    }
                }
            }
        }
    }


    @Inject(method = "updateColors", at = @At("HEAD"), cancellable = true)
    private void elemental$preventBlueprintMapScan(World world, Entity entity, MapState state, CallbackInfo ci) {
        if (state instanceof BlueprintMapStateAccessor accessor && accessor.elemental$getBlueprintType() != null) {
            ci.cancel();
        }
    }
}
