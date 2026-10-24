package com.elemental.enchantment;

import com.elemental.item.RootboundAxeItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Timber Bastion Enchantment for the Rootbound Axe.
 * Pressing 'R' summons an organic safety barricade of logs (3 blocks tall, 4 blocks wide)
 * in front of the player for 2-3 seconds, with a chance to grant Regeneration.
 */
public class TimberBastionEnchantment extends Enchantment {

    public TimberBastionEnchantment() {
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
        return stack.getItem() instanceof RootboundAxeItem;
    }
}
