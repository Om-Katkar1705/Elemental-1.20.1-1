package com.elemental.quest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages timed, sequential two-way chat dialogue between the player and NPCs.
 * Delivers one line every 1.5 seconds (30 ticks) with sound feedback.
 */
public class DialogueManager {

    public record DialogueEntry(Text text, SoundEvent sound, float pitch) {}

    public static class ActiveConversation {
        private final UUID playerUuid;
        private final List<DialogueEntry> lines;
        private int currentIndex = 0;
        private int timerTicks = 0;
        private final Runnable onComplete;

        public ActiveConversation(UUID playerUuid, List<DialogueEntry> lines, Runnable onComplete) {
            this.playerUuid = playerUuid;
            this.lines = lines;
            this.onComplete = onComplete;
            this.timerTicks = 0; // First line displays immediately
        }
    }

    private static final Map<UUID, ActiveConversation> ACTIVE_CONVERSATIONS = new HashMap<>();

    public static boolean isInConversation(ServerPlayerEntity player) {
        return ACTIVE_CONVERSATIONS.containsKey(player.getUuid());
    }

    public static void startConversation(ServerPlayerEntity player, List<DialogueEntry> lines, Runnable onComplete) {
        ACTIVE_CONVERSATIONS.put(player.getUuid(), new ActiveConversation(player.getUuid(), lines, onComplete));
    }

    public static void cancelConversation(ServerPlayerEntity player) {
        ACTIVE_CONVERSATIONS.remove(player.getUuid());
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE_CONVERSATIONS.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveConversation>> iterator = ACTIVE_CONVERSATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveConversation> entry = iterator.next();
            ActiveConversation conv = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(conv.playerUuid);

            if (player == null || player.isDisconnected()) {
                iterator.remove();
                continue;
            }

            if (conv.timerTicks > 0) {
                conv.timerTicks--;
                continue;
            }

            if (conv.currentIndex < conv.lines.size()) {
                DialogueEntry line = conv.lines.get(conv.currentIndex);
                player.sendMessage(line.text(), false);

                if (line.sound() != null) {
                    player.getWorld().playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            line.sound(),
                            SoundCategory.PLAYERS,
                            0.8f,
                            line.pitch()
                    );
                }

                conv.currentIndex++;
                // 30 ticks = 1.5 seconds delay before the next line appears
                conv.timerTicks = 30;
            } else {
                if (conv.onComplete != null) {
                    conv.onComplete.run();
                }
                iterator.remove();
            }
        }
    }
}
