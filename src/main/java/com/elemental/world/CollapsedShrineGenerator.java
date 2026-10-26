package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.sound.ModSounds;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
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
 * Aesthetic: Anime / Isekai fantasy ancient overgrown temple (48x48 to 54x54 blocks).
 * Features:
 * - Grand sunken central colonnade with collapsed ceiling and cascading jungle canopy
 * - Sacred mossy water fountain with glow flora and water drops
 * - Decoy / Fake traps that click and smoke to induce paranoia
 * - Brutal 1-HP spore traps that strip players of their health
 * - Lethal Insta-Kill pitfall chasm with pointed dripstone spikes
 * - Rootbound Axe sealed vault guarding the ancient offering chest
 */
public class CollapsedShrineGenerator {

    public static final Identifier LOOT_SHRINE = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine");
    public static final Identifier LOOT_SHRINE_SEALED = new Identifier(Elemental.MOD_ID, "chests/collapsed_shrine_sealed");

    // Footprint: 52 x 52 blocks
    public static final int SIZE_X = 52;
    public static final int SIZE_Z = 52;
    public static final int HEIGHT = 20;

    /**
     * Generates a Collapsed Shrine centered near the given position.
     * The floor is recessed 8 blocks below the natural surface to create a sunken anime ruin.
     */
    public static BlockPos generate(ServerWorld world, BlockPos surfaceCenter) {
        Random rand = new Random(surfaceCenter.asLong() ^ world.getSeed());

        int centerX = surfaceCenter.getX();
        int centerZ = surfaceCenter.getZ();
        int naturalY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
        if (naturalY < 62) naturalY = 64;

        // Recessed floor level
        int floorY = naturalY - 7;
        BlockPos origin = new BlockPos(centerX - SIZE_X / 2, floorY, centerZ - SIZE_Z / 2);

        Elemental.LOGGER.info("Generating anime-inspired Collapsed Shrine at {} (floor Y={})", origin, floorY);

        // Track special locations for gameplay mechanics
        List<BlockPos> fakeTraps = new ArrayList<>();
        List<BlockPos> oneHpTraps = new ArrayList<>();
        List<BlockPos> rootBarrierPositions = new ArrayList<>();
        BlockPos sealedChestPos = null;

        // 1. Foundation & Subterranean Excavation
        carveAndBuildChambers(world, origin, floorY, rand);

        // 2. Grand Central Nave & Columns
        buildCentralSanctuary(world, origin, floorY, rand);

        // 3. Traps Wing (East) - Fake traps, 1-HP spore trap, and Insta-Kill chasm
        buildTrapsWing(world, origin, floorY, rand, fakeTraps, oneHpTraps);

        // 4. Overgrown Crypt & Garden Wing (West)
        buildCryptWing(world, origin, floorY, rand);

        // 5. North Altar & Rootbound Axe Sealed Vault
        sealedChestPos = buildNorthAltarAndVault(world, origin, floorY, rand, rootBarrierPositions);

        // 6. Overgrown Foliage & Natural Roof Collapse
        drapeVegetationAndVines(world, origin, floorY, rand);

        // 7. Register Shrine with JungleDungeonManager for active ambience & traps
        BlockPos shrineCenter = origin.add(SIZE_X / 2, 5, SIZE_Z / 2);
        Box shrineBounds = new Box(
                origin.getX() - 4, origin.getY() - 40, origin.getZ() - 4,
                origin.getX() + SIZE_X + 4, origin.getY() + HEIGHT + 10, origin.getZ() + SIZE_Z + 4
        );

        JungleDungeonManager.registerShrine(
                shrineCenter, shrineBounds, fakeTraps, oneHpTraps, rootBarrierPositions, sealedChestPos
        );

        return shrineCenter;
    }

    /**
     * Excavates the temple interior and lays foundation walls, floor, and bedrock boundaries.
     */
    private static void carveAndBuildChambers(ServerWorld world, BlockPos origin, int floorY, Random rand) {
        int minX = origin.getX();
        int maxX = origin.getX() + SIZE_X - 1;
        int minZ = origin.getZ();
        int maxZ = origin.getZ() + SIZE_Z - 1;

        // Solid floor and base
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Foundation underneath floor
                for (int y = floorY - 3; y < floorY; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }

                // Aesthetic floor layer: mixture of mossy stone bricks, cracked bricks, mud bricks, and coarse dirt
                BlockState floorBlock = getAestheticFloorBlock(rand);
                world.setBlockState(new BlockPos(x, floorY, z), floorBlock, Block.NOTIFY_LISTENERS);

                // Clear interior space up to height
                for (int y = floorY + 1; y <= floorY + HEIGHT; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    // Leave peripheral walls, carve interior rooms
                    boolean isOuterWall = (x == minX || x == maxX || z == minZ || z == maxZ);
                    if (isOuterWall) {
                        BlockState wallBlock = getAestheticWallBlock(rand);
                        world.setBlockState(p, wallBlock, Block.NOTIFY_LISTENERS);
                    } else {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    /**
     * Builds the Grand Central Nave: High arched colonnade, massive columns, sacred water font.
     */
    private static void buildCentralSanctuary(ServerWorld world, BlockPos origin, int floorY, Random rand) {
        int centerX = origin.getX() + SIZE_X / 2;
        int centerZ = origin.getZ() + SIZE_Z / 2;

        // 1. Sacred Sunken Water Pool (10x10)
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int distSq = dx * dx + dz * dz;
                BlockPos poolPos = new BlockPos(centerX + dx, floorY, centerZ + dz);
                if (distSq <= 24) {
                    // Sunken water basin
                    world.setBlockState(poolPos.down(), Blocks.PRISMARINE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                    world.setBlockState(poolPos, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                    if (rand.nextFloat() < 0.25f) {
                        world.setBlockState(poolPos.up(), Blocks.LILY_PAD.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                } else if (distSq <= 30) {
                    // Border coping
                    world.setBlockState(poolPos, Blocks.MOSSY_STONE_BRICK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Ancient center altar pedestal inside the pool
        BlockPos altarPedestal = new BlockPos(centerX, floorY + 1, centerZ);
        world.setBlockState(altarPedestal.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(altarPedestal, Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(altarPedestal.up(), Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);

        // 2. Colonnade: 8 grand weathered stone pillars (3x3 fluted base)
        int[][] pillarOffsets = {
                {-12, -12}, {0, -14}, {12, -12},
                {-14, 0},             {14, 0},
                {-12, 12},  {0, 14},  {12, 12}
        };

        for (int[] offset : pillarOffsets) {
            int px = centerX + offset[0];
            int pz = centerZ + offset[1];
            buildWeatheredPillar(world, new BlockPos(px, floorY + 1, pz), HEIGHT - 3, rand);
        }
    }

    /**
     * Builds a massive anime-style weathered stone pillar with vines, cracked bricks, and broken capitals.
     */
    private static void buildWeatheredPillar(ServerWorld world, BlockPos base, int height, Random rand) {
        // Base pedestal (3x3)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(base.add(dx, 0, dz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Main column shaft
        int actualHeight = height - rand.nextInt(3); // Some pillars broken at top
        for (int y = 1; y <= actualHeight; y++) {
            BlockPos core = base.up(y);
            BlockState shaftBlock = (rand.nextFloat() < 0.6f) ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() :
                    (rand.nextFloat() < 0.5f ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
            world.setBlockState(core, shaftBlock, Block.NOTIFY_LISTENERS);

            // Hanging vines along pillar sides
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (rand.nextFloat() < 0.45f) {
                    BlockPos vinePos = core.offset(dir);
                    if (world.getBlockState(vinePos).isAir()) {
                        world.setBlockState(vinePos, Blocks.VINE.getDefaultState().with(VineBlock.getFacingProperty(dir.getOpposite()), true), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    /**
     * Builds the East Wing with:
     * 1. Decoy / Fake Traps (clicks, smoke, false alarms)
     * 2. 1-HP Traps (ancient botanical poison surge that drops player to 0.5 heart)
     * 3. Insta-Kill Trap (35-block abyssal drop onto pointed dripstone stalagmites)
     */
    private static void buildTrapsWing(ServerWorld world, BlockPos origin, int floorY, Random rand,
                                       List<BlockPos> fakeTraps, List<BlockPos> oneHpTraps) {
        int wingStartX = origin.getX() + SIZE_X / 2 + 10;
        int wingZ = origin.getZ() + SIZE_Z / 2;

        // Corridor dividing walls
        for (int z = wingZ - 10; z <= wingZ + 10; z++) {
            world.setBlockState(new BlockPos(wingStartX, floorY + 1, z), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(wingStartX, floorY + 2, z), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        // Archway opening into traps wing
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            world.setBlockState(new BlockPos(wingStartX, y, wingZ), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(wingStartX, y, wingZ + 1), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // --- TRAP 1: The Fake / Decoy Trap Corridor ---
        // Conspicuous pressure plates that click and emit harmless campfire smoke to psych out the player
        BlockPos fakePlatePos1 = new BlockPos(wingStartX + 4, floorY + 1, wingZ - 3);
        world.setBlockState(fakePlatePos1.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(fakePlatePos1, Blocks.STONE_PRESSURE_PLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(fakePlatePos1);

        BlockPos fakePlatePos2 = new BlockPos(wingStartX + 7, floorY + 1, wingZ + 2);
        world.setBlockState(fakePlatePos2.down(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(fakePlatePos2, Blocks.STONE_PRESSURE_PLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
        fakeTraps.add(fakePlatePos2);

        // --- TRAP 2: The Brutal 1-HP Spore Trap ---
        // Hidden under false moss carpet: when stepped on, erupts in toxic spore burst and leaves player at 1 HP
        BlockPos oneHpPlatePos = new BlockPos(wingStartX + 6, floorY + 1, wingZ - 5);
        world.setBlockState(oneHpPlatePos.down(), Blocks.MUD_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(oneHpPlatePos, Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        oneHpTraps.add(oneHpPlatePos);

        // Dispenser loaded with Poison II & Slowness hidden in wall near 1-HP trap
        BlockPos dispenserPos = new BlockPos(wingStartX + 6, floorY + 2, wingZ - 7);
        world.setBlockState(dispenserPos, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(dispenserPos) instanceof DispenserBlockEntity dispenser) {
            ItemStack poisonArrow = new ItemStack(Items.TIPPED_ARROW, 8);
            PotionUtil.setPotion(poisonArrow, Potions.STRONG_POISON);
            dispenser.setStack(4, poisonArrow);
        }

        // --- TRAP 3: The Insta-Kill Pitfall Chasm (35 Blocks Deep + Pointed Dripstone) ---
        // A false stone walkway leading to a chest on a tempting pedestal
        int chasmX = wingStartX + 8;
        int chasmZ = wingZ + 4;

        // Carve deep death pit
        int pitDepth = 35;
        int bottomY = Math.max(-60, floorY - pitDepth);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos colPos = new BlockPos(chasmX + dx, floorY, chasmZ + dz);
                // Hollow out the pit
                for (int y = bottomY; y <= floorY; y++) {
                    world.setBlockState(new BlockPos(colPos.getX(), y, colPos.getZ()), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                // Bedrock & Deepslate walls and floor
                world.setBlockState(new BlockPos(colPos.getX(), bottomY - 1, colPos.getZ()), Blocks.DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);

                // Lethal pointed dripstone stalagmites at the bottom
                world.setBlockState(new BlockPos(colPos.getX(), bottomY, colPos.getZ()),
                        Blocks.POINTED_DRIPSTONE.getDefaultState()
                                .with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
                                .with(PointedDripstoneBlock.THICKNESS, net.minecraft.block.enums.Thickness.TIP_MERGE),
                        Block.NOTIFY_LISTENERS);
            }
        }

        // Fragile walkway over the chasm (Scaffolding disguised with moss carpet)
        for (int dz = -2; dz <= 1; dz++) {
            BlockPos bridgePos = new BlockPos(chasmX, floorY, chasmZ + dz);
            world.setBlockState(bridgePos, Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(bridgePos.up(), Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // The lure: A treasure chest perched across the fragile pit
        BlockPos lureChestPos = new BlockPos(chasmX, floorY + 1, chasmZ + 2);
        world.setBlockState(lureChestPos.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(lureChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(lureChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }
    }

    /**
     * Builds the West Wing: Overgrown Crypt & Garden of Whispers.
     * Contains ancient stone shelves, water pools, and a secondary exploration chest.
     */
    private static void buildCryptWing(ServerWorld world, BlockPos origin, int floorY, Random rand) {
        int wingStartX = origin.getX() + SIZE_X / 2 - 12;
        int wingZ = origin.getZ() + SIZE_Z / 2;

        // Crypt alcoves with decorative bookshelves and cobwebs
        for (int dz = -8; dz <= 8; dz += 4) {
            BlockPos alcove = new BlockPos(wingStartX - 6, floorY + 1, wingZ + dz);
            world.setBlockState(alcove, Blocks.BOOKSHELF.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(alcove.up(), Blocks.COBWEB.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(alcove.offset(Direction.EAST), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), Block.NOTIFY_LISTENERS);
        }

        // Crypt exploration chest
        BlockPos cryptChestPos = new BlockPos(wingStartX - 8, floorY + 1, wingZ);
        world.setBlockState(cryptChestPos.down(), Blocks.MUD_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cryptChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(cryptChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }
    }

    /**
     * Builds the North Altar and the Rootbound Axe Sealed Vault:
     * - Grand elevated dais with steps
     * - Primary shrine offering chest
     * - Root-sealed vault: enclosed by impenetrable ancient roots
     * - Only clearable using the Rootbound Axe
     */
    private static BlockPos buildNorthAltarAndVault(ServerWorld world, BlockPos origin, int floorY, Random rand,
                                                   List<BlockPos> rootBarrierPositions) {
        int centerX = origin.getX() + SIZE_X / 2;
        int altarZ = origin.getZ() + 10;

        // Elevated Dais (3-step raised stone platform)
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos daisPos = new BlockPos(centerX + dx, floorY + 1, altarZ + dz);
                world.setBlockState(daisPos, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                if (Math.abs(dx) <= 5 && Math.abs(dz) <= 2) {
                    world.setBlockState(daisPos.up(), Blocks.DEEPSLATE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }

        // Mossy stone stairs leading up to the dais
        for (int dx = -3; dx <= 3; dx++) {
            world.setBlockState(new BlockPos(centerX + dx, floorY + 1, altarZ + 5),
                    Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
            world.setBlockState(new BlockPos(centerX + dx, floorY + 2, altarZ + 3),
                    Blocks.POLISHED_DEEPSLATE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), Block.NOTIFY_LISTENERS);
        }

        // Primary Altar Chest
        BlockPos altarChestPos = new BlockPos(centerX, floorY + 3, altarZ);
        world.setBlockState(altarChestPos.down(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(altarChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(altarChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE, rand.nextLong());
        }

        // --- THE ROOTBOUND VAULT (Behind the Altar) ---
        // A secret 6x6 chamber sealed by thick mangrove roots, muddy roots, and dense weeping vines
        int vaultZ = altarZ - 7;
        BlockPos sealedChestPos = new BlockPos(centerX, floorY + 1, vaultZ);

        // Hollow out vault chamber
        for (int vx = centerX - 3; vx <= centerX + 3; vx++) {
            for (int vz = vaultZ - 3; vz <= vaultZ + 1; vz++) {
                for (int vy = floorY + 1; vy <= floorY + 5; vy++) {
                    world.setBlockState(new BlockPos(vx, vy, vz), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                world.setBlockState(new BlockPos(vx, floorY, vz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }

        // Place the Root-Sealed Offering Chest
        world.setBlockState(sealedChestPos.down(), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sealedChestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(sealedChestPos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(LOOT_SHRINE_SEALED, rand.nextLong());
        }

        // Seal the front entrance of the vault with impenetrable ancient roots (3 wide, 3 tall)
        for (int rx = centerX - 2; rx <= centerX + 2; rx++) {
            for (int ry = floorY + 1; ry <= floorY + 3; ry++) {
                BlockPos rootPos = new BlockPos(rx, ry, vaultZ + 2);
                BlockState rootState = (ry == floorY + 3) ? Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState() :
                        (rand.nextBoolean() ? Blocks.MANGROVE_ROOTS.getDefaultState() : Blocks.WEEPING_VINES.getDefaultState());
                world.setBlockState(rootPos, rootState, Block.NOTIFY_LISTENERS);
                rootBarrierPositions.add(rootPos);
            }
        }

        // Ancient Inscribed Sign in front of the root barrier
        BlockPos signPos = new BlockPos(centerX + 2, floorY + 1, vaultZ + 3);
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

        return sealedChestPos;
    }

    /**
     * Creates intentional anime-style roof breaches, hanging jungle canopies, weeping vines, and glow flora.
     */
    private static void drapeVegetationAndVines(ServerWorld world, BlockPos origin, int floorY, Random rand) {
        int minX = origin.getX() + 4;
        int maxX = origin.getX() + SIZE_X - 5;
        int minZ = origin.getZ() + 4;
        int maxZ = origin.getZ() + SIZE_Z - 5;

        // Natural vegetation clusters across the floor
        for (int x = minX; x <= maxX; x += 3) {
            for (int z = minZ; z <= maxZ; z += 3) {
                if (rand.nextFloat() < 0.35f) {
                    BlockPos p = new BlockPos(x, floorY + 1, z);
                    if (world.getBlockState(p).isAir() && world.getBlockState(p.down()).isOpaqueFullCube(world, p.down())) {
                        float vType = rand.nextFloat();
                        if (vType < 0.35f) {
                            world.setBlockState(p, Blocks.FERN.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else if (vType < 0.65f) {
                            world.setBlockState(p, Blocks.MOSS_CARPET.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else if (vType < 0.85f) {
                            world.setBlockState(p, Blocks.FLOWERING_AZALEA.getDefaultState(), Block.NOTIFY_LISTENERS);
                        } else {
                            world.setBlockState(p, Blocks.BAMBOO.getDefaultState().with(BambooBlock.LEAVES, net.minecraft.block.enums.BambooLeaves.LARGE), Block.NOTIFY_LISTENERS);
                        }
                    }
                }
            }
        }

        // Hanging jungle vines and glow berries from the ceiling perimeter
        int ceilingY = floorY + HEIGHT - 2;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                if (rand.nextFloat() < 0.20f) {
                    int vineLen = 2 + rand.nextInt(5);
                    for (int len = 0; len < vineLen; len++) {
                        BlockPos vinePos = new BlockPos(x, ceilingY - len, z);
                        if (world.getBlockState(vinePos).isAir()) {
                            if (rand.nextFloat() < 0.3f) {
                                world.setBlockState(vinePos, Blocks.CAVE_VINES.getDefaultState().with(CaveVines.BERRIES, true), Block.NOTIFY_LISTENERS);
                            } else {
                                world.setBlockState(vinePos, Blocks.WEEPING_VINES_PLANT.getDefaultState(), Block.NOTIFY_LISTENERS);
                            }
                        }
                    }
                }
            }
        }
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
}
