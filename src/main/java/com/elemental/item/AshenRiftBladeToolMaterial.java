package com.elemental.item;

import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

public class AshenRiftBladeToolMaterial implements ToolMaterial {
    public static final AshenRiftBladeToolMaterial INSTANCE = new AshenRiftBladeToolMaterial();

    @Override
    public int getDurability() {
        return 1561;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return 8.0f;
    }

    @Override
    public float getAttackDamage() {
        return 4.0f;
    }

    @Override
    public int getMiningLevel() {
        return 3;
    }

    @Override
    public int getEnchantability() {
        return 15;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ModItems.FIRE_ELEMENT, Items.NETHERITE_SCRAP, Items.OBSIDIAN);
    }
}
