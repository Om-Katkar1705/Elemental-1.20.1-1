package com.elemental.item;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroup {
    public static final ItemGroup ELEMENTAL_INGREDIENT_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(Elemental.MOD_ID, "elemental_ingredient_group"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.elemental_ingredient_group"))
                    .icon(() -> new ItemStack(ModItems.ELEMENT_CORE)).entries((displayContext, entries) -> {
                        entries.add(ModItems.STENCIL);
                        entries.add(ModItems.DRY_HEAT_ELEMENT);
                        entries.add(ModItems.SNOW_ELEMENT);
                        entries.add(ModItems.TREE_ELEMENT);
                        entries.add(ModItems.OCEAN_ELEMENT);
                        entries.add(ModItems.MOUNTAIN_ELEMENT);
                        entries.add(ModItems.FIRE_ELEMENT);
                        entries.add(ModItems.ELEMENT_CORE);
                        entries.add(ModItems.PNEUMATIC_PISTON);
                        entries.add(ModItems.AEROGEL_CORE);
                    }).build());

    public static final ItemGroup ELEMENTAL_WEAPON_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(Elemental.MOD_ID, "elemental_weapon_group"),
            FabricItemGroup.builder().displayName(Text.translatable("itemgroup.elemental_weapon_group"))
                    .icon(() -> new ItemStack(ModItems.SUNFORGED_SCIMITAR)).entries((displayContext, entries) -> {
                        entries.add(ModBlocks.WEAPONER);
                        entries.add(ModBlocks.EVERLIVING_ALTAR);
                        entries.add(ModItems.SUNFORGED_SCIMITAR);
                        entries.add(ModItems.FROSTWAKE_PICK);
                        entries.add(ModItems.ROOTBOUND_AXE);
                        entries.add(ModItems.TIDEBREAKER_TRIDENT);
                        entries.add(ModItems.ELEMENTAL_JETPACK);
                    }).build());

    public static void registerItemGroups() {
    }
}
