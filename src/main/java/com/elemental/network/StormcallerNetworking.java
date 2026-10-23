package com.elemental.network;

import com.elemental.Elemental;
import com.elemental.combat.StormcallerServerCombatHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * Networking system for Stormcaller Hammer player animations, combat timing sync,
 * and first-person camera impact effects.
 */
public class StormcallerNetworking {

    // Client -> Server
    public static final Identifier C2S_MELEE_SWING = Elemental.id("stormcaller_melee_swing");
    public static final Identifier C2S_LIGHTNING_AIM = Elemental.id("stormcaller_lightning_aim");
    public static final Identifier C2S_LIGHTNING_RELEASE = Elemental.id("stormcaller_lightning_release");
    public static final Identifier C2S_GROUND_SMASH = Elemental.id("stormcaller_ground_smash");
    public static final Identifier THUNDERCLAP_PACKET_ID = Elemental.id("stormcaller_thunderclap");

    // Server -> Client
    public static final Identifier S2C_PLAY_ANIMATION = Elemental.id("stormcaller_play_anim");
    public static final Identifier S2C_GROUND_IMPACT = Elemental.id("stormcaller_ground_impact");

    public static MinecraftServer CURRENT_SERVER = null;

    public static void registerServerReceivers() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CURRENT_SERVER = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CURRENT_SERVER = null);

        // Melee Swing
        ServerPlayNetworking.registerGlobalReceiver(C2S_MELEE_SWING, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> StormcallerServerCombatHandler.startMeleeSwing(player));
        });

        // Lightning Aim Start / Stop
        ServerPlayNetworking.registerGlobalReceiver(C2S_LIGHTNING_AIM, (server, player, handler, buf, responseSender) -> {
            boolean isAiming = buf.readBoolean();
            server.execute(() -> StormcallerServerCombatHandler.startLightningAim(player, isAiming));
        });

        // Lightning Release
        ServerPlayNetworking.registerGlobalReceiver(C2S_LIGHTNING_RELEASE, (server, player, handler, buf, responseSender) -> {
            int targetEntityId = buf.readInt();
            float chargeProgress = buf.readFloat();
            server.execute(() -> StormcallerServerCombatHandler.releaseLightningStrike(player, targetEntityId, chargeProgress));
        });

        // Ground Smash
        ServerPlayNetworking.registerGlobalReceiver(C2S_GROUND_SMASH, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> StormcallerServerCombatHandler.startGroundSmash(player));
        });

        // Legacy compatibility
        ServerPlayNetworking.registerGlobalReceiver(THUNDERCLAP_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> StormcallerServerCombatHandler.startGroundSmash(player));
        });
    }

    /**
     * Broadcasts animation playback to the player and all players tracking them.
     */
    public static void broadcastPlayAnimation(ServerPlayerEntity player, String animName) {
        // No-op: player animations disabled per user request
    }

    /**
     * Broadcasts ground impact event to nearby players for screen shake and downward camera snap.
     */
    public static void broadcastGroundImpact(ServerWorld world, double x, double y, double z) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);

        for (ServerPlayerEntity player : PlayerLookup.around(world, new net.minecraft.util.math.Vec3d(x, y, z), 32.0)) {
            ServerPlayNetworking.send(player, S2C_GROUND_IMPACT, buf);
        }
    }

    /**
     * Sends animation playback for playerToAnimate to a specific receiver player (e.g. late tracker / joiner).
     */
    public static void sendPlayAnimationTo(ServerPlayerEntity receiver, ServerPlayerEntity playerToAnimate, String animName) {
        if (receiver == null || playerToAnimate == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(playerToAnimate.getId());
        buf.writeString(animName);
        ServerPlayNetworking.send(receiver, S2C_PLAY_ANIMATION, buf);
    }
}
