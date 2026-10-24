package com.elemental.enchantment;

import com.elemental.item.FrostwakePickItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Spike Shockwave Enchantment for the Frostwake Pick.
 * Transforms the Glacial Shockwave into concentric rings of erupting ice spikes (radius 4 to 10 blocks)
 * that launch enemies upward with heavy knockback while preserving frost slowness.
 */
public class SpikeShockwaveEnchantment extends Enchantment {

    public SpikeShockwaveEnchantment() {
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
        return 10 + (level - 1) * 8;
    }

    @Override
    public int getMaxPower(int level) {
        return this.getMinPower(level) + 20;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof FrostwakePickItem;
    }
}
