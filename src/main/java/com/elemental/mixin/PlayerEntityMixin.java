package com.elemental.mixin;

import com.elemental.util.JetpackSlotHolder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements JetpackSlotHolder {

    @Unique
    private ItemStack elemental$jetpackStack = ItemStack.EMPTY;

    @Unique
    private boolean elemental$jetpackEnabled = true;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public ItemStack elemental$getJetpackSlot() {
        return this.elemental$jetpackStack != null ? this.elemental$jetpackStack : ItemStack.EMPTY;
    }

    @Override
    public void elemental$setJetpackSlot(ItemStack stack) {
        this.elemental$jetpackStack = stack != null ? stack : ItemStack.EMPTY;
    }

    @Override
    public boolean elemental$isJetpackEnabled() {
        return this.elemental$jetpackEnabled;
    }

    @Override
    public void elemental$setJetpackEnabled(boolean enabled) {
        this.elemental$jetpackEnabled = enabled;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void elemental$writeJetpackSlot(NbtCompound nbt, CallbackInfo ci) {
        if (this.elemental$jetpackStack != null && !this.elemental$jetpackStack.isEmpty()) {
            nbt.put("ElementalJetpackSlot", this.elemental$jetpackStack.writeNbt(new NbtCompound()));
        }
        nbt.putBoolean("ElementalJetpackEnabled", this.elemental$jetpackEnabled);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void elemental$readJetpackSlot(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("ElementalJetpackSlot")) {
            this.elemental$jetpackStack = ItemStack.fromNbt(nbt.getCompound("ElementalJetpackSlot"));
        } else {
            this.elemental$jetpackStack = ItemStack.EMPTY;
        }
        if (nbt.contains("ElementalJetpackEnabled")) {
            this.elemental$jetpackEnabled = nbt.getBoolean("ElementalJetpackEnabled");
        } else {
            this.elemental$jetpackEnabled = true;
        }
    }

    @Inject(method = "dropInventory", at = @At("TAIL"))
    private void elemental$dropJetpackSlot(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            if (this.elemental$jetpackStack != null && !this.elemental$jetpackStack.isEmpty()) {
                player.dropItem(this.elemental$jetpackStack, true, false);
                this.elemental$jetpackStack = ItemStack.EMPTY;
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void elemental$tickJetpackSlot(CallbackInfo ci) {
        if (this.elemental$jetpackStack != null && !this.elemental$jetpackStack.isEmpty()) {
            this.elemental$jetpackStack.inventoryTick(this.getWorld(), this, -1, true);
        }
    }

    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void elemental$preventFlightCrouch(CallbackInfo ci) {
        if (this.elemental$jetpackEnabled && !this.isOnGround() && !this.isTouchingWater() && !this.isSwimming() && !this.isFallFlying()) {
            if (this.elemental$jetpackStack != null && this.elemental$jetpackStack.getItem() instanceof com.elemental.item.ElementalJetpackItem) {
                this.setPose(net.minecraft.entity.EntityPose.STANDING);
                ci.cancel();
            }
        }
    }
}
