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

    private static final Text TITLE_CHRONICLES = Text.literal("The Elemental Chronicles").formatted(Formatting.GOLD, Formatting.BOLD);
    private static final Text TITLE_SKYFORGE = Text.literal("✦ Wings of the Skyforge ✦").formatted(Formatting.GOLD, Formatting.BOLD);

    private static final Text TASK_CRAFT_STONE = Text.literal("§e► Craft any Stone Tool to begin");
    private static final Text TASK_FIND_AERON = Text.literal("§e► Locate Aeron's Camp");
    private static final Text TASK_LOCATE_SKYFORGE = Text.literal("§e► Locate buried Skyforge Spaceship");
    private static final Text[] TASK_SALVAGE = new Text[] {
            Text.literal("§e► Collect Recipe Maps: §d0/4"),
            Text.literal("§e► Collect Recipe Maps: §d1/4"),
            Text.literal("§e► Collect Recipe Maps: §d2/4"),
            Text.literal("§e► Collect Recipe Maps: §d3/4"),
            Text.literal("§e► Collect Recipe Maps: §d4/4")
    };
    private static final Text TASK_DELIVER = Text.literal("§e► Deliver 4 Recipe Maps to Aeron at Camp");
    private static final Text TASK_DESERT = Text.literal("§e► Journey to Desert (Sunforged Scimitar)");
    private static final Text TASK_COMPLETE = Text.literal("§a✔ Wings of the Skyforge Completed");

    private static final Text HINT_MINE = Text.literal("§7Mine cobblestone & craft stone tools").formatted(Formatting.GRAY);
    private static final Text HINT_WAYPOINT = Text.literal("§8Waypoint unlocks upon tool craft").formatted(Formatting.DARK_GRAY);
    private static final Text HINT_ALL_CLEAR = Text.translatable("quest.elemental.all_clear").formatted(Formatting.GREEN);
    private static final Text HINT_GUIDANCE_READY = Text.translatable("quest.elemental.guidance_ready").formatted(Formatting.GRAY);
    private static final Text HINT_TOGGLE = Text.literal("§8[G] Toggle HUD");

    private static final Text VERT_LEVEL = Text.literal("§a◆ LEVEL ELEVATION");

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
        Text title = state == QuestState.CRAFT_STONE_TOOLS ? TITLE_CHRONICLES : TITLE_SKYFORGE;
        drawContext.drawText(tr, title, x, y, 0xFFFFFF, false);

        // Line 2: Current Task
        Text taskText = getTaskDescriptionText(state);
        drawContext.drawText(tr, taskText, x, y + 12, 0xE0E0E0, false);

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
            Text vertText;
            if (deltaY > 6) {
                vertText = Text.literal("§6▲ ASCEND (+" + deltaY + "m)");
            } else if (deltaY < -6) {
                vertText = Text.literal("§9▼ DESCEND (" + deltaY + "m)");
            } else {
                vertText = VERT_LEVEL;
            }
            drawContext.drawText(tr, vertText, x, y + 36, 0xFFFFFF, false);
        } else if (state == QuestState.CRAFT_STONE_TOOLS) {
            drawContext.drawText(tr, HINT_MINE, x, y + 24, 0xFFFFFF, false);
            drawContext.drawText(tr, HINT_WAYPOINT, x, y + 36, 0xFFFFFF, false);
        } else if (state == QuestState.QUEST_COMPLETE) {
            drawContext.drawText(tr, HINT_ALL_CLEAR, x, y + 24, 0xFFFFFF, false);
            drawContext.drawText(tr, HINT_GUIDANCE_READY, x, y + 36, 0xFFFFFF, false);
        }

        // Toggle hint
        drawContext.drawText(tr, HINT_TOGGLE, x + width - 68, y, 0x888888, false);
    }

    private Text getTaskDescriptionText(QuestState state) {
        return switch (state) {
            case CRAFT_STONE_TOOLS -> TASK_CRAFT_STONE;
            case FIND_AERON -> TASK_FIND_AERON;
            case LOCATE_SKYFORGE -> TASK_LOCATE_SKYFORGE;
            case SALVAGE_MODULES -> {
                int found = Math.min(4, Math.max(0, ClientQuestData.getBlueprintsFound()));
                yield TASK_SALVAGE[found];
            }
            case CRAFT_JETPACK, RETURN_TO_AERON -> TASK_DELIVER;
            case DESERT_EXPEDITION -> TASK_DESERT;
            case QUEST_COMPLETE -> TASK_COMPLETE;
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
