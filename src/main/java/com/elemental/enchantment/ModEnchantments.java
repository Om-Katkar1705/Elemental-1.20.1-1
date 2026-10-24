package com.elemental.enchantment;

import com.elemental.Elemental;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEnchantments {

    // 1. Sunforged Scimitar
    public static final Enchantment SOLAR_BLAST = new SolarBlastEnchantment();
    public static final Enchantment DAWNS_SIPHON = new DawnsSiphonEnchantment();

    // 2. Frostwake Pick
    public static final Enchantment SPIKE_SHOCKWAVE = new SpikeShockwaveEnchantment();

    // 3. Rootbound Axe
    public static final Enchantment TIMBER_BASTION = new TimberBastionEnchantment();

    // 4. Tidebreaker Trident
    public static final Enchantment TIDAL_RAM = new TidalRamEnchantment();

    // 5. Stormcaller Hammer
    public static final Enchantment THUNDEROUS_DESCENT = new ThunderousDescentEnchantment();

    // 6. Ashen Rift Blade
    public static final Enchantment CATACLYSMIC_RIFT = new CataclysmicRiftEnchantment();

    public static void registerModEnchantments() {
        Elemental.LOGGER.info("Registering custom weapon enchantments for Elemental mod...");

        // Sunforged Scimitar
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "solar_blast"), SOLAR_BLAST);
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "dawns_siphon"), DAWNS_SIPHON);

        // Frostwake Pick
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "spike_shockwave"), SPIKE_SHOCKWAVE);

        // Rootbound Axe
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "timber_bastion"), TIMBER_BASTION);

        // Tidebreaker Trident
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "tidal_ram"), TIDAL_RAM);

        // Stormcaller Hammer
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "thunderous_descent"), THUNDEROUS_DESCENT);

        // Ashen Rift Blade
        Registry.register(Registries.ENCHANTMENT, new Identifier(Elemental.MOD_ID, "cataclysmic_rift"), CATACLYSMIC_RIFT);
    }

    public static boolean isCustomEnchantment(Enchantment enchantment) {
        if (enchantment == null) return false;
        Identifier id = Registries.ENCHANTMENT.getId(enchantment);
        return id != null && id.getNamespace().equals(Elemental.MOD_ID);
    }

    public static boolean hasCustomEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var enchants = EnchantmentHelper.get(stack);
        for (Enchantment e : enchants.keySet()) {
            if (isCustomEnchantment(e)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================
    // HELPERS: Sunforged Scimitar
    // ==========================================

    public static int getSolarBlastLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(SOLAR_BLAST, stack);
    }

    public static float getSolarBlastProcChance(int level) {
        return switch (level) {
            case 1 -> 0.25f;
            case 2 -> 0.50f;
            case 3 -> 0.75f;
            case 4 -> 1.00f;
            default -> Math.min(1.0f, level * 0.25f);
        };
    }

    public static int getDawnsSiphonLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(DAWNS_SIPHON, stack);
    }

    public static float getDawnsSiphonRepairChance(int level) {
        return switch (level) {
            case 1 -> 0.15f;
            case 2 -> 0.25f;
            case 3 -> 0.35f;
            case 4 -> 0.50f;
            default -> Math.min(1.0f, level * 0.15f);
        };
    }

    // ==========================================
    // HELPERS: Frostwake Pick
    // ==========================================

    public static int getSpikeShockwaveLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(SPIKE_SHOCKWAVE, stack);
    }

    public static int getSpikeShockwaveMaxRadius(int level) {
        return switch (level) {
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 8;
            case 4 -> 10;
            default -> Math.min(10, 4 + (level - 1) * 2);
        };
    }

    // ==========================================
    // HELPERS: Rootbound Axe
    // ==========================================

    public static int getTimberBastionLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(TIMBER_BASTION, stack);
    }

    public static int getTimberBastionDurationTicks(int level) {
        return switch (level) {
            case 1 -> 40; // 2.0s
            case 2 -> 46; // 2.3s
            case 3 -> 54; // 2.7s
            case 4 -> 60; // 3.0s
            default -> 40 + level * 5;
        };
    }

    public static float getTimberBastionRegenChance(int level) {
        return switch (level) {
            case 1 -> 0.20f;
            case 2 -> 0.35f;
            case 3 -> 0.50f;
            case 4 -> 0.70f;
            default -> Math.min(1.0f, 0.20f + (level - 1) * 0.15f);
        };
    }

    // ==========================================
    // HELPERS: Tidebreaker Trident
    // ==========================================

    public static int getTidalRamLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(TIDAL_RAM, stack);
    }

    public static float getTidalRamDamage(int level) {
        return switch (level) {
            case 1 -> 5.0f;
            case 2 -> 9.0f;
            case 3 -> 13.0f;
            case 4 -> 18.0f;
            default -> 5.0f + (level - 1) * 4.0f;
        };
    }

    // ==========================================
    // HELPERS: Stormcaller Hammer
    // ==========================================

    public static int getThunderousDescentLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(THUNDEROUS_DESCENT, stack);
    }

    public static float getThunderousDescentChance(int level) {
        return switch (level) {
            case 1 -> 0.40f;
            case 2 -> 0.65f;
            case 3, 4 -> 1.00f;
            default -> 1.00f;
        };
    }

    public static float getThunderousDescentMultiplier(int level) {
        return switch (level) {
            case 1 -> 1.5f;
            case 2 -> 2.0f;
            case 3 -> 2.5f;
            case 4 -> 3.2f;
            default -> 1.0f + level * 0.5f;
        };
    }

    // ==========================================
    // HELPERS: Ashen Rift Blade
    // ==========================================

    public static int getCataclysmicRiftLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return EnchantmentHelper.getLevel(CATACLYSMIC_RIFT, stack);
    }

    public static float getCataclysmicRiftChance(int level) {
        return switch (level) {
            case 1 -> 0.15f;
            case 2 -> 0.25f;
            case 3 -> 0.35f;
            case 4 -> 0.50f;
            default -> Math.min(0.75f, 0.15f + (level - 1) * 0.10f);
        };
    }

    public static float getCataclysmicRiftDamage(int level) {
        return switch (level) {
            case 1 -> 14.0f;
            case 2 -> 20.0f;
            case 3 -> 26.0f;
            case 4 -> 34.0f;
            default -> 14.0f + (level - 1) * 6.0f;
        };
    }
}
