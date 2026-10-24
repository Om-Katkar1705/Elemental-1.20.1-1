package com.elemental.block.entity;

import com.elemental.entity.ModEntities;
import com.elemental.entity.SeismicColossusEntity;
import com.elemental.item.ModItems;
import com.elemental.network.SeismicColossusNetworking;
import com.elemental.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Block entity for the Colossus Altar.
 * Detects thrown Dry Heat, Snow, Tree, Ocean, Mountain, and Fire element items,
 * consumes them, and summons Terrakon, the Seismic Colossus.
 */
public class ColossusAltarBlockEntity extends BlockEntity {
    private int cooldown = 0;

    public ColossusAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLOSSUS_ALTAR_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ColossusAltarBlockEntity blockEntity) {
        if (world.isClient()) return;

        if (blockEntity.cooldown > 0) {
            blockEntity.cooldown--;
            return;
        }

        // Check every 5 ticks for performance
        if (world.getTime() % 5 != 0) return;

        // Detect dropped items on or directly above the altar
        Box detectionBox = new Box(pos).expand(1.5, 1.0, 1.5);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, detectionBox, item -> item.isAlive() && !item.getStack().isEmpty());

        ItemEntity dryHeatEntity = null;
        ItemEntity snowEntity = null;
        ItemEntity treeEntity = null;
        ItemEntity oceanEntity = null;
        ItemEntity mountainEntity = null;
        ItemEntity fireEntity = null;

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            if (dryHeatEntity == null && stack.isOf(ModItems.DRY_HEAT_ELEMENT)) {
                dryHeatEntity = item;
            } else if (snowEntity == null && stack.isOf(ModItems.SNOW_ELEMENT)) {
                snowEntity = item;
            } else if (treeEntity == null && stack.isOf(ModItems.TREE_ELEMENT)) {
                treeEntity = item;
            } else if (oceanEntity == null && stack.isOf(ModItems.OCEAN_ELEMENT)) {
                oceanEntity = item;
            } else if (mountainEntity == null && stack.isOf(ModItems.MOUNTAIN_ELEMENT)) {
                mountainEntity = item;
            } else if (fireEntity == null && stack.isOf(ModItems.FIRE_ELEMENT)) {
                fireEntity = item;
            }
        }

        // Check if all 6 elements are present on the altar
        if (dryHeatEntity != null && snowEntity != null && treeEntity != null &&
            oceanEntity != null && mountainEntity != null && fireEntity != null) {

            consumeItem(dryHeatEntity);
            consumeItem(snowEntity);
            consumeItem(treeEntity);
            consumeItem(oceanEntity);
            consumeItem(mountainEntity);
            consumeItem(fireEntity);

            blockEntity.cooldown = 160; // 8 seconds cooldown before another ritual
            performSummoningRitual((ServerWorld) world, pos);
        }
    }

    private static void consumeItem(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getStack();
        stack.decrement(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setStack(stack);
        }
    }

    private static void performSummoningRitual(ServerWorld world, BlockPos pos) {
        double spawnX = pos.getX() + 0.5;
        double spawnY = pos.getY() + 1.0;
        double spawnZ = pos.getZ() + 0.5;

        // Auditory feedback
        world.playSound(null, pos, ModSounds.SEISMIC_COLOSSUS_ROAR, SoundCategory.HOSTILE, 3.0f, 0.7f);
        world.playSound(null, pos, ModSounds.SEISMIC_COLOSSUS_SLAM, SoundCategory.HOSTILE, 2.5f, 0.6f);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.5f, 0.7f);
        world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 2.0f, 0.8f);
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.BLOCKS, 2.0f, 0.5f);

        // Visual FX & Tremors
        world.spawnParticles(ParticleTypes.EXPLOSION, spawnX, spawnY + 1.0, spawnZ, 8, 1.2, 1.2, 1.2, 0.0);
        world.spawnParticles(ParticleTypes.FLASH, spawnX, spawnY + 1.5, spawnZ, 5, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticles(ParticleTypes.LAVA, spawnX, spawnY + 0.5, spawnZ, 50, 1.2, 0.8, 1.2, 0.15);
        world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DEEPSLATE.getDefaultState()),
                spawnX, spawnY + 1.0, spawnZ, 120, 2.0, 1.5, 2.0, 0.25
        );
        world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.getDefaultState()),
                spawnX, spawnY + 1.5, spawnZ, 80, 1.5, 1.5, 1.5, 0.2
        );

        // Epic ground screen shake for nearby players
        SeismicColossusNetworking.sendScreenShakeNearby(world, new Vec3d(spawnX, spawnY, spawnZ), 50.0, 6.0f, 40);

        // Spawn Terrakon, the Seismic Colossus!
        SeismicColossusEntity colossus = ModEntities.SEISMIC_COLOSSUS.create(world);
        if (colossus != null) {
            colossus.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 180.0f, 0.0f);
            world.spawnEntity(colossus);
        }

        // Announcement to nearby players
        Text announcement = Text.literal("§4§l[!] §cTerrakon, the Seismic Colossus has arisen upon the Colossus Altar!");
        world.getPlayers().forEach(p -> {
            if (p.squaredDistanceTo(spawnX, spawnY, spawnZ) < 80.0 * 80.0) {
                p.sendMessage(announcement, false);
            }
        });
    }
}
