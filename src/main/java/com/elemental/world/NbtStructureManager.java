package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.config.ElementalConfig;
import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.entity.ElementalWizardEntity;
import com.elemental.entity.ModEntities;
import com.elemental.quest.QuestSaveData;
import com.elemental.item.BlueprintMaps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.structure.StructureKeys;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.particle.ParticleTypes;
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
 * - elemental:aerons_camp
 * - elemental:crashed_skyforge_ship
 * - elemental:seismic_golem_arena
 */
public class NbtStructureManager {

    public static final Identifier AERONS_CAMP_ID = new Identifier(Elemental.MOD_ID, "aerons_camp");
    public static final Identifier SKYFORGE_SHIP_ID = new Identifier(Elemental.MOD_ID, "crashed_skyforge_ship");
    public static final Identifier SEISMIC_GOLEM_ARENA_ID = new Identifier(Elemental.MOD_ID, "seismic_golem_arena");
    public static final Identifier EVERLIVING_ARENA_ID = new Identifier(Elemental.MOD_ID, "everliving_knight_arena");
    public static final Identifier LUSH_STRONGHOLD_TEMPLE_ID = new Identifier(Elemental.MOD_ID, "lush_stronghold_temple");

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

    public record CampGroundMaterial(Block surfaceBlock, Block subsurfaceBlock) {
    }

    public static CampGroundMaterial sampleSurroundingGroundMaterial(ServerWorld world, BlockPos center) {
        java.util.Map<Block, Integer> surfaceCounts = new java.util.HashMap<>();
        java.util.Map<Block, Integer> subCounts = new java.util.HashMap<>();

        int radius = 6;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.abs(dx) < 4 && Math.abs(dz) < 4)
                    continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos topPos = new BlockPos(x, topY - 1, z);
                BlockState topState = world.getBlockState(topPos);
                if (!topState.isAir() && !isLiquidOrWater(world, topPos)) {
                    surfaceCounts.put(topState.getBlock(), surfaceCounts.getOrDefault(topState.getBlock(), 0) + 1);
                }
                BlockPos subPos = topPos.down();
                BlockState subState = world.getBlockState(subPos);
                if (!subState.isAir() && !isLiquidOrWater(world, subPos)) {
                    subCounts.put(subState.getBlock(), subCounts.getOrDefault(subState.getBlock(), 0) + 1);
                }
            }
        }

        Block bestSurface = Blocks.GRASS_BLOCK;
        int maxSurf = 0;
        for (var entry : surfaceCounts.entrySet()) {
            if (entry.getValue() > maxSurf) {
                maxSurf = entry.getValue();
                bestSurface = entry.getKey();
            }
        }

        Block bestSub = Blocks.DIRT;
        int maxSub = 0;
        for (var entry : subCounts.entrySet()) {
            if (entry.getValue() > maxSub) {
                maxSub = entry.getValue();
                bestSub = entry.getKey();
            }
        }

        if (bestSurface == Blocks.SAND) {
            bestSub = Blocks.SANDSTONE;
        } else if (bestSurface == Blocks.RED_SAND) {
            bestSub = Blocks.RED_SANDSTONE;
        } else if (bestSurface == Blocks.STONE || bestSurface == Blocks.COBBLESTONE) {
            bestSub = Blocks.STONE;
        } else if (bestSurface == Blocks.SNOW_BLOCK || bestSurface == Blocks.POWDER_SNOW) {
            bestSub = Blocks.DIRT;
        }

        return new CampGroundMaterial(bestSurface, bestSub);
    }

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
        CampGroundMaterial groundMaterial = sampleSurroundingGroundMaterial(world, safeCampPos);
        int campY = safeCampPos.getY();

        // Level ground and lay foundation matching surrounding blocks
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                // Clear air above camp floor (dy from 0 to 6)
                for (int dy = 0; dy <= 6; dy++) {
                    world.setBlockState(new BlockPos(safeCampPos.getX() + dx, campY + dy, safeCampPos.getZ() + dz),
                            Blocks.AIR.getDefaultState());
                }

                // Ground level at safeCampPos.getY() - 1:
                BlockPos floorPos = new BlockPos(safeCampPos.getX() + dx, campY - 1, safeCampPos.getZ() + dz);
                world.setBlockState(floorPos, groundMaterial.surfaceBlock().getDefaultState());

                // Foundation below ground level (from -4 to -2 relative to campY):
                for (int fillY = -4; fillY <= -2; fillY++) {
                    BlockPos underPos = new BlockPos(safeCampPos.getX() + dx, campY + fillY, safeCampPos.getZ() + dz);
                    if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                        world.setBlockState(underPos, groundMaterial.subsurfaceBlock().getDefaultState());
                    }
                }
            }
        }

        // Place directly from aerons_camp.nbt
        placeNbtStructure(world, AERONS_CAMP_ID, placePos);

        saveData.setAeronsTentLocation(safeCampPos);
        saveData.setTentGenerated(true);
        saveData.setGuideSpawned(true);
        saveData.markDirty();

        spawnAeron(world, safeCampPos);
        Elemental.LOGGER.info("Aeron's Camp initialized from NBT at safe dry land: {}", safeCampPos);
    }

    private static long lastGuideCampCheckTick = 0;

    public static void ensureGuideAtCamp(ServerWorld world, BlockPos campPos, QuestSaveData saveData) {
        if (!ElementalConfig.get().enableIntroNpc)
            return;
        if (campPos == null)
            return;

        // If chunk is not loaded, do not check or spawn entities to prevent ghost duplicates
        if (!world.isChunkLoaded(campPos.getX() >> 4, campPos.getZ() >> 4)) {
            return;
        }

        // Throttle checks when guide is already spawned to avoid repeated heavy entity searches
        long currentTick = world.getTime();
        if (saveData.isGuideSpawned() && currentTick - lastGuideCampCheckTick < 60) {
            return;
        }
        lastGuideCampCheckTick = currentTick;

        List<ChronicleGuideEntity> existingGuides = world.getEntitiesByClass(
                ChronicleGuideEntity.class,
                new net.minecraft.util.math.Box(campPos).expand(48.0),
                e -> true);

        if (existingGuides.isEmpty()) {
            if (!saveData.isGuideSpawned()) {
                spawnAeron(world, campPos);
                saveData.setGuideSpawned(true);
                saveData.markDirty();
            }
        } else {
            // Ensure first guide is not stuck inside any block
            ChronicleGuideEntity guide = existingGuides.get(0);
            BlockPos currentPos = guide.getBlockPos();
            if (!world.getBlockState(currentPos).isAir()) {
                world.setBlockState(campPos, Blocks.AIR.getDefaultState());
                world.setBlockState(campPos.up(), Blocks.AIR.getDefaultState());
                guide.refreshPositionAndAngles(campPos.getX() + 0.5, campPos.getY(), campPos.getZ() + 0.5, 0.0f, 0.0f);
            }
            // Keep exactly one guide entity, discard any duplicate guides
            if (existingGuides.size() > 1) {
                for (int i = 1; i < existingGuides.size(); i++) {
                    existingGuides.get(i).discard();
                }
            }
        }
    }

    private static void spawnAeron(ServerWorld world, BlockPos safeCampPos) {
        // Center of camp at template coordinate (4, 1, 4): open dirt path directly
        // between campfire and tent
        BlockPos npcPos = safeCampPos;
        world.setBlockState(npcPos, Blocks.AIR.getDefaultState());
        world.setBlockState(npcPos.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(npcPos.up(2), Blocks.AIR.getDefaultState());

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
        BADLANDS_TERRACOTTA("crashed_skyforge_ship_badlands_terracotta", Blocks.TERRACOTTA, Blocks.TERRACOTTA,
                Blocks.STONE),
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
                    if (b == Blocks.GRASS_BLOCK)
                        grassCount++;
                    else if (b == Blocks.SAND)
                        sandCount++;
                    else if (b == Blocks.SANDSTONE || b == Blocks.SMOOTH_SANDSTONE)
                        sandstoneCount++;
                    else if (b == Blocks.RED_SAND)
                        redSandCount++;
                    else if (b == Blocks.RED_SANDSTONE || b == Blocks.SMOOTH_RED_SANDSTONE)
                        redSandstoneCount++;
                    else if (b == Blocks.TERRACOTTA || b.getTranslationKey().contains("terracotta"))
                        terracottaCount++;
                    else if (b == Blocks.PODZOL)
                        podzolCount++;
                    else if (b == Blocks.COARSE_DIRT)
                        coarseDirtCount++;
                    else if (b == Blocks.SNOW || b == Blocks.POWDER_SNOW)
                        snowCount++;
                    else if (b == Blocks.SNOW_BLOCK)
                        snowBlockCount++;
                    else if (b == Blocks.GRAVEL)
                        gravelCount++;
                    else if (b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.GRANITE
                            || b == Blocks.DEEPSLATE)
                        stoneCount++;
                    else if (b == Blocks.CALCITE)
                        calciteCount++;
                    else if (b == Blocks.MUD || b == Blocks.PACKED_MUD || b == Blocks.MUDDY_MANGROVE_ROOTS)
                        mudCount++;
                    else if (b == Blocks.ICE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE)
                        iceCount++;
                }
            }
        }

        var biome = world.getBiome(center);

        if (biome.isIn(BiomeTags.IS_BADLANDS) || biome.matchesKey(BiomeKeys.BADLANDS)
                || biome.matchesKey(BiomeKeys.ERODED_BADLANDS) || biome.matchesKey(BiomeKeys.WOODED_BADLANDS)) {
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

        if (biome.matchesKey(BiomeKeys.SNOWY_SLOPES) || biome.matchesKey(BiomeKeys.JAGGED_PEAKS)
                || biome.matchesKey(BiomeKeys.FROZEN_PEAKS)) {
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
        if (iceCount >= 4)
            return AirshipVariant.FROZEN_OCEAN;
        if (mudCount >= 4)
            return AirshipVariant.SWAMP;
        if (calciteCount >= 10 && stoneCount >= 8)
            return AirshipVariant.CALCITE;
        if (snowBlockCount >= 5)
            return AirshipVariant.SNOWY_SLOPES;
        if (snowCount >= 5)
            return AirshipVariant.SNOWY_PLAINS;
        if (terracottaCount >= 5)
            return AirshipVariant.BADLANDS_TERRACOTTA;
        if (redSandCount >= 5 || redSandstoneCount >= 4)
            return AirshipVariant.BADLANDS_RED_SAND;
        if (sandCount >= 5) {
            return sandstoneCount > 0 ? AirshipVariant.DESERT : AirshipVariant.BEACH;
        }
        if (podzolCount >= 4)
            return AirshipVariant.OLD_GROWTH_TAIGA;
        if (coarseDirtCount >= 4)
            return AirshipVariant.WINDSWEPT_FOREST;
        if (gravelCount >= 5)
            return AirshipVariant.GRAVELLY_HILLS;
        if (stoneCount >= 10 && grassCount <= 2)
            return AirshipVariant.STONY_PEAKS;

        return AirshipVariant.PLAINS_FOREST;
    }

    public static void blendCraterPerimeter(ServerWorld world, BlockPos crashPos, AirshipVariant variant) {
        int centerX = crashPos.getX();
        int centerZ = crashPos.getZ();
        int rimY = crashPos.getY() + 8;

        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq >= 22 && distSq <= 42) {
                    BlockPos rimPos = new BlockPos(centerX + dx, rimY, centerZ + dz);
                    if (world.getBlockState(rimPos.up()).isAir() && !world.getBlockState(rimPos).isAir()) {
                        // Natural, varied rim palette - NOT plain single-color blocks!
                        int hash = Math.abs((dx * 37 + dz * 19) ^ (centerX + centerZ)) % 100;
                        BlockState topMat;
                        if (hash < 40) {
                            topMat = variant.topBlock.getDefaultState();
                        } else if (hash < 60) {
                            topMat = Blocks.COBBLESTONE.getDefaultState();
                        } else if (hash < 75) {
                            topMat = Blocks.MOSSY_COBBLESTONE.getDefaultState();
                        } else if (hash < 85) {
                            topMat = Blocks.CRACKED_STONE_BRICKS.getDefaultState();
                        } else if (hash < 93) {
                            topMat = Blocks.COARSE_DIRT.getDefaultState();
                        } else {
                            topMat = Blocks.BASALT.getDefaultState(); // scorched impact earth
                        }
                        world.setBlockState(rimPos, topMat);

                        BlockPos below = rimPos.down();
                        if (!world.getBlockState(below).isAir()) {
                            BlockState subMat = (hash % 2 == 0) ? variant.midBlock.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
                            world.setBlockState(below, subMat);
                        }
                    }
                }
            }
        }
    }

    public static void packCrashedAirshipPerimeter(ServerWorld world, BlockPos crashPos, AirshipVariant variant) {
        int surfaceY = crashPos.getY() + 8;
        int centerX = crashPos.getX();
        int centerZ = crashPos.getZ();

        // 1. Pack solidly underneath the entire ship template (down 12 blocks below crashPos to solid ground)
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 64) {
                    for (int fillY = -12; fillY <= -1; fillY++) {
                        BlockPos underPos = crashPos.add(dx, fillY, dz);
                        if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                            world.setBlockState(underPos, variant.baseBlock.getDefaultState());
                        }
                    }
                }
            }
        }

        // 2. Pack all exterior sides completely so the ship is 100% buried from all sides and only the top crater is visible
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int distSq = dx * dx + dz * dz;
                // Outside the inner crater bowl:
                if (distSq >= 22 && distSq <= 100) {
                    int targetTopY;
                    if (distSq <= 36) {
                        targetTopY = surfaceY; // Rim height
                    } else if (distSq <= 49) {
                        targetTopY = surfaceY;
                    } else if (distSq <= 64) {
                        targetTopY = surfaceY - 1; // Natural berm slope
                    } else if (distSq <= 81) {
                        targetTopY = surfaceY - 2;
                    } else {
                        targetTopY = surfaceY - 3;
                    }

                    for (int y = crashPos.getY() - 4; y <= targetTopY; y++) {
                        BlockPos p = new BlockPos(centerX + dx, y, centerZ + dz);
                        if (world.getBlockState(p).isAir() || isLiquidOrWater(world, p)) {
                            BlockState fillState;
                            if (y == targetTopY) {
                                fillState = variant.topBlock.getDefaultState();
                            } else if (y >= targetTopY - 2) {
                                fillState = variant.midBlock.getDefaultState();
                            } else {
                                fillState = variant.baseBlock.getDefaultState();
                            }
                            world.setBlockState(p, fillState);
                        }
                    }
                }
            }
        }
    }

    public static void decorateCrashedAirship(ServerWorld world, BlockPos crashPos, AirshipVariant variant) {
        // 1. Smoking wreckage: Campfire on a hay block produces a tall 25+ block smoke column!
        BlockPos fireBase = crashPos.add(-2, 0, -2);
        world.setBlockState(fireBase, Blocks.HAY_BLOCK.getDefaultState());
        world.setBlockState(fireBase.up(), Blocks.CAMPFIRE.getDefaultState());
        world.setBlockState(fireBase.up(2), Blocks.IRON_BARS.getDefaultState());

        // 2. Scorched earth & glowing impact embers on the crater floor
        world.setBlockState(crashPos.add(1, 0, 1), Blocks.MAGMA_BLOCK.getDefaultState());
        world.setBlockState(crashPos.add(-3, 0, 0), Blocks.MAGMA_BLOCK.getDefaultState());
        world.setBlockState(crashPos.add(0, 0, 2), Blocks.MAGMA_BLOCK.getDefaultState());

        world.setBlockState(crashPos.add(2, 0, -1), Blocks.BASALT.getDefaultState());
        world.setBlockState(crashPos.add(-1, 0, 3), Blocks.SMOOTH_BASALT.getDefaultState());
        world.setBlockState(crashPos.add(-3, 0, -1), Blocks.BLACKSTONE.getDefaultState());

        // 3. Artificer debris & structural wreckage embedded in the crater walls
        world.setBlockState(crashPos.add(2, 1, 0), Blocks.EXPOSED_COPPER.getDefaultState());
        world.setBlockState(crashPos.add(-2, 2, -1), Blocks.WEATHERED_COPPER.getDefaultState());
        world.setBlockState(crashPos.add(2, 2, 0), Blocks.LIGHTNING_ROD.getDefaultState());
        world.setBlockState(crashPos.add(1, 1, -3), Blocks.IRON_BARS.getDefaultState());
        world.setBlockState(crashPos.add(-1, 2, 2), Blocks.CHAIN.getDefaultState());

        // 4. Subtle debris scatter outside the rim (gravel, scorched basalt, copper fragment)
        world.setBlockState(crashPos.add(7, 8, 2), Blocks.COARSE_DIRT.getDefaultState());
        world.setBlockState(crashPos.add(-7, 8, -3), Blocks.GRAVEL.getDefaultState());
        world.setBlockState(crashPos.add(3, 8, 7), Blocks.MOSSY_COBBLESTONE.getDefaultState());
        world.setBlockState(crashPos.add(-4, 8, -7), Blocks.BASALT.getDefaultState());
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
                if (world.getBlockEntity(p) instanceof ChestBlockEntity
                        || world.getBlockState(p).isOf(Blocks.BLAST_FURNACE)
                        || world.getBlockState(p).isOf(Blocks.DARK_OAK_PLANKS)) {
                    hasShip = true;
                    break;
                }
            }
            if (hasShip) {
                // Ensure existing ship site is packed from all sides and decorated!
                AirshipVariant existingVariant = selectAirshipVariant(world, existing.add(0, 8, 0));
                packCrashedAirshipPerimeter(world, existing, existingVariant);
                blendCraterPerimeter(world, existing, existingVariant);
                decorateCrashedAirship(world, existing, existingVariant);
                return existing;
            }
            crashPos = existing;
            bestSurfacePos = existing.add(0, 8, 0);
            placePos = testPos;
        } else {
            bestSurfacePos = findSafeAirshipSite(world, nearPos);
            int surfaceY = bestSurfacePos.getY();
            // Template Y=8 contains the top layer grass & surface blocks.
            // By placing template origin at crashY = surfaceY - 8, the ship's top layer
            // matches the ground level (surfaceY) exactly!
            int crashY = surfaceY - 8;
            crashPos = new BlockPos(bestSurfacePos.getX(), crashY, bestSurfacePos.getZ());
            placePos = crashPos.add(-6, 0, -6);
        }

        int surfaceY = bestSurfacePos.getY();

        // Check blocks and biome around generation area to choose matching variant
        AirshipVariant variant = selectAirshipVariant(world, bestSurfacePos);
        Elemental.LOGGER.info("Selected biome-adaptive variant '{}' for Crashed Skyforge Ship at {}",
                variant.structureName, crashPos);

        // Prepare crater foundation with matching base block
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= 25) {
                    for (int fillY = -4; fillY <= -1; fillY++) {
                        BlockPos underPos = crashPos.add(dx, fillY, dz);
                        if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                            world.setBlockState(underPos, variant.baseBlock.getDefaultState());
                        }
                    }
                    int maxY = Math.max(surfaceY + 8, crashPos.getY() + 10);
                    for (int y = surfaceY + 1; y <= maxY; y++) {
                        world.setBlockState(new BlockPos(crashPos.getX() + dx, y, crashPos.getZ() + dz),
                                Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Place selected variant template (falls back to default if variant not found)
        Identifier variantId = new Identifier(Elemental.MOD_ID, variant.structureName);
        if (!placeNbtStructure(world, variantId, placePos)) {
            placeNbtStructure(world, SKYFORGE_SHIP_ID, placePos);
        }

        // Encase all sides completely so only the top crater is visible
        packCrashedAirshipPerimeter(world, crashPos, variant);

        // Seamless crater rim landscape integration with rich textures
        blendCraterPerimeter(world, crashPos, variant);

        // Add smoking wreckage, magma embers, and artificer debris
        decorateCrashedAirship(world, crashPos, variant);

        // Populate chest with initialized blueprint maps & remove empty chests
        populateAirshipChests(world, placePos);

        saveData.setSkyforgeAirshipLocation(crashPos);
        saveData.markDirty();
        Elemental.LOGGER.info("Crashed Skyforge Ship initialized from NBT variant '{}' at highland: {}",
                variant.structureName, crashPos);
        return crashPos;
    }

    public static void populateAirshipChests(ServerWorld world, BlockPos placePos) {
        ChestBlockEntity mainChest = null;
        BlockPos mainChestPos = null;

        // Search the ship's 12x9x11 bounds for all chests - check block state first before requesting BlockEntity
        for (BlockPos p : BlockPos.iterate(placePos, placePos.add(11, 8, 10))) {
            if (world.getBlockState(p).isOf(Blocks.CHEST) && world.getBlockEntity(p) instanceof ChestBlockEntity chest) {
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
            mainChest.setStack(10,
                    BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
            mainChest.setStack(12,
                    BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.AEROGEL_CORE));
            mainChest.setStack(14,
                    BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENT_CORE));
            mainChest.setStack(16,
                    BlueprintMaps.createBlueprintMap(world, mainChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
            mainChest.markDirty();
        }
    }

    public static void ensureAirshipChestsPopulated(ServerWorld world, BlockPos crashPos) {
        BlockPos placePos = crashPos.add(-6, 0, -6);
        if (!world.isChunkLoaded(placePos.getX() >> 4, placePos.getZ() >> 4)) {
            return;
        }

        ChestBlockEntity targetChest = null;
        BlockPos targetChestPos = null;

        for (BlockPos p : BlockPos.iterate(placePos.add(-2, -2, -2), placePos.add(13, 10, 12))) {
            if (world.getBlockState(p).isOf(Blocks.CHEST) && world.getBlockEntity(p) instanceof ChestBlockEntity chest) {
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
            AirshipVariant variant = selectAirshipVariant(world, crashPos.add(0, 8, 0));
            Identifier variantId = new Identifier(Elemental.MOD_ID, variant.structureName);
            if (!placeNbtStructure(world, variantId, placePos)) {
                placeNbtStructure(world, SKYFORGE_SHIP_ID, placePos);
            }
            packCrashedAirshipPerimeter(world, crashPos, variant);
            blendCraterPerimeter(world, crashPos, variant);
            decorateCrashedAirship(world, crashPos, variant);
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
                targetChest.setStack(10,
                        BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.PNEUMATIC_PISTON));
                targetChest.setStack(12,
                        BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.AEROGEL_CORE));
                targetChest.setStack(14,
                        BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.ELEMENT_CORE));
                targetChest.setStack(16,
                        BlueprintMaps.createBlueprintMap(world, targetChestPos, BlueprintMaps.Type.ELEMENTAL_JETPACK));
                targetChest.markDirty();
            }
        }
    }

    // ==========================================
    // 3. Desert Expedition Location & Wizard Setup
    // ==========================================

    public static BlockPos findOrGenerateDesertSanctuary(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getDesertLocation();
        if (existing != null) {
            ensureWizardAtVillage(world, existing, saveData);
            return existing;
        }

        BlockPos desertCenter = null;
        // 1. Try locating a vanilla Desert Village first
        try {
            var structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
            var villageEntry = structureRegistry.getEntry(StructureKeys.VILLAGE_DESERT);
            if (villageEntry.isPresent()) {
                var result = world.getChunkManager().getChunkGenerator().locateStructure(
                        world,
                        net.minecraft.registry.entry.RegistryEntryList.of(villageEntry.get()),
                        nearPos,
                        100,
                        false
                );
                if (result != null) {
                    BlockPos villagePos = result.getFirst();
                    BlockPos ground = findTrueGroundSurface(world, villagePos.getX(), villagePos.getZ());
                    desertCenter = ground != null ? ground : villagePos;
                    Elemental.LOGGER.info("Located vanilla Desert Village at: {}", desertCenter);
                }
            }
        } catch (Exception e) {
            Elemental.LOGGER.warn("Could not locate desert village structure: ", e);
        }

        // 2. Fallback to Desert or Badlands Biome
        if (desertCenter == null) {
            try {
                var pair = world.locateBiome(
                        entry -> entry.matchesKey(BiomeKeys.DESERT),
                        nearPos,
                        6400,
                        32,
                        64);
                if (pair != null) {
                    desertCenter = pair.getFirst();
                } else {
                    var badlandsPair = world.locateBiome(
                            entry -> entry.isIn(BiomeTags.IS_BADLANDS),
                            nearPos,
                            6400,
                            32,
                            64);
                    if (badlandsPair != null) {
                        desertCenter = badlandsPair.getFirst();
                    }
                }
            } catch (Exception e) {
                Elemental.LOGGER.error("Failed to locate desert biome: ", e);
            }
        }

        if (desertCenter == null) {
            desertCenter = nearPos.add(1200, 0, 950);
        }

        BlockPos bestSurface = findSafeDesertSite(world, desertCenter);
        saveData.setDesertLocation(bestSurface);
        saveData.markDirty();
        Elemental.LOGGER.info("Desert Village location marked at: {}", bestSurface);

        ensureWizardAtVillage(world, bestSurface, saveData);
        return bestSurface;
    }

    public static void ensureWizardAtVillage(ServerWorld world, BlockPos villagePos, QuestSaveData saveData) {
        if (villagePos == null) return;

        List<ElementalWizardEntity> existingWizards = world.getEntitiesByClass(
                ElementalWizardEntity.class,
                new net.minecraft.util.math.Box(villagePos).expand(48.0),
                e -> true);

        if (existingWizards.isEmpty()) {
            if (!saveData.isWizardSpawned()) {
                spawnWizard(world, villagePos);
                saveData.setWizardSpawned(true);
                saveData.markDirty();
            }
        } else {
            ElementalWizardEntity wizard = existingWizards.get(0);
            BlockPos currentPos = wizard.getBlockPos();
            if (!world.getBlockState(currentPos).isAir()) {
                world.setBlockState(villagePos, Blocks.AIR.getDefaultState());
                world.setBlockState(villagePos.up(), Blocks.AIR.getDefaultState());
                wizard.refreshPositionAndAngles(villagePos.getX() + 0.5, villagePos.getY(), villagePos.getZ() + 0.5, 0.0f, 0.0f);
            }
            if (existingWizards.size() > 1) {
                for (int i = 1; i < existingWizards.size(); i++) {
                    existingWizards.get(i).discard();
                }
            }
        }
    }

    private static void spawnWizard(ServerWorld world, BlockPos safeVillagePos) {
        BlockPos npcPos = safeVillagePos;
        world.setBlockState(npcPos, Blocks.AIR.getDefaultState());
        world.setBlockState(npcPos.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(npcPos.up(2), Blocks.AIR.getDefaultState());

        ElementalWizardEntity wizard = ModEntities.ELEMENTAL_WIZARD.create(world);
        if (wizard != null) {
            wizard.refreshPositionAndAngles(npcPos.getX() + 0.5, npcPos.getY(), npcPos.getZ() + 0.5, 0.0f, 0.0f);
            wizard.setHomePos(safeVillagePos);
            wizard.setPersistent();
            world.spawnEntity(wizard);
        }
    }

    public static ElementalWizardEntity teleportWizardToPlayer(ServerWorld world, ServerPlayerEntity player) {
        double yawRad = Math.toRadians(player.getYaw());
        int targetX = (int) Math.round(player.getX() - Math.sin(yawRad) * 2.5);
        int targetZ = (int) Math.round(player.getZ() + Math.cos(yawRad) * 2.5);
        BlockPos surface = findTrueGroundSurface(world, targetX, targetZ);
        BlockPos telePos = surface != null ? surface : player.getBlockPos().add(1, 0, 1);

        world.setBlockState(telePos, Blocks.AIR.getDefaultState());
        world.setBlockState(telePos.up(), Blocks.AIR.getDefaultState());

        List<ElementalWizardEntity> wizards = world.getEntitiesByClass(
                ElementalWizardEntity.class,
                new net.minecraft.util.math.Box(player.getBlockPos()).expand(10000.0),
                e -> true);

        ElementalWizardEntity wizard;
        if (!wizards.isEmpty()) {
            wizard = wizards.get(0);
            wizard.teleport(world, telePos.getX() + 0.5, telePos.getY(), telePos.getZ() + 0.5, java.util.Collections.emptySet(), player.getYaw() + 180.0f, 0.0f);
        } else {
            wizard = ModEntities.ELEMENTAL_WIZARD.create(world);
            if (wizard != null) {
                wizard.refreshPositionAndAngles(telePos.getX() + 0.5, telePos.getY(), telePos.getZ() + 0.5, player.getYaw() + 180.0f, 0.0f);
                wizard.setPersistent();
                world.spawnEntity(wizard);
            }
        }

        if (wizard != null) {
            wizard.setHomePos(telePos);
            world.spawnParticles(ParticleTypes.PORTAL, telePos.getX() + 0.5, telePos.getY() + 1.0, telePos.getZ() + 0.5, 40, 0.5, 1.0, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.ENCHANT, telePos.getX() + 0.5, telePos.getY() + 1.0, telePos.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.2);
            world.spawnParticles(ParticleTypes.FLASH, telePos.getX() + 0.5, telePos.getY() + 1.0, telePos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.0);
            world.playSound(null, telePos.getX(), telePos.getY(), telePos.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0f, 0.9f);
        }

        return wizard;
    }

    public static BlockPos findNearestSnowBiome(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSnowBiomeLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos center = null;
        try {
            var pair = world.locateBiome(
                    entry -> entry.matchesKey(BiomeKeys.SNOWY_PLAINS)
                            || entry.matchesKey(BiomeKeys.SNOWY_SLOPES)
                            || entry.matchesKey(BiomeKeys.FROZEN_PEAKS)
                            || entry.matchesKey(BiomeKeys.JAGGED_PEAKS)
                            || entry.matchesKey(BiomeKeys.SNOWY_TAIGA)
                            || entry.matchesKey(BiomeKeys.ICE_SPIKES)
                            || entry.matchesKey(BiomeKeys.GROVE),
                    nearPos,
                    6400,
                    32,
                    64
            );
            if (pair != null) {
                center = pair.getFirst();
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to locate snow biome: ", e);
        }

        if (center == null) {
            center = nearPos.add(800, 0, 800);
        }

        BlockPos ground = findTrueGroundSurface(world, center.getX(), center.getZ());
        BlockPos result = ground != null ? ground : center;
        saveData.setSnowBiomeLocation(result);
        saveData.markDirty();
        return result;
    }

    public static BlockPos findNearestForestBiome(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getForestBiomeLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos center = null;
        try {
            var darkForestPair = world.locateBiome(
                    entry -> entry.matchesKey(BiomeKeys.DARK_FOREST),
                    nearPos,
                    6400,
                    32,
                    64
            );
            if (darkForestPair != null) {
                center = darkForestPair.getFirst();
            } else {
                var forestPair = world.locateBiome(
                        entry -> entry.isIn(BiomeTags.IS_FOREST) || entry.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST),
                        nearPos,
                        6400,
                        32,
                        64
                );
                if (forestPair != null) {
                    center = forestPair.getFirst();
                }
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to locate forest biome: ", e);
        }

        if (center == null) {
            center = nearPos.add(800, 0, -800);
        }

        BlockPos ground = findTrueGroundSurface(world, center.getX(), center.getZ());
        BlockPos result = ground != null ? ground : center;
        saveData.setForestBiomeLocation(result);
        saveData.markDirty();
        return result;
    }

    public static BlockPos findOrGenerateEverlivingArena(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getEverlivingArenaLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos arenaCenter = findLargePlainAreaForArena(world, nearPos);
        generateEverlivingArenaAt(world, arenaCenter);

        saveData.setEverlivingArenaLocation(arenaCenter);
        saveData.markDirty();
        Elemental.LOGGER.info("Everliving Knight Arena generated properly on plain with stepped boundary at: {}", arenaCenter);
        return arenaCenter;
    }

    public static BlockPos findLargePlainAreaForArena(ServerWorld world, BlockPos nearPos) {
        final int halfX = 35;
        final int halfZ = 36;

        BlockPos bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;

        // 1. Locate nearby large plains/meadow/savanna biome
        BlockPos plainsBiomePos = null;
        try {
            var pair = world.locateBiome(
                    entry -> entry.matchesKey(BiomeKeys.PLAINS)
                            || entry.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)
                            || entry.matchesKey(BiomeKeys.MEADOW)
                            || entry.matchesKey(BiomeKeys.SAVANNA),
                    nearPos,
                    1600,
                    32,
                    64
            );
            if (pair != null) {
                plainsBiomePos = pair.getFirst();
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to locate plains biome for arena: ", e);
        }

        List<BlockPos> candidatePoints = new java.util.ArrayList<>();

        // Search around located plains biome in a grid
        if (plainsBiomePos != null) {
            for (int dx = -160; dx <= 160; dx += 32) {
                for (int dz = -160; dz <= 160; dz += 32) {
                    candidatePoints.add(new BlockPos(plainsBiomePos.getX() + dx, 0, plainsBiomePos.getZ() + dz));
                }
            }
        }

        // Also test concentric rings around nearPos (from 280 to 700 blocks out)
        int[] radii = new int[] { 320, 400, 480, 560, 640 };
        for (int r : radii) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 30) {
                double rad = Math.toRadians(angleDeg);
                int testX = nearPos.getX() + (int) (r * Math.cos(rad));
                int testZ = nearPos.getZ() + (int) (r * Math.sin(rad));
                candidatePoints.add(new BlockPos(testX, 0, testZ));
            }
        }

        for (BlockPos cand : candidatePoints) {
            BlockPos groundCenter = findTrueGroundSurface(world, cand.getX(), cand.getZ());
            if (groundCenter == null) continue;

            int cx = groundCenter.getX();
            int cz = groundCenter.getZ();

            // Evaluate the 69x72 footprint using 49 sample points
            int minH = Integer.MAX_VALUE;
            int maxH = Integer.MIN_VALUE;
            int sumH = 0;
            int count = 0;
            boolean valid = true;

            for (int sx = -halfX; sx <= halfX; sx += 11) {
                for (int sz = -halfZ; sz <= halfZ; sz += 12) {
                    int sampleX = cx + sx;
                    int sampleZ = cz + sz;
                    world.getChunk(sampleX >> 4, sampleZ >> 4);
                    int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                    BlockPos p = new BlockPos(sampleX, sampleY, sampleZ);

                    if (isLiquidOrWater(world, p) || isLiquidOrWater(world, p.down()) || isWaterBiome(world, p)) {
                        valid = false;
                        break;
                    }

                    if (sampleY < minH) minH = sampleY;
                    if (sampleY > maxH) maxH = sampleY;
                    sumH += sampleY;
                    count++;
                }
                if (!valid) break;
            }

            if (!valid || count == 0) continue;

            int diff = maxH - minH;
            if (diff > 8) continue;

            int avgH = sumH / count;
            int variance = 0;
            for (int sx = -halfX; sx <= halfX; sx += 11) {
                for (int sz = -halfZ; sz <= halfZ; sz += 12) {
                    int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + sx, cz + sz);
                    variance += Math.abs(sampleY - avgH);
                }
            }

            var biome = world.getBiome(groundCenter);
            boolean isPlains = biome.matchesKey(BiomeKeys.PLAINS)
                    || biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)
                    || biome.matchesKey(BiomeKeys.MEADOW)
                    || biome.matchesKey(BiomeKeys.SAVANNA);

            double dist = Math.sqrt(groundCenter.getSquaredDistance(nearPos));

            int score = 6000 - (diff * 500) - (variance * 20);
            if (isPlains) {
                score += 3000;
            }
            score -= (int) (Math.abs(dist - 420) * 2);

            if (diff <= 2 && isPlains && dist >= 300 && dist <= 650) {
                return groundCenter;
            }

            if (score > bestScore) {
                bestScore = score;
                bestCandidate = groundCenter;
            }
        }

        if (bestCandidate != null) {
            return bestCandidate;
        }

        BlockPos fallback = findTrueGroundSurface(world, nearPos.getX() + 380, nearPos.getZ() + 320);
        return fallback != null ? fallback : nearPos.add(380, 0, 320);
    }

    public static void generateEverlivingArenaAt(ServerWorld world, BlockPos arenaCenter) {
        final int sizeX = 69;
        final int sizeY = 29;
        final int sizeZ = 72;
        final int altarOffsetX = 35;
        final int altarOffsetY = 2; // Floor is at local Y = 2
        final int altarOffsetZ = 36;

        // 1. Calculate the median ground height across the 69x72 area
        List<Integer> groundHeights = new java.util.ArrayList<>();
        int startX = arenaCenter.getX() - altarOffsetX;
        int startZ = arenaCenter.getZ() - altarOffsetZ;

        for (int x = startX; x < startX + sizeX; x += 4) {
            for (int z = startZ; z < startZ + sizeZ; z += 4) {
                world.getChunk(x >> 4, z >> 4);
                int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                groundHeights.add(ty);
            }
        }
        java.util.Collections.sort(groundHeights);
        int floorY = groundHeights.isEmpty() ? arenaCenter.getY() : groundHeights.get(groundHeights.size() / 2);

        int originX = startX;
        int originY = floorY - altarOffsetY;
        int originZ = startZ;
        BlockPos originPos = new BlockPos(originX, originY, originZ);

        // Pre-load all chunks needed for the arena + boundary (69x72 + 6 blocks border)
        for (int cx = (originX - 8) >> 4; cx <= (originX + sizeX + 8) >> 4; cx++) {
            for (int cz = (originZ - 8) >> 4; cz <= (originZ + sizeZ + 8) >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }

        // 2. Foundation: fill underneath the arena structure down to natural ground so it NEVER floats
        for (int x = originX; x < originX + sizeX; x++) {
            for (int z = originZ; z < originZ + sizeZ; z++) {
                int naturalY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (naturalY < originY) {
                    for (int y = originY - 1; y >= naturalY - 2; y--) {
                        BlockPos fillPos = new BlockPos(x, y, z);
                        if (y >= originY - 2) {
                            world.setBlockState(fillPos, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(fillPos, Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }

                // If natural ground is above the arena floor, clear obstructive terrain inside the coliseum
                if (naturalY > originY + 2) {
                    for (int y = originY + 3; y <= Math.min(naturalY + 1, originY + sizeY - 1); y++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        if (!world.getBlockState(clearPos).isAir()) {
                            world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        // 3. Place the NBT structure
        placeNbtStructure(world, EVERLIVING_ARENA_ID, originPos);

        // 4. Connect the blocks to arena and create a steps boundary as if it's normal!
        buildArenaSteppedBoundary(world, originPos, floorY, sizeX, sizeZ);
    }

    public static void buildArenaSteppedBoundary(ServerWorld world, BlockPos originPos, int floorY, int sizeX, int sizeZ) {
        int minX = originPos.getX();
        int maxX = originPos.getX() + sizeX - 1;
        int minZ = originPos.getZ();
        int maxZ = originPos.getZ() + sizeZ - 1;

        int stepRings = 5;

        for (int x = minX - stepRings; x <= maxX + stepRings; x++) {
            for (int z = minZ - stepRings; z <= maxZ + stepRings; z++) {
                int dx = (x < minX) ? (minX - x) : ((x > maxX) ? (x - maxX) : 0);
                int dz = (z < minZ) ? (minZ - z) : ((z > maxZ) ? (z - maxZ) : 0);
                int dist = Math.max(dx, dz);
                if (dist < 1 || dist > stepRings) continue;

                int naturalY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

                if (naturalY < floorY) {
                    // Outer terrain is lower: step down from floorY to natural terrain
                    int targetStepY = Math.max(naturalY, floorY - dist);

                    // Solid foundation below the step
                    for (int y = targetStepY - 1; y >= naturalY - 2; y--) {
                        BlockPos fillPos = new BlockPos(x, y, z);
                        if (y >= targetStepY - 2) {
                            world.setBlockState(fillPos, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(fillPos, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }

                    // Stepped surface
                    BlockPos stepPos = new BlockPos(x, targetStepY, z);
                    if (targetStepY > naturalY) {
                        BlockState stairState;
                        if (dx > dz) {
                            Direction facing = (x < minX) ? Direction.WEST : Direction.EAST;
                            stairState = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, facing);
                        } else if (dz > dx) {
                            Direction facing = (z < minZ) ? Direction.NORTH : Direction.SOUTH;
                            stairState = Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, facing);
                        } else {
                            stairState = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getDefaultState();
                        }
                        world.setBlockState(stepPos, stairState, Block.NOTIFY_LISTENERS);
                    } else {
                        // Level with natural ground: blend into grass
                        world.setBlockState(stepPos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                        world.setBlockState(stepPos.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }

                    // Clear air above step
                    for (int y = targetStepY + 1; y <= floorY + 4; y++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        if (!world.getBlockState(clearPos).isAir()) {
                            world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                } else if (naturalY > floorY) {
                    // Outer terrain is higher: step down towards the arena
                    int targetStepY = Math.min(naturalY, floorY + dist);

                    BlockPos stepPos = new BlockPos(x, targetStepY, z);
                    if (dist <= 2) {
                        world.setBlockState(stepPos, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(stepPos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                        world.setBlockState(stepPos.down(), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }

                    // Clear terrain above the terrace
                    for (int y = targetStepY + 1; y <= naturalY + 2; y++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        if (!world.getBlockState(clearPos).isAir()) {
                            world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                } else {
                    // Exactly level with floorY
                    BlockPos borderPos = new BlockPos(x, floorY, z);
                    if (dist == 1) {
                        world.setBlockState(borderPos, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(borderPos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    public static BlockPos findOrGenerateSeismicGolemArena(ServerWorld world, BlockPos nearPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getSeismicGolemArenaLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos arenaCenter = findLargePlainAreaForArena(world, nearPos.add(300, 0, 300));
        generateSeismicGolemArenaAt(world, arenaCenter);

        saveData.setSeismicGolemArenaLocation(arenaCenter);
        saveData.markDirty();
        Elemental.LOGGER.info("Seismic Golem Arena generated properly at: {}", arenaCenter);
        return arenaCenter;
    }

    public static void generateSeismicGolemArenaAt(ServerWorld world, BlockPos arenaCenter) {
        final int sizeX = 69;
        final int sizeY = 28;
        final int sizeZ = 69;
        final int altarOffsetX = 34;
        final int altarOffsetY = 5;
        final int altarOffsetZ = 34;

        List<Integer> groundHeights = new java.util.ArrayList<>();
        int startX = arenaCenter.getX() - altarOffsetX;
        int startZ = arenaCenter.getZ() - altarOffsetZ;

        for (int x = startX; x < startX + sizeX; x += 4) {
            for (int z = startZ; z < startZ + sizeZ; z += 4) {
                world.getChunk(x >> 4, z >> 4);
                int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                groundHeights.add(ty);
            }
        }
        java.util.Collections.sort(groundHeights);
        int floorY = groundHeights.isEmpty() ? arenaCenter.getY() : groundHeights.get(groundHeights.size() / 2);

        int originX = startX;
        int originY = floorY - altarOffsetY;
        int originZ = startZ;
        BlockPos originPos = new BlockPos(originX, originY, originZ);

        for (int cx = (originX - 8) >> 4; cx <= (originX + sizeX + 8) >> 4; cx++) {
            for (int cz = (originZ - 8) >> 4; cz <= (originZ + sizeZ + 8) >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }

        for (int x = originX; x < originX + sizeX; x++) {
            for (int z = originZ; z < originZ + sizeZ; z++) {
                int naturalY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (naturalY < originY) {
                    for (int y = originY - 1; y >= naturalY - 2; y--) {
                        BlockPos fillPos = new BlockPos(x, y, z);
                        if (y >= originY - 2) {
                            world.setBlockState(fillPos, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(fillPos, Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }

                if (naturalY > originY + 5) {
                    for (int y = originY + 6; y <= Math.min(naturalY + 1, originY + sizeY - 1); y++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        if (!world.getBlockState(clearPos).isAir()) {
                            world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        placeNbtStructure(world, SEISMIC_GOLEM_ARENA_ID, originPos);
        buildArenaSteppedBoundary(world, originPos, floorY, sizeX, sizeZ);
    }

    // ==========================================
    // 4. Safe Dry Surface & Terrain Search Helpers
    // ==========================================

    public static BlockPos findSafeSurface(ServerWorld world, BlockPos origin) {
        int[] radii = new int[] { 60, 80, 100, 120, 140, 160, 180, 200 };
        int numAngles = 24;
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int x = origin.getX() + (int) Math.round(r * Math.cos(angle));
                int z = origin.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, x, z);
                if (ground == null)
                    continue;

                // Evaluate flatness strictly across radius 4 (covers the 9x9 camp)
                int minH = Integer.MAX_VALUE;
                int maxH = Integer.MIN_VALUE;
                int totalVariance = 0;
                boolean safe = true;

                for (int dx = -4; dx <= 4; dx += 2) {
                    for (int dz = -4; dz <= 4; dz += 2) {
                        int tx = ground.getX() + dx;
                        int tz = ground.getZ() + dz;
                        world.getChunk(tx >> 4, tz >> 4);
                        int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tx, tz);
                        BlockPos p = new BlockPos(tx, ty, tz);
                        if (isLiquidOrWater(world, p) || isLiquidOrWater(world, p.down()) || isWaterBiome(world, p)) {
                            safe = false;
                            break;
                        }
                        if (ty < minH)
                            minH = ty;
                        if (ty > maxH)
                            maxH = ty;
                        totalVariance += Math.abs(ty - ground.getY());
                    }
                    if (!safe)
                        break;
                }

                if (!safe)
                    continue;

                int diff = maxH - minH;
                if (diff > 2)
                    continue; // Must be flat or almost flat

                var biome = world.getBiome(ground);
                boolean isPlainsBiome = biome.matchesKey(BiomeKeys.PLAINS)
                        || biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS)
                        || biome.matchesKey(BiomeKeys.MEADOW)
                        || biome.matchesKey(BiomeKeys.SAVANNA);

                // Scoring: completely flat (diff == 0) in plains biome gets massive priority
                int score = 2000 - (diff * 600) - (totalVariance * 40) - Math.abs(r - 100) * 3;
                if (isPlainsBiome)
                    score += 1200;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                    // If we found a completely flat plain in a plains biome near ~100 blocks, use
                    // it immediately
                    if (diff == 0 && isPlainsBiome && Math.abs(r - 100) <= 30) {
                        return bestPos;
                    }
                }
            }
        }

        if (bestPos != null)
            return bestPos;

        BlockPos fallback = findTrueGroundSurface(world, origin.getX() + 90, origin.getZ() + 90);
        return fallback != null ? fallback : origin.add(90, 0, 90);
    }

    public static BlockPos findSafeCliffSite(ServerWorld world, BlockPos campPos) {
        return findSafeAirshipSite(world, campPos);
    }

    public static BlockPos findSafeAirshipSite(ServerWorld world, BlockPos campPos) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;
        int[] radii = new int[] { 140, 170, 200, 230, 260 };
        int numAngles = 20;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = campPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = campPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground == null)
                    continue;

                // Check flatness strictly across radius 8 (covers full 16x16 area of ship and packing)
                int minH = Integer.MAX_VALUE;
                int maxH = Integer.MIN_VALUE;
                int totalVariance = 0;
                boolean safe = true;

                for (int dx = -8; dx <= 8; dx += 2) {
                    for (int dz = -8; dz <= 8; dz += 2) {
                        int tx = ground.getX() + dx;
                        int tz = ground.getZ() + dz;
                        world.getChunk(tx >> 4, tz >> 4);
                        int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tx, tz);
                        BlockPos p = new BlockPos(tx, ty, tz);
                        if (isLiquidOrWater(world, p) || isLiquidOrWater(world, p.down()) || isWaterBiome(world, p)) {
                            safe = false;
                            break;
                        }
                        if (ty < minH) minH = ty;
                        if (ty > maxH) maxH = ty;
                        totalVariance += Math.abs(ty - ground.getY());
                    }
                    if (!safe) break;
                }

                if (!safe) continue;

                int slopeDiff = maxH - minH;
                // Avoid steep cliff edges where terrain drops off significantly
                if (slopeDiff > 4) continue;

                // Prefer elevated plateaus / highlands with minimal slope difference
                int elev = ground.getY() - world.getSeaLevel();
                int score = (elev * 3) - (slopeDiff * 300) - (totalVariance * 20) - Math.abs(r - 180) * 2;
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = ground;
                }
            }
        }

        if (bestPos != null)
            return bestPos;

        for (int r = 120; r <= 280; r += 20) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 30) {
                double rad = Math.toRadians(angleDeg);
                int testX = campPos.getX() + (int) (r * Math.cos(rad));
                int testZ = campPos.getZ() + (int) (r * Math.sin(rad));
                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground != null && isSafeDryLandFootprint(world, ground, 6, 2)) {
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
        int[] radii = new int[] { 0, 32, 64, 96, 128, 160, 200 };
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
                if (ground == null)
                    continue;
                if (!isSafeDryLandFootprint(world, ground, 8, 3))
                    continue;

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

        if (bestPos != null)
            return bestPos;

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
        if (pos == null)
            return false;
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || !world.getFluidState(pos).isEmpty())
            return true;
        return state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS)
                || state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.BUBBLE_COLUMN) || state.isOf(Blocks.ICE)
                || state.isOf(Blocks.FROSTED_ICE);
    }

    public static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        if (pos == null)
            return false;
        try {
            var biome = world.getBiome(pos);
            return biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isFoliageOrTree(BlockState state) {
        if (state.isAir())
            return true;
        if (state.isIn(BlockTags.LEAVES))
            return true;
        if (state.isIn(BlockTags.LOGS))
            return true;
        if (state.isIn(BlockTags.FLOWERS))
            return true;
        if (state.isIn(BlockTags.SAPLINGS))
            return true;
        if (state.isIn(BlockTags.WART_BLOCKS))
            return true;
        if (state.isOf(Blocks.MUSHROOM_STEM) || state.isOf(Blocks.RED_MUSHROOM_BLOCK)
                || state.isOf(Blocks.BROWN_MUSHROOM_BLOCK))
            return true;
        if (state.isOf(Blocks.VINE) || state.isOf(Blocks.GLOW_LICHEN) || state.isOf(Blocks.HANGING_ROOTS)
                || state.isOf(Blocks.MANGROVE_ROOTS))
            return true;
        if (state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH))
            return true;
        if (state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING) || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.CACTUS))
            return true;
        if (state.isOf(Blocks.SNOW))
            return true;
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
        if (center == null)
            return false;
        int centerY = center.getY();
        int seaLevel = world.getSeaLevel();

        if (centerY < seaLevel)
            return false;
        if (isWaterBiome(world, center))
            return false;

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

    // ==========================================
    // 5. Lush Stronghold Temple NBT Generation
    // ==========================================

    public static BlockPos findOrGenerateLushStrongholdTemple(ServerWorld world, BlockPos originPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getLushStrongholdTempleLocation();
        if (existing != null) {
            return existing;
        }

        BlockPos targetPos = JungleDungeonManager.findNearestJungleBiome(world, originPos);
        if (targetPos == null) {
            targetPos = originPos;
        }

        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetPos.getX(), targetPos.getZ());
        if (surfaceY < 64) surfaceY = 64;

        int undergroundY = Math.max(world.getBottomY() + 10, surfaceY - 38);
        BlockPos placePos = new BlockPos(targetPos.getX() - 24, undergroundY, targetPos.getZ() - 24);

        BlockPos centerPos = generateLushStrongholdTempleAt(world, placePos);
        saveData.setLushStrongholdTempleLocation(centerPos);
        saveData.markDirty();
        return centerPos;
    }

    public static BlockPos generateLushStrongholdTempleAt(ServerWorld world, BlockPos placePos) {
        for (int cx = placePos.getX() >> 4; cx <= (placePos.getX() + 48) >> 4; cx++) {
            for (int cz = placePos.getZ() >> 4; cz <= (placePos.getZ() + 48) >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }

        placeNbtStructure(world, LUSH_STRONGHOLD_TEMPLE_ID, placePos);

        BlockPos center = placePos.add(24, 6, 24);
        net.minecraft.util.math.Box bounds = new net.minecraft.util.math.Box(
                placePos.getX(), placePos.getY(), placePos.getZ(),
                placePos.getX() + 48, placePos.getY() + 36, placePos.getZ() + 48
        );
        JungleDungeonManager.registerShrine(center, bounds, java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList(), null);

        Elemental.LOGGER.info("Generated Lush Stronghold Temple at {} (center: {})", placePos, center);
        return center;
    }
}
