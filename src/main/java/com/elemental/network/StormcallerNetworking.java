package com.elemental.network;

import com.elemental.Elemental;
import com.elemental.item.StormcallerHammerItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class StormcallerNetworking {
    public static final Identifier THUNDERCLAP_PACKET_ID = Elemental.id("stormcaller_thunderclap");

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(THUNDERCLAP_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                StormcallerHammerItem.performThunderclap(player);
            });
        });
    }
}
