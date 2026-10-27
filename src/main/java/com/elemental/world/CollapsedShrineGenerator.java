package com.elemental.world;

import com.elemental.Elemental;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Procedural generator for the Collapsed Shrine (Tier 1 Jungle Dungeon).
 *
 * Structure Overview:
 * 1. Surface Temple: Mesoamerican / Mayan-style stepped pyramid ziggurat matching reference photo.
 *    - Front elevated causeway approach with wide stone stairs and flanking carved stone pillar monoliths.
 *    - Multi-tiered stepped pyramid (Tier 1 base, Tier 2 mid-terrace, Tier 3 upper sanctuary).
 *    - Front central grand staircase ascending Tier 1 and Tier 2 straight into the temple doorway.
 *    - Weathered stone brick, mossy cobblestone, pilasters, and hanging vines.
 * 2. 4x4 Round Spiral Staircase:
 *    - Descends from inside the surface temple sanctuary down into the subterranean complex.
 *    - Lit with hanging chains and lanterns in the central opening.
 * 3. 5 Distinct Subterranean Rooms (12x12x6 inner dimensions):
 *    - Room 1: Sacred Sunken Fountain Sanctuary (Entry room, prismarine water pool, spore blossom, pillars)
 *    - Room 2: Trap & Pitfall Chasm Gauntlet (Decoy clicker plates, hidden 1-HP spore trap, 30-block death pit with pointed dripstone stalagmites, scaffolding bridge, loot chest)
 *    - Room 3: Overgrown Crypt & Archives (Ancient stone bookshelves, sarcophagi, cobwebs, crypt chest)
 *    - Room 4: Double Spawner Challenge Arena (Cave Spider spawner in web nest + Skeleton spawner on raised stone tower, combat obstacles, challenge chest)
 *    - Room 5: Grand Altar & Rootbound Vault (Raised ceremonial dais with altar chest, sealed vault with ancient impenetrable roots, inscribed lore sign, gold pedestal with sealed chest)
 * 4. Interconnecting Corridors & Stairs:
 *    - 3-wide x 4-high arched corridors with stone stairs bridging elevation changes between rooms.
 *    - Ample lighting everywhere (wall lanterns, hanging lanterns, sea lanterns, spore blossoms, glow lichen).
 */
public class CollapsedShrineGenerator {

    public static final Identifier LOOT_SHRINE = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine");
    public static final Identifier LOOT_SHRINE_SEALED = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine_sealed");

    public static final int ROOM_INNER_SIZE = 12;
    public static final int ROOM_INNER_HEIGHT = 6;

    /**
     * Generates the complete Collapsed Shrine complex (surface temple + subterranean dungeon).
     */
    public static BlockPos generate(ServerWorld world, BlockPos surfaceCenter) {
        Random rand = new Random(surfaceCenter.asLong() ^ world.getSeed());

        int centerX = surfaceCenter.getX();
        int centerZ = surfaceCenter.getZ();
        int cz = centerZ - 10;
        int naturalY = findBaselineGroundY(world, centerX, cz);
        if (naturalY < 62) naturalY = 64;

        // Subterranean floor Y level (deep underground below jungle surface)
        int floorY = naturalY - 18;

        Elemental.LOGGER.info("Generating Collapsed Shrine with 17-block surface temple at Y={} (crown Y={}) and subterranean rooms at Y={}", naturalY, naturalY + 17, floorY);

        List<BlockPos> fakeTraps = new ArrayList<>();
        List<BlockPos> oneHpTraps = new ArrayList<>();
        List<BlockPos> rootBarrierPositions = new ArrayList<>();
        BlockPos sealedChestPos;

        // 1. Build the Surface Stepped Pyramid Temple (17 blocks height above ground, matching reference photo)
        buildSurfaceTemple(world, centerX, naturalY, cz, rand);

        // 2. Build the 4x4 Round Spiral Staircase connecting surface temple to subterranean dungeon
        int stairTopY = naturalY + 9;
        buildRoundStaircase(world, centerX, cz, stairTopY, floorY, rand);

        // 3. Build Subterranean Complex: 5 Distinct 12x12x6 Rooms
        // Room 1: Sacred Sunken Fountain Sanctuary (Entry Room)
        BlockPos r1Origin = new BlockPos(centerX - 6, floorY, centerZ - 6);
        carveAndBuildRoomShell(world, r1Origin, rand);
        buildRoom1Fountain(world, r1Origin, rand);

        // Room 2: Trap & Pitfall Chasm Gauntlet (East)
        BlockPos r2Origin = new BlockPos(centerX + 18, floorY - 1, centerZ - 6);
        carveAndBuildRoomShell(world, r2Origin, rand);
        buildRoom2TrapsAndChasm(world, r2Origin, rand, fakeTraps, oneHpTraps);

        // Room 3: Overgrown Crypt & Archives (West)
        BlockPos r3Origin = new BlockPos(centerX - 30, floorY, centerZ - 6);
        carveAndBuildRoomShell(world, r3Origin, rand);
        buildRoom3Crypt(world, r3Origin, rand);

        // Room 4: Double Spawner Challenge Arena (South-East)
        BlockPos r4Origin = new BlockPos(centerX + 14, floorY - 3, centerZ + 18);
        carveAndBuildRoomShell(world, r4Origin, rand);
        buildRoom4DoubleSpawner(world, r4Origin, rand);

        // Room 5: Grand Altar & Rootbound Vault (North)
        BlockPos r5Origin = new BlockPos(centerX - 6, floorY + 1, centerZ - 28);
        carveAndBuildRoomShell(world, r5Origin, rand);
        sealedChestPos = buildRoom5GrandAltarAndVault(world, r5Origin, rand, rootBarrierPositions);

        // 4. Build Connecting Corridors and Stairs
        buildCorridorsAndStairs(world, centerX, centerZ, floorY, rand);

        // 5. Connect 4x4 staircase bottom into Room 1
        for (int x = centerX - 1; x <= centerX; x++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                world.setBlockState(new BlockPos(x, y, centerZ - 7), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(new BlockPos(x, y, centerZ - 6), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        // Archway framing at entrance to Room 1
        world.setBlockState(new BlockPos(centerX - 2, floorY + 1, centerZ - 7), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(centerX - 2, floorY + 2, centerZ - 7), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(centerX + 1, floorY + 1, centerZ - 7), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(centerX + 1, floorY + 2, centerZ - 7), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        placeWallLantern(world, new BlockPos(centerX - 2, floorY + 3, centerZ - 6), Direction.SOUTH);
        placeWallLantern(world, new BlockPos(centerX + 1, floorY + 3, centerZ - 6), Direction.SOUTH);

        // 6. Register Shrine with JungleDungeonManager
        BlockPos shrineCenter = new BlockPos(centerX, floorY + 3, centerZ);
        Box shrineBounds = new Box(
                centerX - 42, floorY - 38, centerZ - 42,
                centerX + 42, naturalY + 22, centerZ + 36
        );

        JungleDungeonManager.registerShrine(
                shrineCenter, shrineBounds, fakeTraps, oneHpTraps, rootBarrierPositions, sealedChestPos
        );

        return shrineCenter;
    }

    // =========================================================================
    // 1. SURFACE TEMPLE (Matching Reference Photo)
    // =========================================================================

    /**
     * Builds the stepped Mayan-style pyramid temple with front elevated causeway,
     * decorative pillar monoliths, wide stairs, stepped tiers with pilasters,
     * upper sanctuary house, hanging vines, and central grand staircase.
     * Total height above ground is 17 blocks (peak crown slab at naturalY + 17).
     *
     * Adapted with vanilla Jungle Temple mechanics:
     * - Carves foliage/canopy clearance envelope for all tiers, causeway, and crown.
     * - Downward foundation fill (downwards) ensuring the structure never hovers on slopes.
     * - Overgrown exterior hanging vines matching ancient jungle pyramid aesthetics.
     */
    private static void buildSurfaceTemple(ServerWorld world, int cx, int naturalY, int cz, Random rand) {
        // --- 0. Structure Dimensions & Envelope Parameters (17-Block Height) ---
        int t1Y = naturalY + 1;             // Tier 1 Base: Y + 1 to Y + 4
        int t2Y = t1Y + 4;                  // Tier 2 Mid: Y + 5 to Y + 8
        int t3Y = t2Y + 4;                  // Tier 3 Sanctuary: Y + 9 to Y + 13
        int r1Y = t3Y + 5;                  // Roof Layer 1: Y + 14
        int r2Y = r1Y + 1;                  // Roof Layer 2: Y + 15
        int r3Y = r2Y + 1;                  // Roof Cap & Crown: Y + 16 to Y + 17 (Peak height = 17 blocks above ground)

        int causewayZStart = cz + 7;
        int causewayZEnd = cz + 19;
        int frontStairZ = causewayZEnd + 1;
        int causewayWidth = 10;
        int causewayY = naturalY + 2;

        // --- 1. Clearance Envelope (Clearing Jungle Canopy, Leaves, and Logs) ---
        // Causeway clearance: clear air along the walkway and monolith corridor
        for (int x = cx - causewayWidth / 2; x <= cx + causewayWidth / 2; x++) {
            for (int z = causewayZStart; z <= frontStairZ + 3; z++) {
                for (int y = causewayY + 1; y <= causewayY + 5; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Tier 1 terrace clearance (above Tier 1 ledge, outside Tier 2)
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int z = cz - 10; z <= cz + 6; z++) {
                boolean inTier2 = (x >= cx - 8 && x <= cx + 8 && z >= cz - 8 && z <= cz + 4);
                if (!inTier2) {
                    for (int y = t1Y + 4; y <= t1Y + 7; y++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Tier 2 terrace clearance (above Tier 2 ledge, outside Tier 3)
        for (int x = cx - 8; x <= cx + 8; x++) {
            for (int z = cz - 8; z <= cz + 4; z++) {
                boolean inTier3 = (x >= cx - 5 && x <= cx + 5 && z >= cz - 6 && z <= cz + 2);
                if (!inTier3) {
                    for (int y = t2Y + 4; y <= t2Y + 7; y++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Roof & Crown clearance (up to naturalY + 20 for unobstructed sky view)
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 7; z <= cz + 3; z++) {
                for (int y = t3Y + 5; y <= naturalY + 20; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // --- 2. Foundations (Downward Terrain Adaptation like Vanilla Jungle Temple) ---
        // Solid base foundation under pyramid down to solid ground
        for (int x = cx - 11; x <= cx + 11; x++) {
            for (int z = cz - 11; z <= cz + 7; z++) {
                for (int y = naturalY - 4; y <= naturalY; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                fillFoundationDownwards(world, new BlockPos(x, naturalY - 5, z), rand);
            }
        }

        // Foundation and floor for Elevated Causeway
        for (int x = cx - causewayWidth / 2; x <= cx + causewayWidth / 2; x++) {
            for (int z = causewayZStart; z <= causewayZEnd; z++) {
                for (int y = naturalY - 2; y < causewayY; y++) {
                    world.setBlockState(new BlockPos(x, y, z), getAestheticFoundationBlock(rand), Block.NOTIFY_LISTENERS);
                }
                fillFoundationDownwards(world, new BlockPos(x, naturalY - 3, z), rand);
                world.setBlockState(new BlockPos(x, causewayY, z), getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
            }
        }

        // Wide stone stairs at front of causeway leading up from ground
        for (int step = 0; step <= 2; step++) {
            int sy = causewayY - step;
            int sz = frontStairZ + step;
            for (int x = cx - 4; x <= cx + 4; x++) {
                world.setBlockState(new BlockPos(x, sy, sz),
                        Blocks.MOSSY_STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
                for (int y = naturalY - 1; y < sy; y++) {
                    world.setBlockState(new BlockPos(x, y, sz), Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                fillFoundationDownwards(world, new BlockPos(x, naturalY - 2, sz), rand);
            }
        }

        // Pair of Front Pillar Monoliths flanking the entrance stairs
        buildStoneMonolith(world, new BlockPos(cx - 5, causewayY + 1, frontStairZ), 4, rand);
        buildStoneMonolith(world, new BlockPos(cx + 5, causewayY + 1, frontStairZ), 4, rand);

        // Pair of Mid Causeway Pillar Monoliths
        buildStoneMonolith(world, new BlockPos(cx - 5, causewayY + 1, cz + 10), 5, rand);
        buildStoneMonolith(world, new BlockPos(cx + 5, causewayY + 1, cz + 10), 5, rand);

        // --- 3. Stepped Pyramid (Ziggurat Tiers) ---
        // Tier 1: Base Tier (20x18 footprint, height 4 blocks: naturalY + 1 to naturalY + 4)
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int z = cz - 10; z <= cz + 6; z++) {
                for (int y = t1Y; y <= t1Y + 3; y++) {
                    boolean isEdge = (x == cx - 10 || x == cx + 10 || z == cz - 10 || z == cz + 6);
                    if (isEdge) {
                        boolean isPilaster = (Math.abs(x - cx) % 3 == 0 || Math.abs(z - cz) % 3 == 0);
                        BlockState state = isPilaster ? Blocks.CHISELED_STONE_BRICKS.getDefaultState() : getAestheticWallBlock(rand);
                        world.setBlockState(new BlockPos(x, y, z), state, Block.NOTIFY_LISTENERS);

                        // Jungle Temple hanging vines on exterior walls
                        if (x == cx - 10) maybePlaceVines(world, new BlockPos(x, y, z), Direction.WEST, rand);
                        if (x == cx + 10) maybePlaceVines(world, new BlockPos(x, y, z), Direction.EAST, rand);
                        if (z == cz - 10) maybePlaceVines(world, new BlockPos(x, y, z), Direction.NORTH, rand);
                        if (z == cz + 6)  maybePlaceVines(world, new BlockPos(x, y, z), Direction.SOUTH, rand);
                    } else {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Tier 2: Mid Tier (16x14 footprint, height 4 blocks: naturalY + 5 to naturalY + 8)
        for (int x = cx - 8; x <= cx + 8; x++) {
            for (int z = cz - 8; z <= cz + 4; z++) {
                for (int y = t2Y; y <= t2Y + 3; y++) {
                    boolean isEdge = (x == cx - 8 || x == cx + 8 || z == cz - 8 || z == cz + 4);
                    if (isEdge) {
                        boolean isPilaster = (Math.abs(x - cx) % 3 == 0 || Math.abs(z - cz) % 3 == 0);
                        BlockState state = isPilaster ? Blocks.CHISELED_STONE_BRICKS.getDefaultState() : getAestheticWallBlock(rand);
                        world.setBlockState(new BlockPos(x, y, z), state, Block.NOTIFY_LISTENERS);

                        // Jungle Temple hanging vines on exterior walls
                        if (x == cx - 8) maybePlaceVines(world, new BlockPos(x, y, z), Direction.WEST, rand);
                        if (x == cx + 8) maybePlaceVines(world, new BlockPos(x, y, z), Direction.EAST, rand);
                        if (z == cz - 8) maybePlaceVines(world, new BlockPos(x, y, z), Direction.NORTH, rand);
                        if (z == cz + 4) maybePlaceVines(world, new BlockPos(x, y, z), Direction.SOUTH, rand);
                    } else {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Tier 3: Upper Temple Sanctuary Chamber (10x10 footprint, height 5 blocks: naturalY + 9 to naturalY + 13)
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int z = cz - 6; z <= cz + 2; z++) {
                // Sanctuary floor at naturalY + 8
                world.setBlockState(new BlockPos(x, t3Y - 1, z), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

                for (int y = t3Y; y <= t3Y + 4; y++) {
                    boolean isOuterWall = (x == cx - 5 || x == cx + 5 || z == cz - 6 || z == cz + 2);
                    if (isOuterWall) {
                        // Front door opening on South wall (z = cz + 2)
                        boolean isDoor = (z == cz + 2 && (x == cx || x == cx - 1) && y <= t3Y + 2);
                        if (isDoor) {
                            world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(new BlockPos(x, y, z), getAestheticWallBlock(rand), Block.NOTIFY_LISTENERS);

                            // Jungle Temple hanging vines on sanctuary exterior
                            if (x == cx - 5) maybePlaceVines(world, new BlockPos(x, y, z), Direction.WEST, rand);
                            if (x == cx + 5) maybePlaceVines(world, new BlockPos(x, y, z), Direction.EAST, rand);
                            if (z == cz - 6) maybePlaceVines(world, new BlockPos(x, y, z), Direction.NORTH, rand);
                            if (z == cz + 2) maybePlaceVines(world, new BlockPos(x, y, z), Direction.SOUTH, rand);
                        }
                    } else {
                        // Interior sanctuary room
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Doorway lintel and decorative frame
        world.setBlockState(new BlockPos(cx - 2, t3Y + 3, cz + 2), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx + 1, t3Y + 3, cz + 2), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx - 1, t3Y + 3, cz + 2), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx, t3Y + 3, cz + 2), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        placeWallLantern(world, new BlockPos(cx - 2, t3Y + 2, cz + 3), Direction.SOUTH);
        placeWallLantern(world, new BlockPos(cx + 1, t3Y + 2, cz + 3), Direction.SOUTH);

        // --- 4. Stepped Pyramid Roof (Y + 14 to Y + 17) ---
        // Roof Layer 1 (Y = naturalY + 14)
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int z = cz - 5; z <= cz + 1; z++) {
                boolean isEdge = (x == cx - 4 || x == cx + 4 || z == cz - 5 || z == cz + 1);
                world.setBlockState(new BlockPos(x, r1Y, z), isEdge ? Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState() : Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        // Roof Layer 2 (Y = naturalY + 15)
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 4; z <= cz; z++) {
                boolean isEdge = (x == cx - 3 || x == cx + 3 || z == cz - 4 || z == cz);
                world.setBlockState(new BlockPos(x, r2Y, z), isEdge ? Blocks.STONE_BRICK_SLAB.getDefaultState() : Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        // Roof Cap / Crown Ridge (Y = naturalY + 16 to naturalY + 17, peak 17 blocks above naturalY)
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 3; z <= cz - 1; z++) {
                world.setBlockState(new BlockPos(x, r3Y, z), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(new BlockPos(x, r3Y + 1, z), Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Hanging lantern inside the upper sanctuary
        placeHangingLantern(world, new BlockPos(cx, t3Y + 4, cz - 2));

        // --- 5. Central Grand Staircase ---
        // Climbs from causeway (cz + 7, Y = naturalY + 2) straight up Tier 1 and Tier 2 into doorway (cz + 2, Y = t3Y)
        int stairSteps = 6;
        for (int i = 0; i < stairSteps; i++) {
            int sz = cz + 7 - i;
            int sy = causewayY + 1 + i;
            if (sy > t3Y - 1) sy = t3Y - 1;

            // 4-block wide stairs
            for (int x = cx - 2; x <= cx + 1; x++) {
                world.setBlockState(new BlockPos(x, sy, sz),
                        Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
                for (int y = naturalY + 1; y < sy; y++) {
                    world.setBlockState(new BlockPos(x, y, sz), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Clear clearance above steps
                world.setBlockState(new BlockPos(x, sy + 1, sz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(new BlockPos(x, sy + 2, sz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(new BlockPos(x, sy + 3, sz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }

            // Balustrade buttresses on sides of central stairs
            world.setBlockState(new BlockPos(cx - 3, sy + 1, sz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(cx + 2, sy + 1, sz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * Builds a decorative ancient carved stone pillar monolith with flared base and cap.
     */
    private static void buildStoneMonolith(ServerWorld world, BlockPos basePos, int height, Random rand) {
        // Base pedestal (2x2)
        world.setBlockState(basePos, Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(basePos.east(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(basePos.south(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(basePos.east().south(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Shaft
        for (int y = 1; y < height; y++) {
            BlockState b = (rand.nextFloat() < 0.6f) ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            world.setBlockState(basePos.up(y), b, Block.NOTIFY_LISTENERS);
            world.setBlockState(basePos.east().up(y), b, Block.NOTIFY_LISTENERS);
            world.setBlockState(basePos.south().up(y), b, Block.NOTIFY_LISTENERS);
            world.setBlockState(basePos.east().south().up(y), b, Block.NOTIFY_LISTENERS);
        }

        // Capital
        BlockPos cap = basePos.up(height);
        world.setBlockState(cap, Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cap.east(), Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cap.south(), Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cap.east().south(), Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Lantern on top
        placeStandingLantern(world, cap.up());
    }

    // =========================================================================
    // 2. 4x4 ROUND SPIRAL STAIRCASE
    // =========================================================================

    /**
     * Builds a circular/spiral 4x4 staircase descending from inside the surface temple
     * all the way down to the subterranean dungeon floor.
     * Footprint: cx - 2 to cx + 1, cz - 2 to cz + 1 (4x4 blocks).
     */
    private static void buildRoundStaircase(ServerWorld world, int cx, int cz, int topY, int bottomY, Random rand) {
        int minX = cx - 2;
        int minZ = cz - 2;

        // Step coordinates around the 4x4 perimeter (8-step circular sequence):
        // 0: North-Mid-Left  (1, 0)
        // 1: North-Mid-Right (2, 0)
        // 2: East-Mid-Top    (3, 1)
        // 3: East-Mid-Bottom (3, 2)
        // 4: South-Mid-Right (2, 3)
        // 5: South-Mid-Left  (1, 3)
        // 6: West-Mid-Bottom (0, 2)
        // 7: West-Mid-Top    (0, 1)
        int[][] stepCoords = {
                {1, 0}, {2, 0},
                {3, 1}, {3, 2},
                {2, 3}, {1, 3},
                {0, 2}, {0, 1}
        };

        // Enclosing stone shaft walls from top down to bottom
        for (int y = bottomY; y <= topY; y++) {
            for (int dx = -1; dx <= 4; dx++) {
                for (int dz = -1; dz <= 4; dz++) {
                    boolean isOuterWall = (dx == -1 || dx == 4 || dz == -1 || dz == 4);
                    boolean isCorner = (dx == -1 && dz == -1) || (dx == 4 && dz == -1) || (dx == -1 && dz == 4) || (dx == 4 && dz == 4);
                    BlockPos p = new BlockPos(minX + dx, y, minZ + dz);

                    if (isOuterWall && !isCorner) {
                        BlockState wallState = (rand.nextFloat() < 0.5f) ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.CRACKED_STONE_BRICKS.getDefaultState();
                        world.setBlockState(p, wallState, Block.NOTIFY_LISTENERS);
                    } else if (!isOuterWall) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 4 Corner support pillars inside the 4x4 shaft
        for (int y = bottomY; y <= topY; y++) {
            world.setBlockState(new BlockPos(minX, y, minZ), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(minX + 3, y, minZ), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(minX, y, minZ + 3), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(minX + 3, y, minZ + 3), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // Central hanging chain and lanterns in the 2x2 core: (minX+1, minZ+1)
        for (int y = bottomY + 2; y <= topY - 1; y++) {
            world.setBlockState(new BlockPos(minX + 1, y, minZ + 1), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y), Block.NOTIFY_LISTENERS);
            if ((y - bottomY) % 5 == 0) {
                placeStandingLantern(world, new BlockPos(minX + 2, y, minZ + 1));
            }
        }

        // Carve spiral staircase winding down
        int totalHeight = topY - bottomY;
        int totalHalfSteps = totalHeight * 2;

        for (int s = 0; s < totalHalfSteps; s++) {
            int stepIndex = s % 8;
            int[] coord = stepCoords[stepIndex];
            int px = minX + coord[0];
            int pz = minZ + coord[1];

            double currentY = topY - (s * 0.5);
            int blockY = (int) Math.floor(currentY);
            boolean isTopSlab = (currentY - blockY) >= 0.5;

            BlockPos stepPos = new BlockPos(px, blockY, pz);

            // Step block
            BlockState slabState = Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, isTopSlab ? SlabType.TOP : SlabType.BOTTOM);
            world.setBlockState(stepPos, slabState, Block.NOTIFY_LISTENERS);

            // Solid support under bottom slab
            if (!isTopSlab) {
                world.setBlockState(stepPos.down(), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }

            // Headroom clearance
            world.setBlockState(stepPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(stepPos.up(2), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(stepPos.up(3), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    // =========================================================================
    // 3. SUBTERRANEAN ROOMS (12x12x6 Inner Dimensions)
    // =========================================================================

    /**
     * Carves out a 12x12x6 room space and builds surrounding stone brick floor, walls, and ceiling.
     */
    private static void carveAndBuildRoomShell(ServerWorld world, BlockPos origin, Random rand) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        for (int dx = -1; dx <= ROOM_INNER_SIZE; dx++) {
            for (int dz = -1; dz <= ROOM_INNER_SIZE; dz++) {
                int x = ox + dx;
                int z = oz + dz;

                // Solid foundation underneath
                world.setBlockState(new BlockPos(x, oy - 1, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Floor layer
                world.setBlockState(new BlockPos(x, oy, z), getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);

                // Walls and interior
                for (int dy = 1; dy <= ROOM_INNER_HEIGHT; dy++) {
                    int y = oy + dy;
                    BlockPos p = new BlockPos(x, y, z);
                    boolean isWall = (dx == -1 || dx == ROOM_INNER_SIZE || dz == -1 || dz == ROOM_INNER_SIZE);
                    if (isWall) {
                        world.setBlockState(p, getAestheticWallBlock(rand), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }

                // Ceiling layer
                world.setBlockState(new BlockPos(x, oy + ROOM_INNER_HEIGHT + 1, z), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    /**
     * Room 1: Sacred Sunken Fountain Sanctuary (Entry Room)
     * Features:
     * - Sunken circular water fountain with prismarine bricks, water, and lily pads
     * - Center pedestal with Spore Blossom emitting soothing green spore particles
     * - 4 Grand weathered stone pillars with capitals holding hanging lanterns
     * - Lush foliage, flowering azalea, and glowing cave vines
     */
    private static void buildRoom1Fountain(ServerWorld world, BlockPos origin, Random rand) {
        int cx = origin.getX() + 6;
        int cz = origin.getZ() + 6;
        int fy = origin.getY();

        // Sunken Circular Water Basin (radius 3)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int distSq = dx * dx + dz * dz;
                BlockPos p = new BlockPos(cx + dx, fy, cz + dz);
                if (distSq <= 6) {
                    world.setBlockState(p.down(), Blocks.PRISMARINE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (rand.nextFloat() < 0.35f) {
                        world.setBlockState(p.up(), Blocks.LILY_PAD.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                } else if (distSq <= 9) {
                    world.setBlockState(p, Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Center Spore Blossom Pedestal
        BlockPos pedestal = new BlockPos(cx, fy + 1, cz);
        world.setBlockState(pedestal.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(pedestal, Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(pedestal.up(), Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 4 Corner Columns with Hanging Lanterns
        int[][] colOffsets = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] offset : colOffsets) {
            int px = cx + offset[0];
            int pz = cz + offset[1];
            // Base
            world.setBlockState(new BlockPos(px, fy + 1, pz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Shaft
            for (int y = fy + 2; y <= fy + 5; y++) {
                world.setBlockState(new BlockPos(px, y, pz), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            // Capital & Lantern
            world.setBlockState(new BlockPos(px, fy + 6, pz), Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
            placeHangingLantern(world, new BlockPos(px, fy + 4, pz + 1));
        }

        // Lush ground flora
        world.setBlockState(new BlockPos(cx - 4, fy + 1, cz - 1), Blocks.FLOWERING_AZALEA.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx + 4, fy + 1, cz + 1), Blocks.FLOWERING_AZALEA.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx - 2, fy + 1, cz + 4), Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(cx + 2, fy + 1, cz - 4), Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Wall sconce lanterns
        placeWallLantern(world, new BlockPos(origin.getX(), fy + 3, cz), Direction.EAST);
        placeWallLantern(world, new BlockPos(origin.getX() + 11, fy + 3, cz), Direction.WEST);
    }

    /**
     * Room 2: Trap & Pitfall Chasm Gauntlet (East Wing)
     * Features:
     * - Conspicuous stone pressure plates that trigger decoy click sound + smoke (fakeTraps)
     * - Hidden 1-HP spore trap carpet + dispenser with Strong Poison arrows (oneHpTraps)
     * - Lethal 30-block abyssal pitfall chasm with pointed dripstone stalagmites
     * - Fragile scaffolding bridge with moss carpet across the chasm
     * - Treasure chest perched across the fragile bridge on a chiseled pedestal (LOOT_SHRINE)
     * - Ample lighting with wall lanterns and inset floor shroomlights under iron trapdoors
     */
    private static void buildRoom2TrapsAndChasm(ServerWorld world, BlockPos origin, Random rand,
                                                List<BlockPos> fakeTraps, List<BlockPos> oneHpTraps) {
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        // 1. Fake / Decoy Trap Pressure Plates (clicking harmless traps)
        BlockPos fake1 = new BlockPos(ox + 3, fy + 1, oz + 3);
        world.setBlockState(fake1.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(fake1, Blocks.STONE_PRESSURE_PLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(fake1);

        BlockPos fake2 = new BlockPos(ox + 3, fy + 1, oz + 8);
        world.setBlockState(fake2.down(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(fake2, Blocks.STONE_PRESSURE_PLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(fake2);

        // 2. Brutal 1-HP Spore Trap hidden under moss carpet
        BlockPos oneHp = new BlockPos(ox + 5, fy + 1, oz + 5);
        world.setBlockState(oneHp.down(), Blocks.MUD_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(oneHp, Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        oneHpTraps.add(oneHp);

        // Hidden wall dispenser loaded with Strong Poison arrows
        BlockPos dispenserPos = new BlockPos(ox + 5, fy + 2, oz - 1);
        world.setBlockState(dispenserPos, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(dispenserPos) instanceof DispenserBlockEntity dispenser) {
            ItemStack poisonArrow = new ItemStack(Items.TIPPED_ARROW, 8);
            PotionUtil.setPotion(poisonArrow, Potions.STRONG_POISON);
            dispenser.setStack(4, poisonArrow);
        }

        // 3. Lethal Pitfall Chasm (30 blocks deep with pointed dripstone stalagmites)
        // Positioned at X = ox + 7 to ox + 10, Z = oz + 2 to oz + 9
        int pitDepth = 30;
        int bottomY = Math.max(-58, fy - pitDepth);

        for (int x = ox + 7; x <= ox + 10; x++) {
            for (int z = oz + 2; z <= oz + 9; z++) {
                // Hollow out the pit
                for (int y = bottomY; y <= fy; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Deepslate bedrock base
                world.setBlockState(new BlockPos(x, bottomY - 1, z), Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Pointed dripstone stalagmites at the bottom
                world.setBlockState(new BlockPos(x, bottomY, z),
                        Blocks.POINTED_DRIPSTONE.getDefaultState()
                                .with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
                                .with(PointedDripstoneBlock.THICKNESS, Thickness.TIP_MERGE),
                        Block.NOTIFY_LISTENERS);
            }
        }

        // Fragile walkway bridge across the chasm (Scaffolding with Moss Carpet)
        int bridgeZ = oz + 6;
        for (int x = ox + 7; x <= ox + 9; x++) {
            BlockPos bPos = new BlockPos(x, fy, bridgeZ);
            world.setBlockState(bPos, Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(bPos.up(), Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // Treasure chest on a raised stone pedestal across the chasm
        BlockPos chestPos = new BlockPos(ox + 10, fy + 1, bridgeZ);
        world.setBlockState(chestPos.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }

        // 4. Bright and Atmospheric Lighting
        placeWallLantern(world, new BlockPos(ox + 1, fy + 3, oz + 2), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox + 1, fy + 3, oz + 9), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 1), Direction.WEST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 10), Direction.WEST);

        // Hanging chains and lanterns over the chasm
        placeHangingLantern(world, new BlockPos(ox + 8, fy + 5, oz + 4));
        placeHangingLantern(world, new BlockPos(ox + 8, fy + 5, oz + 8));
    }

    /**
     * Room 3: Overgrown Crypt & Archives (West Wing)
     * Features:
     * - Ancient stone bookshelves, mud bricks, and cobwebs
     * - Two stone sarcophagi / tombs in the center with chiseled stone details
     * - Secondary exploration treasure chest (LOOT_SHRINE)
     * - Warm illumination with copper lanterns, wall sconces, and sea lanterns
     */
    private static void buildRoom3Crypt(ServerWorld world, BlockPos origin, Random rand) {
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        // North and South Wall Bookshelves and Cobwebs
        for (int x = ox + 2; x <= ox + 9; x += 2) {
            // North wall alcoves
            world.setBlockState(new BlockPos(x, fy + 1, oz), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, fy + 2, oz), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, fy + 3, oz), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);

            // South wall alcoves
            world.setBlockState(new BlockPos(x, fy + 1, oz + 11), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, fy + 2, oz + 11), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, fy + 3, oz + 11), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // Two Ancient Sarcophagi / Stone Tombs in the center
        // Tomb 1 (North-Center)
        buildSarcophagus(world, new BlockPos(ox + 4, fy + 1, oz + 4));
        // Tomb 2 (South-Center)
        buildSarcophagus(world, new BlockPos(ox + 4, fy + 1, oz + 7));

        // Crypt exploration chest on a mud-brick altar
        BlockPos cryptChestPos = new BlockPos(ox + 8, fy + 1, oz + 6);
        world.setBlockState(cryptChestPos.down(), Blocks.MUD_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cryptChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(cryptChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }

        // Lighting: Copper lanterns and wall lanterns
        placeWallLantern(world, new BlockPos(ox, fy + 3, oz + 3), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox, fy + 3, oz + 8), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 3), Direction.WEST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 8), Direction.WEST);
        placeHangingLantern(world, new BlockPos(ox + 5, fy + 5, oz + 4));
        placeHangingLantern(world, new BlockPos(ox + 5, fy + 5, oz + 7));
    }

    private static void buildSarcophagus(ServerWorld world, BlockPos basePos) {
        for (int dx = 0; dx <= 2; dx++) {
            BlockPos p = basePos.add(dx, 0, 0);
            world.setBlockState(p, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(p.up(), Blocks.POLISHED_DEEPSLATE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        world.setBlockState(basePos.add(0, 1, 0), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    /**
     * Room 4: Double Spawner Challenge Arena (South-East Wing)
     * Features:
     * - Recessed underground battle arena floor
     * - Two DIFFERENT mob spawners:
     *   1. Cave Spider Spawner nested in a sunken webbed alcove
     *   2. Skeleton Spawner atop a raised stone brick watchtower platform
     * - Combat cover pillars, iron bars, and obstacle terrain
     * - Challenge reward chest guarded by the spawners (LOOT_SHRINE)
     * - Well lit with wall lanterns and hanging soul lanterns
     */
    private static void buildRoom4DoubleSpawner(ServerWorld world, BlockPos origin, Random rand) {
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        // 1. Spawner 1: Cave Spider Spawner in a webbed nest (North-West corner)
        BlockPos spiderSpawnerPos = new BlockPos(ox + 2, fy + 1, oz + 2);
        world.setBlockState(spiderSpawnerPos.down(), Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(spiderSpawnerPos, Blocks.SPAWNER.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(spiderSpawnerPos) instanceof MobSpawnerBlockEntity spawner) {
            spawner.getLogic().setEntityId(EntityType.CAVE_SPIDER, world, world.getRandom(), spiderSpawnerPos);
        }
        // Cobweb nest surrounding spider spawner
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos webPos = spiderSpawnerPos.offset(dir);
            if (world.getBlockState(webPos).isAir()) {
                world.setBlockState(webPos, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        world.setBlockState(spiderSpawnerPos.up(), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 2. Spawner 2: Skeleton Spawner on a raised watchtower platform (South-East corner)
        // 3x3 Stone Watchtower Platform
        for (int dx = 7; dx <= 9; dx++) {
            for (int dz = 7; dz <= 9; dz++) {
                world.setBlockState(new BlockPos(ox + dx, fy + 1, oz + dz), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        BlockPos skeletonSpawnerPos = new BlockPos(ox + 8, fy + 2, oz + 8);
        world.setBlockState(skeletonSpawnerPos, Blocks.SPAWNER.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(skeletonSpawnerPos) instanceof MobSpawnerBlockEntity spawner) {
            spawner.getLogic().setEntityId(EntityType.SKELETON, world, world.getRandom(), skeletonSpawnerPos);
        }
        // Iron bars and partial battlements around watchtower
        world.setBlockState(new BlockPos(ox + 7, fy + 2, oz + 7), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 9, fy + 2, oz + 7), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 7, fy + 2, oz + 9), Blocks.IRON_BARS.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 3. Combat Obstacle Cover Pillars in the center of the arena
        world.setBlockState(new BlockPos(ox + 4, fy + 1, oz + 6), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 4, fy + 2, oz + 6), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 4, fy + 3, oz + 6), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

        world.setBlockState(new BlockPos(ox + 7, fy + 1, oz + 4), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 7, fy + 2, oz + 4), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 4. Challenge Reward Chest at the back on an altar dais
        BlockPos rewardChestPos = new BlockPos(ox + 2, fy + 1, oz + 9);
        world.setBlockState(rewardChestPos.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(rewardChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(rewardChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }

        // 5. Plentiful Lighting so the player has clear visibility in combat
        placeWallLantern(world, new BlockPos(ox + 1, fy + 3, oz + 5), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 5), Direction.WEST);
        placeWallLantern(world, new BlockPos(ox + 5, fy + 3, oz + 1), Direction.SOUTH);
        placeWallLantern(world, new BlockPos(ox + 5, fy + 3, oz + 10), Direction.NORTH);

        placeHangingSoulLantern(world, new BlockPos(ox + 4, fy + 5, oz + 4));
        placeHangingSoulLantern(world, new BlockPos(ox + 8, fy + 5, oz + 4));
    }

    /**
     * Room 5: Grand Altar & Rootbound Vault Sanctuary (North Wing)
     * Features:
     * - Grand elevated ceremonial dais with polished deepslate steps
     * - Primary shrine offering chest (LOOT_SHRINE)
     * - Secret 6x6 Rootbound Vault chamber sealed by dense ancient roots (rootBarrierPositions)
     * - Ancient inscribed sign with Rootbound Axe lore
     * - Golden pedestal with Sealed Offering Chest (LOOT_SHRINE_SEALED)
     * - Radiant sea lanterns and spore blossoms
     */
    private static BlockPos buildRoom5GrandAltarAndVault(ServerWorld world, BlockPos origin, Random rand,
                                                        List<BlockPos> rootBarrierPositions) {
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        // 1. Raised Ceremonial Altar Dais in center of room
        for (int dx = 3; dx <= 8; dx++) {
            for (int dz = 4; dz <= 8; dz++) {
                world.setBlockState(new BlockPos(ox + dx, fy + 1, oz + dz), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (int dx = 4; dx <= 7; dx++) {
            for (int dz = 5; dz <= 7; dz++) {
                world.setBlockState(new BlockPos(ox + dx, fy + 2, oz + dz), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Mossy stone stairs leading up to dais
        for (int dx = 4; dx <= 7; dx++) {
            world.setBlockState(new BlockPos(ox + dx, fy + 1, oz + 9),
                    Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(ox + dx, fy + 2, oz + 8),
                    Blocks.POLISHED_DEEPSLATE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
        }

        // Primary Altar Chest
        BlockPos altarChestPos = new BlockPos(ox + 5, fy + 3, oz + 6);
        world.setBlockState(altarChestPos.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(altarChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(altarChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }

        // 2. Secret Rootbound Vault Chamber (North extension behind wall)
        // Footprint: X = ox + 3 to ox + 8, Z = oz - 6 to oz - 1
        int vaultZ = oz - 4;
        BlockPos sealedChestPos = new BlockPos(ox + 5, fy + 1, vaultZ);

        for (int vx = ox + 2; vx <= ox + 9; vx++) {
            for (int vz = oz - 6; vz <= oz - 1; vz++) {
                // Foundation and floor
                world.setBlockState(new BlockPos(vx, fy - 1, vz), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(new BlockPos(vx, fy, vz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Clear interior
                for (int vy = fy + 1; vy <= fy + 5; vy++) {
                    boolean isVaultWall = (vx == ox + 2 || vx == ox + 9 || vz == oz - 6);
                    if (isVaultWall) {
                        world.setBlockState(new BlockPos(vx, vy, vz), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(new BlockPos(vx, vy, vz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
                // Vault ceiling
                world.setBlockState(new BlockPos(vx, fy + 6, vz), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Place the Root-Sealed Offering Chest on a Gold Block pedestal
        world.setBlockState(sealedChestPos.down(), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sealedChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(sealedChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE_SEALED, rand.nextLong());
        }

        // Vault ambient lighting
        world.setBlockState(new BlockPos(ox + 5, fy + 5, vaultZ), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 3. Dense Impenetrable Root Barrier sealing vault entrance (3 wide, 3 tall)
        for (int rx = ox + 4; rx <= ox + 7; rx++) {
            for (int ry = fy + 1; ry <= fy + 3; ry++) {
                BlockPos rootPos = new BlockPos(rx, ry, oz);
                BlockState rootState = (ry == fy + 3) ? Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState() :
                        (rand.nextBoolean() ? Blocks.MANGROVE_ROOTS.getDefaultState() : Blocks.WEEPING_VINES.getDefaultState());
                world.setBlockState(rootPos, rootState, Block.NOTIFY_LISTENERS);
                rootBarrierPositions.add(rootPos);
            }
        }

        // Ancient Inscribed Lore Sign in front of the root barrier
        BlockPos signPos = new BlockPos(ox + 8, fy + 1, oz + 1);
        world.setBlockState(signPos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText signText = sign.getText(true)
                    .withMessage(0, Text.literal("§2[ANCIENT VAULT]").formatted(Formatting.DARK_GREEN))
                    .withMessage(1, Text.literal("§7Bound by Roots").formatted(Formatting.GRAY))
                    .withMessage(2, Text.literal("§aRootbound Axe").formatted(Formatting.GREEN))
                    .withMessage(3, Text.literal("§7severs the seal").formatted(Formatting.DARK_GRAY));
            sign.setText(signText, true);
            sign.markDirty();
        }

        // 4. Radiant Illumination
        world.setBlockState(new BlockPos(ox + 5, fy + 6, oz + 6), Blocks.SEA_LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ox + 5, fy + 5, oz + 6), Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
        placeWallLantern(world, new BlockPos(ox + 1, fy + 3, oz + 6), Direction.EAST);
        placeWallLantern(world, new BlockPos(ox + 10, fy + 3, oz + 6), Direction.WEST);

        return sealedChestPos;
    }

    // =========================================================================
    // 4. CORRIDORS & STAIRS
    // =========================================================================

    /**
     * Builds interconnecting 3-wide x 4-high corridors and stairs between the 5 rooms.
     */
    private static void buildCorridorsAndStairs(ServerWorld world, int cx, int cz, int floorY, Random rand) {
        // --- Corridor 1 -> 2: From Room 1 (Fountain) to Room 2 (Traps & Chasm) [East] ---
        int c12StartX = cx + 6;
        int c12EndX = cx + 18;
        int c12Z = cz;
        for (int x = c12StartX; x <= c12EndX; x++) {
            int yFloor = (x >= cx + 16) ? floorY - 1 : floorY;
            buildCorridorSliceX(world, x, yFloor, c12Z, rand);

            // Step down stair
            if (x == cx + 16) {
                for (int z = c12Z - 1; z <= c12Z + 1; z++) {
                    world.setBlockState(new BlockPos(x, floorY, z),
                            Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
                }
            }
            // Lanterns every 4 blocks
            if ((x - c12StartX) % 4 == 0) {
                placeWallLantern(world, new BlockPos(x, yFloor + 3, c12Z - 1), Direction.SOUTH);
            }
        }

        // --- Corridor 1 -> 3: From Room 1 (Fountain) to Room 3 (Crypt) [West] ---
        int c13StartX = cx - 18;
        int c13EndX = cx - 7;
        int c13Z = cz;
        for (int x = c13StartX; x <= c13EndX; x++) {
            buildCorridorSliceX(world, x, floorY, c13Z, rand);
            if ((x - c13StartX) % 4 == 0) {
                placeWallLantern(world, new BlockPos(x, floorY + 3, c13Z + 1), Direction.NORTH);
            }
        }

        // --- Corridor 1 -> 5: From Room 1 (Fountain) to Room 5 (Grand Altar) [North] ---
        // Runs along X = cx + 3, flanking the staircase, ascending from floorY up to floorY + 1
        int c15ZStart = cz - 17;
        int c15ZEnd = cz - 7;
        int c15X = cx + 3;
        for (int z = c15ZStart; z <= c15ZEnd; z++) {
            int yFloor = (z <= cz - 12) ? floorY + 1 : floorY;
            buildCorridorSliceZ(world, c15X, yFloor, z, rand);

            // Step up stair
            if (z == cz - 12) {
                for (int x = c15X - 1; x <= c15X + 1; x++) {
                    world.setBlockState(new BlockPos(x, floorY + 1, z),
                            Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
                }
            }
            if ((z - c15ZStart) % 4 == 0) {
                placeWallLantern(world, new BlockPos(c15X + 1, yFloor + 3, z), Direction.WEST);
            }
        }
        // Connect Room 5 entrance doorway
        for (int x = cx - 1; x <= cx + 4; x++) {
            for (int y = floorY + 2; y <= floorY + 4; y++) {
                world.setBlockState(new BlockPos(x, y, cz - 17), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // --- Corridor 2 -> 4: From Room 2 (Traps) to Room 4 (Double Spawner) [South] ---
        // Runs from z = cz + 6 to cz + 17 at X = cx + 20, descending from floorY - 1 to floorY - 3
        int c24ZStart = cz + 6;
        int c24ZEnd = cz + 18;
        int c24X = cx + 20;
        for (int z = c24ZStart; z <= c24ZEnd; z++) {
            int yFloor = (z >= cz + 13) ? floorY - 3 : (z >= cz + 9 ? floorY - 2 : floorY - 1);
            buildCorridorSliceZ(world, c24X, yFloor, z, rand);

            // Descending stairs
            if (z == cz + 9) {
                for (int x = c24X - 1; x <= c24X + 1; x++) {
                    world.setBlockState(new BlockPos(x, floorY - 1, z),
                            Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
                }
            } else if (z == cz + 13) {
                for (int x = c24X - 1; x <= c24X + 1; x++) {
                    world.setBlockState(new BlockPos(x, floorY - 2, z),
                            Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
                }
            }

            if ((z - c24ZStart) % 4 == 0) {
                placeWallLantern(world, new BlockPos(c24X - 1, yFloor + 3, z), Direction.EAST);
            }
        }

        // --- Corridor 1 -> 4: Secondary connection from Room 1 South wall to Room 4 ---
        // Runs from z = cz + 6 to cz + 14 at X = cx, then turns East into Room 4
        for (int z = cz + 6; z <= cz + 14; z++) {
            buildCorridorSliceZ(world, cx, floorY, z, rand);
        }
        for (int x = cx; x <= cx + 14; x++) {
            int yFloor = (x >= cx + 7) ? floorY - 3 : floorY - 1;
            buildCorridorSliceX(world, x, yFloor, cz + 14, rand);
            if (x == cx + 7) {
                for (int z = cz + 13; z <= cz + 15; z++) {
                    world.setBlockState(new BlockPos(x, floorY - 1, z),
                            Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
                }
            }
        }
        // Open doorway into Room 4
        for (int y = floorY - 2; y <= floorY; y++) {
            for (int z = cz + 20; z <= cz + 22; z++) {
                world.setBlockState(new BlockPos(cx + 14, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void buildCorridorSliceX(ServerWorld world, int x, int yFloor, int centerZ, Random rand) {
        for (int z = centerZ - 2; z <= centerZ + 2; z++) {
            // Foundation
            world.setBlockState(new BlockPos(x, yFloor - 1, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Floor
            world.setBlockState(new BlockPos(x, yFloor, z), getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);

            for (int dy = 1; dy <= 4; dy++) {
                int y = yFloor + dy;
                BlockPos p = new BlockPos(x, y, z);
                boolean isWall = (z == centerZ - 2 || z == centerZ + 2);
                if (isWall) {
                    world.setBlockState(p, getAestheticWallBlock(rand), Block.NOTIFY_LISTENERS);
                } else {
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
            // Ceiling
            world.setBlockState(new BlockPos(x, yFloor + 5, z), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private static void buildCorridorSliceZ(ServerWorld world, int centerX, int yFloor, int z, Random rand) {
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            // Foundation
            world.setBlockState(new BlockPos(x, yFloor - 1, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Floor
            world.setBlockState(new BlockPos(x, yFloor, z), getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);

            for (int dy = 1; dy <= 4; dy++) {
                int y = yFloor + dy;
                BlockPos p = new BlockPos(x, y, z);
                boolean isWall = (x == centerX - 2 || x == centerX + 2);
                if (isWall) {
                    world.setBlockState(p, getAestheticWallBlock(rand), Block.NOTIFY_LISTENERS);
                } else {
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
            // Ceiling
            world.setBlockState(new BlockPos(x, yFloor + 5, z), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    // =========================================================================
    // 5. LIGHTING & BLOCK PALETTE UTILITIES
    // =========================================================================

    private static void placeWallLantern(ServerWorld world, BlockPos pos, Direction facing) {
        world.setBlockState(pos, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), Block.NOTIFY_LISTENERS);
    }

    private static void placeStandingLantern(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), Block.NOTIFY_LISTENERS);
    }

    private static void placeHangingLantern(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos.up(), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y), Block.NOTIFY_LISTENERS);
        world.setBlockState(pos, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
    }

    private static void placeHangingSoulLantern(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos.up(), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y), Block.NOTIFY_LISTENERS);
        world.setBlockState(pos, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
    }

    private static BlockState getAestheticFloorBlock(Random rand) {
        float f = rand.nextFloat();
        if (f < 0.40f) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (f < 0.65f) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if (f < 0.85f) return Blocks.MUD_BRICKS.getDefaultState();
        return Blocks.COARSE_DIRT.getDefaultState();
    }

    private static BlockState getAestheticWallBlock(Random rand) {
        float f = rand.nextFloat();
        if (f < 0.50f) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (f < 0.75f) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        if (f < 0.90f) return Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        return Blocks.COBBLESTONE.getDefaultState();
    }

    private static BlockState getAestheticFoundationBlock(Random rand) {
        float f = rand.nextFloat();
        if (f < 0.50f) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if (f < 0.80f) return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.DEEPSLATE_BRICKS.getDefaultState();
    }

    // =========================================================================
    // 6. JUNGLE TEMPLE TERRAIN ADAPTATION & FLORA UTILITIES
    // =========================================================================

    /**
     * Determines the optimal baseline ground Y level for the 17-block surface temple
     * across the temple's footprint, sampling beneath jungle tree canopies to true ground
     * (matching vanilla Jungle Temple / ShiftableStructurePiece placement logic).
     */
    private static int findBaselineGroundY(ServerWorld world, int cx, int cz) {
        int minZ = cz - 11;
        int maxZ = cz + 20; // covers pyramid, causeway, and front stairs
        int minX = cx - 11;
        int maxX = cx + 11;

        int totalY = 0;
        int samples = 0;
        int minFound = Integer.MAX_VALUE;

        // Sample in a grid across the structure footprint
        for (int x = minX; x <= maxX; x += 5) {
            for (int z = minZ; z <= maxZ; z += 6) {
                int groundY = findTrueGroundY(world, x, z);
                if (groundY > world.getBottomY()) {
                    totalY += groundY;
                    samples++;
                    if (groundY < minFound) minFound = groundY;
                }
            }
        }

        if (samples == 0) {
            int fallback = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            return Math.max(fallback, 64);
        }

        int avgY = totalY / samples;
        // Settle comfortably into terrain (similar to vanilla ShiftableStructurePiece baseline)
        int chosenY = Math.min(avgY, minFound + 3);
        return Math.max(chosenY, 64);
    }

    private static int findTrueGroundY(ServerWorld world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, topY, z);
        while (pos.getY() > world.getBottomY()) {
            BlockState state = world.getBlockState(pos);
            if (isFoliageOrAir(state)) {
                pos.move(Direction.DOWN);
            } else {
                return pos.getY();
            }
        }
        return world.getSeaLevel();
    }

    private static void fillFoundationDownwards(ServerWorld world, BlockPos startPos, Random rand) {
        BlockPos.Mutable mut = startPos.mutableCopy();
        int maxDepth = 20;
        int depth = 0;
        while (depth < maxDepth && mut.getY() > world.getBottomY()) {
            BlockState current = world.getBlockState(mut);
            if (isSolidTerrain(world, mut, current)) {
                break;
            }
            world.setBlockState(mut, getAestheticFoundationBlock(rand), Block.NOTIFY_LISTENERS);
            mut.move(Direction.DOWN);
            depth++;
        }
    }

    private static boolean isSolidTerrain(ServerWorld world, BlockPos pos, BlockState state) {
        if (isFoliageOrAir(state)) return false;
        if (!state.getFluidState().isEmpty() || state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return false;
        return state.isSolidBlock(world, pos);
    }

    private static boolean isFoliageOrAir(BlockState state) {
        if (state.isAir()) return true;
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) return true;
        if (state.isOf(Blocks.VINE) || state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING)) return true;
        if (state.isOf(Blocks.COCOA) || state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.MOSS_BLOCK)) return true;
        if (state.isOf(Blocks.HANGING_ROOTS) || state.isOf(Blocks.MANGROVE_ROOTS)) return true;
        if (state.isIn(BlockTags.FLOWERS) || state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.FERN) || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.SUGAR_CANE)) return true;
        return false;
    }

    private static void maybePlaceVines(ServerWorld world, BlockPos wallPos, Direction face, Random rand) {
        if (rand.nextFloat() > 0.30f) return;
        BlockPos vinePos = wallPos.offset(face);
        BooleanProperty prop = getVineFacingProperty(face.getOpposite());
        if (prop == null) return;
        BlockState vineState = Blocks.VINE.getDefaultState().with(prop, true);
        if (world.getBlockState(vinePos).isAir()) {
            world.setBlockState(vinePos, vineState, Block.NOTIFY_LISTENERS);
            int length = rand.nextInt(3);
            BlockPos.Mutable downPos = vinePos.mutableCopy();
            for (int i = 0; i < length; i++) {
                downPos.move(Direction.DOWN);
                if (world.getBlockState(downPos).isAir()) {
                    world.setBlockState(downPos, vineState, Block.NOTIFY_LISTENERS);
                } else {
                    break;
                }
            }
        }
    }

    private static BooleanProperty getVineFacingProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case WEST -> VineBlock.WEST;
            case EAST -> VineBlock.EAST;
            case UP -> VineBlock.UP;
            default -> null;
        };
    }
}
