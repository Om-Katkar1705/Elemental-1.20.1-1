package com.elemental.client.quest;

import com.elemental.quest.QuestState;
import net.minecraft.util.math.BlockPos;

public class ClientQuestData {
    private static QuestState state = QuestState.CRAFT_STONE_TOOLS;
    private static int pistonsSalvaged = 0;
    private static int aerogelExtracted = 0;
    private static int blueprintsFound = 0;
    private static boolean guidanceActive = true;
    private static BlockPos targetWaypoint = null;

    public static void update(QuestState newState, int pistons, int aerogel, int blueprints, boolean guidance, BlockPos target) {
        state = newState;
        pistonsSalvaged = pistons;
        aerogelExtracted = aerogel;
        blueprintsFound = blueprints;
        guidanceActive = guidance;
        targetWaypoint = target;
    }

    public static QuestState getState() {
        return state;
    }

    public static int getPistonsSalvaged() {
        return pistonsSalvaged;
    }

    public static int getAerogelExtracted() {
        return aerogelExtracted;
    }

    public static int getBlueprintsFound() {
        return blueprintsFound;
    }

    public static boolean isGuidanceActive() {
        return guidanceActive;
    }

    public static BlockPos getTargetWaypoint() {
        return targetWaypoint;
    }
}
