package com.elemental.quest;

public enum QuestState {
    CRAFT_STONE_TOOLS(0, "quest.elemental.state.craft_stone_tools"),
    FIND_AERON(1, "quest.elemental.state.find_aeron"),
    LOCATE_SKYFORGE(2, "quest.elemental.state.locate_skyforge"),
    SALVAGE_MODULES(3, "quest.elemental.state.salvage_modules"),
    RETURN_TO_AERON(4, "quest.elemental.state.return_to_aeron"),
    CRAFT_PNEUMATIC_PISTON(5, "quest.elemental.state.craft_pneumatic_piston"),
    CRAFT_AEROGEL_CORE(6, "quest.elemental.state.craft_aerogel_core"),
    CRAFT_ELEMENT_CORE(7, "quest.elemental.state.craft_element_core"),
    CRAFT_JETPACK(8, "quest.elemental.state.craft_jetpack"),
    BIOME_EXPEDITION_1(9, "quest.elemental.state.biome_expedition_1"),
    REACH_BIOME_2(10, "quest.elemental.state.reach_biome_2"),
    FORGE_WEAPON_2(11, "quest.elemental.state.forge_weapon_2"),
    REACH_BIOME_3(12, "quest.elemental.state.reach_biome_3"),
    FORGE_WEAPON_3(13, "quest.elemental.state.forge_weapon_3"),
    LOCATE_EVERLIVING_ARENA(14, "quest.elemental.state.locate_everliving_arena"),
    DEFEAT_EVERLIVING_KNIGHT(15, "quest.elemental.state.defeat_everliving_knight"),
    QUEST_COMPLETE(16, "quest.elemental.state.quest_complete"),

    // Legacy compatibility values
    BIOME_EXPEDITION_2(100, "quest.elemental.state.reach_biome_2"),
    BIOME_EXPEDITION_3(101, "quest.elemental.state.reach_biome_3"),
    DESERT_EXPEDITION(17, "quest.elemental.state.desert_expedition"),
    TALK_TO_WIZARD(18, "quest.elemental.state.talk_to_wizard"),
    CRAFT_WEAPONER(19, "quest.elemental.state.craft_weaponer"),
    CRAFT_DRY_HEAT_ELEMENT(20, "quest.elemental.state.craft_dry_heat_element"),
    FORGE_SUNFORGED_SCIMITAR(21, "quest.elemental.state.forge_sunforged_scimitar"),
    CRAFT_SNOW_ELEMENT(22, "quest.elemental.state.craft_snow_element"),
    FORGE_FROSTWAKE_PICK(23, "quest.elemental.state.forge_frostwake_pick"),
    CRAFT_TREE_ELEMENT(24, "quest.elemental.state.craft_tree_element"),
    FORGE_ROOTBOUND_AXE(25, "quest.elemental.state.forge_rootbound_axe");

    private final int id;
    private final String translationKey;

    QuestState(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public static QuestState fromId(int id) {
        return switch (id) {
            case 0 -> CRAFT_STONE_TOOLS;
            case 1 -> FIND_AERON;
            case 2 -> LOCATE_SKYFORGE;
            case 3 -> SALVAGE_MODULES;
            case 4 -> RETURN_TO_AERON;
            case 5 -> CRAFT_PNEUMATIC_PISTON;
            case 6 -> CRAFT_AEROGEL_CORE;
            case 7 -> CRAFT_ELEMENT_CORE;
            case 8 -> CRAFT_JETPACK;
            case 9 -> BIOME_EXPEDITION_1;
            case 10 -> REACH_BIOME_2;
            case 11 -> FORGE_WEAPON_2;
            case 12 -> REACH_BIOME_3;
            case 13 -> FORGE_WEAPON_3;
            case 14 -> LOCATE_EVERLIVING_ARENA;
            case 15 -> DEFEAT_EVERLIVING_KNIGHT;
            case 16 -> QUEST_COMPLETE;
            case 100 -> REACH_BIOME_2;
            case 101 -> REACH_BIOME_3;
            default -> {
                for (QuestState state : values()) {
                    if (state.id == id) yield state;
                }
                yield CRAFT_STONE_TOOLS;
            }
        };
    }
}
