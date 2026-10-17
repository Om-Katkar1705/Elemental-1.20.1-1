package com.elemental.network;

import com.elemental.Elemental;
import com.elemental.item.ElementalJetpackItem;
import com.elemental.quest.PlayerQuestData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class QuestNetworking {
    public static final Identifier QUEST_SYNC_PACKET_ID = Elemental.id("quest_sync");
    public static final Identifier JETPACK_THRUST_PACKET_ID = Elemental.id("jetpack_thrust");
    public static final Identifier JETPACK_GLIDE_PACKET_ID = Elemental.id("jetpack_glide");
    public static final Identifier JETPACK_TOGGLE_GUIDANCE_PACKET_ID = Elemental.id("jetpack_toggle_guidance");
    public static final Identifier JETPACK_TOGGLE_PACKET_ID = Elemental.id("jetpack_toggle");
    public static final Identifier EXTRA_SLOT_SYNC_PACKET_ID = Elemental.id("extra_slot_sync");
    public static final Identifier PLAYER_FIST_BUMP_PACKET_ID = Elemental.id("player_fist_bump");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(JETPACK_THRUST_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            boolean hasForward = buf.isReadable() && buf.readBoolean();
            server.execute(() -> ElementalJetpackItem.performThrust(player, hasForward));
        });

        ServerPlayNetworking.registerGlobalReceiver(JETPACK_GLIDE_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> ElementalJetpackItem.performGlide(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(JETPACK_TOGGLE_GUIDANCE_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> ElementalJetpackItem.toggleGuidance(player));
        });

        // Toggle jetpack on/off (` key)
        ServerPlayNetworking.registerGlobalReceiver(JETPACK_TOGGLE_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (player instanceof com.elemental.util.JetpackSlotHolder holder) {
                    boolean newState = !holder.elemental$isJetpackEnabled();
                    holder.elemental$setJetpackEnabled(newState);
                    sendExtraSlotSync(player);

                    player.getWorld().playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            newState ? net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value() : net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                            net.minecraft.sound.SoundCategory.PLAYERS,
                            0.8f,
                            newState ? 1.4f : 0.7f
                    );

                    if (newState) {
                        player.sendMessage(net.minecraft.text.Text.translatable("message.elemental.jetpack_enabled").formatted(net.minecraft.util.Formatting.GREEN), true);
                    } else {
                        player.sendMessage(net.minecraft.text.Text.translatable("message.elemental.jetpack_disabled").formatted(net.minecraft.util.Formatting.RED), true);
                    }
                }
            });
        });
    }

    public static void sendQuestSync(ServerPlayerEntity player, PlayerQuestData data) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(data.getState().getId());
        buf.writeInt(data.getPistonsSalvaged());
        buf.writeInt(data.getAerogelExtracted());
        buf.writeInt(data.getBlueprintsFound());
        buf.writeBoolean(data.isGuidanceActive());

        BlockPos target = data.getTargetWaypoint();
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeInt(target.getX());
            buf.writeInt(target.getY());
            buf.writeInt(target.getZ());
        }

        ServerPlayNetworking.send(player, QUEST_SYNC_PACKET_ID, buf);
    }

    public static void sendExtraSlotSync(ServerPlayerEntity player) {
        net.minecraft.item.ItemStack stack = com.elemental.util.JetpackSlotHolder.getJetpackStack(player);
        boolean enabled = com.elemental.util.JetpackSlotHolder.isJetpackEnabled(player);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(player.getId());
        buf.writeItemStack(stack);
        buf.writeBoolean(enabled);
        ServerPlayNetworking.send(player, EXTRA_SLOT_SYNC_PACKET_ID, buf);

        // Replicate to all other players tracking this player so remote visual model is visible
        for (ServerPlayerEntity tracker : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
            PacketByteBuf trackerBuf = PacketByteBufs.create();
            trackerBuf.writeInt(player.getId());
            trackerBuf.writeItemStack(stack);
            trackerBuf.writeBoolean(enabled);
            ServerPlayNetworking.send(tracker, EXTRA_SLOT_SYNC_PACKET_ID, trackerBuf);
        }
    }

    public static void sendExtraSlotSyncTo(ServerPlayerEntity playerWithJetpack, ServerPlayerEntity recipient) {
        net.minecraft.item.ItemStack stack = com.elemental.util.JetpackSlotHolder.getJetpackStack(playerWithJetpack);
        boolean enabled = com.elemental.util.JetpackSlotHolder.isJetpackEnabled(playerWithJetpack);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(playerWithJetpack.getId());
        buf.writeItemStack(stack);
        buf.writeBoolean(enabled);
        ServerPlayNetworking.send(recipient, EXTRA_SLOT_SYNC_PACKET_ID, buf);
    }

    public static void sendPlayerFistBump(ServerPlayerEntity player, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(player.getId());
        buf.writeInt(durationTicks);
        ServerPlayNetworking.send(player, PLAYER_FIST_BUMP_PACKET_ID, buf);

        for (ServerPlayerEntity tracker : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
            PacketByteBuf trackerBuf = PacketByteBufs.create();
            trackerBuf.writeInt(player.getId());
            trackerBuf.writeInt(durationTicks);
            ServerPlayNetworking.send(tracker, PLAYER_FIST_BUMP_PACKET_ID, trackerBuf);
        }
    }
}
