package com.elemental.enchantment;

import com.elemental.item.TidebreakerTridentItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Tidal Ram Enchantment for the Tidebreaker Trident.
 * Weaponizes the Tidebreaker Dash so it damages and blasts aside all mobs in the player's path.
 */
public class TidalRamEnchantment extends Enchantment {

    public TidalRamEnchantment() {
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
        return stack.getItem() instanceof TidebreakerTridentItem;
    }
}
