package com.elemental.client.screen;

import com.elemental.client.quest.ClientQuestData;
import com.elemental.item.ModItems;
import com.elemental.network.QuestNetworking;
import com.elemental.quest.QuestState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Elemental Arsenal: Weapon Detail Screen.
 * Displays deep encyclopedic lore, keybinds, attacks, and detailed explanations
 * for all elemental weapons.
 * Locked weapons have their names, textures, and descriptions hidden with a black outline silhouette box.
 */
public class WeaponDetailScreen extends Screen {

    public record AbilityEntry(String keybind, String name, String description) {}

    public static class WeaponInfo {
        public final String id;
        public final String name;
        public final ItemStack stack;
        public final String element;
        public final String weaponClass;
        public final int color;
        public final String stats;
        public final String lore;
        public final List<AbilityEntry> abilities;

        public WeaponInfo(String id, String name, ItemStack stack, String element, String weaponClass,
                          int color, String stats, String lore, List<AbilityEntry> abilities) {
            this.id = id;
            this.name = name;
            this.stack = stack;
            this.element = element;
            this.weaponClass = weaponClass;
            this.color = color;
            this.stats = stats;
            this.lore = lore;
            this.abilities = abilities;
        }
    }

    private final List<WeaponInfo> weapons = new ArrayList<>();
    private int selectedIndex = 0;
    private ButtonWidget questActionButton;
    private ButtonWidget closeButton;

    public WeaponDetailScreen() {
        this("sunforged_scimitar");
    }

    public WeaponDetailScreen(String initialWeaponId) {
        super(Text.literal("Elemental Arsenal"));
        initWeaponData();

        if (initialWeaponId != null) {
            for (int i = 0; i < weapons.size(); i++) {
                if (weapons.get(i).id.equalsIgnoreCase(initialWeaponId)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
    }

    private void initWeaponData() {
        // 1. Sunforged Scimitar
        weapons.add(new WeaponInfo(
                "sunforged_scimitar",
                "Sunforged Scimitar",
                new ItemStack(ModItems.SUNFORGED_SCIMITAR),
                "Dry Heat & Solar Flame",
                "Elemental Curved Blade",
                0xFFD700,
                "Attack Damage: 7.0  |  Speed: 1.8  |  Heat Absorber",
                "Imbued with the concentrated solar radiance of desert dunes. Slices effortlessly through packed sand and releases blazing heat waves against adversaries.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Solar Arc Flame Strike",
                                "Unleashes a sweeping crescent wave of solar flame that pierces multiple enemies in a straight line, dealing heavy fire and elemental damage."),
                        new AbilityEntry("[Passive]", "Solar Infusion",
                                "Channels environmental sunlight to deal bonus damage against undead targets and mines sand, red sand, and suspicious sand at extreme speed.")
                )
        ));

        // 2. Frostwake Pick
        weapons.add(new WeaponInfo(
                "frostwake_pick",
                "Frostwake Pick",
                new ItemStack(ModItems.FROSTWAKE_PICK),
                "Permafrost & Glacial Ice",
                "Elemental Pickaxe-Warhammer",
                0x55FFFF,
                "Attack Damage: 6.0  |  Speed: 1.2  |  Permafrost Core",
                "Carved from ancient sub-zero glacial permafrost. Shatters deep stone and ice formations with ease while erupting piercing frost stalagmites through the earth.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Glacial Spikes",
                                "Thrusts sharp ice stalagmites forward through the ground, impaling foes, dealing heavy freeze damage, and inflicting Slowness III."),
                        new AbilityEntry("[Passive]", "Permafrost Cleave",
                                "Rapidly fractures frozen ice and dense deepslate blocks while preserving ore yields with crystalline precision.")
                )
        ));

        // 3. Rootbound Axe
        weapons.add(new WeaponInfo(
                "rootbound_axe",
                "Rootbound Axe",
                new ItemStack(ModItems.ROOTBOUND_AXE),
                "Ancient Canopy & Verdant Life",
                "Elemental Greataxe",
                0x55FF55,
                "Attack Damage: 9.0  |  Speed: 1.0  |  Thorn Weaver",
                "Forged from ancient dark forest heartwood and primal roots. Commands thorny overgrowth from beneath foes to trap them in place and fells entire trees with single blows.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Root Eruption",
                                "Erupts sharp thorny briars beneath your crosshair target, entangling foes in place, dealing continuous bleed damage, and transferring vitality to you."),
                        new AbilityEntry("[Passive]", "Timber Resonance",
                                "Cleaves massive tree trunks in an instant and delivers armor-crushing execution sweeps to heavily armored adversaries.")
                )
        ));

        // 4. Tidebreaker Trident
        weapons.add(new WeaponInfo(
                "tidebreaker_trident",
                "Tidebreaker Trident",
                new ItemStack(ModItems.TIDEBREAKER_TRIDENT),
                "Abyssal Depths & Ocean Surge",
                "Elemental Polearm",
                0x00AAAA,
                "Attack Damage: 8.5  |  Speed: 1.1  |  Hydrodynamic Core",
                "Infused with the crushing weight of oceanic trenches. Riptides through turbulent depths and traps enemies within spiraling vortexes.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Abyssal Whirlpool",
                                "Summons a roaring water vortex that lifts nearby enemies into the air, trapping and crushing them under hydrodynamic pressure."),
                        new AbilityEntry("[R Key]", "Tide Surge Dash",
                                "Propels the wielder through air or water with massive hydrodynamic acceleration, knocking away hostile entities.")
                )
        ));

        // 5. Stormcaller Hammer
        weapons.add(new WeaponInfo(
                "stormcaller_hammer",
                "Stormcaller Hammer",
                new ItemStack(ModItems.STORMCALLER_HAMMER),
                "Tempest Storm & Lightning",
                "Elemental Warhammer",
                0xFFFF55,
                "Attack Damage: 10.0  |  Speed: 0.8  |  Thunder Rod Core",
                "Forged beneath turbulent lightning tempest storms. Strikes the earth with seismic thunderclaps and calls lightning strikes from the heavens upon adversaries.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Call Lightning",
                                "Summons lightning strikes from the sky directly onto targeted foes, setting the impact zone ablaze and shocking surrounding enemies."),
                        new AbilityEntry("[R Key]", "Thunderclap Smash",
                                "Slams the warhammer into the earth, producing an expanding 360-degree high-voltage electric shockwave ring that stuns enemies.")
                )
        ));

        // 6. Ashen Rift Blade
        weapons.add(new WeaponInfo(
                "ashen_rift_blade",
                "Ashen Rift Blade",
                new ItemStack(ModItems.ASHEN_RIFT_BLADE),
                "Astral Void & Gravitational Rupture",
                "Astral Voidblade",
                0xAA00AA,
                "Attack Damage: 8.0  |  Speed: 1.6  |  Astral Singularity",
                "Forged beyond the veil of spatial reality. Displaces the wielder across the void instantaneously and creates micro black-hole gravitational tears.",
                List.of(
                        new AbilityEntry("[Right-Click]", "Void Blink",
                                "Tears through spatial rifts to teleport the wielder 8 blocks forward instantaneously, phase-striking all targets near the destination."),
                        new AbilityEntry("[R Key]", "Ashen Rupture",
                                "Opens a gravitational singularity that pulls all nearby enemies toward its core and detonates with catastrophic cosmic damage.")
                )
        ));
    }

    public boolean isWeaponUnlocked(String weaponId, QuestState state) {
        if ("sunforged_scimitar".equalsIgnoreCase(weaponId)) {
            return state.getId() >= QuestState.CRAFT_WEAPONER.getId();
        }
        if ("frostwake_pick".equalsIgnoreCase(weaponId)) {
            return state.getId() >= QuestState.CRAFT_SNOW_ELEMENT.getId();
        }
        if ("rootbound_axe".equalsIgnoreCase(weaponId)) {
            return state.getId() >= QuestState.CRAFT_TREE_ELEMENT.getId();
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        updateActionButtons();
    }

    private void updateActionButtons() {
        this.clearChildren();

        int dialogWidth = Math.min(410, this.width - 24);
        int dialogHeight = Math.min(240, this.height - 24);
        int startX = (this.width - dialogWidth) / 2;
        int startY = (this.height - dialogHeight) / 2;

        int rightX = startX + 134;
        int rightWidth = dialogWidth - 144;
        int bottomY = startY + dialogHeight - 26;

        WeaponInfo current = weapons.get(selectedIndex);
        QuestState state = ClientQuestData.getState();

        Text buttonText;
        boolean buttonActive = false;
        Runnable action = null;

        if (!isWeaponUnlocked(current.id, state)) {
            buttonText = Text.literal("✦ Weapon Locked ✦").formatted(Formatting.DARK_GRAY);
            buttonActive = false;
        } else if ("frostwake_pick".equalsIgnoreCase(current.id)) {
            if (state == QuestState.CRAFT_SNOW_ELEMENT || state == QuestState.FORGE_FROSTWAKE_PICK) {
                buttonText = Text.literal("✦ Active Quest: Frostwake Pick ✦").formatted(Formatting.AQUA);
                buttonActive = false;
            } else if (state.getId() >= QuestState.CRAFT_TREE_ELEMENT.getId()) {
                buttonText = Text.literal("✔ Frostwake Pick Mastered").formatted(Formatting.GREEN);
                buttonActive = false;
            } else {
                buttonText = Text.literal("✦ Begin Quest: Forge Frostwake Pick ✦").formatted(Formatting.AQUA, Formatting.BOLD);
                buttonActive = true;
                action = () -> sendSelectWeaponQuest("frostwake_pick");
            }
        } else if ("rootbound_axe".equalsIgnoreCase(current.id)) {
            if (state == QuestState.CRAFT_TREE_ELEMENT || state == QuestState.FORGE_ROOTBOUND_AXE) {
                buttonText = Text.literal("✦ Active Quest: Rootbound Axe ✦").formatted(Formatting.GREEN);
                buttonActive = false;
            } else if (state.getId() >= QuestState.LOCATE_EVERLIVING_ARENA.getId()) {
                buttonText = Text.literal("✔ Rootbound Axe Mastered").formatted(Formatting.GREEN);
                buttonActive = false;
            } else {
                buttonText = Text.literal("✦ Begin Quest: Forge Rootbound Axe ✦").formatted(Formatting.GREEN, Formatting.BOLD);
                buttonActive = true;
                action = () -> sendSelectWeaponQuest("rootbound_axe");
            }
        } else if ("sunforged_scimitar".equalsIgnoreCase(current.id)) {
            if (state.getId() >= QuestState.CRAFT_SNOW_ELEMENT.getId()) {
                buttonText = Text.literal("✔ Sunforged Scimitar Mastered").formatted(Formatting.GOLD);
                buttonActive = false;
            } else {
                buttonText = Text.literal("✦ Active Quest: Sunforged Scimitar ✦").formatted(Formatting.GOLD);
                buttonActive = false;
            }
        } else {
            buttonText = Text.literal("✦ Weapon Locked ✦").formatted(Formatting.DARK_GRAY);
            buttonActive = false;
        }

        final Runnable finalAction = action;
        questActionButton = ButtonWidget.builder(buttonText, btn -> {
            if (finalAction != null) {
                finalAction.run();
                this.close();
            }
        }).dimensions(rightX + 4, bottomY, rightWidth - 8, 20).build();
        questActionButton.active = buttonActive;
        this.addDrawableChild(questActionButton);

        // Close button at top right
        closeButton = ButtonWidget.builder(Text.literal("✕"), btn -> this.close())
                .dimensions(startX + dialogWidth - 22, startY + 6, 16, 16)
                .build();
        this.addDrawableChild(closeButton);
    }

    private void sendSelectWeaponQuest(String weaponId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(weaponId);
        ClientPlayNetworking.send(QuestNetworking.SELECT_WEAPON_QUEST_PACKET_ID, buf);
        if (client != null && client.player != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2F));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dialogWidth = Math.min(410, this.width - 24);
        int dialogHeight = Math.min(240, this.height - 24);
        int startX = (this.width - dialogWidth) / 2;
        int startY = (this.height - dialogHeight) / 2;

        int listX = startX + 8;
        int listY = startY + 28;
        int tabWidth = 122;
        int tabHeight = 31;

        for (int i = 0; i < weapons.size(); i++) {
            int ty = listY + (i * (tabHeight + 2));
            if (mouseX >= listX && mouseX <= listX + tabWidth && mouseY >= ty && mouseY <= ty + tabHeight) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    if (client != null) {
                        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    updateActionButtons();
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int dialogWidth = Math.min(410, this.width - 24);
        int dialogHeight = Math.min(240, this.height - 24);
        int startX = (this.width - dialogWidth) / 2;
        int startY = (this.height - dialogHeight) / 2;

        // 1. Master Frame Background & Borders
        context.fill(startX, startY, startX + dialogWidth, startY + dialogHeight, 0xF00D111A);
        context.drawBorder(startX, startY, dialogWidth, dialogHeight, 0xFF4A90E2);
        context.drawBorder(startX + 1, startY + 1, dialogWidth - 2, dialogHeight - 2, 0xFF1C2738);

        // Header Banner
        context.fill(startX + 2, startY + 2, startX + dialogWidth - 2, startY + 24, 0x90162030);
        context.drawText(this.textRenderer, Text.literal("✦ ELEMENTAL ARSENAL — WEAPON DETAILS ✦").formatted(Formatting.GOLD, Formatting.BOLD),
                startX + 12, startY + 8, 0xFFFFFF, true);

        // 2. Left Panel: Weapon Tabs List
        int listX = startX + 8;
        int listY = startY + 28;
        int tabWidth = 122;
        int tabHeight = 31;

        QuestState state = ClientQuestData.getState();

        for (int i = 0; i < weapons.size(); i++) {
            WeaponInfo w = weapons.get(i);
            int ty = listY + (i * (tabHeight + 2));
            boolean isSelected = (i == selectedIndex);
            boolean isHovered = mouseX >= listX && mouseX <= listX + tabWidth && mouseY >= ty && mouseY <= ty + tabHeight;
            boolean unlocked = isWeaponUnlocked(w.id, state);

            int bgCol = isSelected ? 0x604A90E2 : (isHovered ? 0x403A4D6B : 0x40162030);
            context.fill(listX, ty, listX + tabWidth, ty + tabHeight, bgCol);

            int borderCol = isSelected ? (unlocked ? w.color : 0xFF777777) : (isHovered ? 0xFF88AACC : 0xFF2A394E);
            context.drawBorder(listX, ty, tabWidth, tabHeight, borderCol);

            if (unlocked) {
                // Unlocked item: Draw actual 3D item texture
                context.drawItem(w.stack, listX + 4, ty + 7);

                // Weapon Title
                context.drawText(this.textRenderer, Text.literal(w.name).styled(s -> s.withBold(isSelected)),
                        listX + 24, ty + 5, w.color, false);

                // Status Badge
                String status = getStatusTag(w.id, state);
                int statusCol = getStatusColor(w.id, state);
                context.drawText(this.textRenderer, Text.literal(status), listX + 24, ty + 17, statusCol, false);
            } else {
                // Locked item: Draw black outline silhouette box instead of texture
                context.fill(listX + 4, ty + 7, listX + 20, ty + 23, 0xFF080B10);
                context.drawBorder(listX + 4, ty + 7, 16, 16, 0xFF2A2A2A);
                context.drawBorder(listX + 5, ty + 8, 14, 14, 0xFF141414);
                context.drawText(this.textRenderer, Text.literal("?").formatted(Formatting.DARK_GRAY, Formatting.BOLD),
                        listX + 10, ty + 11, 0x555555, false);

                // Hidden Weapon Name & Locked status
                context.drawText(this.textRenderer, Text.literal("??? (Locked)").formatted(Formatting.DARK_GRAY),
                        listX + 24, ty + 5, 0x666666, false);
                context.drawText(this.textRenderer, Text.literal("✦ Locked"), listX + 24, ty + 17, 0x555555, false);
            }
        }

        // 3. Right Panel: Selected Weapon Detailed Card
        int rightX = startX + 134;
        int rightY = startY + 28;
        int rightWidth = dialogWidth - 144;
        int rightHeight = dialogHeight - 58;

        context.fill(rightX, rightY, rightX + rightWidth, rightY + rightHeight, 0x500C121D);
        context.drawBorder(rightX, rightY, rightWidth, rightHeight, 0xFF2D3C52);

        WeaponInfo sel = weapons.get(selectedIndex);
        boolean selUnlocked = isWeaponUnlocked(sel.id, state);

        if (selUnlocked) {
            // UNLOCKED VIEW: Full details, texture, stats, lore, and attacks
            context.drawItem(sel.stack, rightX + 8, rightY + 8);
            context.drawText(this.textRenderer, Text.literal(sel.name).formatted(Formatting.BOLD),
                    rightX + 32, rightY + 7, sel.color, true);
            context.drawText(this.textRenderer, Text.literal(sel.element + "  •  " + sel.weaponClass),
                    rightX + 32, rightY + 18, 0xAAAAAA, false);

            // Stats Banner
            int statY = rightY + 31;
            context.fill(rightX + 4, statY, rightX + rightWidth - 4, statY + 13, 0x40182537);
            context.drawText(this.textRenderer, Text.literal(sel.stats).formatted(Formatting.YELLOW),
                    rightX + 8, statY + 3, 0xFFFF77, false);

            // Lore Section
            int curY = statY + 17;
            List<OrderedText> loreLines = this.textRenderer.wrapLines(Text.literal("§7" + sel.lore), rightWidth - 16);
            for (OrderedText line : loreLines) {
                context.drawText(this.textRenderer, line, rightX + 8, curY, 0xCCCCCC, false);
                curY += 9;
            }

            // Abilities Section
            curY += 4;
            context.fill(rightX + 6, curY, rightX + rightWidth - 6, curY + 1, 0x404A90E2);
            curY += 4;
            context.drawText(this.textRenderer, Text.literal("⚔ ATTACK TECHNIQUES & KEYBINDS").formatted(Formatting.GOLD, Formatting.BOLD),
                    rightX + 8, curY, 0xFFFFFF, true);
            curY += 12;

            for (AbilityEntry ability : sel.abilities) {
                // Combined into one Text component to prevent ANY text overlapping!
                Text abilityHeader = Text.empty()
                        .append(Text.literal(ability.keybind).formatted(Formatting.AQUA, Formatting.BOLD))
                        .append(Text.literal("  " + ability.name).formatted(Formatting.WHITE, Formatting.BOLD));
                context.drawText(this.textRenderer, abilityHeader, rightX + 8, curY, 0xFFFFFF, false);
                curY += 11;

                List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal("▪ " + ability.description), rightWidth - 16);
                for (OrderedText dLine : descLines) {
                    context.drawText(this.textRenderer, dLine, rightX + 8, curY, 0xCCCCCC, false);
                    curY += 9;
                }
                curY += 4;
            }
        } else {
            // LOCKED VIEW: Black outline silhouette, obscured name, hidden lore & attacks
            context.fill(rightX + 8, rightY + 8, rightX + 26, rightY + 26, 0xFF080B10);
            context.drawBorder(rightX + 8, rightY + 8, 18, 18, 0xFF333333);
            context.drawBorder(rightX + 9, rightY + 9, 16, 16, 0xFF141414);
            context.drawText(this.textRenderer, Text.literal("?").formatted(Formatting.DARK_GRAY, Formatting.BOLD),
                    rightX + 15, rightY + 13, 0x666666, false);

            context.drawText(this.textRenderer, Text.literal("??? Locked Weapon").formatted(Formatting.DARK_GRAY, Formatting.BOLD),
                    rightX + 32, rightY + 7, 0x777777, false);
            context.drawText(this.textRenderer, Text.literal("Unknown Element  •  Secret Weapon").formatted(Formatting.DARK_GRAY),
                    rightX + 32, rightY + 18, 0x555555, false);

            // Obscured Stats Banner
            int statY = rightY + 31;
            context.fill(rightX + 4, statY, rightX + rightWidth - 4, statY + 13, 0x40121620);
            context.drawText(this.textRenderer, Text.literal("Attack Damage: ???  |  Speed: ???  |  Special: ???").formatted(Formatting.DARK_GRAY),
                    rightX + 8, statY + 3, 0x666666, false);

            // Obscured Lore
            int curY = statY + 17;
            List<OrderedText> loreLines = this.textRenderer.wrapLines(Text.literal("§8This elemental weapon is currently locked. Forge preceding elemental weapons and progress through the trials to reveal this weapon's secrets."), rightWidth - 16);
            for (OrderedText line : loreLines) {
                context.drawText(this.textRenderer, line, rightX + 8, curY, 0x555555, false);
                curY += 9;
            }

            // Hidden Abilities Section
            curY += 4;
            context.fill(rightX + 6, curY, rightX + rightWidth - 6, curY + 1, 0x30333333);
            curY += 4;
            context.drawText(this.textRenderer, Text.literal("⚔ ATTACK TECHNIQUES & KEYBINDS").formatted(Formatting.DARK_GRAY, Formatting.BOLD),
                    rightX + 8, curY, 0x666666, false);
            curY += 12;

            Text lockedAbilityHeader = Text.empty()
                    .append(Text.literal("[???]").formatted(Formatting.DARK_GRAY, Formatting.BOLD))
                    .append(Text.literal("  Classified Technique").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
            context.drawText(this.textRenderer, lockedAbilityHeader, rightX + 8, curY, 0x555555, false);
            curY += 11;

            List<OrderedText> descLines = this.textRenderer.wrapLines(Text.literal("▪ Attack techniques and keybind combinations will be revealed upon unlocking this weapon."), rightWidth - 16);
            for (OrderedText dLine : descLines) {
                context.drawText(this.textRenderer, dLine, rightX + 8, curY, 0x444444, false);
                curY += 9;
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String getStatusTag(String weaponId, QuestState state) {
        if (!isWeaponUnlocked(weaponId, state)) {
            return "✦ Locked";
        }
        if ("sunforged_scimitar".equalsIgnoreCase(weaponId)) {
            if (state.getId() >= QuestState.CRAFT_SNOW_ELEMENT.getId()) return "✔ Mastered";
            return "✦ Active Quest";
        }
        if ("frostwake_pick".equalsIgnoreCase(weaponId)) {
            if (state.getId() >= QuestState.CRAFT_TREE_ELEMENT.getId()) return "✔ Mastered";
            if (state == QuestState.CRAFT_SNOW_ELEMENT || state == QuestState.FORGE_FROSTWAKE_PICK) return "✦ Active Quest";
            return "✦ Next Weapon";
        }
        if ("rootbound_axe".equalsIgnoreCase(weaponId)) {
            if (state.getId() >= QuestState.LOCATE_EVERLIVING_ARENA.getId()) return "✔ Mastered";
            if (state == QuestState.CRAFT_TREE_ELEMENT || state == QuestState.FORGE_ROOTBOUND_AXE) return "✦ Active Quest";
            return "✦ Next Weapon";
        }
        return "✦ Locked";
    }

    private int getStatusColor(String weaponId, QuestState state) {
        String tag = getStatusTag(weaponId, state);
        if (tag.startsWith("✔")) return 0x55FF55;
        if (tag.contains("Active")) return 0x55FFFF;
        if (tag.contains("Next")) return 0xFFAA00;
        return 0x555555;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
