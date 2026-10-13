package com.elemental.network;

import com.elemental.Elemental;
import com.elemental.item.AshenRiftBladeItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class AshenRiftNetworking {
    public static final Identifier RIFT_COLLAPSE_PACKET_ID = Elemental.id("ashen_rift_collapse");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RIFT_COLLAPSE_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                AshenRiftBladeItem.performRiftCollapse(player);
            });
        });
    }
}
