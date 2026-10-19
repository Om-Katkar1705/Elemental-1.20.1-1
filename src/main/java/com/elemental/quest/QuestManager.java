package com.elemental.quest;

import com.elemental.Elemental;
import com.elemental.item.BlueprintMaps;
import com.elemental.item.ModItems;
import com.elemental.network.QuestNetworking;
import com.elemental.sound.ModSounds;
import com.elemental.world.NbtStructureManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

public class QuestManager {

    public static PlayerQuestData getPlayerData(ServerPlayerEntity player) {
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);
        return saveData.getPlayerData(player.getUuid());
    }

    public static void setQuestState(ServerPlayerEntity player, QuestState newState) {
        PlayerQuestData data = getPlayerData(player);
        data.setState(newState);
        updateWaypointForState(player, data);
        QuestSaveData.getServerState(player.server).markDirty();
        syncToAll(player.server);
    }

    public static void updateWaypointForState(ServerPlayerEntity player, PlayerQuestData data) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        switch (data.getState()) {
            case CRAFT_STONE_TOOLS -> {
                data.setTargetWaypoint(null);
            }
            case FIND_AERON, RETURN_TO_AERON, CRAFT_JETPACK -> {
                BlockPos campPos = saveData.getAeronsTentLocation();
                data.setTargetWaypoint(campPos != null ? campPos : player.getBlockPos());
            }
            case LOCATE_SKYFORGE, SALVAGE_MODULES -> {
                BlockPos airshipPos = NbtStructureManager.findOrGenerateAirship(world, player.getBlockPos());
                data.setTargetWaypoint(airshipPos);
            }
            case DESERT_EXPEDITION -> {
                BlockPos desertPos = saveData.getDesertLocation();
                if (desertPos == null) {
                    desertPos = NbtStructureManager.findOrGenerateDesertSanctuary(world, player.getBlockPos());
                }
                data.setTargetWaypoint(desertPos);
            }
            case QUEST_COMPLETE -> {
                data.setTargetWaypoint(null);
            }
        }
    }

    public static BlockPos findNearestDesert(ServerWorld world, BlockPos center) {
        try {
            var pair = world.locateBiome(
                    entry -> entry.matchesKey(BiomeKeys.DESERT),
                    center,
                    6400,
                    32,
                    64
            );
            if (pair != null) {
                return pair.getFirst();
            }

            var fallbackPair = world.locateBiome(
                    entry -> entry.isIn(BiomeTags.IS_BADLANDS),
                    center,
                    6400,
                    32,
                    64
            );
            if (fallbackPair != null) {
                return fallbackPair.getFirst();
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Error locating desert biome: ", e);
        }
        return center.add(1000, 0, 800);
    }

    /**
     * Periodic server-side check for inventory items, proximity, and collective objective advancement.
     */
    public static void tickPlayer(ServerPlayerEntity player) {
        PlayerQuestData data = getPlayerData(player);
        QuestState state = data.getState();

        boolean updated = false;

        // 1. Initial State: Any player crafts any Stone Tool -> advance to FIND_AERON collectively
        if (state == QuestState.CRAFT_STONE_TOOLS) {
            if (hasStoneTool(player)) {
                data.setState(QuestState.FIND_AERON);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("message.elemental.stone_tool_crafted").formatted(Formatting.GOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Count combined blueprints across all online players
        int blueprintsCount = BlueprintMaps.countUniqueBlueprintsCombined(player.server.getPlayerManager().getPlayerList());
        boolean hasAllBlueprints = blueprintsCount >= BlueprintMaps.Type.values().length;

        // Track blueprints count collectively
        if (state.getId() < QuestState.RETURN_TO_AERON.getId() && !data.hasClaimedJetpack()) {
            if (blueprintsCount > data.getBlueprintsFound()) {
                data.setBlueprintsFound(blueprintsCount);
                updated = true;
            }
        } else if (data.getBlueprintsFound() < 4) {
            data.setBlueprintsFound(4);
            updated = true;
        }

        // 2. Proximity check for LOCATE_SKYFORGE -> SALVAGE_MODULES collectively
        if (state == QuestState.LOCATE_SKYFORGE) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 32 * 32) {
                data.setState(QuestState.SALVAGE_MODULES);
                NbtStructureManager.ensureAirshipChestsPopulated(player.getServerWorld(), target);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.airship_reached").formatted(Formatting.GOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 3. In SALVAGE_MODULES: ensure chests are populated, and advance to RETURN_TO_AERON ONLY when all 4 recipe maps are collected
        if (state == QuestState.SALVAGE_MODULES) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 48 * 48) {
                NbtStructureManager.ensureAirshipChestsPopulated(player.getServerWorld(), target);
            }
            if (hasAllBlueprints) {
                data.setState(QuestState.RETURN_TO_AERON);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.all_blueprints_salvaged").formatted(Formatting.AQUA, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.2f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 4. Legacy CRAFT_JETPACK state transition: advance to RETURN_TO_AERON once all maps are held
        if (state == QuestState.CRAFT_JETPACK && hasAllBlueprints) {
            data.setState(QuestState.RETURN_TO_AERON);
            updateWaypointForState(player, data);
            syncToAll(player.server);
            updated = true;
        }

        // 5. Proximity check for DESERT_EXPEDITION -> arrival at Desert Sanctuary collectively
        if (state == QuestState.DESERT_EXPEDITION) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 32 * 32) {
                data.setState(QuestState.QUEST_COMPLETE);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.desert_sanctuary_reached").formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.quest_complete").formatted(Formatting.GREEN, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.QUEST_COMPLETED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        if (updated) {
            QuestSaveData.getServerState(player.server).markDirty();
            syncToAll(player.server);
        }
    }

    private static boolean hasStoneTool(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.STONE_PICKAXE) || stack.isOf(Items.STONE_AXE)
                    || stack.isOf(Items.STONE_SWORD) || stack.isOf(Items.STONE_SHOVEL)
                    || stack.isOf(Items.STONE_HOE)) {
                return true;
            }
        }
        return false;
    }

    private static int countItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasItemOrWorn(ServerPlayerEntity player, net.minecraft.item.Item item) {
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(item)) return true;
        if (com.elemental.util.JetpackSlotHolder.getJetpackStack(player).isOf(item)) return true;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    public static void syncToClient(ServerPlayerEntity player) {
        PlayerQuestData data = getPlayerData(player);
        QuestNetworking.sendQuestSync(player, data);
    }

    public static void syncToAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            syncToClient(p);
        }
    }
}
