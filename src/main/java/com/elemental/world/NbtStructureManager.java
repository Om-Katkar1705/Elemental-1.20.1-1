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
                .setIgnoreEntities(structureId.equals(AERONS_CAMP_ID));

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

        saveData.setAeronsTentLocation(safeCampPos);
        saveData.setTentGenerated(true);
        saveData.markDirty();

        ensureGuideAtCamp(world, safeCampPos, saveData);
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
        } else if (existingGuides.size() > 1) {
            // Keep exactly one guide entity, remove any extra duplicate guides
            for (int i = 1; i < existingGuides.size(); i++) {
                existingGuides.get(i).discard();
            }
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
    // 2. Crashed Skyforge Ship (Airship) - Biome-Adaptive
    // ==========================================

    public enum AirshipVariant {
        PLAINS_FOREST("crashed_skyforge_ship_plains", Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE),
        DESERT("crashed_skyforge_ship_desert", Blocks.SAND, Blocks.SANDSTONE, Blocks.STONE),
        BADLANDS_RED_SAND("crashed_skyforge_ship_badlands", Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.STONE),
        BADLANDS_TERRACOTTA("crashed_skyforge_ship_badlands_terracotta", Blocks.TERRACOTTA, Blocks.TERRACOTTA, Blocks.STONE),
        OLD_GROWTH_TAIGA("crashed_skyforge_ship_old_growth_taiga", Blocks.PODZOL, Blocks.DIRT, Blocks.STONE),
        WINDSWEPT_FOREST("crashed_skyforge_ship_windswept_forest", Blocks.COARSE_DIRT, Blocks.DIRT, Blocks.STONE),
        SNOWY_PLAINS("crashed_skyforge_ship_snowy_plains", Blocks.SNOW, Blocks.GRASS_BLOCK, Blocks.STONE),
        SNOWY_SLOPES("crashed_skyforge_ship_snowy_slopes", Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.STONE),
        GRAVELLY_HILLS("crashed_skyforge_ship_gravelly_hills", Blocks.GRAVEL, Blocks.STONE, Blocks.STONE),
        STONY_PEAKS("crashed_skyforge_ship_stony_peaks", Blocks.STONE, Blocks.STONE, Blocks.STONE),
        CALCITE("crashed_skyforge_ship_calcite", Blocks.CALCITE, Blocks.STONE, Blocks.STONE),
        SWAMP("crashed_skyforge_ship_swamp", Blocks.MUD, Blocks.STONE, Blocks.STONE),
        BEACH("crashed_skyforge_ship_beach", Blocks.SAND, Blocks.STONE, Blocks.STONE),
        FROZEN_OCEAN("crashed_skyforge_ship_frozen_ocean", Blocks.ICE, Blocks.STONE, Blocks.STONE);

        public final String structureName;
        public final Block topBlock;
        public final Block midBlock;
        public final Block baseBlock;

        AirshipVariant(String structureName, Block topBlock, Block midBlock, Block baseBlock) {
            this.structureName = structureName;
            this.topBlock = topBlock;
            this.midBlock = midBlock;
            this.baseBlock = baseBlock;
        }
    }

    public static AirshipVariant selectAirshipVariant(ServerWorld world, BlockPos center) {
        int grassCount = 0;
        int sandCount = 0;
        int sandstoneCount = 0;
        int redSandCount = 0;
        int redSandstoneCount = 0;
        int terracottaCount = 0;
        int podzolCount = 0;
        int coarseDirtCount = 0;
        int snowCount = 0;
        int snowBlockCount = 0;
        int gravelCount = 0;
        int stoneCount = 0;
        int calciteCount = 0;
        int mudCount = 0;
        int iceCount = 0;

        int radius = 16;
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos p = new BlockPos(x, topY - 1 - dy, z);
                    BlockState st = world.getBlockState(p);
                    Block b = st.getBlock();
                    if (b == Blocks.GRASS_BLOCK) grassCount++;
                    else if (b == Blocks.SAND) sandCount++;
                    else if (b == Blocks.SANDSTONE || b == Blocks.SMOOTH_SANDSTONE) sandstoneCount++;
                    else if (b == Blocks.RED_SAND) redSandCount++;
                    else if (b == Blocks.RED_SANDSTONE || b == Blocks.SMOOTH_RED_SANDSTONE) redSandstoneCount++;
                    else if (b == Blocks.TERRACOTTA || b.getTranslationKey().contains("terracotta")) terracottaCount++;
                    else if (b == Blocks.PODZOL) podzolCount++;
                    else if (b == Blocks.COARSE_DIRT) coarseDirtCount++;
                    else if (b == Blocks.SNOW || b == Blocks.POWDER_SNOW) snowCount++;
                    else if (b == Blocks.SNOW_BLOCK) snowBlockCount++;
                    else if (b == Blocks.GRAVEL) gravelCount++;
                    else if (b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE || b == Blocks.DEEPSLATE) stoneCount++;
                    else if (b == Blocks.CALCITE) calciteCount++;
                    else if (b == Blocks.MUD || b == Blocks.PACKED_MUD || b == Blocks.MUDDY_MANGROVE_ROOTS) mudCount++;
                    else if (b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE) iceCount++;
                }
            }
        }

        var biome = world.getBiome(center);

        if (biome.isIn(BiomeTags.IS_BADLANDS) || biome.matchesKey(BiomeKeys.BADLANDS) || biome.matchesKey(BiomeKeys.ERODED_BADLANDS) || biome.matchesKey(BiomeKeys.WOODED_BADLANDS)) {
            if (terracottaCount > redSandCount + redSandstoneCount) {
                return AirshipVariant.BADLANDS_TERRACOTTA;
            }
            return AirshipVariant.BADLANDS_RED_SAND;
        }

        if (biome.matchesKey(BiomeKeys.DESERT)) {
            return AirshipVariant.DESERT;
        }

        if (biome.isIn(BiomeTags.IS_BEACH) || biome.matchesKey(BiomeKeys.BEACH)) {
            if (sandstoneCount > 0) {
                return AirshipVariant.DESERT;
            }
            return AirshipVariant.BEACH;
        }

        if (biome.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA) || biome.matchesKey(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA)) {
            if (coarseDirtCount > podzolCount) {
                return AirshipVariant.WINDSWEPT_FOREST;
            }
            return AirshipVariant.OLD_GROWTH_TAIGA;
        }

        if (biome.matchesKey(BiomeKeys.WINDSWEPT_FOREST)) {
            return AirshipVariant.WINDSWEPT_FOREST;
        }

        if (biome.matchesKey(BiomeKeys.SNOWY_PLAINS) || biome.matchesKey(BiomeKeys.SNOWY_TAIGA)) {
            return AirshipVariant.SNOWY_PLAINS;
        }

        if (biome.matchesKey(BiomeKeys.SNOWY_SLOPES) || biome.matchesKey(BiomeKeys.JAGGED_PEAKS) || biome.matchesKey(BiomeKeys.FROZEN_PEAKS)) {
            return AirshipVariant.SNOWY_SLOPES;
        }

        if (biome.matchesKey(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS) || biome.matchesKey(BiomeKeys.STONY_SHORE)) {
            return AirshipVariant.GRAVELLY_HILLS;
        }

        if (biome.matchesKey(BiomeKeys.STONY_PEAKS)) {
            if (calciteCount > 0) {
                return AirshipVariant.CALCITE;
            }
            return AirshipVariant.STONY_PEAKS;
        }

        if (biome.matchesKey(BiomeKeys.WINDSWEPT_HILLS)) {
            return AirshipVariant.STONY_PEAKS;
        }

        if (biome.matchesKey(BiomeKeys.SWAMP) || biome.matchesKey(BiomeKeys.MANGROVE_SWAMP)) {
            return AirshipVariant.SWAMP;
        }

        if (biome.matchesKey(BiomeKeys.FROZEN_OCEAN) || biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN)) {
            return AirshipVariant.FROZEN_OCEAN;
        }

        // Surrounding blocks evaluation
        if (iceCount >= 4) return AirshipVariant.FROZEN_OCEAN;
        if (mudCount >= 4) return AirshipVariant.SWAMP;
        if (calciteCount >= 4) return AirshipVariant.CALCITE;
        if (snowBlockCount >= 5) return AirshipVariant.SNOWY_SLOPES;
        if (snowCount >= 5) return AirshipVariant.SNOWY_PLAINS;
        if (terracottaCount >= 5) return AirshipVariant.BADLANDS_TERRACOTTA;
        if (redSandCount >= 5 || redSandstoneCount >= 4) return AirshipVariant.BADLANDS_RED_SAND;
        if (sandCount >= 5) {
            return sandstoneCount > 0 ? AirshipVariant.DESERT : AirshipVariant.BEACH;
        }
        if (podzolCount >= 4) return AirshipVariant.OLD_GROWTH_TAIGA;
        if (coarseDirtCount >= 4) return AirshipVariant.WINDSWEPT_FOREST;
        if (gravelCount >= 5) return AirshipVariant.GRAVELLY_HILLS;
        if (stoneCount >= 10 && grassCount <= 2) return AirshipVariant.STONY_PEAKS;

        return AirshipVariant.PLAINS_FOREST;
    }

    public static void blendCraterPerimeter(ServerWorld world, BlockPos crashPos, AirshipVariant variant) {
        int centerX = crashPos.getX();
        int centerZ = crashPos.getZ();
        int rimY = crashPos.getY() + 6;

        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq >= 24 && distSq <= 40) {
                    BlockPos rimPos = new BlockPos(centerX + dx, rimY, centerZ + dz);
                    BlockState current = world.getBlockState(rimPos);
                    if (!current.isAir() && !isLiquidOrWater(world, rimPos)) {
                        BlockPos above = rimPos.up();
                        if (world.getBlockState(above).isAir()) {
                            world.setBlockState(rimPos, variant.topBlock.getDefaultState());
                            BlockPos below = rimPos.down();
                            if (!world.getBlockState(below).isAir()) {
                                world.setBlockState(below, variant.midBlock.getDefaultState());
                            }
                        }
                    }
                }
            }
        }
    }

    public static BlockPos findOrGenerateAirship(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSkyforgeAirshipLocation();
        BlockPos bestSurfacePos;
        BlockPos crashPos;
        BlockPos placePos;

        if (existing != null) {
            BlockPos testPos = existing.add(-6, 0, -6);
            boolean hasShip = false;
            for (BlockPos p : BlockPos.iterate(testPos, testPos.add(11, 4, 10))) {
                if (world.getBlockEntity(p) instanceof ChestBlockEntity || world.getBlockState(p).isOf(Blocks.BLAST_FURNACE) || world.getBlockState(p).isOf(Blocks.DARK_OAK_PLANKS)) {
                    hasShip = true;
                    break;
                }
            }
            if (hasShip) {
                return existing;
            }
            // Ship missing at existing saved location (e.g. prior load failure); respawn at existing location!
            crashPos = existing;
            bestSurfacePos = existing.add(0, 6, 0);
            placePos = testPos;
        } else {
            bestSurfacePos = findSafeCliffSite(world, nearPos);
            int surfaceY = bestSurfacePos.getY();
            int crashY = surfaceY - 6;
            crashPos = new BlockPos(bestSurfacePos.getX(), crashY, bestSurfacePos.getZ());
            placePos = crashPos.add(-6, 0, -6);
        }

        int surfaceY = bestSurfacePos.getY();

        // Check blocks and biome around generation area to choose matching variant
        AirshipVariant variant = selectAirshipVariant(world, bestSurfacePos);
        Elemental.LOGGER.info("Selected biome-adaptive variant '{}' for Crashed Skyforge Ship at {}", variant.structureName, crashPos);

        // Prepare crater foundation with matching base block
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 25) {
                    for (int fillY = -3; fillY <= -1; fillY++) {
                        BlockPos underPos = crashPos.add(dx, fillY, dz);
                        if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                            world.setBlockState(underPos, variant.baseBlock.getDefaultState());
                        }
                    }
                    int maxY = Math.max(surfaceY + 6, crashPos.getY() + 8);
                    for (int y = crashPos.getY() + 5; y <= maxY; y++) {
                        world.setBlockState(new BlockPos(crashPos.getX() + dx, y, crashPos.getZ() + dz), Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Place selected variant template (falls back to default if variant not found)
        Identifier variantId = new Identifier(Elemental.MOD_ID, variant.structureName);
        if (!placeNbtStructure(world, variantId, placePos)) {
            placeNbtStructure(world, SKYFORGE_SHIP_ID, placePos);
        }

        // Seamless crater rim landscape integration
        blendCraterPerimeter(world, crashPos, variant);

        // Populate chest with initialized blueprint maps & remove empty chests
        populateAirshipChests(world, placePos);

        saveData.setSkyforgeAirshipLocation(crashPos);
        saveData.markDirty();
        Elemental.LOGGER.info("Crashed Skyforge Ship initialized from NBT variant '{}' at cliff: {}", variant.structureName, crashPos);
        return crashPos;
    }

    public static void populateAirshipChests(ServerWorld world, BlockPos placePos) {
        ChestBlockEntity mainChest = null;
        BlockPos mainChestPos = null;

        // Search the ship's 12x9x11 bounds for all chests
        for (BlockPos p : BlockPos.iterate(placePos, placePos.add(11, 8, 10))) {
            if (world.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                if (mainChest == null) {
                    mainChest = chest;
                    mainChestPos = p.toImmutable();
                } else {
                    // Remove every additional/empty chest - keep exactly one chest
                    chest.clear();
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        if (mainChest != null && mainChestPos != null) {
            mainChest.clear();
            mainChest.setStack(10, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
            mainChest.setStack(12, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.AEROGEL_CORE));
            mainChest.setStack(14, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENT_CORE));
            mainChest.setStack(16, BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
            mainChest.markDirty();
        }
    }

    public static void ensureAirshipChestsPopulated(ServerWorld world, BlockPos crashPos) {
        BlockPos placePos = crashPos.add(-6, 0, -6);
        ChestBlockEntity targetChest = null;
        BlockPos targetChestPos = null;

        for (BlockPos p : BlockPos.iterate(placePos.add(-2, -2, -2), placePos.add(13, 10, 12))) {
            if (world.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                if (targetChest == null) {
                    targetChest = chest;
                    targetChestPos = p.toImmutable();
                } else {
                    // Remove any extra chests
                    chest.clear();
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        if (targetChest == null) {
            // Ship structure missing or unspawned (e.g. prior load failure); spawn it now!
            AirshipVariant variant = selectAirshipVariant(world, crashPos.add(0, 6, 0));
            Identifier variantId = new Identifier(Elemental.MOD_ID, variant.structureName);
            if (!placeNbtStructure(world, variantId, placePos)) {
                placeNbtStructure(world, SKYFORGE_SHIP_ID, placePos);
            }
            blendCraterPerimeter(world, crashPos, variant);
            populateAirshipChests(world, placePos);
            return;
        }

        if (targetChest != null && targetChestPos != null) {
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
                targetChest.setStack(10, BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
                targetChest.setStack(12, BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.AEROGEL_CORE));
                targetChest.setStack(14, BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.ELEMENT_CORE));
                targetChest.setStack(16, BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
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
