package com.elemental.client.gui;

import com.elemental.client.quest.ClientQuestData;
import com.elemental.config.ElementalConfig;
import com.elemental.item.ModItems;
import com.elemental.quest.QuestState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
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
    private static final Text TASK_DESERT = Text.literal("§e► Journey to Desert Village (Find Wizard)");
    private static final Text TASK_TALK_WIZARD = Text.literal("§e► Speak with Eldrin, Elemental Wizard");
    private static final Text TASK_LOCATE_ARENA = Text.literal("§c► Follow Waypoint to Everliving Coliseum");
    private static final Text TASK_DEFEAT_KNIGHT = Text.literal("§c► Awaken & Slay Sir Solvane, Everliving Knight");
    private static final Text TASK_COMPLETE = Text.literal("§a✔ Everliving Knight Slain! Arsenal Trial Complete");

    private static final Text HINT_MINE = Text.literal("§7Mine cobblestone & craft stone tools").formatted(Formatting.GRAY);
    private static final Text HINT_WAYPOINT = Text.literal("§8Waypoint unlocks upon tool craft").formatted(Formatting.DARK_GRAY);
    private static final Text HINT_ALL_CLEAR = Text.translatable("quest.elemental.all_clear").formatted(Formatting.GREEN);
    private static final Text HINT_GUIDANCE_READY = Text.translatable("quest.elemental.guidance_ready").formatted(Formatting.GRAY);
    private static final Text HINT_TOGGLE = Text.literal("§8[G] Toggle HUD");

    private static final Text VERT_LEVEL = Text.literal("§a◆ LEVEL ELEVATION");

    private boolean isCraftingTask(QuestState state) {
        return state == QuestState.CRAFT_PNEUMATIC_PISTON
                || state == QuestState.CRAFT_AEROGEL_CORE
                || state == QuestState.CRAFT_ELEMENT_CORE
                || state == QuestState.CRAFT_JETPACK
                || state == QuestState.CRAFT_WEAPONER
                || state == QuestState.CRAFT_DRY_HEAT_ELEMENT
                || state == QuestState.FORGE_SUNFORGED_SCIMITAR
                || state == QuestState.CRAFT_SNOW_ELEMENT
                || state == QuestState.FORGE_FROSTWAKE_PICK
                || state == QuestState.CRAFT_TREE_ELEMENT
                || state == QuestState.FORGE_ROOTBOUND_AXE;
    }

    private int getCraftingHudHeight(QuestState state) {
        return switch (state) {
            case CRAFT_PNEUMATIC_PISTON -> 90;
            case CRAFT_AEROGEL_CORE -> 78;
            case CRAFT_ELEMENT_CORE -> 66;
            case CRAFT_JETPACK -> 78;
            default -> 66;
        };
    }

    private int count(PlayerEntity player, Item item) {
        if (player == null || item == null) return 0;
        return player.getInventory().count(item);
    }

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
        int width = 252;
        boolean crafting = isCraftingTask(state);
        int height = crafting ? getCraftingHudHeight(state) : 54;

        // Dark glassmorphic background
        drawContext.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x900B111E);
        drawContext.drawBorder(x - 4, y - 4, width + 8, height + 8, 0xFF4A90E2);

        // Header: Quest Title
        Text title = state == QuestState.CRAFT_STONE_TOOLS ? TITLE_CHRONICLES : TITLE_SKYFORGE;
        drawContext.drawText(tr, title, x, y, 0xFFFFFF, false);

        if (crafting) {
            // CRAFTING TASK HUD: Remove direction bar, display recipe materials checklist
            renderCraftingChecklist(drawContext, tr, player, state, x, y);
        } else {
            // EXPLORATION / NAVIGATION HUD: Display Task Line and Direction Bar
            Text taskText = getTaskDescriptionText(state);
            drawContext.drawText(tr, taskText, x, y + 12, 0xE0E0E0, false);

            BlockPos target = ClientQuestData.getTargetWaypoint();
            if (target != null && state != QuestState.QUEST_COMPLETE) {
                double dx = target.getX() + 0.5 - player.getX();
                double dz = target.getZ() + 0.5 - player.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);

                String arrow = getDirectionArrow(player.getYaw(), dx, dz);
                String distText = "§b" + dist + "m §8| " + arrow;
                drawContext.drawText(tr, Text.literal(distText), x, y + 24, 0xFFFFFF, false);

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
        }

        // Toggle hint
        drawContext.drawText(tr, HINT_TOGGLE, x + width - 68, y, 0x888888, false);
    }

    private void renderCraftingChecklist(DrawContext context, TextRenderer tr, PlayerEntity player, QuestState state, int x, int y) {
        switch (state) {
            case CRAFT_PNEUMATIC_PISTON -> {
                int iron = count(player, Items.IRON_INGOT);
                int copper = count(player, Items.COPPER_INGOT);
                int piston = count(player, Items.PISTON);
                int redstone = count(player, Items.REDSTONE);
                context.drawText(tr, Text.literal("§6► Task 1: Craft Pneumatic Piston"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((iron >= 1 ? "§a✔ " : "§c✖ ") + "Iron Ingot: " + iron + "/1"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((copper >= 2 ? "§a✔ " : "§c✖ ") + "Copper Ingot: " + copper + "/2"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((piston >= 1 ? "§a✔ " : "§c✖ ") + "Piston: " + piston + "/1"), x + 4, y + 48, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((redstone >= 1 ? "§a✔ " : "§c✖ ") + "Redstone Dust: " + redstone + "/1"), x + 4, y + 60, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§eCraft at Crafting Table (Yields 2 Pistons)"), x + 4, y + 73, 0xFFFFFF, false);
            }
            case CRAFT_AEROGEL_CORE -> {
                int glass = count(player, Items.GLASS);
                int feather = count(player, Items.FEATHER);
                int catalyst = count(player, Items.SNOWBALL) + count(player, Items.SLIME_BALL) + count(player, Items.QUARTZ);
                context.drawText(tr, Text.literal("§b► Task 2: Craft Aerogel Core"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((glass >= 4 ? "§a✔ " : "§c✖ ") + "Glass: " + glass + "/4"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((feather >= 4 ? "§a✔ " : "§c✖ ") + "Feather: " + feather + "/4"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((catalyst >= 1 ? "§a✔ " : "§c✖ ") + "Catalyst (Snow/Slime/Quartz): " + catalyst + "/1"), x + 4, y + 48, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§bCraft at Crafting Table"), x + 4, y + 61, 0xFFFFFF, false);
            }
            case CRAFT_ELEMENT_CORE -> {
                int base = count(player, Items.COPPER_INGOT) + count(player, Items.COBBLESTONE);
                int coal = count(player, Items.COAL) + count(player, Items.CHARCOAL);
                context.drawText(tr, Text.literal("§d► Task 3: Craft Element Core"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((base >= 4 ? "§a✔ " : "§c✖ ") + "Copper / Cobblestone: " + base + "/4"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((coal >= 1 ? "§a✔ " : "§c✖ ") + "Coal / Charcoal: " + coal + "/1"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§dCraft at Table §e(Tip: Make a handful!)"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case CRAFT_JETPACK -> {
                int pistons = count(player, ModItems.PNEUMATIC_PISTON);
                int aerogel = count(player, ModItems.AEROGEL_CORE);
                int cores = count(player, ModItems.ELEMENT_CORE);
                context.drawText(tr, Text.literal("§a► Task 4: Assemble Elemental Jetpack"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((pistons >= 4 ? "§a✔ " : "§c✖ ") + "Pneumatic Piston: " + pistons + "/4"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((aerogel >= 2 ? "§a✔ " : "§c✖ ") + "Aerogel Core: " + aerogel + "/2"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((cores >= 1 ? "§a✔ " : "§c✖ ") + "Element Core: " + cores + "/1"), x + 4, y + 48, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§aAssemble at Crafting Table"), x + 4, y + 61, 0xFFFFFF, false);
            }
            case CRAFT_WEAPONER -> {
                int iron = count(player, Items.IRON_INGOT);
                int stone = count(player, Items.SMOOTH_STONE);
                context.drawText(tr, Text.literal("§6► Task 1: Craft Weaponer Workstation"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((iron >= 3 ? "§a✔ " : "§c✖ ") + "Iron Ingot: " + iron + "/3"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((stone >= 4 ? "§a✔ " : "§c✖ ") + "Smooth Stone: " + stone + "/4"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§eCraft at Crafting Table (3 Iron + 4 Smooth Stone)"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case CRAFT_DRY_HEAT_ELEMENT -> {
                int core = count(player, ModItems.ELEMENT_CORE);
                int cactus = count(player, Items.CACTUS);
                context.drawText(tr, Text.literal("§6► Task 2: Synthesize Dry Heat Element"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((core >= 1 ? "§a✔ " : "§c✖ ") + "Element Core: " + core + "/1 (Coal + Copper/Cobble)"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((cactus >= 1 ? "§a✔ " : "§c✖ ") + "Cactus: " + cactus + "/1 (Harvest in Desert)"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§eCombine Core + Cactus inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case FORGE_SUNFORGED_SCIMITAR -> {
                int stencil = count(player, ModItems.STENCIL);
                int heat = count(player, ModItems.DRY_HEAT_ELEMENT);
                context.drawText(tr, Text.literal("§6► Task 3: Forge Sunforged Scimitar"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((stencil >= 1 ? "§a✔ " : "§c✖ ") + "Weapon Stencil: " + stencil + "/1 (Iron, Gold, Core)"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((heat >= 1 ? "§a✔ " : "§c✖ ") + "Dry Heat Element: " + heat + "/1"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§6Combine Stencil + Dry Heat inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case CRAFT_SNOW_ELEMENT -> {
                int core = count(player, ModItems.ELEMENT_CORE);
                int snow = count(player, Items.SNOW_BLOCK);
                context.drawText(tr, Text.literal("§b► Task: Craft Snow Element"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((core >= 1 ? "§a✔ " : "§c✖ ") + "Element Core: " + core + "/1"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((snow >= 1 ? "§a✔ " : "§c✖ ") + "Snow Block: " + snow + "/1 (Snowy Biome)"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§bCombine Core + Snow Block inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case FORGE_FROSTWAKE_PICK -> {
                int stencil = count(player, ModItems.STENCIL);
                int snowElement = count(player, ModItems.SNOW_ELEMENT);
                context.drawText(tr, Text.literal("§b► Task: Forge Frostwake Pick"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((stencil >= 1 ? "§a✔ " : "§c✖ ") + "Weapon Stencil: " + stencil + "/1"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((snowElement >= 1 ? "§a✔ " : "§c✖ ") + "Snow Element: " + snowElement + "/1"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§bCombine Stencil + Snow Element inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case CRAFT_TREE_ELEMENT -> {
                int core = count(player, ModItems.ELEMENT_CORE);
                int log = count(player, Items.DARK_OAK_LOG);
                context.drawText(tr, Text.literal("§a► Task: Craft Tree Element"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((core >= 1 ? "§a✔ " : "§c✖ ") + "Element Core: " + core + "/1"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((log >= 1 ? "§a✔ " : "§c✖ ") + "Dark Oak Log: " + log + "/1 (Forest Biome)"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§aCombine Core + Dark Oak Log inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            case FORGE_ROOTBOUND_AXE -> {
                int stencil = count(player, ModItems.STENCIL);
                int treeElement = count(player, ModItems.TREE_ELEMENT);
                context.drawText(tr, Text.literal("§a► Task: Forge Rootbound Axe"), x, y + 12, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((stencil >= 1 ? "§a✔ " : "§c✖ ") + "Weapon Stencil: " + stencil + "/1"), x + 4, y + 24, 0xFFFFFF, false);
                context.drawText(tr, Text.literal((treeElement >= 1 ? "§a✔ " : "§c✖ ") + "Tree Element: " + treeElement + "/1"), x + 4, y + 36, 0xFFFFFF, false);
                context.drawText(tr, Text.literal("§aCombine Stencil + Tree Element inside Weaponer"), x + 4, y + 49, 0xFFFFFF, false);
            }
            default -> {}
        }
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
            case RETURN_TO_AERON -> TASK_DELIVER;
            case DESERT_EXPEDITION -> TASK_DESERT;
            case TALK_TO_WIZARD -> TASK_TALK_WIZARD;
            case LOCATE_EVERLIVING_ARENA -> TASK_LOCATE_ARENA;
            case DEFEAT_EVERLIVING_KNIGHT -> TASK_DEFEAT_KNIGHT;
            case QUEST_COMPLETE -> TASK_COMPLETE;
            default -> Text.literal("");
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
