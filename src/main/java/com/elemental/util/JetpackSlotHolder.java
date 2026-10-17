package com.elemental.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public interface JetpackSlotHolder {
    ItemStack elemental$getJetpackSlot();
    void elemental$setJetpackSlot(ItemStack stack);

    boolean elemental$isJetpackEnabled();
    void elemental$setJetpackEnabled(boolean enabled);

    static ItemStack getJetpackStack(LivingEntity entity) {
        if (entity == null) return ItemStack.EMPTY;
        if (entity instanceof JetpackSlotHolder holder) {
            ItemStack stack = holder.elemental$getJetpackSlot();
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        // Also check standard chestplate armor slot in case player equipped it there
        ItemStack chest = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        if (chest != null && chest.getItem() instanceof com.elemental.item.ElementalJetpackItem) {
            return chest;
        }
        return ItemStack.EMPTY;
    }

    static boolean isJetpackEnabled(LivingEntity entity) {
        if (entity instanceof JetpackSlotHolder holder) {
            return holder.elemental$isJetpackEnabled();
        }
        return true;
    }
}
