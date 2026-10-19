package com.elemental.network;

import com.elemental.Elemental;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class SeismicColossusNetworking {
    public static final Identifier S2C_SCREEN_SHAKE = Elemental.id("seismic_screen_shake");

    public static void sendScreenShake(ServerPlayerEntity player, float intensity, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(intensity);
        buf.writeInt(durationTicks);
        ServerPlayNetworking.send(player, S2C_SCREEN_SHAKE, buf);
    }

    public static void sendScreenShakeNearby(ServerWorld world, Vec3d epicenter, double radius, float maxIntensity, int durationTicks) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            double distSq = player.squaredDistanceTo(epicenter);
            if (distSq <= radius * radius) {
                double dist = Math.sqrt(distSq);
                float falloff = (float) (1.0 - (dist / radius));
                if (falloff > 0.05f) {
                    sendScreenShake(player, maxIntensity * falloff, durationTicks);
                }
            }
        }
    }
}
