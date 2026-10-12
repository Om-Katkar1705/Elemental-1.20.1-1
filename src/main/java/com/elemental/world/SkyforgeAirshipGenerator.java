package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.item.BlueprintMaps;
import com.elemental.item.ModItems;
import com.elemental.quest.QuestSaveData;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class SkyforgeAirshipGenerator {

    /**
     * Resolves or generates the Crashed Skyforge Spaceship landmark buried between stone and dirt layer.
     */
    public static BlockPos findOrGenerateAirship(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSkyforgeAirshipLocation();
        if (existing != null) {
            return existing;
        }

        // Offset crash location ~250 blocks from nearPos
        int targetX = nearPos.getX() + 200;
        int targetZ = nearPos.getZ() + 150;

        // Force chunk loading and find true solid ground surface (never trees/leaves)
        BlockPos groundPos = AeronsCampHelper.findTrueGroundSurface(world, targetX, targetZ);
        int surfaceY = groundPos.getY();

        // Spawn genuinely buried between stone and dirt layer (~4 blocks beneath the surface)
        int crashY = surfaceY - 4;
        BlockPos crashPos = new BlockPos(targetX, crashY, targetZ);

        generateBuriedSpaceship(world, crashPos, surfaceY);
        saveData.setSkyforgeAirshipLocation(crashPos);
        saveData.markDirty();
        Elemental.LOGGER.info("Generated Crashed Spaceship buried at stone/dirt layer: {} (Surface: {})", crashPos, surfaceY);

        return crashPos;
    }

    private static void generateBuriedSpaceship(ServerWorld world, BlockPos center, int surfaceY) {
        // 1. Excavate an impact crater revealing the stone/dirt layer interface
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 25) {
                    // Impact floor around center Y
                    BlockPos groundPos = center.add(dx, 0, dz);
                    if (distSq <= 9) {
                        world.setBlockState(groundPos, Blocks.COARSE_DIRT.getDefaultState());
                    } else if (distSq <= 16) {
                        world.setBlockState(groundPos, (dx + dz) % 2 == 0 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState());
                    } else {
                        world.setBlockState(groundPos, Blocks.MOSSY_COBBLESTONE.getDefaultState());
                    }

                    // Clear crater chamber above up through surface and any tree canopies
                    int maxY = Math.max(surfaceY + 6, center.getY() + 8);
                    for (int y = center.getY() + 1; y <= maxY; y++) {
                        world.setBlockState(new BlockPos(center.getX() + dx, y, center.getZ() + dz), Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // 2. Spaceship hull structure (shattered wooden and metal hull)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floorPos = center.add(dx, 0, dz);
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    world.setBlockState(floorPos, Blocks.DARK_OAK_PLANKS.getDefaultState());
                }
            }
        }

        // Structural ribs & struts
        world.setBlockState(center.add(-3, 1, -1), Blocks.SPRUCE_LOG.getDefaultState());
        world.setBlockState(center.add(-3, 2, -1), Blocks.SPRUCE_LOG.getDefaultState());
        world.setBlockState(center.add(3, 1, 1), Blocks.SPRUCE_LOG.getDefaultState());
        world.setBlockState(center.add(3, 2, 1), Blocks.SPRUCE_LOG.getDefaultState());
        world.setBlockState(center.add(0, 1, -2), Blocks.IRON_BARS.getDefaultState());
        world.setBlockState(center.add(0, 2, -2), Blocks.CHAIN.getDefaultState());

        // Engine compartment machinery
        world.setBlockState(center.add(-1, 1, 0), Blocks.BLAST_FURNACE.getDefaultState());
        world.setBlockState(center.add(-2, 1, 0), Blocks.COPPER_BLOCK.getDefaultState());
        world.setBlockState(center.add(-2, 1, 1), Blocks.SOUL_CAMPFIRE.getDefaultState());

        // Chest 1: Engine Compartment (4 Pneumatic Pistons + Blueprint: Pneumatic Piston + Blueprint: Element Core + Iron/Redstone)
        BlockPos engineChestPos = center.add(-1, 1, 1);
        world.setBlockState(engineChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(engineChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.engine_compartment"));
            // 4 Pneumatic Pistons
            chest.setStack(11, new ItemStack(ModItems.PNEUMATIC_PISTON, 4));
            // Blueprint: Pneumatic Piston
            chest.setStack(12, BlueprintMaps.createBlueprintMap(world, engineChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
            // Blueprint: Element Core
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, engineChestPos, BlueprintMaps.Type.ELEMENT_CORE));
            // Iron & Redstone
            chest.setStack(15, new ItemStack(Items.IRON_INGOT, 4));
            chest.setStack(16, new ItemStack(Items.REDSTONE, 8));
            chest.markDirty();
        }

        // Chest 2: Flotation Chamber (2 Aerogel Cores + Blueprint: Aerogel Core + Feathers/Glass)
        BlockPos flotationChestPos = center.add(1, 1, 0);
        world.setBlockState(flotationChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(flotationChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.flotation_chamber"));
            // 2 Aerogel Cores
            chest.setStack(11, new ItemStack(ModItems.AEROGEL_CORE, 2));
            // Blueprint: Aerogel Core
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, flotationChestPos, BlueprintMaps.Type.AEROGEL_CORE));
            chest.setStack(14, new ItemStack(Items.FEATHER, 4));
            chest.setStack(15, new ItemStack(Items.GLASS, 4));
            chest.markDirty();
        }

        // Chest 3: Cockpit Flight Deck Archive (Blueprint: Elemental Jetpack + Copper/Coal/Compass)
        BlockPos cockpitChestPos = center.add(0, 1, -1);
        world.setBlockState(cockpitChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(cockpitChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.cockpit_archive"));
            // Blueprint: Elemental Jetpack
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, cockpitChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
            chest.setStack(11, new ItemStack(Items.COPPER_INGOT, 6));
            chest.setStack(12, new ItemStack(Items.COAL, 8));
            chest.setStack(15, new ItemStack(Items.COMPASS, 1));
            chest.markDirty();
        }
    }
}
