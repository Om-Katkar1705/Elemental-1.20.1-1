package com.elemental.world.shrine;

import com.elemental.Elemental;
import com.elemental.quest.QuestSaveData;
import com.elemental.world.JungleDungeonManager;
import com.elemental.world.NbtStructureManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * World generation and placement coordinator for the Collasped Shrine.
 * Handles terrain sampling, foundation blending, modular NBT template placement,
 * biome restrictions, and registration with JungleDungeonManager.
 */
public class CollaspedShrineStructureGenerator {

    public static final Identifier STRUCTURE_ID = new Identifier(Elemental.MOD_ID, "collasped_shrine");
    public static final int FOOTPRINT_WIDTH = 17;
    public static final int FOOTPRINT_LENGTH = 23;
    public static final int MAX_ACCEPTABLE_SLOPE = 6;

    /**
     * Locates the nearest Jungle biome or safe spot and generates the Collasped Shrine.
     */
    public static BlockPos findOrGenerateCollaspedShrine(ServerWorld world, BlockPos originPos) {
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getCollapsedShrineLocation();
        if (existing != null) {
            return existing;
        }

        // Search for nearest jungle biome
        BlockPos junglePos = findNearestJungleBiome(world, originPos);
        if (junglePos == null) {
            junglePos = originPos; // Fallback if no jungle nearby
        }

        // Search for an optimal, flat, non-liquid terrain placement inside jungle
        BlockPos optimalPos = findOptimalJunglePlacement(world, junglePos);
        if (optimalPos == null) {
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, junglePos.getX(), junglePos.getZ());
            optimalPos = new BlockPos(junglePos.getX(), surfaceY, junglePos.getZ());
        }

        BlockPos generatedPos = generate(world, optimalPos);
        saveData.setCollapsedShrineLocation(generatedPos);
        saveData.markDirty();
        return generatedPos;
    }

    /**
     * Finds an optimal terrain placement with solid ground, low slope variance, and jungle-exclusive bounds.
     */
    public static BlockPos findOptimalJunglePlacement(ServerWorld world, BlockPos startPos) {
        int[] radii = new int[]{0, 32, 64, 96, 128, 160, 200};
        int numAngles = 16;

        for (int r : radii) {
            if (r == 0) {
                if (isSiteSuitable(world, startPos.getX(), startPos.getZ())) {
                    return startPos;
                }
                continue;
            }

            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int x = startPos.getX() + (int) Math.round(r * Math.cos(angle));
                int z = startPos.getZ() + (int) Math.round(r * Math.sin(angle));

                if (isSiteSuitable(world, x, z)) {
                    int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                    return new BlockPos(x, topY, z);
                }
            }
        }
        return null;
    }

    private static boolean isSiteSuitable(ServerWorld world, int startX, int startZ) {
        // 1. Strict Biome Check: All 4 corners + center must be inside Jungle biomes
        BlockPos[] testPoints = new BlockPos[]{
                new BlockPos(startX, 64, startZ),
                new BlockPos(startX + FOOTPRINT_WIDTH - 1, 64, startZ),
                new BlockPos(startX, 64, startZ + FOOTPRINT_LENGTH - 1),
                new BlockPos(startX + FOOTPRINT_WIDTH - 1, 64, startZ + FOOTPRINT_LENGTH - 1),
                new BlockPos(startX + FOOTPRINT_WIDTH / 2, 64, startZ + FOOTPRINT_LENGTH / 2)
        };

        for (BlockPos p : testPoints) {
            if (!world.getBiome(p).isIn(BiomeTags.IS_JUNGLE)) {
                return false;
            }
        }

        // 2. Liquid check & slope variance
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;

        for (int dx = 0; dx < FOOTPRINT_WIDTH; dx += 4) {
            for (int dz = 0; dz < FOOTPRINT_LENGTH; dz += 4) {
                int x = startX + dx;
                int z = startZ + dz;
                world.getChunk(x >> 4, z >> 4);

                BlockPos ground = NbtStructureManager.findTrueGroundSurface(world, x, z);
                if (ground == null) {
                    return false; // water, lake, or liquid present
                }

                int h = ground.getY();
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
            }
        }

        return (maxH - minH) <= MAX_ACCEPTABLE_SLOPE;
    }

    /**
     * Generates the complete modular Collasped Shrine at the specified location.
     */
    public static BlockPos generate(ServerWorld world, BlockPos originPos) {
        Random rand = new Random(originPos.asLong() ^ world.getSeed());
        int startX = originPos.getX();
        int startZ = originPos.getZ();

        // 1. Terrain Height Sampling across Surface Footprint (17x23)
        List<Integer> heights = new ArrayList<>();
        int step = 4;
        for (int dx = 0; dx < FOOTPRINT_WIDTH; dx += step) {
            for (int dz = 0; dz < FOOTPRINT_LENGTH; dz += step) {
                int ty = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, startX + dx, startZ + dz);
                heights.add(ty);
            }
        }
        Collections.sort(heights);

        // Use median ground level for solid foundation anchoring
        int baseFloorY = heights.isEmpty() ? originPos.getY() : heights.get(heights.size() / 2);
        if (baseFloorY < 62) baseFloorY = 64; // Safe minimum sea level

        BlockPos surfaceAnchor = new BlockPos(startX, baseFloorY, startZ);
        Elemental.LOGGER.info("Generating Collasped Shrine at {} (base ground Y={})", surfaceAnchor, baseFloorY);

        // 2. Clear canopy foliage above temple footprint
        for (int x = startX; x < startX + FOOTPRINT_WIDTH; x++) {
            for (int z = startZ; z < startZ + FOOTPRINT_LENGTH; z++) {
                for (int y = baseFloorY + 1; y <= baseFloorY + 16; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = world.getBlockState(p);
                    if (NbtStructureManager.isFoliageOrTree(s)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 3. Clear entrance front approach (Z=23..27 in front of the broad stairs)
        for (int x = startX + 5; x <= startX + 11; x++) {
            for (int z = startZ + 23; z <= startZ + 27; z++) {
                for (int y = baseFloorY; y <= baseFloorY + 4; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = world.getBlockState(p);
                    if (NbtStructureManager.isFoliageOrTree(s)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 4. Downward Foundation Pass: Extend stone down to ground so structure never floats
        for (int x = startX; x < startX + FOOTPRINT_WIDTH; x++) {
            for (int z = startZ; z < startZ + FOOTPRINT_LENGTH; z++) {
                int naturalY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (naturalY < baseFloorY) {
                    for (int fy = Math.max(naturalY, baseFloorY - 12); fy < baseFloorY; fy++) {
                        BlockPos fPos = new BlockPos(x, fy, z);
                        if (world.getBlockState(fPos).isAir() || NbtStructureManager.isFoliageOrTree(world.getBlockState(fPos))) {
                            Block b = (rand.nextFloat() < 0.35f) ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS;
                            world.setBlockState(fPos, b.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        // 5. Assemble Procedural Layout
        CollaspedShrineLayoutGenerator.LayoutResult layout =
                CollaspedShrineLayoutGenerator.generateLayout(surfaceAnchor, world.getSeed(), BlockRotation.NONE);

        StructureTemplateManager templateManager = world.getStructureTemplateManager();

        // 6. Place Modular NBT Templates
        List<BlockPos> fakeTraps = new ArrayList<>();
        List<BlockPos> oneHpTraps = new ArrayList<>();
        List<BlockPos> rootBarriers = new ArrayList<>();
        BlockPos sanctumChestPos = null;

        for (CollaspedShrineLayoutGenerator.PlacedPiece piece : layout.getPlacedPieces()) {
            Identifier tid = piece.getTemplate().getTemplateId();
            Optional<StructureTemplate> opt = templateManager.getTemplate(tid);
            if (opt.isEmpty()) {
                Elemental.LOGGER.warn("Missing Collasped Shrine NBT template: {}", tid);
                continue;
            }

            StructurePlacementData placementData = new StructurePlacementData()
                    .setRotation(piece.getRotation())
                    .setMirror(BlockMirror.NONE)
                    .setIgnoreEntities(false);

            StructureTemplate template = opt.get();
            BlockPos placePos = template.offsetByTransformedSize(piece.getOriginPos(), BlockMirror.NONE, piece.getRotation());
            template.place(world, placePos, BlockPos.ORIGIN, placementData, world.getRandom(), Block.NOTIFY_LISTENERS);

            // Track special rooms
            if (piece.getTemplate().getRoomType() == CollaspedShrineRoomType.INNER_SANCTUM) {
                BlockPos transformedChestRel = ShrinePieceTemplate.transformLocalPos(
                        new BlockPos(6, 3, 9), piece.getTemplate().getSize(), piece.getRotation()
                );
                sanctumChestPos = piece.getOriginPos().add(transformedChestRel);
            }
            if (piece.getTemplate().getRoomType() == CollaspedShrineRoomType.TRAP_GALLERY) {
                BlockPos transformedTrapRel = ShrinePieceTemplate.transformLocalPos(
                        new BlockPos(2, 1, 5), piece.getTemplate().getSize(), piece.getRotation()
                );
                fakeTraps.add(piece.getOriginPos().add(transformedTrapRel));
            }
        }

        // 7. Register with JungleDungeonManager
        BlockBox bounds = layout.getTotalBounds();
        BlockPos center = new BlockPos(
                (bounds.getMinX() + bounds.getMaxX()) / 2,
                (bounds.getMinY() + bounds.getMaxY()) / 2,
                (bounds.getMinZ() + bounds.getMaxZ()) / 2
        );
        Box activeBox = new Box(
                bounds.getMinX() - 4, bounds.getMinY() - 4, bounds.getMinZ() - 4,
                bounds.getMaxX() + 4, bounds.getMaxY() + 4, bounds.getMaxZ() + 4
        );

        JungleDungeonManager.registerShrine(
                center, activeBox, fakeTraps, oneHpTraps, rootBarriers, sanctumChestPos
        );

        Elemental.LOGGER.info("Collasped Shrine generated successfully with {} modular pieces across bounds {}",
                layout.getPlacedPieces().size(), bounds);

        // Player spawn position on the surface terrace
        return new BlockPos(startX + 8, baseFloorY + 4, startZ + 14);
    }

    /**
     * Scans outward to locate a Jungle biome.
     */
    public static BlockPos findNearestJungleBiome(ServerWorld world, BlockPos startPos) {
        int maxRadius = 1500;
        int step = 64;

        for (int r = 64; r <= maxRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos testPos = startPos.add(dx, 0, dz);
                    var biomeEntry = world.getBiome(testPos);
                    if (biomeEntry.isIn(BiomeTags.IS_JUNGLE)) {
                        return testPos;
                    }
                }
            }
        }
        return null;
    }
}
