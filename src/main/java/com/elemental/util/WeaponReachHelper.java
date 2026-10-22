package com.elemental.util;

import com.elemental.item.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Utility helper for managing attack reach and range for custom Elemental weapons.
 * Vanilla Minecraft survival attack reach is 3.0 blocks.
 * Custom swords, axes, pickaxes, hammers, and tridents are extended slightly by +1.25 blocks (4.25 blocks total).
 */
public class WeaponReachHelper {

    /**
     * Generalized attack range for custom weapons:
     * Vanilla reach (3.0) + 1.25 blocks = 4.25 blocks.
     */
    public static final double EXTENDED_ATTACK_RANGE = 4.25;

    public static boolean isCustomWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SunforgedScimitarItem
                || item instanceof AshenRiftBladeItem
                || item instanceof RootboundAxeItem
                || item instanceof FrostwakePickItem
                || item instanceof StormcallerHammerItem
                || item instanceof TidebreakerTridentItem;
    }

    public static double getAttackRange(ItemStack stack) {
        return isCustomWeapon(stack) ? EXTENDED_ATTACK_RANGE : 3.0;
    }
}
