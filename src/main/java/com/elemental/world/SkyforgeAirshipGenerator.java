package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.item.BlueprintMaps;
import com.elemental.item.ModItems;
import com.elemental.quest.QuestSaveData;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;
import java.util.Optional;

public class SkyforgeAirshipGenerator {

    /**
     * Resolves or generates the Crashed Skyforge Spaceship landmark buried between stone and dirt layer
     * on a verified dry highland / cliff site.
     */
    public static BlockPos findOrGenerateAirship(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSkyforgeAirshipLocation();
        if (existing != null) {
            return existing;
        }

        // Search concentric rings around Aeron's camp (nearPos) for a safe dry cliff/highland
        BlockPos bestSurfacePos = findSafeCliffSite(world, nearPos);
        int surfaceY = bestSurfacePos.getY();

        // Spawn buried between stone and dirt layer (~6 blocks beneath the surface)
        int crashY = surfaceY - 6;
        BlockPos crashPos = new BlockPos(bestSurfacePos.getX(), crashY, bestSurfacePos.getZ());

        StructureTemplateManager templateManager = world.getStructureTemplateManager();
        Optional<StructureTemplate> templateOpt = templateManager.getTemplate(new Identifier(Elemental.MOD_ID, "crashed_skyforge_ship"));
        if (templateOpt.isPresent()) {
            BlockPos placePos = crashPos.add(-6, 0, -6);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq <= 25) {
                        for (int fillY = -3; fillY <= -1; fillY++) {
                            BlockPos underPos = crashPos.add(dx, fillY, dz);
                            if (world.getBlockState(underPos).isAir() || AeronsCampHelper.isLiquidOrWater(world, underPos)) {
                                world.setBlockState(underPos, Blocks.STONE.getDefaultState());
                            }
                        }
                        int maxY = Math.max(surfaceY + 6, crashPos.getY() + 8);
                        for (int y = crashPos.getY() + 1; y <= maxY; y++) {
                            world.setBlockState(new BlockPos(crashPos.getX() + dx, y, crashPos.getZ() + dz), Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
            templateOpt.get().place(world, placePos, placePos, new StructurePlacementData(), world.getRandom(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            populateAirshipChests(world, crashPos);
            Elemental.LOGGER.info("Placed Crashed Spaceship from NBT template at {}", placePos);
        } else {
            generateBuriedSpaceship(world, crashPos, surfaceY);
        }

        saveData.setSkyforgeAirshipLocation(crashPos);
        saveData.markDirty();
        Elemental.LOGGER.info("Generated Crashed Spaceship on safe dry highland/cliff: {} (Surface: {})", crashPos, surfaceY);

        return crashPos;
    }

    public static BlockPos findSafeCliffSite(ServerWorld world, BlockPos campPos) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;

        // Search concentric rings 160 to 320 blocks from Aeron's camp
        int[] radii = new int[]{160, 200, 240, 280, 320};
        int numAngles = 16;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = campPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = campPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = AeronsCampHelper.findTrueGroundSurface(world, testX, testZ);
                if (ground == null) continue;

                // Ship crater footprint (radius 5) must be completely dry land
                if (!AeronsCampHelper.isSafeDryLandFootprint(world, ground, 5, 4)) continue;

                // Lore: "There’s a crashed Skyforge ship on a high cliff nearby."
                // Heavily favor higher elevations
                int elevBonus = (ground.getY() - world.getSeaLevel()) * 8;
                if (ground.getY() >= 75) elevBonus += 40;
                if (ground.getY() >= 85) elevBonus += 80;
                if (ground.getY() >= 95) elevBonus += 120;

                int score = 100 + elevBonus;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                }
            }
        }

        if (bestPos != null) {
            return bestPos;
        }

        // Fallback: wider radii or relaxed elevation
        int[] widerRadii = new int[]{130, 150, 360, 400};
        for (int r : widerRadii) {
            for (int i = 0; i < 16; i++) {
                double angle = (2.0 * Math.PI * i) / 16;
                int testX = campPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = campPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = AeronsCampHelper.findTrueGroundSurface(world, testX, testZ);
                if (ground != null && AeronsCampHelper.isSafeDryLandFootprint(world, ground, 4, 5)) {
                    return ground;
                }
            }
        }

        // Local dry fallback
        for (int dx = 140; dx <= 260; dx += 16) {
            for (int dz = 120; dz <= 240; dz += 16) {
                BlockPos ground = AeronsCampHelper.findTrueGroundSurface(world, campPos.getX() + dx, campPos.getZ() + dz);
                if (ground != null) {
                    return ground;
                }
            }
        }

        return campPos.add(180, 15, 140);
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

                    // Fill beneath impact floor with stone to prevent any hollow voids
                    for (int fillY = -3; fillY <= -1; fillY++) {
                        BlockPos underPos = center.add(dx, fillY, dz);
                        if (world.getBlockState(underPos).isAir() || AeronsCampHelper.isLiquidOrWater(world, underPos)) {
                            world.setBlockState(underPos, Blocks.STONE.getDefaultState());
                        }
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
        world.setBlockState(center.add(3, 2, 1), Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        world.setBlockState(center.add(0, 1, -2), Blocks.IRON_BARS.getDefaultState());
        world.setBlockState(center.add(0, 2, -2), Blocks.CHAIN.getDefaultState());

        // Engine compartment machinery
        world.setBlockState(center.add(-1, 1, 0), Blocks.BLAST_FURNACE.getDefaultState());
        world.setBlockState(center.add(-1, 2, 0), Blocks.STONE_BRICK_WALL.getDefaultState());
        world.setBlockState(center.add(-1, 3, 0), Blocks.LIGHTNING_ROD.getDefaultState());
        world.setBlockState(center.add(-2, 1, 0), Blocks.COPPER_BLOCK.getDefaultState());

        // 3. Sturdy ladder climbing shaft on the north crater rim from floor up to surface
        int ladderHeight = Math.max(6, surfaceY - center.getY() + 1);
        for (int y = 1; y <= ladderHeight; y++) {
            world.setBlockState(center.add(0, y, -5), Blocks.STONE_BRICKS.getDefaultState());
            world.setBlockState(center.add(0, y, -4), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
        }

        // Populate the 3 chests
        populateAirshipChests(world, center);
    }

    public static void populateAirshipChests(ServerWorld world, BlockPos center) {
        // Chest 1: Engine Compartment (4 Pneumatic Pistons + Blueprint: Pneumatic Piston + Blueprint: Element Core + Iron/Redstone)
        BlockPos engineChestPos = center.add(-1, 1, 1);
        world.setBlockState(engineChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(engineChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.engine_compartment"));
            chest.setStack(12, BlueprintMaps.createBlueprintMap(world, engineChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, engineChestPos, BlueprintMaps.Type.ELEMENT_CORE));
            chest.markDirty();
        }

        // Chest 2: Flotation Chamber (2 Aerogel Cores + Blueprint: Aerogel Core + Feathers/Glass)
        BlockPos flotationChestPos = center.add(1, 1, 0);
        world.setBlockState(flotationChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(flotationChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.flotation_chamber"));
            chest.setStack(11, new ItemStack(ModItems.AEROGEL_CORE, 2));
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, flotationChestPos, BlueprintMaps.Type.AEROGEL_CORE));
            chest.markDirty();
        }

        // Chest 3: Cockpit Flight Deck Archive (Blueprint: Elemental Jetpack)
        BlockPos cockpitChestPos = center.add(0, 1, -1);
        world.setBlockState(cockpitChestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(cockpitChestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.cockpit_archive"));
            chest.setStack(13, BlueprintMaps.createBlueprintMap(world, cockpitChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
            chest.markDirty();
        }
    }
}
