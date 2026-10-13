package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.quest.QuestSaveData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Optional;

public class DesertSanctuaryGenerator {

    /**
     * Resolves or generates the Ancient Desert Sanctuary domed structure in the desert.
     * Uses the NBT structure template with full programmatic fallback.
     */
    public static BlockPos findOrGenerateDesertSanctuary(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getDesertLocation();
        if (existing != null) {
            return existing;
        }

        // 1. Locate Desert Biome center
        BlockPos desertCenter = null;
        try {
            var pair = world.locateBiome(
                    entry -> entry.matchesKey(BiomeKeys.DESERT),
                    nearPos,
                    6400,
                    32,
                    64
            );
            if (pair != null) {
                desertCenter = pair.getFirst();
            } else {
                var badlandsPair = world.locateBiome(
                        entry -> entry.isIn(BiomeTags.IS_BADLANDS),
                        nearPos,
                        6400,
                        32,
                        64
                );
                if (badlandsPair != null) {
                    desertCenter = badlandsPair.getFirst();
                }
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to locate desert biome: ", e);
        }

        if (desertCenter == null) {
            desertCenter = nearPos.add(1200, 0, 950);
        }

        // 2. Search for a flat, safe dry desert site around desertCenter
        BlockPos bestSurface = findSafeDesertSite(world, desertCenter);
        BlockPos placePos = bestSurface.add(-8, 0, -8);

        // 3. Clear air above and ensure solid foundation below
        prepareFoundationAndClearing(world, bestSurface);

        // 4. Place from NBT Structure Template
        StructureTemplateManager templateManager = world.getStructureTemplateManager();
        Optional<StructureTemplate> templateOpt = templateManager.getTemplate(new Identifier(Elemental.MOD_ID, "desert_sanctuary"));
        if (templateOpt.isPresent()) {
            StructurePlacementData placementData = new StructurePlacementData()
                    .setMirror(BlockMirror.NONE)
                    .setRotation(BlockRotation.NONE)
                    .setIgnoreEntities(false);
            templateOpt.get().place(world, placePos, placePos, placementData, world.getRandom(), Block.NOTIFY_LISTENERS);
            Elemental.LOGGER.info("Placed Desert Sanctuary from NBT template at {}", placePos);
        } else {
            Elemental.LOGGER.warn("NBT template desert_sanctuary not found in template manager, building directly");
            buildDesertSanctuaryDirect(world, bestSurface);
        }

        // 5. Populate Central Altar Chest at center (bestSurface.add(0, 2, 0))
        populateAltarChest(world, bestSurface.add(0, 2, 0));

        saveData.setDesertLocation(bestSurface);
        saveData.markDirty();
        Elemental.LOGGER.info("Generated Desert Sanctuary at: {}", bestSurface);

        return bestSurface;
    }

    public static BlockPos findSafeDesertSite(ServerWorld world, BlockPos center) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;

        int[] radii = new int[]{0, 24, 48, 72, 96, 128, 160, 200, 250};
        int numAngles = 16;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = center.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = center.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = AeronsCampHelper.findTrueGroundSurface(world, testX, testZ);
                if (ground == null) continue;

                // Footprint (radius 8 for the 17x19 dome structure)
                if (!AeronsCampHelper.isSafeDryLandFootprint(world, ground, 8, 3)) continue;

                BlockState groundState = world.getBlockState(ground.down());
                int score = 100;

                // Prefer desert sands and sandstone
                if (groundState.isIn(BlockTags.SAND) || groundState.isOf(Blocks.SAND) || groundState.isOf(Blocks.RED_SAND)) {
                    score += 50;
                } else if (groundState.isOf(Blocks.SANDSTONE) || groundState.isOf(Blocks.RED_SANDSTONE)) {
                    score += 40;
                } else if (groundState.isIn(BlockTags.TERRACOTTA)) {
                    score += 30;
                }

                // Prefer elevation between 64 and 85
                if (ground.getY() >= 64 && ground.getY() <= 85) {
                    score += 30;
                } else if (ground.getY() > 85) {
                    score -= (ground.getY() - 85);
                }

                score -= Math.abs(r - 48) / 4;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                    if (bestScore >= 160) {
                        return bestPos;
                    }
                }
            }
        }

        if (bestPos != null) {
            return bestPos;
        }

        // Fallback: wider radii
        int[] widerRadii = new int[]{280, 320, 380, 450};
        for (int r : widerRadii) {
            for (int i = 0; i < 16; i++) {
                double angle = (2.0 * Math.PI * i) / 16;
                int testX = center.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = center.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = AeronsCampHelper.findTrueGroundSurface(world, testX, testZ);
                if (ground != null && AeronsCampHelper.isSafeDryLandFootprint(world, ground, 7, 4)) {
                    return ground;
                }
            }
        }

        BlockPos fallback = AeronsCampHelper.findTrueGroundSurface(world, center.getX(), center.getZ());
        return fallback != null ? fallback : center.withY(65);
    }

    private static void prepareFoundationAndClearing(ServerWorld world, BlockPos center) {
        // Clear air above dome up to Y+12
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 11; dz++) {
                for (int dy = 1; dy <= 12; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
                // Fill down beneath foundation with sandstone to prevent holes
                for (int fillY = -4; fillY <= 0; fillY++) {
                    BlockPos underPos = center.add(dx, fillY, dz);
                    if (world.getBlockState(underPos).isAir() || AeronsCampHelper.isLiquidOrWater(world, underPos)) {
                        world.setBlockState(underPos, Blocks.SANDSTONE.getDefaultState());
                    }
                }
            }
        }
    }

    public static void populateAltarChest(ServerWorld world, BlockPos chestPos) {
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.desert_sanctuary_chest"));
            chest.setStack(10, new ItemStack(Items.GOLD_INGOT, 8));
            chest.setStack(11, new ItemStack(Items.BLAZE_POWDER, 4));
            chest.setStack(13, new ItemStack(Items.GOLDEN_APPLE, 1));
            chest.setStack(14, new ItemStack(Items.LAPIS_LAZULI, 12));
            chest.setStack(15, new ItemStack(Items.IRON_INGOT, 6));
            chest.setStack(16, new ItemStack(Items.BOOK, 1));
            chest.markDirty();
        }
    }

    /**
     * Programmatic block builder fallback that constructs the exact same desert dome structure
     * shown in the reference image if the NBT template is ever unavailable.
     */
    public static void buildDesertSanctuaryDirect(ServerWorld world, BlockPos center) {
        // 1. Foundation & Floor (Y=0)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 10; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 56) {
                    if (distSq <= 9) {
                        world.setBlockState(center.add(dx, 0, dz), Blocks.CUT_SANDSTONE.getDefaultState());
                    } else if (distSq <= 36) {
                        world.setBlockState(center.add(dx, 0, dz), Blocks.SMOOTH_SANDSTONE.getDefaultState());
                    } else {
                        world.setBlockState(center.add(dx, 0, dz), Blocks.SANDSTONE.getDefaultState());
                    }
                } else if (Math.abs(dx) <= 3 && dz >= 6 && dz <= 10) {
                    world.setBlockState(center.add(dx, 0, dz), Blocks.SPRUCE_PLANKS.getDefaultState());
                }
            }
        }

        // 2. Stepped Dome Walls
        // Tier 1: Y=1..3, R=7
        for (int y = 1; y <= 3; y++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq >= 34 && distSq <= 52) {
                        world.setBlockState(center.add(dx, y, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    }
                }
            }
        }

        // Tier 2: Y=4..5, R=6
        for (int y = 4; y <= 5; y++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq >= 23 && distSq <= 38) {
                        world.setBlockState(center.add(dx, y, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    }
                }
            }
        }

        // Tier 3: Y=6..7, R=5
        for (int y = 6; y <= 7; y++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq >= 14 && distSq <= 27) {
                        world.setBlockState(center.add(dx, y, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    }
                }
            }
        }

        // Tier 4: Y=8..9, R=4
        for (int y = 8; y <= 9; y++) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq >= 8 && distSq <= 17) {
                        world.setBlockState(center.add(dx, y, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
                    }
                }
            }
        }

        // Tier 5: Y=10 crown flat roof
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    world.setBlockState(center.add(dx, 10, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
                }
            }
        }

        // 3. Entrance Portal & Pergola (South Face, dz = 6..10)
        // Clear doorway at dx = -1, 0, dz = 6
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(center.add(-1, y, 6), Blocks.AIR.getDefaultState());
            world.setBlockState(center.add(0, y, 6), Blocks.AIR.getDefaultState());
        }

        // Door arch
        world.setBlockState(center.add(-2, 1, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(-2, 2, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(-2, 3, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(1, 1, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(1, 2, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(1, 3, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(-1, 3, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());
        world.setBlockState(center.add(0, 3, 6), Blocks.SMOOTH_QUARTZ.getDefaultState());

        // Double spruce doors
        world.setBlockState(center.add(-1, 1, 6), Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .with(DoorBlock.HINGE, DoorHinge.LEFT));
        world.setBlockState(center.add(-1, 2, 6), Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .with(DoorBlock.HINGE, DoorHinge.LEFT));
        world.setBlockState(center.add(0, 1, 6), Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .with(DoorBlock.HINGE, DoorHinge.RIGHT));
        world.setBlockState(center.add(0, 2, 6), Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH)
                .with(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .with(DoorBlock.HINGE, DoorHinge.RIGHT));

        // Pergola pillars at dx = -3, 2, dz = 8
        world.setBlockState(center.add(-3, 1, 8), Blocks.COBBLESTONE_WALL.getDefaultState());
        world.setBlockState(center.add(-3, 2, 8), Blocks.SPRUCE_FENCE.getDefaultState());
        world.setBlockState(center.add(-3, 3, 8), Blocks.SPRUCE_FENCE.getDefaultState());

        world.setBlockState(center.add(2, 1, 8), Blocks.COBBLESTONE_WALL.getDefaultState());
        world.setBlockState(center.add(2, 2, 8), Blocks.SPRUCE_FENCE.getDefaultState());
        world.setBlockState(center.add(2, 3, 8), Blocks.SPRUCE_FENCE.getDefaultState());

        // Pergola roof crossbeams (Y=4)
        for (int dx = -3; dx <= 2; dx++) {
            world.setBlockState(center.add(dx, 4, 8), Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
            world.setBlockState(center.add(dx, 4, 7), Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, net.minecraft.block.enums.BlockHalf.TOP));
            world.setBlockState(center.add(dx, 4, 9), Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, net.minecraft.block.enums.BlockHalf.TOP));
        }

        // Foliage & Berry bushes
        world.setBlockState(center.add(-5, 1, 7), Blocks.OAK_LEAVES.getDefaultState());
        world.setBlockState(center.add(-4, 1, 7), Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState());
        world.setBlockState(center.add(-4, 1, 8), Blocks.OAK_LEAVES.getDefaultState());
        world.setBlockState(center.add(3, 1, 7), Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState());
        world.setBlockState(center.add(3, 1, 8), Blocks.SWEET_BERRY_BUSH.getDefaultState().with(SweetBerryBushBlock.AGE, 3));
        world.setBlockState(center.add(4, 1, 7), Blocks.OAK_LEAVES.getDefaultState());

        // Lamp post on the left
        world.setBlockState(center.add(-6, 1, 8), Blocks.COBBLESTONE_WALL.getDefaultState());
        world.setBlockState(center.add(-6, 2, 8), Blocks.SPRUCE_FENCE.getDefaultState());
        world.setBlockState(center.add(-6, 3, 8), Blocks.LANTERN.getDefaultState());

        // Interior Central Altar
        world.setBlockState(center.add(0, 1, 0), Blocks.CHISELED_SANDSTONE.getDefaultState());
        populateAltarChest(world, center.add(0, 2, 0));

        // Carpets
        world.setBlockState(center.add(-1, 1, 0), Blocks.ORANGE_CARPET.getDefaultState());
        world.setBlockState(center.add(1, 1, 0), Blocks.ORANGE_CARPET.getDefaultState());
        world.setBlockState(center.add(0, 1, -1), Blocks.ORANGE_CARPET.getDefaultState());
        world.setBlockState(center.add(0, 1, 1), Blocks.ORANGE_CARPET.getDefaultState());
        world.setBlockState(center.add(-1, 1, -1), Blocks.RED_CARPET.getDefaultState());
        world.setBlockState(center.add(1, 1, 1), Blocks.RED_CARPET.getDefaultState());

        // Hanging lantern
        world.setBlockState(center.add(0, 9, 0), Blocks.CHAIN.getDefaultState());
        world.setBlockState(center.add(0, 8, 0), Blocks.CHAIN.getDefaultState());
        world.setBlockState(center.add(0, 7, 0), Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));

        // Amenities
        world.setBlockState(center.add(-5, 1, -1), Blocks.CRAFTING_TABLE.getDefaultState());
        world.setBlockState(center.add(-5, 1, 0), Blocks.BLAST_FURNACE.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.EAST));
        world.setBlockState(center.add(-5, 1, 1), Blocks.CHISELED_BOOKSHELF.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.EAST));

        world.setBlockState(center.add(5, 1, -1), Blocks.SMOOTH_SANDSTONE_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.WEST));
        world.setBlockState(center.add(5, 1, 0), Blocks.SMOOTH_SANDSTONE_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.WEST));
        world.setBlockState(center.add(5, 1, 1), Blocks.SMOOTH_SANDSTONE_STAIRS.getDefaultState().with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, Direction.WEST));
        world.setBlockState(center.add(5, 2, -1), Blocks.POTTED_CACTUS.getDefaultState());
    }
}
