package com.elemental.quest;

public enum QuestState {
    CRAFT_STONE_TOOLS(0, "quest.elemental.state.craft_stone_tools"),
    FIND_AERON(1, "quest.elemental.state.find_aeron"),
    LOCATE_SKYFORGE(2, "quest.elemental.state.locate_skyforge"),
    SALVAGE_MODULES(3, "quest.elemental.state.salvage_modules"),
    CRAFT_JETPACK(4, "quest.elemental.state.craft_jetpack"),
    RETURN_TO_AERON(5, "quest.elemental.state.return_to_aeron"),
    DESERT_EXPEDITION(6, "quest.elemental.state.desert_expedition"),
    QUEST_COMPLETE(7, "quest.elemental.state.quest_complete");

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
