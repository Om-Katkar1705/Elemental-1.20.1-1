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
    private final Map<UUID, PlayerQuestData> playerDataMap = new HashMap<>();

    public QuestSaveData() {
        super();
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
            PlayerQuestData data = new PlayerQuestData(id);
            this.markDirty();
            return data;
        });
    }

    public static QuestSaveData fromNbt(NbtCompound nbt) {
        QuestSaveData data = new QuestSaveData();
        data.guideSpawned = nbt.getBoolean("GuideSpawned");
        data.tentGenerated = nbt.getBoolean("TentGenerated");

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
                PlayerQuestData pData = new PlayerQuestData(uuid);
                pData.readNbt(playerNbt);
                data.playerDataMap.put(uuid, pData);
            }
        }

        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("GuideSpawned", this.guideSpawned);
        nbt.putBoolean("TentGenerated", this.tentGenerated);

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
