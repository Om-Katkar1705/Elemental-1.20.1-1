package com.elemental.mixin;

import com.elemental.item.ElementalJetpackItem;
import com.elemental.network.QuestNetworking;
import com.elemental.util.JetpackInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin extends ScreenHandler {

    @Unique
    private static final int JETPACK_SLOT_INDEX = 46;

    protected PlayerScreenHandlerMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void elemental$addJetpackSlot(PlayerInventory inventory, boolean onServer, PlayerEntity owner, CallbackInfo ci) {
        this.addSlot(new Slot(new JetpackInventory(owner), 0, 77, 44) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof ElementalJetpackItem;
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }

            @Override
            public void markDirty() {
                super.markDirty();
                if (owner instanceof ServerPlayerEntity serverPlayer) {
                    QuestNetworking.sendExtraSlotSync(serverPlayer);
                }
            }
        });
    }

    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void elemental$quickMoveJetpack(PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return;
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) return;

        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();

        // 1. Shift-clicking FROM the extra jetpack slot (slot 46) -> move to inventory/hotbar
        if (slotIndex == JETPACK_SLOT_INDEX) {
            if (!this.insertItem(original, 9, 45, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            slot.onQuickTransfer(original, copy);
            if (original.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            cir.setReturnValue(copy);
            return;
        }

        // 2. Shift-clicking an ElementalJetpack FROM inventory/hotbar -> move into extra slot (slot 46)
        if (slotIndex >= 9 && slotIndex < 45) {
            if (original.getItem() instanceof ElementalJetpackItem) {
                Slot extraSlot = this.slots.get(JETPACK_SLOT_INDEX);
                if (extraSlot != null && !extraSlot.hasStack() && extraSlot.canInsert(original)) {
                    if (this.insertItem(original, JETPACK_SLOT_INDEX, JETPACK_SLOT_INDEX + 1, false)) {
                        slot.onQuickTransfer(original, copy);
                        if (original.isEmpty()) {
                            slot.setStack(ItemStack.EMPTY);
                        } else {
                            slot.markDirty();
                        }
                        cir.setReturnValue(copy);
                        return;
                    }
                }
            }
        }
    }
}
