package com.elemental.network;

import com.elemental.Elemental;
import com.elemental.item.RootboundAxeItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class RootboundNetworking {
    public static final Identifier TIMBER_BASTION_PACKET_ID = Elemental.id("rootbound_timber_bastion");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(TIMBER_BASTION_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                RootboundAxeItem.performTimberBastion(player);
            });
        });
    }
}
