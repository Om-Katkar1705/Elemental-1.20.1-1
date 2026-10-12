package com.elemental.client.gui;

import com.elemental.client.quest.ClientQuestData;
import com.elemental.config.ElementalConfig;
import com.elemental.quest.QuestState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class QuestGuidanceHud implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        if (!ElementalConfig.get().enableJetpackGuidance) return;

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) return;

        if (!ClientQuestData.isGuidanceActive()) return;

        QuestState state = ClientQuestData.getState();

        TextRenderer tr = client.textRenderer;
        int x = 12;
        int y = 12;
        int width = 220;
        int height = 54;

        // Subtle dark-blue glassmorphic background
        drawContext.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x900B111E);
        drawContext.drawBorder(x - 4, y - 4, width + 8, height + 8, 0xFF4A90E2);

        // Header: Quest Title
        String titleKey = state == QuestState.CRAFT_STONE_TOOLS ? "The Elemental Chronicles" : "✦ Wings of the Skyforge ✦";
        drawContext.drawText(tr, Text.literal(titleKey).formatted(Formatting.GOLD, Formatting.BOLD), x, y, 0xFFFFFF, false);

        // Line 2: Current Task
        String taskString = getTaskDescription(state);
        drawContext.drawText(tr, Text.literal(taskString), x, y + 12, 0xE0E0E0, false);

        // Line 3: Waypoint Distance & Compass Arrow
        BlockPos target = ClientQuestData.getTargetWaypoint();
        if (target != null && state != QuestState.QUEST_COMPLETE) {
            double dx = target.getX() + 0.5 - player.getX();
            double dz = target.getZ() + 0.5 - player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);

            String arrow = getDirectionArrow(player.getYaw(), dx, dz);
            String distText = "§b" + dist + "m §8| " + arrow;
            drawContext.drawText(tr, Text.literal(distText), x, y + 24, 0xFFFFFF, false);

            // Line 4: Vertical Guidance
            int deltaY = (int) (target.getY() - player.getY());
            String vertText;
            if (deltaY > 6) {
                vertText = "§6▲ ASCEND (+" + deltaY + "m)";
            } else if (deltaY < -6) {
                vertText = "§9▼ DESCEND (" + deltaY + "m)";
            } else {
                vertText = "§a◆ LEVEL ELEVATION";
            }
            drawContext.drawText(tr, Text.literal(vertText), x, y + 36, 0xFFFFFF, false);
        } else if (state == QuestState.CRAFT_STONE_TOOLS) {
            drawContext.drawText(tr, Text.literal("§7Mine cobblestone & craft stone tools").formatted(Formatting.GRAY), x, y + 24, 0xFFFFFF, false);
            drawContext.drawText(tr, Text.literal("§8Waypoint unlocks upon tool craft").formatted(Formatting.DARK_GRAY), x, y + 36, 0xFFFFFF, false);
        } else if (state == QuestState.QUEST_COMPLETE) {
            drawContext.drawText(tr, Text.translatable("quest.elemental.all_clear").formatted(Formatting.GREEN), x, y + 24, 0xFFFFFF, false);
            drawContext.drawText(tr, Text.translatable("quest.elemental.guidance_ready").formatted(Formatting.GRAY), x, y + 36, 0xFFFFFF, false);
        }

        // Toggle hint
        drawContext.drawText(tr, Text.literal("§8[G] Toggle HUD"), x + width - 68, y, 0x888888, false);
    }

    private String getTaskDescription(QuestState state) {
        return switch (state) {
            case CRAFT_STONE_TOOLS -> "§e► Craft any Stone Tool to begin";
            case FIND_AERON -> "§e► Locate Aeron's Camp";
            case LOCATE_SKYFORGE -> "§e► Locate buried Skyforge Spaceship";
            case SALVAGE_MODULES -> "§e► Salvage: §6" + ClientQuestData.getPistonsSalvaged() + "/4 Pist §8| §b" + ClientQuestData.getAerogelExtracted() + "/2 Aero";
            case CRAFT_JETPACK -> "§e► Assemble Jetpack at a Weaponer";
            case RETURN_TO_AERON -> "§e► Return to Aeron with your Jetpack";
            case DESERT_EXPEDITION -> "§e► Journey to Desert (Sunforged Scimitar)";
            case QUEST_COMPLETE -> "§a✔ Wings of the Skyforge Completed";
        };
    }

    private String getDirectionArrow(float playerYaw, double dx, double dz) {
        double targetAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = (targetAngle - playerYaw) % 360.0;
        if (diff < -180.0) diff += 360.0;
        if (diff > 180.0) diff -= 360.0;

        if (Math.abs(diff) <= 22.5) return "§a▲ Ahead";
        if (diff > 22.5 && diff <= 67.5) return "§e↗ Ahead-Right";
        if (diff > 67.5 && diff <= 112.5) return "§e→ Right";
        if (diff > 112.5 && diff <= 157.5) return "§c↘ Behind-Right";
        if (Math.abs(diff) > 157.5) return "§c▼ Behind";
        if (diff < -22.5 && diff >= -67.5) return "§e↖ Ahead-Left";
        if (diff < -67.5 && diff >= -112.5) return "§e← Left";
        return "§c↙ Behind-Left";
    }
}
