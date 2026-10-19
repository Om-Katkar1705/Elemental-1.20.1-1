package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.config.ElementalConfig;
import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.entity.ModEntities;
import com.elemental.quest.QuestSaveData;
import com.elemental.item.BlueprintMaps;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;
import java.util.Optional;

/**
 * Clean NBT-based Structure Manager.
 * Loads and places structures exclusively from .nbt StructureTemplates:
 *  - elemental:aerons_camp
 *  - elemental:crashed_skyforge_ship
 *  - elemental:desert_sanctuary
 */
public class NbtStructureManager {

    public static final Identifier AERONS_CAMP_ID = new Identifier(Elemental.MOD_ID, "aerons_camp");
    public static final Identifier SKYFORGE_SHIP_ID = new Identifier(Elemental.MOD_ID, "crashed_skyforge_ship");
    public static final Identifier DESERT_SANCTUARY_ID = new Identifier(Elemental.MOD_ID, "desert_sanctuary");
    public static final Identifier FINAL_ARENA_ID = new Identifier(Elemental.MOD_ID, "final_arena");

    /**
     * Generic loader and placer for NBT structure templates.
     */
    public static boolean placeNbtStructure(ServerWorld world, Identifier structureId, BlockPos placePos) {
        StructureTemplateManager templateManager = world.getStructureTemplateManager();
        Optional<StructureTemplate> templateOpt = templateManager.getTemplate(structureId);
        if (templateOpt.isEmpty()) {
            Elemental.LOGGER.error("Failed to load NBT structure template: {}", structureId);
            return false;
        }

        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setIgnoreEntities(false);

        StructureTemplate template = templateOpt.get();
        template.place(world, placePos, placePos, placementData, world.getRandom(), Block.NOTIFY_LISTENERS);
        Elemental.LOGGER.info("Placed NBT structure '{}' at {}", structureId, placePos);
        return true;
    }

    // ==========================================
    // 1. Aeron's Camp Initialization
    // ==========================================

    public static void initializeWorld(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        if (saveData.isTentGenerated() && saveData.getAeronsTentLocation() != null) {
            ensureGuideAtCamp(world, saveData.getAeronsTentLocation(), saveData);
            return;
        }

        BlockPos worldSpawn = world.getSpawnPos();
        BlockPos safeCampPos = findSafeSurface(world, worldSpawn);

        // Camp template offset (center at safeCampPos)
        BlockPos placePos = safeCampPos.add(-4, -1, -4);

        // Prepare clean ground foundation and clear air
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int fillY = -4; fillY <= -2; fillY++) {
                    BlockPos underPos = safeCampPos.add(dx, fillY, dz);
                    if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                        world.setBlockState(underPos, Blocks.DIRT.getDefaultState());
                    }
                }
                for (int dy = 0; dy <= 6; dy++) {
                    world.setBlockState(safeCampPos.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }

        // Place directly from aerons_camp.nbt
        placeNbtStructure(world, AERONS_CAMP_ID, placePos);

        if (ElementalConfig.get().enableIntroNpc) {
            spawnAeron(world, safeCampPos);
            saveData.setGuideSpawned(true);
        }

        saveData.setAeronsTentLocation(safeCampPos);
        saveData.setTentGenerated(true);
        saveData.markDirty();
        Elemental.LOGGER.info("Aeron's Camp initialized from NBT at safe dry land: {}", safeCampPos);
    }

    public static void ensureGuideAtCamp(ServerWorld world, BlockPos campPos, QuestSaveData saveData) {
        if (!ElementalConfig.get().enableIntroNpc) return;
        List<ChronicleGuideEntity> existingGuides = world.getEntitiesByClass(
                ChronicleGuideEntity.class,
                new net.minecraft.util.math.Box(campPos).expand(32.0),
                e -> true
        );
        if (existingGuides.isEmpty()) {
            spawnAeron(world, campPos);
            saveData.setGuideSpawned(true);
            saveData.markDirty();
        }
    }

    private static void spawnAeron(ServerWorld world, BlockPos safeCampPos) {
        BlockPos npcPos = safeCampPos.add(1, 0, 1);
        ChronicleGuideEntity guide = ModEntities.CHRONICLE_GUIDE.create(world);
        if (guide != null) {
            guide.refreshPositionAndAngles(npcPos.getX() + 0.5, npcPos.getY(), npcPos.getZ() + 0.5, 0.0f, 0.0f);
            guide.setHomePos(safeCampPos);
            guide.setPersistent();
            world.spawnEntity(guide);
        }
    }

    // ==========================================
    // 2. Crashed Skyforge Ship (Airship)
    // ==========================================

    public static BlockPos findOrGenerateAirship(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSkyforgeAirshipLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos bestSurfacePos = findSafeCliffSite(world, nearPos);
        int surfaceY = bestSurfacePos.getY();
        int crashY = surfaceY - 6;
        BlockPos crashPos = new BlockPos(bestSurfacePos.getX(), crashY, bestSurfacePos.getZ());
        BlockPos placePos = crashPos.add(-6, 0, -6);

        // Prepare crater foundation
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 25) {
                    for (int fillY = -3; fillY <= -1; fillY++) {
                        BlockPos underPos = crashPos.add(dx, fillY, dz);
                        if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
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

        // Place directly from crashed_skyforge_ship.nbt
        placeNbtStructure(world, SKYFORGE_SHIP_ID, placePos);

        // Populate chest with initialized blueprint maps
        populateAirshipChests(world, placePos);

        saveData.setSkyforgeAirshipLocation(crashPos);
        saveData.markDirty();
        Elemental.LOGGER.info("Crashed Skyforge Ship initialized from NBT at cliff: {}", crashPos);
        return crashPos;
    }

    public static void populateAirshipChests(ServerWorld world, BlockPos placePos) {
        BlockPos mainChestPos = placePos.add(5, 1, 7);
        if (world.getBlockEntity(mainChestPos) instanceof ChestBlockEntity chest) {
            chest.clear();
            chest.setStack(10, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
            chest.setStack(12, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.AEROGEL_CORE));
            chest.setStack(14, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENT_CORE));
            chest.setStack(16, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
            chest.markDirty();
        }
        for (BlockPos otherPos : new BlockPos[]{placePos.add(7, 1, 6), placePos.add(6, 1, 5)}) {
            if (world.getBlockEntity(otherPos) instanceof ChestBlockEntity otherChest) {
                otherChest.clear();
                otherChest.markDirty();
            }
        }
    }

    public static void ensureAirshipChestsPopulated(ServerWorld world, BlockPos crashPos) {
        BlockPos placePos = crashPos.add(-6, 0, -6);
        BlockPos mainChestPos = placePos.add(5, 1, 7);
        ChestBlockEntity targetChest = null;
        if (world.getBlockEntity(mainChestPos) instanceof ChestBlockEntity chest) {
            targetChest = chest;
        } else {
            // Search nearby area in case ship was placed with slightly different origin
            for (BlockPos p : BlockPos.iterate(crashPos.add(-8, -4, -8), crashPos.add(8, 6, 8))) {
                if (world.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                    targetChest = chest;
                    mainChestPos = p;
                    break;
                }
            }
        }

        if (targetChest != null) {
            boolean hasAll = true;
            for (BlueprintMaps.Type t : BlueprintMaps.Type.values()) {
                boolean found = false;
                for (int i = 0; i < targetChest.size(); i++) {
                    if (BlueprintMaps.isBlueprint(targetChest.getStack(i), t)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    hasAll = false;
                    break;
                }
            }
            if (!hasAll) {
                targetChest.clear();
                targetChest.setStack(10, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
                targetChest.setStack(12, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.AEROGEL_CORE));
                targetChest.setStack(14, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENT_CORE));
                targetChest.setStack(16, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
                targetChest.markDirty();
            }
        }
    }

    // ==========================================
    // 3. Desert Sanctuary
    // ==========================================

    public static BlockPos findOrGenerateDesertSanctuary(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getDesertLocation();
        if (existing != null) {
            return existing;
        }

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

        BlockPos bestSurface = findSafeDesertSite(world, desertCenter);
        BlockPos placePos = bestSurface.add(-8, 0, -8);

        // Foundation & clearing
        int radius = 8;
        int surfaceY = bestSurface.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos colPos = new BlockPos(bestSurface.getX() + dx, surfaceY, bestSurface.getZ() + dz);
                for (int fillY = surfaceY - 4; fillY < surfaceY; fillY++) {
                    BlockPos underPos = new BlockPos(colPos.getX(), fillY, colPos.getZ());
                    if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                        world.setBlockState(underPos, Blocks.SANDSTONE.getDefaultState());
                    }
                }
                for (int airY = surfaceY; airY <= surfaceY + 12; airY++) {
                    BlockPos airPos = new BlockPos(colPos.getX(), airY, colPos.getZ());
                    world.setBlockState(airPos, Blocks.AIR.getDefaultState());
                }
            }
        }

        // Place directly from desert_sanctuary.nbt
        placeNbtStructure(world, DESERT_SANCTUARY_ID, placePos);

        saveData.setDesertLocation(bestSurface);
        saveData.markDirty();
        Elemental.LOGGER.info("Desert Sanctuary initialized from NBT in desert: {}", bestSurface);
        return bestSurface;
    }

    // ==========================================
    // 4. Safe Dry Surface & Terrain Search Helpers
    // ==========================================

    public static BlockPos findSafeSurface(ServerWorld world, BlockPos origin) {
        int[] radii = new int[]{90, 110, 130, 70, 150};
        int numAngles = 16;
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int x = origin.getX() + (int) Math.round(r * Math.cos(angle));
                int z = origin.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, x, z);
                if (ground == null) continue;
                if (!isSafeDryLandFootprint(world, ground, 5, 2)) continue;

                int score = 1000 - Math.abs(r - 100) * 5;
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                }
            }
            if (bestPos != null) return bestPos;
        }

        if (bestPos != null) return bestPos;

        BlockPos fallback = findTrueGroundSurface(world, origin.getX() + 90, origin.getZ() + 90);
        return fallback != null ? fallback : origin.add(90, 0, 90);
    }

    public static BlockPos findSafeCliffSite(ServerWorld world, BlockPos campPos) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;
        int[] radii = new int[]{160, 200, 240, 280, 320};
        int numAngles = 16;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = campPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = campPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground == null) continue;
                if (!isSafeDryLandFootprint(world, ground, 5, 4)) continue;

                int elevBonus = (ground.getY() - world.getSeaLevel()) * 8;
                int score = elevBonus - Math.abs(r - 200);
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                }
            }
        }

        if (bestPos != null) return bestPos;

        for (int r = 140; r <= 300; r += 20) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 30) {
                double rad = Math.toRadians(angleDeg);
                int testX = campPos.getX() + (int) (r * Math.cos(rad));
                int testZ = campPos.getZ() + (int) (r * Math.sin(rad));
                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground != null && isSafeDryLandFootprint(world, ground, 4, 5)) {
                    return ground;
                }
            }
        }

        BlockPos fallback = findTrueGroundSurface(world, campPos.getX() + 180, campPos.getZ() + 140);
        return fallback != null ? fallback : campPos.add(180, 15, 140);
    }

    public static BlockPos findSafeDesertSite(ServerWorld world, BlockPos center) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;
        int[] radii = new int[]{0, 32, 64, 96, 128, 160, 200};
        int numAngles = 12;

        for (int r : radii) {
            if (r == 0) {
                BlockPos ground = findTrueGroundSurface(world, center.getX(), center.getZ());
                if (ground != null && isSafeDryLandFootprint(world, ground, 8, 3)) {
                    return ground;
                }
                continue;
            }
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = center.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = center.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground == null) continue;
                if (!isSafeDryLandFootprint(world, ground, 8, 3)) continue;

                var biome = world.getBiome(ground);
                boolean isDesertBiome = biome.matchesKey(BiomeKeys.DESERT) || biome.isIn(BiomeTags.IS_BADLANDS);
                int score = (isDesertBiome ? 500 : 0) - r;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                }
            }
            if (bestPos != null && bestScore > 0) {
                return bestPos;
            }
        }

        if (bestPos != null) return bestPos;

        for (int r = 16; r <= 256; r += 32) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 45) {
                double rad = Math.toRadians(angleDeg);
                int testX = center.getX() + (int) (r * Math.cos(rad));
                int testZ = center.getZ() + (int) (r * Math.sin(rad));
                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground != null && isSafeDryLandFootprint(world, ground, 7, 4)) {
                    return ground;
                }
            }
        }

        BlockPos fallback = findTrueGroundSurface(world, center.getX(), center.getZ());
        return fallback != null ? fallback : center;
    }

    public static boolean isLiquidOrWater(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isLiquid()) return true;
        if (!world.getFluidState(pos).isEmpty()) return true;
        return state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS)
                || state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.BUBBLE_COLUMN) || state.isOf(Blocks.ICE)
                || state.isOf(Blocks.FROSTED_ICE);
    }

    public static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        try {
            var biome = world.getBiome(pos);
            return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isFoliageOrTree(BlockState state) {
        if (state.isAir()) return true;
        if (state.isIn(BlockTags.LEAVES)) return true;
        if (state.isIn(BlockTags.LOGS)) return true;
        if (state.isIn(BlockTags.FLOWERS)) return true;
        if (state.isIn(BlockTags.SAPLINGS)) return true;
        if (state.isIn(BlockTags.WART_BLOCKS)) return true;
        if (state.isOf(Blocks.MUSHROOM_STEM) || state.isOf(Blocks.RED_MUSHROOM_BLOCK) || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK)) return true;
        if (state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN) || state.isOf(Blocks.HANGING_ROOTS) || state.isOf(Blocks.MANGROVE_ROOTS)) return true;
        if (state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH)) return true;
        if (state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING) || state.isOf(Blocks.SUGAR_CANE) || state.isOf(Blocks.CACTUS)) return true;
        if (state.isOf(Blocks.SNOW)) return true;
        return false;
    }

    public static BlockPos findTrueGroundSurface(ServerWorld world, int x, int z) {
        world.getChunk(x >> 4, z >> 4);
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (topY <= world.getBottomY() + 5 || topY > 250) {
            topY = 80;
        }

        int seaLevel = world.getSeaLevel();
        if (isLiquidOrWater(world, new BlockPos(x, topY, z)) ||
            isLiquidOrWater(world, new BlockPos(x, Math.max(topY - 1, seaLevel), z)) ||
            isLiquidOrWater(world, new BlockPos(x, seaLevel, z))) {
            return null;
        }

        BlockPos.Mutable checkPos = new BlockPos.Mutable(x, topY, z);
        while (checkPos.getY() > world.getBottomY() + 5) {
            if (isLiquidOrWater(world, checkPos) || isLiquidOrWater(world, checkPos.down())) {
                return null;
            }

            BlockState belowState = world.getBlockState(checkPos.down());
            if (!isFoliageOrTree(belowState)) {
                if (checkPos.getY() < seaLevel) {
                    return null;
                }
                if (isWaterBiome(world, checkPos)) {
                    return null;
                }
                return checkPos.toImmutable();
            }
            checkPos.move(Direction.DOWN);
        }
        return null;
    }

    public static boolean isSafeDryLandFootprint(ServerWorld world, BlockPos center, int radius, int maxSlope) {
        if (center == null) return false;
        int centerY = center.getY();
        int seaLevel = world.getSeaLevel();

        if (centerY < seaLevel) return false;
        if (isWaterBiome(world, center)) return false;

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                int testX = center.getX() + dx;
                int testZ = center.getZ() + dz;

                world.getChunk(testX >> 4, testZ >> 4);
                int testY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, testX, testZ);
                if (Math.abs(testY - centerY) > maxSlope + 3) {
                    return false;
                }

                BlockPos samplePos = new BlockPos(testX, testY, testZ);
                if (isLiquidOrWater(world, samplePos) ||
                    isLiquidOrWater(world, samplePos.down()) ||
                    isLiquidOrWater(world, new BlockPos(testX, seaLevel, testZ))) {
                    return false;
                }
                if (isWaterBiome(world, samplePos)) {
                    return false;
                }
            }
        }
        return true;
    }
}
