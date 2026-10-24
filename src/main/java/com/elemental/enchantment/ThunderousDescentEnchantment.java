package com.elemental.enchantment;

import com.elemental.item.StormcallerHammerItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Thunderous Descent Enchantment for the Stormcaller Hammer.
 * Multiplies melee attack damage based on falling height.
 * Levels 1 & 2 have a high probability to trigger, while Levels 3 & 4 guarantee height-multiplied damage.
 */
public class ThunderousDescentEnchantment extends Enchantment {

    public ThunderousDescentEnchantment() {
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
        return stack.getItem() instanceof StormcallerHammerItem;
    }
}
