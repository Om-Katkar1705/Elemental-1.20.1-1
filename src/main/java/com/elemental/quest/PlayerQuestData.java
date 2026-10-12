package com.elemental.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class PlayerQuestData {
    private final UUID playerUuid;
    private QuestState state = QuestState.CRAFT_STONE_TOOLS;
    private int pistonsSalvaged = 0;
    private int aerogelExtracted = 0;
    private int blueprintsFound = 0;
    private boolean claimedJetpack = false;
    private BlockPos targetWaypoint = null;
    private boolean guidanceActive = true;

    public PlayerQuestData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public QuestState getState() {
        return state;
    }

    public void setState(QuestState state) {
        this.state = state;
    }

    public int getPistonsSalvaged() {
        return pistonsSalvaged;
    }

    public void setPistonsSalvaged(int pistonsSalvaged) {
        this.pistonsSalvaged = pistonsSalvaged;
    }

    public int getAerogelExtracted() {
        return aerogelExtracted;
    }

    public void setAerogelExtracted(int aerogelExtracted) {
        this.aerogelExtracted = aerogelExtracted;
    }

    public int getBlueprintsFound() {
        return blueprintsFound;
    }

    public void setBlueprintsFound(int blueprintsFound) {
        this.blueprintsFound = blueprintsFound;
    }

    public boolean isBlueprintFound() {
        return blueprintsFound >= 4;
    }

    public void setBlueprintFound(boolean found) {
        if (found && this.blueprintsFound == 0) {
            this.blueprintsFound = 4;
        }
    }

    public boolean hasClaimedJetpack() {
        return claimedJetpack;
    }

    public void setClaimedJetpack(boolean claimedJetpack) {
        this.claimedJetpack = claimedJetpack;
    }

    public BlockPos getTargetWaypoint() {
        return targetWaypoint;
    }

    public void setTargetWaypoint(BlockPos targetWaypoint) {
        this.targetWaypoint = targetWaypoint;
    }

    public boolean isGuidanceActive() {
        return guidanceActive;
    }

    public void setGuidanceActive(boolean guidanceActive) {
        this.guidanceActive = guidanceActive;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putUuid("PlayerUuid", this.playerUuid);
        nbt.putInt("State", this.state.getId());
        nbt.putInt("Pistons", this.pistonsSalvaged);
        nbt.putInt("Aerogel", this.aerogelExtracted);
        nbt.putInt("BlueprintsFound", this.blueprintsFound);
        nbt.putBoolean("ClaimedJetpack", this.claimedJetpack);
        nbt.putBoolean("GuidanceActive", this.guidanceActive);
        if (this.targetWaypoint != null) {
            nbt.putInt("TargetX", this.targetWaypoint.getX());
            nbt.putInt("TargetY", this.targetWaypoint.getY());
            nbt.putInt("TargetZ", this.targetWaypoint.getZ());
        }
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("State")) {
            this.state = QuestState.fromId(nbt.getInt("State"));
        }
        if (nbt.contains("Pistons")) {
            this.pistonsSalvaged = nbt.getInt("Pistons");
        }
        if (nbt.contains("Aerogel")) {
            this.aerogelExtracted = nbt.getInt("Aerogel");
        }
        if (nbt.contains("BlueprintsFound")) {
            this.blueprintsFound = nbt.getInt("BlueprintsFound");
        } else if (nbt.contains("Blueprint")) {
            this.blueprintsFound = nbt.getBoolean("Blueprint") ? 4 : 0;
        }
        if (nbt.contains("ClaimedJetpack")) {
            this.claimedJetpack = nbt.getBoolean("ClaimedJetpack");
        }
        if (nbt.contains("GuidanceActive")) {
            this.guidanceActive = nbt.getBoolean("GuidanceActive");
        }
        if (nbt.contains("TargetX") && nbt.contains("TargetY") && nbt.contains("TargetZ")) {
            this.targetWaypoint = new BlockPos(nbt.getInt("TargetX"), nbt.getInt("TargetY"), nbt.getInt("TargetZ"));
        }
    }
}
