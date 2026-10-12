package com.elemental.block;

import com.elemental.Elemental;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block WEAPONER = registerBlock("weaponer",
            new WeaponerBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(4.0f, 6.0f)
                    .sounds(BlockSoundGroup.ANVIL)
                    .nonOpaque()
            ));

    public static final Block EVERLIVING_ALTAR = registerBlock("everliving_altar",
            new EverlivingAltarBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.BLACK)
                    .strength(5.0f, 1200.0f)
                    .sounds(BlockSoundGroup.DEEPSLATE)
                    .luminance(state -> 10)
                    .requiresTool()
            ));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier(Elemental.MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier(Elemental.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
    }

    public static void registerModBlocks() {
        Elemental.LOGGER.info("Registering ModBlocks for " + Elemental.MOD_ID);
    }
}
