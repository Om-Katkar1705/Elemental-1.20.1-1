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
import java.util.function.Consumer;

/**
 * Manages timed, sequential two-way chat dialogue between the player and NPCs.
 * Supports configurable per-line delays, action hooks, and skippable conversations.
 */
public class DialogueManager {

    public record DialogueEntry(Text text, SoundEvent sound, float pitch, int delayTicks, Consumer<ServerPlayerEntity> onDisplay) {
        public DialogueEntry(Text text, SoundEvent sound, float pitch) {
            this(text, sound, pitch, 30, null);
        }

        public DialogueEntry(Text text, SoundEvent sound, float pitch, int delayTicks) {
            this(text, sound, pitch, delayTicks, null);
        }

        public DialogueEntry(Text text, SoundEvent sound, float pitch, Consumer<ServerPlayerEntity> onDisplay) {
            this(text, sound, pitch, 30, onDisplay);
        }
    }

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

    public static boolean isAnyConversationActive() {
        return !ACTIVE_CONVERSATIONS.isEmpty();
    }

    public static boolean isInConversation(ServerPlayerEntity player) {
        return ACTIVE_CONVERSATIONS.containsKey(player.getUuid());
    }

    public static void startConversation(ServerPlayerEntity player, List<DialogueEntry> lines, Runnable onComplete) {
        ACTIVE_CONVERSATIONS.put(player.getUuid(), new ActiveConversation(player.getUuid(), lines, onComplete));
    }

    public static void cancelConversation(ServerPlayerEntity player) {
        ACTIVE_CONVERSATIONS.remove(player.getUuid());
    }

    public static void skipConversation(ServerPlayerEntity player) {
        ActiveConversation conv = ACTIVE_CONVERSATIONS.remove(player.getUuid());
        if (conv != null) {
            // Execute any remaining onDisplay actions so rewards or critical triggers are never lost
            while (conv.currentIndex < conv.lines.size()) {
                DialogueEntry line = conv.lines.get(conv.currentIndex);
                if (line.onDisplay() != null) {
                    line.onDisplay().accept(player);
                }
                conv.currentIndex++;
            }
            if (conv.onComplete != null) {
                conv.onComplete.run();
            }
            if (player.server != null) {
                player.server.getPlayerManager().broadcast(Text.literal("§7[Dialogue skipped by " + player.getName().getString() + "]"), false);
            }
        }
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
                // Broadcast to whole chat across the server
                server.getPlayerManager().broadcast(line.text(), false);

                if (line.sound() != null) {
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        p.playSound(line.sound(), SoundCategory.PLAYERS, 0.8f, line.pitch());
                    }
                }

                if (line.onDisplay() != null) {
                    line.onDisplay().accept(player);
                }

                conv.currentIndex++;
                conv.timerTicks = line.delayTicks() > 0 ? line.delayTicks() : 30;
            } else {
                if (conv.onComplete != null) {
                    conv.onComplete.run();
                }
                iterator.remove();
            }
        }
    }
}
