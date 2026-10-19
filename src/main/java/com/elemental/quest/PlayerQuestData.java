package com.elemental.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class PlayerQuestData {
    private final UUID playerUuid;
    private QuestSaveData parent;
    private QuestState state = QuestState.CRAFT_STONE_TOOLS;
    private int pistonsSalvaged = 0;
    private int aerogelExtracted = 0;
    private int blueprintsFound = 0;
    private boolean claimedJetpack = false;
    private boolean completedPhase1Dialogue = false;
    private BlockPos targetWaypoint = null;
    private boolean guidanceActive = true;

    public PlayerQuestData(UUID playerUuid) {
        this(playerUuid, null);
    }

    public PlayerQuestData(UUID playerUuid, QuestSaveData parent) {
        this.playerUuid = playerUuid;
        this.parent = parent;
    }

    public void setParent(QuestSaveData parent) {
        this.parent = parent;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public QuestState getState() {
        return parent != null ? parent.getCollectiveState() : state;
    }

    public void setState(QuestState state) {
        if (parent != null) {
            parent.setCollectiveState(state);
        } else {
            this.state = state;
        }
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
        return parent != null ? parent.getCollectiveBlueprintsFound() : blueprintsFound;
    }

    public void setBlueprintsFound(int blueprintsFound) {
        if (parent != null) {
            parent.setCollectiveBlueprintsFound(blueprintsFound);
        } else {
            this.blueprintsFound = blueprintsFound;
        }
    }

    public boolean isBlueprintFound() {
        return getBlueprintsFound() >= 4;
    }

    public void setBlueprintFound(boolean found) {
        if (found && getBlueprintsFound() == 0) {
            setBlueprintsFound(4);
        }
    }

    public boolean hasClaimedJetpack() {
        return parent != null ? parent.isCollectiveClaimedJetpack() : claimedJetpack;
    }

    public void setClaimedJetpack(boolean claimedJetpack) {
        if (parent != null) {
            parent.setCollectiveClaimedJetpack(claimedJetpack);
        } else {
            this.claimedJetpack = claimedJetpack;
        }
    }

    public boolean hasCompletedPhase1Dialogue() {
        return parent != null ? parent.isCollectiveCompletedPhase1Dialogue() : completedPhase1Dialogue;
    }

    public void setCompletedPhase1Dialogue(boolean completedPhase1Dialogue) {
        if (parent != null) {
            parent.setCollectiveCompletedPhase1Dialogue(completedPhase1Dialogue);
        } else {
            this.completedPhase1Dialogue = completedPhase1Dialogue;
        }
    }

    public BlockPos getTargetWaypoint() {
        return parent != null ? parent.getCollectiveTargetWaypoint() : targetWaypoint;
    }

    public void setTargetWaypoint(BlockPos targetWaypoint) {
        if (parent != null) {
            parent.setCollectiveTargetWaypoint(targetWaypoint);
        } else {
            this.targetWaypoint = targetWaypoint;
        }
    }

    public boolean isGuidanceActive() {
        return guidanceActive;
    }

    public void setGuidanceActive(boolean guidanceActive) {
        this.guidanceActive = guidanceActive;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putUuid("PlayerUuid", this.playerUuid);
        nbt.putInt("State", getState().getId());
        nbt.putInt("Pistons", this.pistonsSalvaged);
        nbt.putInt("Aerogel", this.aerogelExtracted);
        nbt.putInt("BlueprintsFound", getBlueprintsFound());
        nbt.putBoolean("ClaimedJetpack", hasClaimedJetpack());
        nbt.putBoolean("CompletedPhase1", hasCompletedPhase1Dialogue());
        nbt.putBoolean("GuidanceActive", this.guidanceActive);
        BlockPos wp = getTargetWaypoint();
        if (wp != null) {
            nbt.putInt("TargetX", wp.getX());
            nbt.putInt("TargetY", wp.getY());
            nbt.putInt("TargetZ", wp.getZ());
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
        if (nbt.contains("CompletedPhase1")) {
            this.completedPhase1Dialogue = nbt.getBoolean("CompletedPhase1");
        } else if (this.state.getId() >= QuestState.LOCATE_SKYFORGE.getId()) {
            this.completedPhase1Dialogue = true;
        }
        if (nbt.contains("GuidanceActive")) {
            this.guidanceActive = nbt.getBoolean("GuidanceActive");
        }
        if (nbt.contains("TargetX") && nbt.contains("TargetY") && nbt.contains("TargetZ")) {
            this.targetWaypoint = new BlockPos(nbt.getInt("TargetX"), nbt.getInt("TargetY"), nbt.getInt("TargetZ"));
        }
    }
}
