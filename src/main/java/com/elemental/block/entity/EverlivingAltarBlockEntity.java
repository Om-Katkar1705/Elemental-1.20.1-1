package com.elemental.block.entity;

import com.elemental.entity.EverlivingKnightEntity;
import com.elemental.entity.ModEntities;
import com.elemental.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Block entity for the Everliving Altar.
 * Detects thrown Dry Heat, Snow, and Tree element items, consumes them,
 * and summons Sir Solvane, the Everliving Knight at the arena center.
 */
public class EverlivingAltarBlockEntity extends BlockEntity {
    private int cooldown = 0;

    public EverlivingAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EVERLIVING_ALTAR_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, EverlivingAltarBlockEntity blockEntity) {
        if (world.isClient()) return;

        if (blockEntity.cooldown > 0) {
            blockEntity.cooldown--;
            return;
        }

        // Check every 5 ticks for efficiency
        if (world.getTime() % 5 != 0) return;

        // Detect dropped items on or directly above the altar
        Box detectionBox = new Box(pos).expand(1.2, 0.8, 1.2);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, detectionBox, item -> item.isAlive() && !item.getStack().isEmpty());

        ItemEntity dryHeatEntity = null;
        ItemEntity snowEntity = null;
        ItemEntity treeEntity = null;

        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            if (dryHeatEntity == null && stack.isOf(ModItems.DRY_HEAT_ELEMENT)) {
                dryHeatEntity = item;
            } else if (snowEntity == null && stack.isOf(ModItems.SNOW_ELEMENT)) {
                snowEntity = item;
            } else if (treeEntity == null && stack.isOf(ModItems.TREE_ELEMENT)) {
                treeEntity = item;
            }
        }

        // If all 3 elements are thrown onto the altar
        if (dryHeatEntity != null && snowEntity != null && treeEntity != null) {
            consumeItem(dryHeatEntity);
            consumeItem(snowEntity);
            consumeItem(treeEntity);

            blockEntity.cooldown = 140; // ~7 seconds cooldown before another ritual
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
        world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.BLOCKS, 1.2f, 0.8f);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 1.5f, 1.0f);
        world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Visual FX
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, spawnX, spawnY + 0.5, spawnZ, 60, 0.8, 0.8, 0.8, 0.12);
        world.spawnParticles(ParticleTypes.EXPLOSION, spawnX, spawnY + 0.5, spawnZ, 4, 0.3, 0.3, 0.3, 0.0);
        world.spawnParticles(ParticleTypes.FLASH, spawnX, spawnY + 1.0, spawnZ, 3, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, spawnX, spawnY + 1.0, spawnZ, 40, 0.5, 1.0, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.LAVA, spawnX, spawnY + 0.5, spawnZ, 25, 0.5, 0.5, 0.5, 0.1);

        // Spawn Sir Solvane, the Everliving Knight!
        EverlivingKnightEntity knight = ModEntities.EVERLIVING_KNIGHT.create(world);
        if (knight != null) {
            knight.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 180.0f, 0.0f);
            knight.setArenaCenter(pos);
            world.spawnEntity(knight);
            knight.triggerAnimation("phase_two"); // Dramatic roar stance
        }

        // Announcement to nearby players
        Text announcement = Text.literal("§4§l[!] §cSir Solvane, the Everliving Knight has awakened upon the altar!");
        world.getPlayers().forEach(p -> {
            if (p.squaredDistanceTo(spawnX, spawnY, spawnZ) < 64.0 * 64.0) {
                p.sendMessage(announcement, false);
            }
        });
    }
}
