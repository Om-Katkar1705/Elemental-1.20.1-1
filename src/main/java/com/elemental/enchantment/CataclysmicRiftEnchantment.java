package com.elemental.enchantment;

import com.elemental.item.AshenRiftBladeItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Cataclysmic Rift Enchantment for the Ashen Rift Blade.
 * Grants a chance when activating Rift Collapse to summon a blazing meteorite
 * from the sky onto the collapsed ground, devastating all trapped enemies.
 */
public class CataclysmicRiftEnchantment extends Enchantment {

    public CataclysmicRiftEnchantment() {
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
        return stack.getItem() instanceof AshenRiftBladeItem;
    }
}
