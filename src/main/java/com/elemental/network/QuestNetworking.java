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
    public static final Identifier EXTRA_SLOT_SYNC_PACKET_ID = Elemental.id("extra_slot_sync");

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
    }

    public static void sendQuestSync(ServerPlayerEntity player, PlayerQuestData data) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(data.getState().getId());
        buf.writeInt(data.getPistonsSalvaged());
        buf.writeInt(data.getAerogelExtracted());
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
        PacketByteBuf buf = PacketByteBufs.create();
        net.minecraft.item.ItemStack stack = com.elemental.util.JetpackSlotHolder.getJetpackStack(player);
        buf.writeItemStack(stack);
        ServerPlayNetworking.send(player, EXTRA_SLOT_SYNC_PACKET_ID, buf);
    }
}
