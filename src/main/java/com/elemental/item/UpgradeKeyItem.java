package com.elemental.item;

import com.elemental.block.ElementalAnvilBlock;
import com.elemental.block.ModBlocks;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UpgradeKeyItem extends Item {

    public UpgradeKeyItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        PlayerEntity player = context.getPlayer();

        if (state.isIn(BlockTags.ANVIL) && !state.isOf(ModBlocks.ELEMENTAL_ANVIL)) {
            return transformAnvil(world, pos, state, player, context.getStack());
        }

        return super.useOnBlock(context);
    }

    public static ActionResult transformAnvil(World world, BlockPos pos, BlockState state, @Nullable PlayerEntity player, ItemStack keyStack) {
        if (!state.isIn(BlockTags.ANVIL) || state.isOf(ModBlocks.ELEMENTAL_ANVIL)) {
            return ActionResult.PASS;
        }

        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        Direction facing = Direction.NORTH;
        if (state.contains(AnvilBlock.FACING)) {
            facing = state.get(AnvilBlock.FACING);
        }

        BlockState newState = ModBlocks.ELEMENTAL_ANVIL.getDefaultState();
        if (newState.contains(ElementalAnvilBlock.FACING)) {
            newState = newState.with(ElementalAnvilBlock.FACING, facing);
        }

        world.setBlockState(pos, newState, Block.NOTIFY_ALL);

        // Soundscape
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.5f, 0.85f);
        world.playSound(null, pos, SoundEvents.ITEM_TOTEM_USE, SoundCategory.BLOCKS, 0.75f, 1.4f);

        // Visual bursts
        if (world instanceof ServerWorld serverWorld) {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.6;
            double cz = pos.getZ() + 0.5;

            serverWorld.spawnParticles(ParticleTypes.ENCHANT, cx, cy + 0.3, cz, 60, 0.4, 0.4, 0.4, 0.5);
            serverWorld.spawnParticles(ParticleTypes.FLASH, cx, cy + 0.4, cz, 2, 0.1, 0.1, 0.1, 0.0);
            serverWorld.spawnParticles(ParticleTypes.END_ROD, cx, cy + 0.3, cz, 30, 0.3, 0.3, 0.3, 0.08);
            serverWorld.spawnParticles(ParticleTypes.GLOW, cx, cy + 0.2, cz, 35, 0.35, 0.35, 0.35, 0.05);
        }

        // Consume 1 key
        if (player != null && !player.getAbilities().creativeMode) {
            keyStack.decrement(1);
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.translatable("message.elemental.anvil_transformed").formatted(Formatting.AQUA, Formatting.BOLD), true);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§6✦ Ancient Boss Relic").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§7Right-click any standard §fAnvil§7 to transform it into an §bElemental Anvil§7.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§8Dropped by Sir Solvane, the Everliving Knight.").formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
