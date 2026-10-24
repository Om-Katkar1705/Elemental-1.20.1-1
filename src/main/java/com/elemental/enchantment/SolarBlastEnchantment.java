package com.elemental.enchantment;

import com.elemental.item.SunforgedScimitarItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Solar Blast Enchantment for the Sunforged Scimitar.
 * When firing a Solar Arc, grants a chance per level to transform into an explosive Solar Blast
 * that deals heightened damage and bursts into a multi-target AOE solar detonation.
 */
public class SolarBlastEnchantment extends Enchantment {

    public SolarBlastEnchantment() {
        super(Rarity.RARE, EnchantmentTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 4;
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMinPower(int level) {
        return 10 + (level - 1) * 9;
    }

    @Override
    public int getMaxPower(int level) {
        return this.getMinPower(level) + 20;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof SunforgedScimitarItem;
    }
}
