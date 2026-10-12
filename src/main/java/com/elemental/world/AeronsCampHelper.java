package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.config.ElementalConfig;
import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.entity.ModEntities;
import com.elemental.quest.QuestSaveData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.List;

public class AeronsCampHelper {

    public static void initializeWorld(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        // DO NOT TELEPORT THE PLAYER. Player remains at natural seed spawn.
        if (saveData.isTentGenerated() && saveData.getAeronsTentLocation() != null) {
            ensureGuideAtCamp(world, saveData.getAeronsTentLocation(), saveData);
            return;
        }

        // Generate Aeron's rustic tent camp ~120 blocks away from world spawn
        BlockPos worldSpawn = world.getSpawnPos();
        BlockPos rawCampPos = worldSpawn.add(90, 0, 75);
        BlockPos safeCampPos = findSafeSurface(world, rawCampPos);

        buildTentAndCamp(world, safeCampPos);

        if (ElementalConfig.get().enableIntroNpc) {
            spawnAeron(world, safeCampPos);
            saveData.setGuideSpawned(true);
        }

        saveData.setAeronsTentLocation(safeCampPos);
        saveData.setTentGenerated(true);
        saveData.markDirty();
        Elemental.LOGGER.info("Generated Aeron's Tent Camp at safe ground location: {}", safeCampPos);
    }

    public static boolean isFoliageOrTree(BlockState state) {
        if (state.isAir()) return true;
        if (state.isLiquid()) return true;
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

        BlockPos.Mutable checkPos = new BlockPos.Mutable(x, topY, z);
        while (checkPos.getY() > world.getBottomY() + 5) {
            BlockState belowState = world.getBlockState(checkPos.down());
            if (!isFoliageOrTree(belowState)) {
                return checkPos.toImmutable();
            }
            checkPos.move(Direction.DOWN);
        }
        return new BlockPos(x, Math.max(topY, 64), z);
    }

    public static BlockPos findSafeSurface(ServerWorld world, BlockPos centerPos) {
        BlockPos bestPos = null;
        int bestScore = -100;

        // Search in a spiral / grid around centerPos within 32 blocks
        for (int r = 0; r <= 32; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;

                    int testX = centerPos.getX() + dx;
                    int testZ = centerPos.getZ() + dz;
                    BlockPos surface = findTrueGroundSurface(world, testX, testZ);
                    BlockState ground = world.getBlockState(surface.down());

                    // Must be on solid ground (not water, not tree leaves/logs)
                    if (ground.isLiquid() || isFoliageOrTree(ground)) continue;

                    int score = 0;
                    if (ground.isIn(BlockTags.DIRT) || ground.isOf(Blocks.GRASS_BLOCK)) {
                        score += 10;
                    } else if (ground.isIn(BlockTags.BASE_STONE_OVERWORLD) || ground.isIn(BlockTags.SAND)) {
                        score += 5;
                    }

                    // Check corners of camp footprint
                    BlockPos p1 = findTrueGroundSurface(world, testX - 2, testZ - 2);
                    BlockPos p2 = findTrueGroundSurface(world, testX + 2, testZ + 2);
                    int heightDiff = Math.abs(p1.getY() - surface.getY()) + Math.abs(p2.getY() - surface.getY());
                    score -= heightDiff;

                    if (score > bestScore) {
                        bestScore = score;
                        bestPos = surface;
                    }
                }
            }
            if (bestScore >= 8) break; // Found a great flat clearing
        }

        return bestPos != null ? bestPos : findTrueGroundSurface(world, centerPos.getX(), centerPos.getZ());
    }

    public static void buildTentAndCamp(ServerWorld world, BlockPos center) {
        // 1. Flatten camp clearing (7x7) on true ground
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos ground = center.add(dx, -1, dz);
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    world.setBlockState(ground, Blocks.DIRT_PATH.getDefaultState());
                } else {
                    world.setBlockState(ground, Blocks.COARSE_DIRT.getDefaultState());
                }
                // Clear air & foliage above up to 6 blocks high
                for (int dy = 0; dy <= 6; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.AIR.getDefaultState());
                }
            }
        }

        // 2. Campfire with Hay Bale underneath (produces tall signal smoke column)
        BlockPos campfirePos = center.add(0, 0, 1);
        world.setBlockState(campfirePos.down(), Blocks.HAY_BLOCK.getDefaultState());
        world.setBlockState(campfirePos, Blocks.CAMPFIRE.getDefaultState());

        // Log seats around campfire
        world.setBlockState(center.add(-1, 0, 1), Blocks.OAK_LOG.getDefaultState());
        world.setBlockState(center.add(1, 0, 1), Blocks.OAK_LOG.getDefaultState());

        // 3. Rustic A-frame Tent (5x4 footprint on north side)
        // Tent base logs
        for (int dz = -3; dz <= -1; dz++) {
            world.setBlockState(center.add(-2, 0, dz), Blocks.SPRUCE_LOG.getDefaultState());
            world.setBlockState(center.add(2, 0, dz), Blocks.SPRUCE_LOG.getDefaultState());
        }

        // Wool canopy
        for (int dz = -3; dz <= -1; dz++) {
            // Walls
            world.setBlockState(center.add(-2, 1, dz), Blocks.WHITE_WOOL.getDefaultState());
            world.setBlockState(center.add(2, 1, dz), Blocks.WHITE_WOOL.getDefaultState());
            world.setBlockState(center.add(-1, 2, dz), Blocks.ORANGE_WOOL.getDefaultState());
            world.setBlockState(center.add(1, 2, dz), Blocks.ORANGE_WOOL.getDefaultState());
            // Ridge
            world.setBlockState(center.add(0, 3, dz), Blocks.WHITE_WOOL.getDefaultState());
        }

        // Back wall
        world.setBlockState(center.add(-1, 1, -3), Blocks.WHITE_WOOL.getDefaultState());
        world.setBlockState(center.add(0, 1, -3), Blocks.WHITE_WOOL.getDefaultState());
        world.setBlockState(center.add(1, 1, -3), Blocks.WHITE_WOOL.getDefaultState());
        world.setBlockState(center.add(0, 2, -3), Blocks.ORANGE_WOOL.getDefaultState());

        // Tent floor: carpets
        world.setBlockState(center.add(-1, 0, -2), Blocks.WHITE_CARPET.getDefaultState());
        world.setBlockState(center.add(0, 0, -2), Blocks.ORANGE_CARPET.getDefaultState());
        world.setBlockState(center.add(1, 0, -2), Blocks.WHITE_CARPET.getDefaultState());

        // Bed cot
        world.setBlockState(center.add(-1, 0, -1), Blocks.CYAN_BED.getDefaultState());

        // Lantern hanging from tent entrance
        world.setBlockState(center.add(0, 2, -1), Blocks.LANTERN.getDefaultState());

        // 4. Crafting Table beside tent (Weaponer completely removed from camp)
        world.setBlockState(center.add(2, 0, 0), Blocks.CRAFTING_TABLE.getDefaultState());

        // 5. Aeron's Supply Stash Chest inside tent
        BlockPos chestPos = center.add(1, 0, -1);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setCustomName(Text.translatable("container.elemental.aerons_chest"));
            // 16 Cooked Beef (best early game food)
            chest.setStack(13, new ItemStack(Items.COOKED_BEEF, 16));
            // Iron Sword, Iron Pickaxe, Iron Axe
            chest.setStack(11, new ItemStack(Items.IRON_SWORD));
            chest.setStack(12, new ItemStack(Items.IRON_PICKAXE));
            chest.setStack(15, new ItemStack(Items.IRON_AXE));
            chest.markDirty();
        }
    }

    public static void spawnAeron(ServerWorld world, BlockPos campPos) {
        BlockPos guidePos = campPos.add(0, 0, 0);

        ChronicleGuideEntity guide = ModEntities.CHRONICLE_GUIDE.create(world);
        if (guide != null) {
            guide.refreshPositionAndAngles(
                    guidePos.getX() + 0.5,
                    guidePos.getY(),
                    guidePos.getZ() + 0.5,
                    0.0f,
                    0.0f
            );
            guide.setHomePos(guidePos);
            guide.setPersistent();
            world.spawnEntity(guide);
            Elemental.LOGGER.info("Spawned Aeron, the Last Skywright at camp: {}", guidePos);
        }
    }

    public static void ensureGuideAtCamp(ServerWorld world, BlockPos campPos, QuestSaveData saveData) {
        if (!ElementalConfig.get().enableIntroNpc) return;
        List<ChronicleGuideEntity> guides = world.getEntitiesByClass(
                ChronicleGuideEntity.class,
                new net.minecraft.util.math.Box(campPos).expand(32.0),
                e -> true
        );
        if (guides.isEmpty()) {
            spawnAeron(world, campPos);
            saveData.setGuideSpawned(true);
            saveData.markDirty();
        }
    }
}
