package com.elemental.util;

import com.elemental.item.SunforgedScimitarItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public class SunforgedHeatHandler {
    // Process heat ticking once per second (20 ticks) for performance and subtle accumulation
    public static final int HEAT_TICK_INTERVAL = 20;
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= HEAT_TICK_INTERVAL) {
                tickCounter = 0;
                processHeat(server);
            }
        });
    }

    private static void processHeat(MinecraftServer server) {
        if (server == null || server.getPlayerManager() == null) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }

            World world = player.getWorld();
            if (world == null) {
                continue;
            }

            checkAndApplyHeat(player, player.getMainHandStack(), world);
            checkAndApplyHeat(player, player.getOffHandStack(), world);
        }
    }

    private static void checkAndApplyHeat(ServerPlayerEntity player, ItemStack stack, World world) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof SunforgedScimitarItem) {

            boolean isDirectSunlight = world.isDay()
                    && !world.isRaining()
                    && world.isSkyVisible(player.getBlockPos())
                    && world.getLightLevel(LightType.SKY, player.getBlockPos()) >= 12;

            int currentHeat = SunforgedScimitarItem.getHeatLevel(stack);
            int newHeat = currentHeat;

            if (isDirectSunlight) {
                newHeat = Math.min(SunforgedScimitarItem.MAX_HEAT, currentHeat + SunforgedScimitarItem.HEAT_GAIN_RATE);
            } else {
                newHeat = Math.max(0, currentHeat - SunforgedScimitarItem.HEAT_DECAY_RATE);
            }

            if (newHeat != currentHeat) {
                SunforgedScimitarItem.setHeatLevel(stack, newHeat);
            }

            if (newHeat >= SunforgedScimitarItem.HEAT_DAMAGE_THRESHOLD && SunforgedScimitarItem.SELF_DAMAGE_ENABLED) {
                player.damage(player.getDamageSources().hotFloor(), SunforgedScimitarItem.HEAT_SELF_DAMAGE);
            }
        }
    }
}
