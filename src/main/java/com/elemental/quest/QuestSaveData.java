package com.elemental.quest;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QuestSaveData extends PersistentState {
    public static final String IDENTIFIER = "elemental_quests";

    private boolean guideSpawned = false;
    private boolean tentGenerated = false;
    private BlockPos aeronsTentLocation = null;
    private BlockPos skyforgeAirshipLocation = null;
    private BlockPos desertLocation = null;
    // Collective Quest Progress (Shared across all players on the server)
    private QuestState collectiveState = QuestState.CRAFT_STONE_TOOLS;
    private int collectiveBlueprintsFound = 0;
    private boolean collectiveCompletedPhase1Dialogue = false;
    private boolean collectiveClaimedJetpack = false;
    private BlockPos collectiveTargetWaypoint = null;
    private final Map<UUID, PlayerQuestData> playerDataMap = new HashMap<>();

    public QuestSaveData() {
        super();
    }

    public QuestState getCollectiveState() {
        return collectiveState;
    }

    public void setCollectiveState(QuestState state) {
        this.collectiveState = state;
        this.markDirty();
    }

    public int getCollectiveBlueprintsFound() {
        return collectiveBlueprintsFound;
    }

    public void setCollectiveBlueprintsFound(int count) {
        this.collectiveBlueprintsFound = count;
        this.markDirty();
    }

    public boolean isCollectiveCompletedPhase1Dialogue() {
        return collectiveCompletedPhase1Dialogue;
    }

    public void setCollectiveCompletedPhase1Dialogue(boolean val) {
        this.collectiveCompletedPhase1Dialogue = val;
        this.markDirty();
    }

    public boolean isCollectiveClaimedJetpack() {
        return collectiveClaimedJetpack;
    }

    public void setCollectiveClaimedJetpack(boolean val) {
        this.collectiveClaimedJetpack = val;
        this.markDirty();
    }

    public BlockPos getCollectiveTargetWaypoint() {
        return collectiveTargetWaypoint;
    }

    public void setCollectiveTargetWaypoint(BlockPos waypoint) {
        this.collectiveTargetWaypoint = waypoint;
        this.markDirty();
    }

    public boolean isGuideSpawned() {
        return guideSpawned;
    }

    public void setGuideSpawned(boolean guideSpawned) {
        this.guideSpawned = guideSpawned;
        this.markDirty();
    }

    public boolean isTentGenerated() {
        return tentGenerated;
    }

    public void setTentGenerated(boolean tentGenerated) {
        this.tentGenerated = tentGenerated;
        this.markDirty();
    }

    public BlockPos getAeronsTentLocation() {
        return aeronsTentLocation;
    }

    public void setAeronsTentLocation(BlockPos location) {
        this.aeronsTentLocation = location;
        this.markDirty();
    }

    public BlockPos getSkyforgeAirshipLocation() {
        return skyforgeAirshipLocation;
    }

    public void setSkyforgeAirshipLocation(BlockPos location) {
        this.skyforgeAirshipLocation = location;
        this.markDirty();
    }

    public BlockPos getDesertLocation() {
        return desertLocation;
    }

    public void setDesertLocation(BlockPos location) {
        this.desertLocation = location;
        this.markDirty();
    }

    public PlayerQuestData getPlayerData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, id -> {
            PlayerQuestData data = new PlayerQuestData(id, this);
            this.markDirty();
            return data;
        });
    }

    public static QuestSaveData fromNbt(NbtCompound nbt) {
        QuestSaveData data = new QuestSaveData();
        data.guideSpawned = nbt.getBoolean("GuideSpawned");
        data.tentGenerated = nbt.getBoolean("TentGenerated");

        if (nbt.contains("CollectiveState")) {
            data.collectiveState = QuestState.fromId(nbt.getInt("CollectiveState"));
            data.collectiveBlueprintsFound = nbt.getInt("CollectiveBlueprintsFound");
            data.collectiveCompletedPhase1Dialogue = nbt.getBoolean("CollectiveCompletedPhase1");
            data.collectiveClaimedJetpack = nbt.getBoolean("CollectiveClaimedJetpack");
            if (nbt.contains("CollectiveTargetX")) {
                data.collectiveTargetWaypoint = new BlockPos(
                        nbt.getInt("CollectiveTargetX"),
                        nbt.getInt("CollectiveTargetY"),
                        nbt.getInt("CollectiveTargetZ")
                );
            }
        }

        if (nbt.contains("TentX") && nbt.contains("TentY") && nbt.contains("TentZ")) {
            data.aeronsTentLocation = new BlockPos(
                    nbt.getInt("TentX"),
                    nbt.getInt("TentY"),
                    nbt.getInt("TentZ")
            );
        } else if (nbt.contains("VillageX") && nbt.contains("VillageY") && nbt.contains("VillageZ")) {
            data.aeronsTentLocation = new BlockPos(
                    nbt.getInt("VillageX"),
                    nbt.getInt("VillageY"),
                    nbt.getInt("VillageZ")
            );
        }

        if (nbt.contains("SkyforgeX") && nbt.contains("SkyforgeY") && nbt.contains("SkyforgeZ")) {
            data.skyforgeAirshipLocation = new BlockPos(
                    nbt.getInt("SkyforgeX"),
                    nbt.getInt("SkyforgeY"),
                    nbt.getInt("SkyforgeZ")
            );
        }

        if (nbt.contains("DesertX") && nbt.contains("DesertY") && nbt.contains("DesertZ")) {
            data.desertLocation = new BlockPos(
                    nbt.getInt("DesertX"),
                    nbt.getInt("DesertY"),
                    nbt.getInt("DesertZ")
            );
        }

        if (nbt.contains("Players", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound playerNbt = list.getCompound(i);
                UUID uuid = playerNbt.getUuid("PlayerUuid");
                PlayerQuestData pData = new PlayerQuestData(uuid, data);
                pData.readNbt(playerNbt);
                data.playerDataMap.put(uuid, pData);
                // Backward compatibility: If no collective state was stored, infer from player data
                if (!nbt.contains("CollectiveState") && pData.getState().getId() > data.collectiveState.getId()) {
                    data.collectiveState = pData.getState();
                    data.collectiveBlueprintsFound = Math.max(data.collectiveBlueprintsFound, pData.getBlueprintsFound());
                    data.collectiveCompletedPhase1Dialogue = data.collectiveCompletedPhase1Dialogue || pData.hasCompletedPhase1Dialogue();
                    data.collectiveClaimedJetpack = data.collectiveClaimedJetpack || pData.hasClaimedJetpack();
                    if (pData.getTargetWaypoint() != null) {
                        data.collectiveTargetWaypoint = pData.getTargetWaypoint();
                    }
                }
            }
        }

        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("GuideSpawned", this.guideSpawned);
        nbt.putBoolean("TentGenerated", this.tentGenerated);

        nbt.putInt("CollectiveState", this.collectiveState.getId());
        nbt.putInt("CollectiveBlueprintsFound", this.collectiveBlueprintsFound);
        nbt.putBoolean("CollectiveCompletedPhase1", this.collectiveCompletedPhase1Dialogue);
        nbt.putBoolean("CollectiveClaimedJetpack", this.collectiveClaimedJetpack);
        if (this.collectiveTargetWaypoint != null) {
            nbt.putInt("CollectiveTargetX", this.collectiveTargetWaypoint.getX());
            nbt.putInt("CollectiveTargetY", this.collectiveTargetWaypoint.getY());
            nbt.putInt("CollectiveTargetZ", this.collectiveTargetWaypoint.getZ());
        }

        if (this.aeronsTentLocation != null) {
            nbt.putInt("TentX", this.aeronsTentLocation.getX());
            nbt.putInt("TentY", this.aeronsTentLocation.getY());
            nbt.putInt("TentZ", this.aeronsTentLocation.getZ());
        }

        if (this.skyforgeAirshipLocation != null) {
            nbt.putInt("SkyforgeX", this.skyforgeAirshipLocation.getX());
            nbt.putInt("SkyforgeY", this.skyforgeAirshipLocation.getY());
            nbt.putInt("SkyforgeZ", this.skyforgeAirshipLocation.getZ());
        }

        if (this.desertLocation != null) {
            nbt.putInt("DesertX", this.desertLocation.getX());
            nbt.putInt("DesertY", this.desertLocation.getY());
            nbt.putInt("DesertZ", this.desertLocation.getZ());
        }

        NbtList list = new NbtList();
        for (PlayerQuestData pData : this.playerDataMap.values()) {
            NbtCompound playerNbt = new NbtCompound();
            pData.writeNbt(playerNbt);
            list.add(playerNbt);
        }
        nbt.put("Players", list);

        return nbt;
    }

    public static QuestSaveData getServerState(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(QuestSaveData::fromNbt, QuestSaveData::new, IDENTIFIER);
    }
}
