package com.elemental.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public interface JetpackSlotHolder {
    ItemStack elemental$getJetpackSlot();
    void elemental$setJetpackSlot(ItemStack stack);

    static ItemStack getJetpackStack(LivingEntity entity) {
        if (entity instanceof JetpackSlotHolder holder) {
            return holder.elemental$getJetpackSlot();
        }
        return ItemStack.EMPTY;
    }
}
