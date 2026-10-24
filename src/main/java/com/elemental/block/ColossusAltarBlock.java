package com.elemental.block;

import com.elemental.block.entity.ColossusAltarBlockEntity;
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
 * Ritual Altar block for summoning Terrakon, the Seismic Colossus.
 * When Dry Heat, Snow, Tree, Ocean, Mountain, and Fire element items are thrown
 * onto this altar, the elements are consumed and the Seismic Colossus is awakened.
 */
public class ColossusAltarBlock extends BlockWithEntity {

    public ColossusAltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ColossusAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.COLOSSUS_ALTAR_BLOCK_ENTITY, ColossusAltarBlockEntity::tick);
    }

    @Deprecated
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            player.sendMessage(Text.literal("§6§l[Colossus Altar]§r §eOffer all 6 elements (§6Dry Heat§e, §bSnow§e, §aTree§e, §9Ocean§e, §7Mountain§e, and §cFire§e) upon this altar to awaken §4§lTerrakon, the Seismic Colossus§e!"), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;

        if (random.nextFloat() < 0.6f) {
            world.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
        if (random.nextFloat() < 0.4f) {
            world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
        }
        if (random.nextFloat() < 0.25f) {
            world.addParticle(ParticleTypes.PORTAL, x, y, z, (random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
        }
    }
}
