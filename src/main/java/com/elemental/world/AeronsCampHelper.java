package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import com.elemental.config.ElementalConfig;
import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.entity.ModEntities;
import com.elemental.quest.QuestSaveData;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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
        Elemental.LOGGER.info("Generated Aeron's Tent Camp at safe location: {}", safeCampPos);
    }

    public static BlockPos findSafeSurface(ServerWorld world, BlockPos pos) {
        // Force chunk generation & loading
        world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        if (topY <= world.getBottomY() + 4 || topY > 250) {
            topY = 70;
        }

        BlockPos checkPos = new BlockPos(pos.getX(), topY, pos.getZ());

        // Descend to solid ground
        while (checkPos.getY() > world.getBottomY() + 5 && (world.getBlockState(checkPos.down()).isAir() || world.getBlockState(checkPos.down()).isLiquid())) {
            checkPos = checkPos.down();
        }

        // Ascend if inside solid block
        while (checkPos.getY() < world.getTopY() && !world.getBlockState(checkPos).isAir()) {
            checkPos = checkPos.up();
        }

        return checkPos;
    }

    public static void buildTentAndCamp(ServerWorld world, BlockPos center) {
        // 1. Flatten camp clearing (7x7)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos ground = center.add(dx, -1, dz);
                if (Math.abs(dx) + Math.abs(dz) <= 4) {
                    world.setBlockState(ground, Blocks.DIRT_PATH.getDefaultState());
                } else {
                    world.setBlockState(ground, Blocks.COARSE_DIRT.getDefaultState());
                }
                // Clear air above
                for (int dy = 0; dy <= 4; dy++) {
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

        // 4. Weaponer station beside tent
        world.setBlockState(center.add(2, 0, 0), ModBlocks.WEAPONER.getDefaultState());

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
