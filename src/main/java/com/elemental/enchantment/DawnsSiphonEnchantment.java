package com.elemental.enchantment;

import com.elemental.item.SunforgedScimitarItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Dawn's Siphon Enchantment for the Sunforged Scimitar.
 * Siphons thermal energy upon striking enemies in sunlight or at high heat:
 * - Infuses hits with bonus solar fire damage.
 * - Chance to siphon vitality to restore weapon durability.
 * - Grants the wielder brief Fire Resistance to endure intense solar combat.
 */
public class DawnsSiphonEnchantment extends Enchantment {

    public DawnsSiphonEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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
        return 8 + (level - 1) * 7;
    }

    @Override
    public int getMaxPower(int level) {
        return this.getMinPower(level) + 18;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return stack.getItem() instanceof SunforgedScimitarItem;
    }
}
