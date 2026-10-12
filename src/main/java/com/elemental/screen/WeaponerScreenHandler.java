package com.elemental.screen;

import com.elemental.block.ModBlocks;
import com.elemental.item.ModItems;
import com.elemental.recipe.WeaponerRecipes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * ScreenHandler for the Weaponer crafting station.
 * Synchronizes inventory slots between client and server and handles crafting logic.
 */
public class WeaponerScreenHandler extends ScreenHandler {
    public static final int STENCIL_SLOT = 0;
    public static final int ELEMENT_SLOT = 1;
    public static final int RESULT_SLOT = 2;

    private static final int INV_START = 3;
    private static final int INV_END = 30;
    private static final int HOTBAR_START = 30;
    private static final int HOTBAR_END = 39;

    private final Inventory inventory;
    private final ScreenHandlerContext context;
    private final PlayerEntity player;

    // Client-side constructor
    public WeaponerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), ScreenHandlerContext.EMPTY);
    }

    // Server-side / primary constructor
    public WeaponerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, ScreenHandlerContext context) {
        super(ModScreenHandlers.WEAPONER_SCREEN_HANDLER, syncId);
        checkSize(inventory, 3);
        this.inventory = inventory;
        this.context = context;
        this.player = playerInventory.player;
        inventory.onOpen(playerInventory.player);

        // Slot 0: Base Stencil / Element Core (Input)
        this.addSlot(new Slot(inventory, STENCIL_SLOT, 27, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return WeaponerRecipes.isValidBase(stack) || WeaponerRecipes.isValidIngredient(stack);
            }

            @Override
            public void markDirty() {
                super.markDirty();
                updateResult();
            }
        });

        // Slot 1: Element Item / Catalyst Block (Input)
        this.addSlot(new Slot(inventory, ELEMENT_SLOT, 76, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return WeaponerRecipes.isValidIngredient(stack) || WeaponerRecipes.isValidBase(stack);
            }

            @Override
            public void markDirty() {
                super.markDirty();
                updateResult();
            }
        });

        // Slot 2: Result (Output - non-interactive for insertion)
        this.addSlot(new Slot(inventory, RESULT_SLOT, 134, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }

            @Override
            public void onTakeItem(PlayerEntity player, ItemStack stack) {
                // Consume 1 item from each input slot
                inventory.getStack(STENCIL_SLOT).decrement(1);
                inventory.getStack(ELEMENT_SLOT).decrement(1);
                inventory.markDirty();

                // Play anvil sound and spawn enchantment particles on server
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.playSound(
                            null,
                            player.getBlockPos(),
                            SoundEvents.BLOCK_ANVIL_USE,
                            SoundCategory.BLOCKS,
                            0.8F,
                            1.0F
                    );
                    serverWorld.spawnParticles(
                            ParticleTypes.ENCHANT,
                            player.getX(),
                            player.getY() + 1.0,
                            player.getZ(),
                            20,
                            0.4,
                            0.4,
                            0.4,
                            0.1
                    );
                }

                super.onTakeItem(player, stack);
                updateResult();
            }
        });

        // Player Inventory (3 rows x 9 columns)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player Hotbar (9 slots)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // Evaluate craft result on open
        this.updateResult();
    }

    /**
     * Re-calculates the output slot based on current inputs using WeaponerRecipes.
     */
    public void updateResult() {
        ItemStack stencilStack = this.inventory.getStack(STENCIL_SLOT);
        ItemStack elementStack = this.inventory.getStack(ELEMENT_SLOT);
        this.inventory.setStack(RESULT_SLOT, WeaponerRecipes.getCraftingResult(stencilStack, elementStack));
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        if (inventory == this.inventory) {
            this.updateResult();
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(this.context, player, ModBlocks.WEAPONER);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);

        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot == RESULT_SLOT) {
                // Moving result item into player inventory / hotbar
                if (!this.insertItem(originalStack, INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickTransfer(originalStack, newStack);
            } else if (invSlot == STENCIL_SLOT || invSlot == ELEMENT_SLOT) {
                // Moving inputs into player inventory / hotbar
                if (!this.insertItem(originalStack, INV_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory / hotbar into crafting slots
                if (WeaponerRecipes.isValidBase(originalStack)) {
                    if (!this.insertItem(originalStack, STENCIL_SLOT, STENCIL_SLOT + 1, false)) {
                        if (!this.insertItem(originalStack, ELEMENT_SLOT, ELEMENT_SLOT + 1, false)) {
                            if (invSlot < HOTBAR_START) {
                                if (!this.insertItem(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                                    return ItemStack.EMPTY;
                                }
                            } else if (!this.insertItem(originalStack, INV_START, INV_END, false)) {
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                } else if (WeaponerRecipes.isValidIngredient(originalStack)) {
                    if (!this.insertItem(originalStack, ELEMENT_SLOT, ELEMENT_SLOT + 1, false)) {
                        if (!this.insertItem(originalStack, STENCIL_SLOT, STENCIL_SLOT + 1, false)) {
                            if (invSlot < HOTBAR_START) {
                                if (!this.insertItem(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                                    return ItemStack.EMPTY;
                                }
                            } else if (!this.insertItem(originalStack, INV_START, INV_END, false)) {
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                } else if (invSlot >= INV_START && invSlot < HOTBAR_START) {
                    if (!this.insertItem(originalStack, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (invSlot >= HOTBAR_START && invSlot < HOTBAR_END) {
                    if (!this.insertItem(originalStack, INV_START, INV_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }

            if (originalStack.getCount() == newStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTakeItem(player, originalStack);
        }

        return newStack;
    }
}
