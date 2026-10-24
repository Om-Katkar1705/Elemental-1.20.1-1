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
    DESERT_EXPEDITION(9, "quest.elemental.state.desert_expedition"),
    TALK_TO_WIZARD(10, "quest.elemental.state.talk_to_wizard"),
    CRAFT_WEAPONER(11, "quest.elemental.state.craft_weaponer"),
    CRAFT_DRY_HEAT_ELEMENT(12, "quest.elemental.state.craft_dry_heat_element"),
    FORGE_SUNFORGED_SCIMITAR(13, "quest.elemental.state.forge_sunforged_scimitar"),
    CRAFT_SNOW_ELEMENT(14, "quest.elemental.state.craft_snow_element"),
    FORGE_FROSTWAKE_PICK(15, "quest.elemental.state.forge_frostwake_pick"),
    CRAFT_TREE_ELEMENT(16, "quest.elemental.state.craft_tree_element"),
    FORGE_ROOTBOUND_AXE(17, "quest.elemental.state.forge_rootbound_axe"),
    LOCATE_EVERLIVING_ARENA(18, "quest.elemental.state.locate_everliving_arena"),
    DEFEAT_EVERLIVING_KNIGHT(19, "quest.elemental.state.defeat_everliving_knight"),
    QUEST_COMPLETE(20, "quest.elemental.state.quest_complete");

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
        for (QuestState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        return CRAFT_STONE_TOOLS;
    }
}
