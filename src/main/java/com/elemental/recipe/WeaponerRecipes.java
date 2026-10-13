package com.elemental.recipe;

import com.elemental.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-file recipe manager for the Weaponer crafting station.
 * Easily register and retrieve elemental crafting and weapon forging recipes.
 */
public class WeaponerRecipes {
    public record Recipe(Item base, Item ingredient, Item result, int count) {
        public boolean matches(ItemStack stackA, ItemStack stackB) {
            return (stackA.isOf(this.base) && stackB.isOf(this.ingredient))
                    || (stackA.isOf(this.ingredient) && stackB.isOf(this.base));
        }

        public ItemStack createResult() {
            return new ItemStack(this.result, this.count);
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    static {
        // ==========================================
        // 1. WEAPON FORGING RECIPES (Stencil + Element)
        // ==========================================
        register(ModItems.STENCIL, ModItems.DRY_HEAT_ELEMENT, ModItems.SUNFORGED_SCIMITAR);
        register(ModItems.STENCIL, ModItems.SNOW_ELEMENT, ModItems.FROSTWAKE_PICK);
        register(ModItems.STENCIL, ModItems.TREE_ELEMENT, ModItems.ROOTBOUND_AXE);
        register(ModItems.STENCIL, ModItems.OCEAN_ELEMENT, ModItems.TIDEBREAKER_TRIDENT);
        register(ModItems.ELEMENT_CORE, ModItems.MOUNTAIN_ELEMENT, ModItems.STORMCALLER_HAMMER);

        // ==========================================
        // 2. ELEMENT INFUSION RECIPES (Element Core + 1 Biome-Exclusive Common Block)
        // Strictly 1 defining block per element, found only in that element's biome.
        // ==========================================

        // --- 1. Dry Heat Element: Desert Biome (Cactus) ---
        register(ModItems.ELEMENT_CORE, Items.CACTUS, ModItems.DRY_HEAT_ELEMENT);

        // --- 2. Snow Element: Snowy Biomes (Snow Block) ---
        register(ModItems.ELEMENT_CORE, Items.SNOW_BLOCK, ModItems.SNOW_ELEMENT);

        // --- 3. Tree Element: Dark Forest Biome (Dark Oak Log) ---
        register(ModItems.ELEMENT_CORE, Items.DARK_OAK_LOG, ModItems.TREE_ELEMENT);

        // --- 4. Ocean Element: Ocean Biomes (Kelp) ---
        register(ModItems.ELEMENT_CORE, Items.KELP, ModItems.OCEAN_ELEMENT);

        // --- 5. Mountain Element: Mountain Peaks Biome (Calcite) ---
        register(ModItems.ELEMENT_CORE, Items.CALCITE, ModItems.MOUNTAIN_ELEMENT);

        // --- 6. Fire Element: Nether Dimension (Netherrack) ---
        register(ModItems.ELEMENT_CORE, Items.NETHERRACK, ModItems.FIRE_ELEMENT);
    }

    /**
     * Helper method to register a recipe with default count of 1.
     */
    public static void register(Item base, Item ingredient, Item result) {
        register(base, ingredient, result, 1);
    }

    /**
     * Helper method to register a recipe with a custom result count.
     */
    public static void register(Item base, Item ingredient, Item result, int count) {
        RECIPES.add(new Recipe(base, ingredient, result, count));
    }

    /**
     * Helper method to find the matching recipe and return the crafted result.
     * Matches in either input order for seamless player experience.
     */
    public static ItemStack getCraftingResult(ItemStack stackA, ItemStack stackB) {
        if (stackA.isEmpty() || stackB.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(stackA, stackB)) {
                return recipe.createResult();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Checks if an item is a valid base item (Slot 0, e.g. Stencil or Element Core).
     */
    public static boolean isValidBase(ItemStack stack) {
        if (stack.isOf(ModItems.STENCIL) || stack.isOf(ModItems.ELEMENT_CORE)) {
            return true;
        }
        for (Recipe recipe : RECIPES) {
            if (stack.isOf(recipe.base())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an item is a valid ingredient (Slot 1, e.g. elements or catalyst blocks).
     */
    public static boolean isValidIngredient(ItemStack stack) {
        for (Recipe recipe : RECIPES) {
            if (stack.isOf(recipe.ingredient()) || stack.isOf(recipe.base())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Preserved for backward compatibility.
     */
    public static boolean isElementIngredient(ItemStack stack) {
        return isValidIngredient(stack);
    }

    public static List<Recipe> getAllRecipes() {
        return RECIPES;
    }

    public static void init() {
        // Ensures class loading / static initializer runs
    }
}
