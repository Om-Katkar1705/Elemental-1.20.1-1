package com.elemental.block.entity;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registration for mod block entity types.
 */
public class ModBlockEntities {
    public static final BlockEntityType<WeaponerBlockEntity> WEAPONER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Elemental.MOD_ID, "weaponer"),
            FabricBlockEntityTypeBuilder.create(WeaponerBlockEntity::new, ModBlocks.WEAPONER).build()
    );

    public static final BlockEntityType<EverlivingAltarBlockEntity> EVERLIVING_ALTAR_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Elemental.MOD_ID, "everliving_altar"),
            FabricBlockEntityTypeBuilder.create(EverlivingAltarBlockEntity::new, ModBlocks.EVERLIVING_ALTAR).build()
    );

    public static void registerBlockEntities() {
        Elemental.LOGGER.info("Registering Block Entities for " + Elemental.MOD_ID);
    }
}
