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
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

public class AeronsCampHelper {

    public static void initializeWorld(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        // DO NOT TELEPORT THE PLAYER. Player remains at natural seed spawn.
        if (saveData.isTentGenerated() && saveData.getAeronsTentLocation() != null) {
            ensureGuideAtCamp(world, saveData.getAeronsTentLocation(), saveData);
            return;
        }

        // Generate Aeron's rustic tent camp ~90-120 blocks away from world spawn on safe dry land
        BlockPos worldSpawn = world.getSpawnPos();
        BlockPos safeCampPos = findSafeSurface(world, worldSpawn);

        // Try placing from NBT Structure Template with programmatic fallback
        StructureTemplateManager templateManager = world.getStructureTemplateManager();
        Optional<StructureTemplate> templateOpt = templateManager.getTemplate(new Identifier(Elemental.MOD_ID, "aerons_camp"));
        if (templateOpt.isPresent()) {
            BlockPos placePos = safeCampPos.add(-4, -1, -4);
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
            templateOpt.get().place(world, placePos, placePos, new StructurePlacementData(), world.getRandom(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockPos chestPos = safeCampPos.add(1, 0, -1);
            if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setCustomName(Text.translatable("container.elemental.aerons_chest"));
                chest.setStack(13, new ItemStack(Items.COOKED_BEEF, 16));
                chest.setStack(11, new ItemStack(Items.IRON_SWORD));
                chest.setStack(12, new ItemStack(Items.IRON_PICKAXE));
                chest.setStack(15, new ItemStack(Items.IRON_AXE));
                chest.markDirty();
            }
            Elemental.LOGGER.info("Placed Aeron's Camp from NBT template at {}", placePos);
        } else {
            buildTentAndCamp(world, safeCampPos);
        }

        if (ElementalConfig.get().enableIntroNpc) {
            spawnAeron(world, safeCampPos);
            saveData.setGuideSpawned(true);
        }

        saveData.setAeronsTentLocation(safeCampPos);
        saveData.setTentGenerated(true);
        saveData.markDirty();
        Elemental.LOGGER.info("Generated Aeron's Tent Camp at safe dry land location: {}", safeCampPos);
    }

    public static boolean isLiquidOrWater(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isLiquid()) return true;
        if (!world.getFluidState(pos).isEmpty()) return true;
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.TALL_SEAGRASS)
                || state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)
                || state.isOf(Blocks.BUBBLE_COLUMN) || state.isOf(Blocks.ICE)
                || state.isOf(Blocks.FROSTED_ICE)) {
            return true;
        }
        return false;
    }

    public static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        try {
            var biome = world.getBiome(pos);
            if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isFoliageOrTree(BlockState state) {
        if (state.isAir()) return true;
        // Liquids are strictly handled by isLiquidOrWater and must NEVER be treated as foliage
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
        // If surface or sea level in this column has liquid/water, reject immediately
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

    public static boolean isSafeDryLandFootprint(ServerWorld world, BlockPos center, int radius, int maxAllowedElevationDiff) {
        if (center == null) return false;
        int seaLevel = world.getSeaLevel();
        if (center.getY() < seaLevel) return false;
        if (isWaterBiome(world, center)) return false;

        int centerY = center.getY();

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int checkX = center.getX() + dx;
                int checkZ = center.getZ() + dz;

                if (isLiquidOrWater(world, new BlockPos(checkX, seaLevel, checkZ))) {
                    return false;
                }
                if (isLiquidOrWater(world, new BlockPos(checkX, centerY, checkZ))) {
                    return false;
                }
                if (isWaterBiome(world, new BlockPos(checkX, centerY, checkZ))) {
                    return false;
                }

                BlockPos ground = findTrueGroundSurface(world, checkX, checkZ);
                if (ground == null) {
                    return false;
                }

                if (Math.abs(ground.getY() - centerY) > maxAllowedElevationDiff) {
                    return false;
                }
            }
        }
        return true;
    }

    public static BlockPos findSafeSurface(ServerWorld world, BlockPos originPos) {
        BlockPos bestPos = null;
        int bestScore = Integer.MIN_VALUE;

        // Search concentric rings from 60 to 240 blocks around originPos
        int[] radii = new int[]{60, 80, 100, 120, 140, 170, 200, 240};
        int numAngles = 16;

        for (int r : radii) {
            for (int i = 0; i < numAngles; i++) {
                double angle = (2.0 * Math.PI * i) / numAngles;
                int testX = originPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = originPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground == null) continue;

                if (!isSafeDryLandFootprint(world, ground, 3, 2)) continue;

                BlockState groundState = world.getBlockState(ground.down());
                int score = 100;

                // Prefer grass or dirt
                if (groundState.isIn(BlockTags.DIRT) || groundState.isOf(Blocks.GRASS_BLOCK)) {
                    score += 50;
                } else if (groundState.isIn(BlockTags.BASE_STONE_OVERWORLD) || groundState.isIn(BlockTags.SAND)) {
                    score += 20;
                }

                // Prefer sensible walking elevation (Y: 64..85)
                if (ground.getY() >= 64 && ground.getY() <= 85) {
                    score += 30;
                } else if (ground.getY() > 85) {
                    score -= (ground.getY() - 85);
                }

                // Slight preference for distance ~90-120
                score -= Math.abs(r - 100) / 2;

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

        // Expanded fallback if spawn was on a tiny coastal strip:
        int[] widerRadii = new int[]{280, 320, 360};
        for (int r : widerRadii) {
            for (int i = 0; i < 20; i++) {
                double angle = (2.0 * Math.PI * i) / 20;
                int testX = originPos.getX() + (int) Math.round(r * Math.cos(angle));
                int testZ = originPos.getZ() + (int) Math.round(r * Math.sin(angle));

                BlockPos ground = findTrueGroundSurface(world, testX, testZ);
                if (ground != null && isSafeDryLandFootprint(world, ground, 3, 3)) {
                    return ground;
                }
            }
        }

        // Local fallback: search directly around originPos for ANY valid dry land
        for (int dx = -32; dx <= 32; dx += 4) {
            for (int dz = -32; dz <= 32; dz += 4) {
                BlockPos ground = findTrueGroundSurface(world, originPos.getX() + dx, originPos.getZ() + dz);
                if (ground != null) {
                    return ground;
                }
            }
        }

        return originPos.add(90, 10, 75);
    }

    public static void buildTentAndCamp(ServerWorld world, BlockPos center) {
        // 1. Flatten camp clearing (7x7) on true ground with solid foundation
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                // Fill down beneath to guarantee solid dirt under the entire camp
                for (int fillY = -4; fillY <= -2; fillY++) {
                    BlockPos underPos = center.add(dx, fillY, dz);
                    if (world.getBlockState(underPos).isAir() || isLiquidOrWater(world, underPos)) {
                        world.setBlockState(underPos, Blocks.DIRT.getDefaultState());
                    }
                }
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
