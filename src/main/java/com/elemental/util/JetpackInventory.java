package com.elemental.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class JetpackInventory implements Inventory {
    private final PlayerEntity player;

    public JetpackInventory(PlayerEntity player) {
        this.player = player;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return ((JetpackSlotHolder) player).elemental$getJetpackSlot().isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return ((JetpackSlotHolder) player).elemental$getJetpackSlot();
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack current = ((JetpackSlotHolder) player).elemental$getJetpackSlot();
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack split = current.split(amount);
        if (current.isEmpty()) {
            ((JetpackSlotHolder) player).elemental$setJetpackSlot(ItemStack.EMPTY);
        }
        markDirty();
        return split;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack current = ((JetpackSlotHolder) player).elemental$getJetpackSlot();
        ((JetpackSlotHolder) player).elemental$setJetpackSlot(ItemStack.EMPTY);
        markDirty();
        return current;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        ((JetpackSlotHolder) player).elemental$setJetpackSlot(stack);
        markDirty();
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public void markDirty() {
        if (this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            com.elemental.network.QuestNetworking.sendExtraSlotSync(serverPlayer);
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return this.player.isAlive() && this.player.squaredDistanceTo(player) < 64.0;
    }

    @Override
    public void clear() {
        ((JetpackSlotHolder) player).elemental$setJetpackSlot(ItemStack.EMPTY);
    }
}
