package com.elemental.block;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ElementalAnvilBlock extends AnvilBlock {
    private static final Text TITLE = Text.translatable("container.elemental.elemental_anvil");

    public ElementalAnvilBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
        return ActionResult.CONSUME;
    }

    @Nullable
    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new AnvilScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)),
                TITLE
        );
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);

        // Circling particles orbiting the Elemental Anvil
        double time = (world.getTime() % 360) * 0.15;
        for (int i = 0; i < 3; i++) {
            double offsetAngle = time + (i * (2.0 * Math.PI / 3.0)) + random.nextDouble() * 0.2;
            double radius = 0.55 + random.nextDouble() * 0.15;
            double px = pos.getX() + 0.5 + Math.cos(offsetAngle) * radius;
            double py = pos.getY() + 0.25 + (i * 0.22) + random.nextDouble() * 0.15;
            double pz = pos.getZ() + 0.5 + Math.sin(offsetAngle) * radius;

            // Velocity tangent to circular orbit
            double vx = -Math.sin(offsetAngle) * 0.04;
            double vy = 0.01 + random.nextDouble() * 0.02;
            double vz = Math.cos(offsetAngle) * 0.04;

            world.addParticle(ParticleTypes.ENCHANT, px, py, pz, vx, vy, vz);

            if (random.nextFloat() < 0.4f) {
                world.addParticle(ParticleTypes.GLOW, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
            }
            if (random.nextFloat() < 0.2f) {
                world.addParticle(ParticleTypes.PORTAL, px, py, pz,
                        (random.nextDouble() - 0.5) * 0.05,
                        (random.nextDouble() - 0.5) * 0.05,
                        (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("§b✦ Infused with Primal Celestial Runes").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("§7The only anvil capable of forging §ecustom elemental enchantments§7 onto weapons.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§8Created by applying an Upgrade Key to a standard Anvil.").formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, world, tooltip, options);
    }
}
