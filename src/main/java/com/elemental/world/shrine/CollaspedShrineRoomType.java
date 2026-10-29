package com.elemental.world.shrine;

/**
 * Exactly 12 distinct semantic room types for the Collasped Shrine dungeon.
 * The spelling "Collasped Shrine" is preserved as required by project specifications.
 */
public enum CollaspedShrineRoomType {
    ARRIVAL_CHAMBER("Arrival Chamber", 1, 1, 100, true),
    PILLARED_HALL("Pillared Hall", 1, 3, 30, false),
    OFFERING_CHAMBER("Offering Chamber", 1, 2, 25, false),
    BURIAL_CRYPT("Burial Crypt", 1, 2, 25, false),
    OVERGROWN_GARDEN("Overgrown Garden", 1, 2, 20, false),
    WATER_CISTERN("Water Cistern", 1, 2, 20, false),
    COLLAPSED_CHAMBER("Collapsed Chamber", 1, 2, 20, false),
    TRAP_GALLERY("Trap Gallery", 0, 2, 15, false),
    ARCHIVE_ROOM("Archive Room", 0, 2, 15, false),
    STORAGE_CHAMBER("Storage Chamber", 0, 2, 20, false),
    GUARDIAN_HALL("Guardian Hall", 0, 1, 10, false),
    INNER_SANCTUM("Inner Sanctum", 1, 1, 100, true);

    private final String displayName;
    private final int minInstances;
    private final int maxInstances;
    private final int selectionWeight;
    private final boolean guaranteed;

    CollaspedShrineRoomType(String displayName, int minInstances, int maxInstances, int selectionWeight, boolean guaranteed) {
        this.displayName = displayName;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.selectionWeight = selectionWeight;
        this.guaranteed = guaranteed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinInstances() {
        return minInstances;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public int getSelectionWeight() {
        return selectionWeight;
    }

    public boolean isGuaranteed() {
        return guaranteed;
    }
}

