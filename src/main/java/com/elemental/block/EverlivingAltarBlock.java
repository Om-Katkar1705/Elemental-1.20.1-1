package com.elemental.block;

import com.elemental.block.entity.EverlivingAltarBlockEntity;
import com.elemental.block.entity.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Ritual Altar block for summoning Sir Solvane, the Everliving Knight.
 * When Dry Heat, Snow, and Tree element items are thrown onto this altar,
 * the elements are consumed and the boss is summoned at the arena center.
 */
public class EverlivingAltarBlock extends BlockWithEntity {

    public EverlivingAltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EverlivingAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.EVERLIVING_ALTAR_BLOCK_ENTITY, EverlivingAltarBlockEntity::tick);
    }

    @Deprecated
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            player.sendMessage(Text.literal("§6§l[Everliving Altar]§r §eThrow §6Dry Heat§e, §bSnow§e, and §aTree§e elements upon this altar to awaken §cSir Solvane§e!"), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;

        if (random.nextFloat() < 0.7f) {
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
        if (random.nextFloat() < 0.3f) {
            world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.01, 0.0);
        }
    }
}
