package com.elemental.world;

import com.elemental.Elemental;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.Thickness;
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
 * Recreated to match the underground cavern ruins reference images:
 *
 * 1. Massive Vaulted Underground Cavern:
 *    - Colossal organic cave cathedral (~50x54 blocks, 30+ blocks high).
 *    - Vaulted rock ceiling with natural light fissures / skylights streaming sunlight.
 *    - Natural rock, andesite, deepslate, moss, and rooted dirt walls.
 *
 * 2. Authentic Atmospheric Lighting:
 *    - Cave Vines with glowing orange Glow Berries (light level 14) hanging in long clusters.
 *    - Underwater Sea Pickles (light level 12) giving the river a cyan/teal underwater glow.
 *    - Mounted Wall Torches on stone pillars, grand staircases, and corridors.
 *    - Hanging Lanterns suspended from Chains under stone arches and the skybridge.
 *    - Redstone torches and redstone dust trails illuminating treasure nooks.
 *    - Spore Blossoms dripping ambient particles into the cavern.
 *
 * 3. Central Sunken River & Ruined Bridges:
 *    - Sunken river canyon 5 blocks below terrace floor with water lilies, dripleaf, and waterfalls.
 *    - Ruined stone arch bridges spanning the gorge with missing blocks and hanging roots.
 *
 * 4. Left Sector: Sacred Altar & Dais Terrace:
 *    - Monumental stepped stone throne / altar (stone brick stairs, chiseled pillars, canopy arch, moss, altar chest).
 *    - Golden Vault Alcove set into the cavern wall with solid Gold Blocks and sealed chest.
 *    - Pointed Dripstone Spike Pit Trap (3x4 stalagmites pit, trapdoors, skeleton skull on rim).
 *
 * 5. Right Sector: Multi-Tier Fortress / Ruin:
 *    - Tier 1: Riverbank arches, treasure stash with gold blocks, chests, redstone dust trails, grand stone staircase.
 *    - Tier 2: Mezzanine with continuous Iron Bar railing overlooking the gorge, tripwire hallway, ancient library archives.
 *    - Tier 3: Upper gallery, high wooden skybridge spanning high across the gorge with hanging roots & glow vines.
 *    - Tier 4: High overlook balconies and tunnel mouths.
 */
public class CollapsedShrineGenerator {

    public static final Identifier LOOT_SHRINE = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine");
    public static final Identifier LOOT_SHRINE_SEALED = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine_sealed");

    // Cavern horizontal footprint
    public static final int CAVERN_RADIUS_X = 22; // 45 wide (-22 to +22)
    public static final int CAVERN_RADIUS_Z = 24; // 49 long (-24 to +24)

    /**
     * Generates the complete Collapsed Shrine Cavern Dungeon.
     */
    public static BlockPos generate(ServerWorld world, BlockPos originPos) {
        Random rand = new Random(originPos.asLong() ^ world.getSeed());

        int cx = originPos.getX();
        int cz = originPos.getZ();

        // Find baseline ground Y level
        int naturalY = originPos.getY();
        if (naturalY > 60) {
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            if (topY > 60) naturalY = topY;
        }
        if (naturalY < 64) naturalY = 68;

        // Subterranean elevation layout
        int riverBedY = naturalY - 26;
        if (riverBedY < world.getBottomY() + 10) {
            riverBedY = world.getBottomY() + 14;
            naturalY = riverBedY + 26;
        }

        int floorY = riverBedY + 5;     // Main terrace / Left altar floor / Right Lower floor
        int tier2Y = riverBedY + 11;    // Mezzanine / Library / Tripwire corridor
        int tier3Y = riverBedY + 17;    // Upper gallery / Wooden Skybridge
        int tier4Y = riverBedY + 23;    // High overlook balconies
        int ceilingY = riverBedY + 28;  // Vaulted cavern roof

        Elemental.LOGGER.info("Generating Collapsed Shrine Cavern at cx={}, cz={}, riverBedY={}, floorY={}, ceilingY={}",
                cx, cz, riverBedY, floorY, ceilingY);

        List<BlockPos> fakeTraps = new ArrayList<>();
        List<BlockPos> oneHpTraps = new ArrayList<>();
        List<BlockPos> rootBarrierPositions = new ArrayList<>();

        // 1. Carve the Grand Vaulted Cavern Envelope with organic walls and ceiling fissures
        carveGrandCavern(world, cx, cz, riverBedY, ceilingY, naturalY, rand);

        // 2. Central Sunken River & Gorge (water, sea pickles, lily pads, dripleaf, waterfalls)
        buildSunkenRiverAndGorge(world, cx, cz, riverBedY, floorY, rand);

        // 3. Ruined Stone Bridges spanning across the gorge
        buildRuinedBridges(world, cx, cz, riverBedY, floorY, rand);

        // 4. Left Sector: Sacred Altar & Dais Terrace
        BlockPos sealedChestPos = buildLeftAltarTerrace(world, cx, cz, riverBedY, floorY, rand, fakeTraps, oneHpTraps, rootBarrierPositions);

        // 5. Right Sector: Multi-Tier Fortress / Ruin (Tiers 1-4, Grand Stairs, Library, Skybridge)
        buildRightMultiLevelFortress(world, cx, cz, riverBedY, floorY, tier2Y, tier3Y, tier4Y, rand, fakeTraps);

        // 6. Surface Entrance & Descending Overgrown Stairs
        BlockPos playerSpawnPos = buildSurfaceEntranceAndStairs(world, cx, cz, naturalY, floorY, rand);

        // 7. Atmospheric Lighting & Flora (Cave Vines with Glow Berries, Lanterns, Torches, Spore Blossoms)
        populateAtmosphericLightingAndFlora(world, cx, cz, riverBedY, floorY, tier2Y, tier3Y, ceilingY, rand);

        // 8. Register Shrine with JungleDungeonManager
        BlockPos shrineCenter = new BlockPos(cx, floorY + 1, cz);
        Box shrineBounds = new Box(
                cx - CAVERN_RADIUS_X - 6, riverBedY - 6, cz - CAVERN_RADIUS_Z - 6,
                cx + CAVERN_RADIUS_X + 6, naturalY + 10, cz + CAVERN_RADIUS_Z + 6
        );

        JungleDungeonManager.registerShrine(
                shrineCenter, shrineBounds, fakeTraps, oneHpTraps, rootBarrierPositions, sealedChestPos
        );

        return playerSpawnPos;
    }

    // =========================================================================
    // 1. CAVERN ENVELOPE & CARVING
    // =========================================================================

    /**
     * Carves a massive, natural vaulted cavern cathedral into the rock.
     */
    private static void carveGrandCavern(ServerWorld world, int cx, int cz, int riverBedY, int ceilingY, int naturalY, Random rand) {
        int rx = CAVERN_RADIUS_X;
        int rz = CAVERN_RADIUS_Z;
        int height = ceilingY - riverBedY;

        for (int dx = -rx - 3; dx <= rx + 3; dx++) {
            for (int dz = -rz - 3; dz <= rz + 3; dz++) {
                double normX = (double) dx / rx;
                double normZ = (double) dz / rz;
                double horizontalDistSq = normX * normX + normZ * normZ;

                if (horizontalDistSq > 1.25) continue; // outside cavern perimeter

                for (int y = riverBedY - 4; y <= ceilingY + 3; y++) {
                    double normY = (double) (y - riverBedY) / height;
                    // Vaulted dome curve: cavern gets narrower towards ceiling and floor
                    double maxHorizDist = 1.0 - 0.28 * Math.pow(normY - 0.45, 2);

                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);

                    if (horizontalDistSq < maxHorizDist * 0.92 && y >= riverBedY && y <= ceilingY) {
                        // Cavern interior air space
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else if (horizontalDistSq < maxHorizDist * 1.08 && y >= riverBedY - 3 && y <= ceilingY + 2) {
                        // Cavern natural wall/ceiling perimeter lining
                        BlockState current = world.getBlockState(pos);
                        if (current.isAir() || isFoliage(current)) {
                            BlockState naturalBlock = getCavernRockBlock(y, riverBedY, ceilingY, rand);
                            world.setBlockState(pos, naturalBlock, Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        // Natural Skylights / Ceiling Crevices: letting sunbeams pierce the dark canopy
        carveSkylight(world, cx - 8, cz - 6, ceilingY, naturalY, 3, rand);
        carveSkylight(world, cx + 5, cz + 10, ceilingY, naturalY, 2, rand);
    }

    private static void carveSkylight(ServerWorld world, int sx, int sz, int ceilingY, int naturalY, int radius, Random rand) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + 1) continue;
                for (int y = ceilingY - 1; y <= naturalY + 4; y++) {
                    world.setBlockState(new BlockPos(sx + dx, y, sz + dz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Overgrown frame around the skylight
                BlockPos rimPos = new BlockPos(sx + dx, ceilingY, sz + dz);
                if (rand.nextFloat() < 0.4f) {
                    world.setBlockState(rimPos, Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static BlockState getCavernRockBlock(int y, int riverBedY, int ceilingY, Random rand) {
        float f = rand.nextFloat();
        if (y < riverBedY + 3) {
            // Lower layers: Deepslate & Tuff
            if (f < 0.45f) return Blocks.DEEPSLATE.getDefaultState();
            if (f < 0.70f) return Blocks.COBBLED_DEEPSLATE.getDefaultState();
            return Blocks.TUFF.getDefaultState();
        } else if (y > ceilingY - 3) {
            // Ceiling: Rooted dirt, dripstone & stone
            if (f < 0.35f) return Blocks.ROOTED_DIRT.getDefaultState();
            if (f < 0.55f) return Blocks.DRIPSTONE_BLOCK.getDefaultState();
            if (f < 0.80f) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            return Blocks.STONE.getDefaultState();
        } else {
            // Mid walls: Stone, Andesite, Mossy Cobblestone
            if (f < 0.45f) return Blocks.STONE.getDefaultState();
            if (f < 0.70f) return Blocks.ANDESITE.getDefaultState();
            if (f < 0.88f) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
            return Blocks.MOSS_BLOCK.getDefaultState();
        }
    }

    // =========================================================================
    // 2. CENTRAL SUNKEN RIVER & GORGE
    // =========================================================================

    /**
     * Builds the sunken river gorge winding through the center of the cavern,
     * complete with water, submerged glowing sea pickles, lily pads, dripleaf, and waterfalls.
     */
    private static void buildSunkenRiverAndGorge(ServerWorld world, int cx, int cz, int riverBedY, int floorY, Random rand) {
        int riverMinZ = cz - 21;
        int riverMaxZ = cz + 21;

        for (int z = riverMinZ; z <= riverMaxZ; z++) {
            // Gentle winding river curve
            double curveOffset = 1.5 * Math.sin((z - cz) * 0.12);
            int rxCenter = cx - 1 + (int) Math.round(curveOffset);
            int halfWidth = 3 + (int) Math.abs(Math.sin((z - cz) * 0.25) * 1.5);

            for (int x = rxCenter - halfWidth; x <= rxCenter + halfWidth; x++) {
                // 1. Riverbed floor (at riverBedY)
                BlockPos bedPos = new BlockPos(x, riverBedY, z);
                float bf = rand.nextFloat();
                BlockState bedBlock;
                if (bf < 0.40f) bedBlock = Blocks.GRAVEL.getDefaultState();
                else if (bf < 0.65f) bedBlock = Blocks.CLAY.getDefaultState();
                else if (bf < 0.85f) bedBlock = Blocks.MOSS_BLOCK.getDefaultState();
                else bedBlock = Blocks.STONE.getDefaultState();

                world.setBlockState(bedPos, bedBlock, Block.NOTIFY_LISTENERS);
                world.setBlockState(bedPos.down(), Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);

                // 2. Water depth: 2 blocks (riverBedY + 1 and riverBedY + 2)
                BlockPos water1 = bedPos.up();
                BlockPos water2 = bedPos.up(2);
                world.setBlockState(water1, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(water2, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Clear air above water up to bridge/terrace level
                for (int y = riverBedY + 3; y <= floorY + 4; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }

                // 3. Submerged Sea Pickles (CRITICAL UNDERWATER LIGHTING!)
                // In Minecraft 1.20.1, waterlogged Sea Pickles emit vibrant teal light underwater!
                if ((x + z * 3) % 7 == 0 && rand.nextFloat() < 0.75f) {
                    world.setBlockState(water1, Blocks.SEA_PICKLE.getDefaultState()
                            .with(SeaPickleBlock.PICKLES, 2 + rand.nextInt(3))
                            .with(SeaPickleBlock.WATERLOGGED, true), Block.NOTIFY_LISTENERS);
                }

                // 4. Lily Pads on the water surface (riverBedY + 3)
                if (rand.nextFloat() < 0.15f) {
                    BlockPos lilyPos = water2.up();
                    world.setBlockState(lilyPos, Blocks.LILY_PAD.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }

            // Riverbanks: Stone & mossy retaining cliffs rising from riverBedY + 2 to floorY
            int leftBankX = rxCenter - halfWidth - 1;
            int rightBankX = rxCenter + halfWidth + 1;

            buildRiverbankColumn(world, leftBankX, z, riverBedY, floorY, rand);
            buildRiverbankColumn(world, rightBankX, z, riverBedY, floorY, rand);

            // Shoreline Big Dripleaf plants
            if (rand.nextFloat() < 0.20f) {
                placeBigDripleaf(world, new BlockPos(leftBankX + 1, riverBedY + 1, z), 2);
            }
            if (rand.nextFloat() < 0.20f) {
                placeBigDripleaf(world, new BlockPos(rightBankX - 1, riverBedY + 1, z), 2);
            }
        }

        // Natural Waterfalls cascading down into the river:
        // Waterfall 1: West wall into river
        buildWaterfall(world, cx - 18, cz - 12, floorY + 6, riverBedY + 2, Direction.EAST);
        // Waterfall 2: Under mid-terrace arch
        buildWaterfall(world, cx + 10, cz + 14, floorY + 3, riverBedY + 2, Direction.WEST);
    }

    private static void buildRiverbankColumn(ServerWorld world, int x, int z, int riverBedY, int floorY, Random rand) {
        for (int y = riverBedY; y <= floorY; y++) {
            BlockPos p = new BlockPos(x, y, z);
            float f = rand.nextFloat();
            BlockState state;
            if (y == floorY) {
                state = (f < 0.6f) ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.MOSS_BLOCK.getDefaultState();
            } else if (f < 0.5f) {
                state = Blocks.MOSSY_COBBLESTONE.getDefaultState();
            } else if (f < 0.8f) {
                state = Blocks.STONE_BRICKS.getDefaultState();
            } else {
                state = Blocks.COBBLESTONE.getDefaultState();
            }
            world.setBlockState(p, state, Block.NOTIFY_LISTENERS);
        }
    }

    private static void placeBigDripleaf(ServerWorld world, BlockPos waterPos, int height) {
        if (!world.getBlockState(waterPos.down()).isSolidBlock(world, waterPos.down())) return;
        for (int i = 0; i < height; i++) {
            BlockPos p = waterPos.up(i);
            boolean isTop = (i == height - 1);
            if (isTop) {
                world.setBlockState(p, Blocks.BIG_DRIPLEAF.getDefaultState()
                        .with(BigDripleafBlock.FACING, Direction.NORTH)
                        .with(net.minecraft.state.property.Properties.WATERLOGGED, p.getY() <= waterPos.getY() + 1), Block.NOTIFY_LISTENERS);
            } else {
                world.setBlockState(p, Blocks.BIG_DRIPLEAF_STEM.getDefaultState()
                        .with(BigDripleafStemBlock.FACING, Direction.NORTH)
                        .with(net.minecraft.state.property.Properties.WATERLOGGED, true), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void buildWaterfall(ServerWorld world, int sx, int sz, int startY, int endY, Direction flowDir) {
        // Natural stone channel
        for (int y = startY; y >= endY; y--) {
            BlockPos p = new BlockPos(sx, y, sz);
            world.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Flanking stone to contain flow
            world.setBlockState(p.offset(flowDir.rotateYClockwise()), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(p.offset(flowDir.rotateYCounterclockwise()), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(p.offset(flowDir.getOpposite()), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    // =========================================================================
    // 3. RUINED STONE BRIDGES
    // =========================================================================

    /**
     * Builds the ruined ancient stone bridges spanning the river gorge, connecting Left and Right wings.
     */
    private static void buildRuinedBridges(ServerWorld world, int cx, int cz, int riverBedY, int floorY, Random rand) {
        // Bridge 1: Main Lower Ruined Bridge (at cz - 2 to cz + 2, floorY)
        int bridgeZStart = cz - 2;
        int bridgeZEnd = cz + 2;
        int bridgeXStart = cx - 7;
        int bridgeXEnd = cx + 6;

        for (int z = bridgeZStart; z <= bridgeZEnd; z++) {
            for (int x = bridgeXStart; x <= bridgeXEnd; x++) {
                BlockPos deckPos = new BlockPos(x, floorY, z);

                // Simulate weathered ruins: some missing blocks / holes
                boolean isEdgeZ = (z == bridgeZStart || z == bridgeZEnd);
                boolean isHole = (x == cx - 1 && z == cz) || (x == cx + 2 && z == cz + 1 && rand.nextBoolean());

                if (isHole) {
                    world.setBlockState(deckPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    continue;
                }

                // Deck block
                world.setBlockState(deckPos, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);

                // Underside arch support
                int distFromBank = Math.min(x - bridgeXStart, bridgeXEnd - x);
                if (distFromBank <= 2) {
                    BlockPos archPos = deckPos.down();
                    Direction faceDir = (x < cx) ? Direction.EAST : Direction.WEST;
                    world.setBlockState(archPos, Blocks.STONE_BRICK_STAIRS.getDefaultState()
                            .with(StairsBlock.FACING, faceDir)
                            .with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
                }

                // Broken bridge balustrades / railings on edges
                if (isEdgeZ && rand.nextFloat() > 0.35f) {
                    BlockPos railPos = deckPos.up();
                    if (rand.nextBoolean()) {
                        world.setBlockState(railPos, Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else {
                        Direction stairFace = (z == bridgeZStart) ? Direction.SOUTH : Direction.NORTH;
                        world.setBlockState(railPos, Blocks.STONE_BRICK_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, stairFace), Block.NOTIFY_LISTENERS);
                    }
                }

                // Hanging roots dangling beneath bridge over the water
                if (rand.nextFloat() < 0.30f) {
                    BlockPos underPos = deckPos.down();
                    if (world.getBlockState(underPos).isAir()) {
                        world.setBlockState(underPos, Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // Bridge 2: Secondary Ruined Arch / Stepping Stone Ruins (cz + 13 to cz + 15)
        for (int x = cx - 5; x <= cx + 4; x++) {
            for (int z = cz + 13; z <= cz + 15; z++) {
                if ((x + z) % 3 == 0) {
                    // Stepping pillar in the river
                    for (int y = riverBedY + 1; y <= floorY - 1; y++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                    world.setBlockState(new BlockPos(x, floorY, z), Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    // =========================================================================
    // 4. LEFT SECTOR: SACRED ALTAR & DAIS TERRACE
    // =========================================================================

    /**
     * Builds the Left Terrace containing:
     * 1. Monumental Stepped Throne / Altar (dais) matching Image 3.
     * 2. Golden Vault Alcove set in the rock wall with solid gold blocks and sealed chest.
     * 3. Pointed Dripstone Pit Trap with stalagmites, trapdoors, and skeleton skull.
     */
    private static BlockPos buildLeftAltarTerrace(ServerWorld world, int cx, int cz, int riverBedY, int floorY,
                                                 Random rand, List<BlockPos> fakeTraps, List<BlockPos> oneHpTraps,
                                                 List<BlockPos> rootBarriers) {
        int minX = cx - 21;
        int maxX = cx - 5;
        int minZ = cz - 21;
        int maxZ = cz + 20;

        // 1. Pave the Left Terrace Floor
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, floorY, z);
                world.setBlockState(pos, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
                world.setBlockState(pos.down(), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Clear air above terrace
                for (int y = floorY + 1; y <= floorY + 10; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }

                // Lush ground foliage: Moss carpets, Ferns, Grass
                if (rand.nextFloat() < 0.22f) {
                    float vegF = rand.nextFloat();
                    if (vegF < 0.50f) {
                        world.setBlockState(pos.up(), Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else if (vegF < 0.80f) {
                        world.setBlockState(pos.up(), Blocks.FERN.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(pos.up(), Blocks.GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }

        // 2. The Monumental Stepped Throne / Altar (Dais) (Image 1 & 3)
        // Center of the dais at (cx - 13, cz - 4)
        int dx = cx - 13;
        int dz = cz - 4;
        buildMonumentalThroneDais(world, dx, dz, floorY, rand, fakeTraps);

        // 3. Golden Vault Alcove in the back rock wall (cx - 13, cz - 19)
        BlockPos sealedChestPos = buildGoldenVaultAlcove(world, dx, cz - 18, floorY, rand, rootBarriers);

        // 4. Pointed Dripstone Pit Trap (Image 1, 2, 3)
        // Located between the dais and the river at (cx - 8, cz + 6)
        buildDripstonePitTrap(world, cx - 8, cz + 6, floorY, rand, oneHpTraps, fakeTraps);

        return sealedChestPos;
    }

    /**
     * Builds the monumental 4-tiered stepped stone dais and ceremonial throne
     * with outward-facing stone brick stairs, carved corner pillars, canopy arch, moss, and altar chest.
     */
    private static void buildMonumentalThroneDais(ServerWorld world, int dx, int dz, int floorY, Random rand, List<BlockPos> fakeTraps) {
        // Tier 1 Base: 9x9 stepped platform (floorY + 1)
        buildSteppedDaisTier(world, dx, dz, 4, floorY + 1, rand);

        // Tier 2: 7x7 stepped platform (floorY + 2)
        buildSteppedDaisTier(world, dx, dz, 3, floorY + 2, rand);

        // Tier 3: 5x5 stepped platform (floorY + 3)
        buildSteppedDaisTier(world, dx, dz, 2, floorY + 3, rand);

        // Tier 4 Top Platform: 3x3 platform (floorY + 4)
        for (int x = dx - 1; x <= dx + 1; x++) {
            for (int z = dz - 1; z <= dz + 1; z++) {
                world.setBlockState(new BlockPos(x, floorY + 4, z), Blocks.POLISHED_ANDESITE.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Front 3-wide Stone Brick Stairs ascending from ground to top platform
        for (int step = 1; step <= 3; step++) {
            int sy = floorY + step;
            int sz = dz + (5 - step);
            for (int x = dx - 1; x <= dx + 1; x++) {
                world.setBlockState(new BlockPos(x, sy, sz), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
            }
        }

        // Ceremonial Throne Seat on the top platform (at dx, dz - 1, floorY + 5)
        // Seat
        world.setBlockState(new BlockPos(dx, floorY + 5, dz - 1), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), Block.NOTIFY_LISTENERS);
        // Armrests
        world.setBlockState(new BlockPos(dx - 1, floorY + 5, dz - 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx - 1, floorY + 6, dz - 1), Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx + 1, floorY + 5, dz - 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx + 1, floorY + 6, dz - 1), Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);

        // High Throne Backrest & Overhead Canopy Arch (Image 3)
        world.setBlockState(new BlockPos(dx, floorY + 6, dz - 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx, floorY + 7, dz - 1), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        // Overhead Canopy Arch
        world.setBlockState(new BlockPos(dx - 1, floorY + 8, dz - 1), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx + 1, floorY + 8, dz - 1), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx, floorY + 8, dz - 1), Blocks.STONE_BRICK_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);

        // Hanging Vines draped on the sides of the throne
        world.setBlockState(new BlockPos(dx - 2, floorY + 6, dz - 1), Blocks.VINE.getDefaultState()
                .with(VineBlock.EAST, true), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(dx + 2, floorY + 6, dz - 1), Blocks.VINE.getDefaultState()
                .with(VineBlock.WEST, true), Block.NOTIFY_LISTENERS);

        // Altar Loot Chest resting on the lower tier in front of the throne
        BlockPos altarChestPos = new BlockPos(dx + 1, floorY + 2, dz + 3);
        placeChest(world, altarChestPos, Direction.NORTH, LOOT_SHRINE, rand);

        // Approach Tripwire Trap in front of the dais
        BlockPos hook1 = new BlockPos(dx - 2, floorY + 1, dz + 5);
        BlockPos hook2 = new BlockPos(dx + 2, floorY + 1, dz + 5);
        world.setBlockState(hook1.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(hook2.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(hook1, Blocks.TRIPWIRE_HOOK.getDefaultState().with(TripwireHookBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
        world.setBlockState(hook2, Blocks.TRIPWIRE_HOOK.getDefaultState().with(TripwireHookBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
        for (int tx = dx - 1; tx <= dx + 1; tx++) {
            BlockPos tripPos = new BlockPos(tx, floorY + 1, dz + 5);
            world.setBlockState(tripPos, Blocks.TRIPWIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
            fakeTraps.add(tripPos);
        }
    }

    private static void buildSteppedDaisTier(ServerWorld world, int cx, int cz, int radius, int y, Random rand) {
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                BlockPos p = new BlockPos(x, y, z);
                boolean isEdgeN = (z == cz - radius);
                boolean isEdgeS = (z == cz + radius);
                boolean isEdgeW = (x == cx - radius);
                boolean isEdgeE = (x == cx + radius);
                boolean isCorner = (isEdgeN || isEdgeS) && (isEdgeW || isEdgeE);

                if (isCorner) {
                    world.setBlockState(p, Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                } else if (isEdgeN) {
                    world.setBlockState(p, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
                } else if (isEdgeS) {
                    world.setBlockState(p, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
                } else if (isEdgeW) {
                    world.setBlockState(p, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
                } else if (isEdgeE) {
                    world.setBlockState(p, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
                } else {
                    world.setBlockState(p, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    /**
     * Builds the Golden Vault Alcove set into the back rock wall (Image 1, 2, 3),
     * framed by stone brick arches, with solid Gold Blocks and sealed treasure chest.
     */
    private static BlockPos buildGoldenVaultAlcove(ServerWorld world, int ax, int az, int floorY, Random rand, List<BlockPos> rootBarriers) {
        // Carve alcove into wall: 5 wide, 3 deep, 4 high
        for (int x = ax - 2; x <= ax + 2; x++) {
            for (int z = az - 2; z <= az; z++) {
                for (int y = floorY + 1; y <= floorY + 5; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Solid GOLD BLOCKS floor (Image 1, 2, 3)
                world.setBlockState(new BlockPos(x, floorY, z), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Stone Brick Arch Framing the Vault
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            world.setBlockState(new BlockPos(ax - 2, y, az + 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(ax + 2, y, az + 1), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        // Arch top
        world.setBlockState(new BlockPos(ax - 1, floorY + 4, az + 1), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ax + 1, floorY + 4, az + 1), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.TOP), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ax, floorY + 4, az + 1), Blocks.STONE_BRICK_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);

        // Flanking Gold Blocks Pedestal in center
        world.setBlockState(new BlockPos(ax - 1, floorY + 1, az - 1), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(ax + 1, floorY + 1, az - 1), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Sealed Vault Chest in center
        BlockPos sealedChestPos = new BlockPos(ax, floorY + 1, az - 1);
        placeChest(world, sealedChestPos, Direction.SOUTH, LOOT_SHRINE_SEALED, rand);

        // Ancient Root Barrier sealing the vault entrance (severable with Rootbound Axe!)
        for (int x = ax - 1; x <= ax + 1; x++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                BlockPos rootPos = new BlockPos(x, y, az);
                world.setBlockState(rootPos, Blocks.MANGROVE_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
                rootBarriers.add(rootPos);
            }
        }

        return sealedChestPos;
    }

    /**
     * Builds the Pointed Dripstone Spike Pit Trap (Image 1, 2, 3),
     * a 4x4 pit filled with stalagmites, trapdoors, and a skeleton skull on the rim stone.
     */
    private static void buildDripstonePitTrap(ServerWorld world, int px, int pz, int floorY, Random rand,
                                             List<BlockPos> oneHpTraps, List<BlockPos> fakeTraps) {
        int pitDepth = 4;
        int pitFloorY = floorY - pitDepth;

        // Dig out 4x4 pit
        for (int x = px; x <= px + 3; x++) {
            for (int z = pz; z <= pz + 3; z++) {
                // Pit bottom floor: Dripstone block
                world.setBlockState(new BlockPos(x, pitFloorY, z), Blocks.DRIPSTONE_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Stalagmites: Pointed Dripstone pointing UP
                BlockPos spikePos = new BlockPos(x, pitFloorY + 1, z);
                world.setBlockState(spikePos, Blocks.POINTED_DRIPSTONE.getDefaultState()
                        .with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
                        .with(PointedDripstoneBlock.THICKNESS, Thickness.TIP), Block.NOTIFY_LISTENERS);

                // Air space inside pit
                for (int y = pitFloorY + 2; y <= floorY; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }

                oneHpTraps.add(new BlockPos(x, floorY, z));
            }
        }

        // Rim Stone & Oak Trapdoors over part of the pit (Image 1, 3)
        world.setBlockState(new BlockPos(px + 1, floorY, pz - 1), Blocks.OAK_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.NORTH).with(TrapdoorBlock.OPEN, false), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(px + 2, floorY, pz - 1), Blocks.OAK_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.NORTH).with(TrapdoorBlock.OPEN, true), Block.NOTIFY_LISTENERS);

        // Skeleton Skull on the rim stone block (Image 3)
        BlockPos rimSkullPos = new BlockPos(px + 4, floorY + 1, pz + 2);
        world.setBlockState(rimSkullPos.down(), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(rimSkullPos, Blocks.SKELETON_SKULL.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Decoy pressure plate near the edge
        BlockPos decoyPos = new BlockPos(px - 1, floorY + 1, pz + 1);
        world.setBlockState(decoyPos, Blocks.STONE_PRESSURE_PLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(decoyPos);
    }

    // =========================================================================
    // 5. RIGHT SECTOR: MULTI-TIER FORTRESS / RUIN
    // =========================================================================

    /**
     * Builds the multi-tiered ancient fortress built into the eastern cavern wall:
     * - Tier 1: Lower level with treasure stash (gold blocks, multiple chests, redstone trails), grand stone stairs.
     * - Tier 2: Mezzanine with continuous iron bar railing, tripwire corridor, ancient library archives.
     * - Tier 3: Upper gallery and high wooden skybridge spanning high across the gorge.
     * - Tier 4: High overlook balconies and tunnel mouths.
     */
    private static void buildRightMultiLevelFortress(ServerWorld world, int cx, int cz, int riverBedY, int floorY,
                                                    int tier2Y, int tier3Y, int tier4Y, Random rand, List<BlockPos> fakeTraps) {
        int minX = cx + 5;
        int maxX = cx + 21;
        int minZ = cz - 21;
        int maxZ = cz + 20;

        // --- Tier 1 (Lower Level, floorY) ---
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos p = new BlockPos(x, floorY, z);
                world.setBlockState(p, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
                world.setBlockState(p.down(), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int y = floorY + 1; y < tier2Y; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Tier 1 Columns supporting Tier 2 floor
        for (int z = minZ + 3; z <= maxZ - 3; z += 6) {
            for (int x = minX + 2; x <= maxX - 3; x += 6) {
                for (int y = floorY + 1; y < tier2Y; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Wall torches on pillars
                placeWallTorch(world, new BlockPos(x - 1, floorY + 3, z), Direction.WEST);
            }
        }

        // Treasure Cache / Stash in bottom right (Image 1)
        // Located at (cx + 12, cz + 13)
        int tx = cx + 12;
        int tz = cz + 13;
        buildTreasureCache(world, tx, tz, floorY, rand);

        // Grand Stone Staircase ascending from Tier 1 up to Tier 2 (Image 1, 2)
        // Located at (cx + 7 to cx + 9, cz - 2 to cz + 5)
        buildGrandStaircase(world, cx + 7, cz - 2, floorY, tier2Y);

        // --- Tier 2 (Mezzanine, tier2Y) ---
        for (int x = minX + 1; x <= maxX; x++) {
            for (int z = minZ + 2; z <= maxZ - 2; z++) {
                BlockPos p = new BlockPos(x, tier2Y, z);
                world.setBlockState(p, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
                for (int y = tier2Y + 1; y < tier3Y; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Tier 2 Continuous Iron Bar Railing overlooking the river gorge (Image 1, 2)
        int railX = minX + 1;
        for (int z = minZ + 2; z <= maxZ - 2; z++) {
            placeIronBars(world, new BlockPos(railX, tier2Y + 1, z), true, true, false, false);
        }

        // Tripwire Corridor on Tier 2 (Image 2)
        buildTier2TripwireCorridor(world, cx + 10, cz, tier2Y, rand, fakeTraps);

        // Ancient Library / Archives Alcove on Tier 2
        buildAncientLibraryAlcove(world, cx + 14, cz - 10, tier2Y, rand);

        // --- Tier 3 (Upper Gallery, tier3Y) ---
        for (int x = minX + 3; x <= maxX; x++) {
            for (int z = minZ + 4; z <= maxZ - 4; z++) {
                BlockPos p = new BlockPos(x, tier3Y, z);
                world.setBlockState(p, getAestheticFloorBlock(rand), Block.NOTIFY_LISTENERS);
                for (int y = tier3Y + 1; y < tier4Y; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Tier 3 Iron Bar Railing
        for (int z = minZ + 4; z <= maxZ - 4; z++) {
            placeIronBars(world, new BlockPos(minX + 3, tier3Y + 1, z), true, true, false, false);
        }

        // High Wooden Skybridge spanning high across the gorge (Image 1, 2)
        // Spans from Tier 3 balcony (cx + 6) all the way across to western cliff (cx - 14) at (cz - 7)
        buildHighWoodenSkybridge(world, cx - 14, cx + 6, cz - 7, tier3Y, rand);

        // --- Tier 4 (High Overlooks & Tunnels, tier4Y) ---
        for (int x = minX + 6; x <= maxX; x++) {
            for (int z = minZ + 6; z <= maxZ - 6; z++) {
                if ((x + z) % 4 == 0) {
                    BlockPos p = new BlockPos(x, tier4Y, z);
                    world.setBlockState(p, Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    placeIronBars(world, p.up(), true, true, true, true);
                    placeWallTorch(world, p.up(2), Direction.WEST);
                }
            }
        }
    }

    /**
     * Builds the Treasure Cache with gold blocks, multiple chests, and redstone dust trails (Image 1).
     */
    private static void buildTreasureCache(ServerWorld world, int tx, int tz, int floorY, Random rand) {
        // Gold blocks on the floor
        world.setBlockState(new BlockPos(tx, floorY, tz), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(tx + 1, floorY, tz), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(tx, floorY, tz + 1), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Multiple Chests (Image 1)
        placeChest(world, new BlockPos(tx + 1, floorY + 1, tz), Direction.WEST, LOOT_SHRINE, rand);
        placeChest(world, new BlockPos(tx, floorY + 1, tz + 2), Direction.NORTH, LOOT_SHRINE, rand);
        placeChest(world, new BlockPos(tx + 2, floorY + 1, tz + 1), Direction.WEST, LOOT_SHRINE, rand);

        // Redstone Dust Trails running across the stone floor (Image 1)
        for (int dx = -2; dx <= 2; dx++) {
            BlockPos wirePos = new BlockPos(tx + dx, floorY + 1, tz - 1);
            world.setBlockState(wirePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        for (int dz = -1; dz <= 2; dz++) {
            BlockPos wirePos = new BlockPos(tx - 1, floorY + 1, tz + dz);
            world.setBlockState(wirePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // Redstone Torches for atmospheric lighting
        world.setBlockState(new BlockPos(tx - 2, floorY + 1, tz - 2), Blocks.REDSTONE_TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    /**
     * Builds the Grand Stone Staircase connecting Tier 1 up to Tier 2 (Image 1, 2).
     */
    private static void buildGrandStaircase(ServerWorld world, int startX, int startZ, int floorY, int targetY) {
        int steps = targetY - floorY;
        for (int step = 0; step < steps; step++) {
            int y = floorY + 1 + step;
            int z = startZ + step;
            for (int x = startX; x <= startX + 2; x++) {
                // Stone Brick Stair ascending North-to-South
                world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
                // Foundation solid under the stair
                for (int fy = floorY + 1; fy < y; fy++) {
                    world.setBlockState(new BlockPos(x, fy, z), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
            // Railing wall along the open edge
            world.setBlockState(new BlockPos(startX - 1, y + 1, z), Blocks.STONE_BRICK_WALL.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    /**
     * Builds the Tier 2 Tripwire Corridor with stone pillars and stretched tripwire string (Image 2).
     */
    private static void buildTier2TripwireCorridor(ServerWorld world, int hx, int hz, int tier2Y, Random rand, List<BlockPos> fakeTraps) {
        // Flanking Stone Pillars
        for (int y = tier2Y + 1; y <= tier2Y + 4; y++) {
            world.setBlockState(new BlockPos(hx - 2, y, hz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(hx + 2, y, hz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        // Tripwire Hooks on the pillars
        world.setBlockState(new BlockPos(hx - 1, tier2Y + 1, hz), Blocks.TRIPWIRE_HOOK.getDefaultState()
                .with(TripwireHookBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(hx + 1, tier2Y + 1, hz), Blocks.TRIPWIRE_HOOK.getDefaultState()
                .with(TripwireHookBlock.FACING, Direction.WEST), Block.NOTIFY_LISTENERS);
        // Stretched Tripwire String across corridor
        BlockPos tripPos = new BlockPos(hx, tier2Y + 1, hz);
        world.setBlockState(tripPos, Blocks.TRIPWIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(tripPos);

        // Corridor Chests
        placeChest(world, new BlockPos(hx - 2, tier2Y + 1, hz + 2), Direction.EAST, LOOT_SHRINE, rand);
        placeChest(world, new BlockPos(hx + 2, tier2Y + 1, hz + 2), Direction.WEST, LOOT_SHRINE, rand);
    }

    /**
     * Builds the Ancient Library / Archives Alcove on Tier 2 with Bookshelves, carved pedestal, and cobwebs.
     */
    private static void buildAncientLibraryAlcove(ServerWorld world, int lx, int lz, int tier2Y, Random rand) {
        // Bookshelves lining the walls
        for (int x = lx; x <= lx + 4; x++) {
            for (int z = lz; z <= lz + 4; z++) {
                boolean isWall = (x == lx || x == lx + 4 || z == lz || z == lz + 4);
                if (isWall) {
                    for (int y = tier2Y + 1; y <= tier2Y + 3; y++) {
                        if (rand.nextFloat() < 0.80f) {
                            world.setBlockState(new BlockPos(x, y, z), Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(new BlockPos(x, y, z), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        // Center carved pedestal
        world.setBlockState(new BlockPos(lx + 2, tier2Y + 1, lz + 2), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(new BlockPos(lx + 2, tier2Y + 2, lz + 2), Blocks.LECTERN.getDefaultState()
                .with(LecternBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);

        // Wall torch for cozy library lighting
        placeWallTorch(world, new BlockPos(lx + 2, tier2Y + 3, lz + 1), Direction.SOUTH);
    }

    /**
     * Builds the High Wooden Skybridge spanning across the gorge (Image 1, 2)
     * from Tier 3 balcony all the way to the western cliff, with hanging roots and glow berry vines dangling underneath.
     */
    private static void buildHighWoodenSkybridge(ServerWorld world, int minX, int maxX, int z, int tier3Y, Random rand) {
        for (int x = minX; x <= maxX; x++) {
            // Dark Oak plank floor
            world.setBlockState(new BlockPos(x, tier3Y, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(x, tier3Y, z + 1), Blocks.DARK_OAK_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);

            // Support Wooden Beam underneath
            world.setBlockState(new BlockPos(x, tier3Y - 1, z), Blocks.DARK_OAK_LOG.getDefaultState()
                    .with(PillarBlock.AXIS, Direction.Axis.X), Block.NOTIFY_LISTENERS);

            // Side Railings
            placeIronBars(world, new BlockPos(x, tier3Y + 1, z - 1), false, false, true, true);
            placeIronBars(world, new BlockPos(x, tier3Y + 1, z + 2), false, false, true, true);

            // Dangling vegetation underneath the high skybridge (Image 1, 2)
            if (rand.nextFloat() < 0.45f) {
                BlockPos hangPos = new BlockPos(x, tier3Y - 2, z);
                if (rand.nextBoolean()) {
                    world.setBlockState(hangPos, Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
                } else {
                    hangCaveVineString(world, hangPos, 2 + rand.nextInt(4), rand);
                }
            }
        }
    }

    // =========================================================================
    // 6. SURFACE ENTRANCE & DESCENDING STAIRS
    // =========================================================================

    /**
     * Builds the surface sinkhole entrance and descending staircase along the southwestern cave wall,
     * providing a natural entry point that offers the breathtaking panorama seen in Image 1.
     */
    private static BlockPos buildSurfaceEntranceAndStairs(ServerWorld world, int cx, int cz, int naturalY, int floorY, Random rand) {
        int ex = cx - 14;
        int ez = cz + 18;

        // Surface overgrown sinkhole opening (5x5)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = floorY + 10; y <= naturalY + 4; y++) {
                    world.setBlockState(new BlockPos(ex + dx, y, ez + dz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Overgrown surface rim
                BlockPos rim = new BlockPos(ex + dx, naturalY, ez + dz);
                if (dx == -2 || dx == 2 || dz == -2 || dz == 2) {
                    world.setBlockState(rim, (rand.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS).getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Descending Winding Stone Staircase down along the cliff wall to floorY
        int stairsHeight = (floorY + 10) - floorY;
        for (int step = 0; step < stairsHeight; step++) {
            int y = floorY + 1 + step;
            int z = ez - step;
            for (int x = ex - 1; x <= ex + 1; x++) {
                world.setBlockState(new BlockPos(x, y, z), Blocks.STONE_BRICK_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            }
        }

        // Return player vantage point at the foot of the stairs facing into the grand cavern
        return new BlockPos(cx - 13, floorY + 1, cz + 14);
    }

    // =========================================================================
    // 7. ATMOSPHERIC LIGHTING & FLORA (CRITICAL FOCUS)
    // =========================================================================

    /**
     * Populates the cavern with warm, authentic lighting and lush vegetation
     * exactly as seen in the reference photos:
     * - Cave Vines with glowing orange Glow Berries (light level 14).
     * - Wall Torches on stone columns, grand stairs, and balconies.
     * - Hanging Lanterns suspended on Chains under arches and bridges.
     * - Spore Blossoms dripping particles from the ceiling.
     * - Hanging Roots dangling from ceiling and ledges.
     */
    private static void populateAtmosphericLightingAndFlora(ServerWorld world, int cx, int cz, int riverBedY,
                                                           int floorY, int tier2Y, int tier3Y, int ceilingY, Random rand) {
        // 1. Cave Vines with Glow Berries hanging from the ceiling across the entire cavern (Image 1, 2, 3)
        // In Minecraft 1.20.1, CAVE_VINES_PLANT and CAVE_VINES with BERRIES=true emit light level 14!
        for (int x = cx - CAVERN_RADIUS_X + 2; x <= cx + CAVERN_RADIUS_X - 2; x += 3) {
            for (int z = cz - CAVERN_RADIUS_Z + 2; z <= cz + CAVERN_RADIUS_Z - 2; z += 3) {
                if (rand.nextFloat() < 0.65f) {
                    // Find ceiling block
                    for (int y = ceilingY + 1; y >= floorY + 8; y--) {
                        BlockPos checkPos = new BlockPos(x, y, z);
                        if (world.getBlockState(checkPos).isSolidBlock(world, checkPos) && world.getBlockState(checkPos.down()).isAir()) {
                            int length = 3 + rand.nextInt(6); // 3 to 8 blocks long
                            hangCaveVineString(world, checkPos.down(), length, rand);
                            break;
                        }
                    }
                }
            }
        }

        // 2. Hanging Lanterns on Chains under arches and bridges
        placeHangingChainLantern(world, new BlockPos(cx - 1, floorY + 3, cz));
        placeHangingChainLantern(world, new BlockPos(cx + 2, floorY + 3, cz));
        placeHangingChainLantern(world, new BlockPos(cx + 10, tier2Y + 4, cz - 4));
        placeHangingChainLantern(world, new BlockPos(cx + 10, tier2Y + 4, cz + 4));

        // 3. Spore Blossoms dripping atmospheric green particles
        for (int i = 0; i < 8; i++) {
            int sx = cx - 18 + rand.nextInt(36);
            int sz = cz - 18 + rand.nextInt(36);
            for (int y = ceilingY; y >= floorY + 10; y--) {
                BlockPos p = new BlockPos(sx, y, sz);
                if (world.getBlockState(p).isSolidBlock(world, p) && world.getBlockState(p.down()).isAir()) {
                    world.setBlockState(p.down(), Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
                    break;
                }
            }
        }

        // 4. Hanging Roots dangling from stone and rooted dirt ledges
        for (int x = cx - CAVERN_RADIUS_X + 3; x <= cx + CAVERN_RADIUS_X - 3; x += 4) {
            for (int z = cz - CAVERN_RADIUS_Z + 3; z <= cz + CAVERN_RADIUS_Z - 3; z += 4) {
                if (rand.nextFloat() < 0.40f) {
                    for (int y = ceilingY; y >= floorY + 5; y--) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (world.getBlockState(p).isSolidBlock(world, p) && world.getBlockState(p.down()).isAir()) {
                            world.setBlockState(p.down(), Blocks.HANGING_ROOTS.getDefaultState(), Block.NOTIFY_LISTENERS);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Hangs a string of cave vines with glowing berries (light level 14).
     */
    private static void hangCaveVineString(ServerWorld world, BlockPos startPos, int length, Random rand) {
        BlockPos.Mutable mut = startPos.mutableCopy();
        for (int i = 0; i < length; i++) {
            if (!world.getBlockState(mut).isAir()) break;
            boolean isTip = (i == length - 1);
            boolean hasBerries = rand.nextFloat() < 0.70f; // 70% chance of glowing orange berries!

            if (isTip) {
                // Tip block: CAVE_VINES
                world.setBlockState(mut, Blocks.CAVE_VINES.getDefaultState()
                        .with(CaveVines.BERRIES, hasBerries), Block.NOTIFY_LISTENERS);
            } else {
                // Body block: CAVE_VINES_PLANT
                world.setBlockState(mut, Blocks.CAVE_VINES_PLANT.getDefaultState()
                        .with(CaveVines.BERRIES, hasBerries), Block.NOTIFY_LISTENERS);
            }
            mut.move(Direction.DOWN);
        }
    }

    private static void placeHangingChainLantern(ServerWorld world, BlockPos pos) {
        world.setBlockState(pos.up(), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y), Block.NOTIFY_LISTENERS);
        world.setBlockState(pos, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), Block.NOTIFY_LISTENERS);
    }

    private static void placeWallTorch(ServerWorld world, BlockPos pos, Direction facing) {
        world.setBlockState(pos, Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, facing), Block.NOTIFY_LISTENERS);
    }

    private static void placeIronBars(ServerWorld world, BlockPos pos, boolean north, boolean south, boolean east, boolean west) {
        world.setBlockState(pos, Blocks.IRON_BARS.getDefaultState()
                .with(PaneBlock.NORTH, north)
                .with(PaneBlock.SOUTH, south)
                .with(PaneBlock.EAST, east)
                .with(PaneBlock.WEST, west), Block.NOTIFY_LISTENERS);
    }

    private static void placeChest(ServerWorld world, BlockPos pos, Direction facing, Identifier lootTable, Random rand) {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable, rand.nextLong());
        }
    }

    private static BlockState getAestheticFloorBlock(Random rand) {
        float f = rand.nextFloat();
        if (f < 0.40f) return Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        if (f < 0.70f) return Blocks.STONE_BRICKS.getDefaultState();
        if (f < 0.88f) return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        return Blocks.COBBLESTONE.getDefaultState();
    }

    private static boolean isFoliage(BlockState state) {
        if (state.isAir()) return false;
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || state.isOf(Blocks.VINE);
    }
}
