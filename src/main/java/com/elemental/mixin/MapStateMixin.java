package com.elemental.mixin;

import com.elemental.item.BlueprintMapStateAccessor;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MapState.class)
public class MapStateMixin implements BlueprintMapStateAccessor {
    @Unique
    private String elemental$blueprintType = null;

    @Override
    public String elemental$getBlueprintType() {
        return this.elemental$blueprintType;
    }

    @Override
    public void elemental$setBlueprintType(String type) {
        this.elemental$blueprintType = type;
    }

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void elemental$writeBlueprintNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        if (this.elemental$blueprintType != null) {
            nbt.putString("ElementalBlueprint", this.elemental$blueprintType);
        }
    }

    @Inject(method = "fromNbt", at = @At("RETURN"))
    private static void elemental$readBlueprintNbt(NbtCompound nbt, CallbackInfoReturnable<MapState> cir) {
        if (nbt.contains("ElementalBlueprint")) {
            MapState state = cir.getReturnValue();
            if (state instanceof BlueprintMapStateAccessor accessor) {
                accessor.elemental$setBlueprintType(nbt.getString("ElementalBlueprint"));
            }
        }
    }
}
