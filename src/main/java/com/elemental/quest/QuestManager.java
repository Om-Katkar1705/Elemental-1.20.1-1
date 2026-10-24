package com.elemental.quest;

import java.util.List;
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
            case FIND_AERON, RETURN_TO_AERON, CRAFT_PNEUMATIC_PISTON, CRAFT_AEROGEL_CORE, CRAFT_ELEMENT_CORE, CRAFT_JETPACK -> {
                BlockPos campPos = saveData.getAeronsTentLocation();
                data.setTargetWaypoint(campPos != null ? campPos : player.getBlockPos());
            }
            case LOCATE_SKYFORGE, SALVAGE_MODULES -> {
                BlockPos airshipPos = NbtStructureManager.findOrGenerateAirship(world, player.getBlockPos());
                data.setTargetWaypoint(airshipPos);
            }
            case DESERT_EXPEDITION, TALK_TO_WIZARD, CRAFT_WEAPONER, CRAFT_DRY_HEAT_ELEMENT, FORGE_SUNFORGED_SCIMITAR -> {
                BlockPos desertPos = saveData.getDesertLocation();
                if (desertPos == null) {
                    desertPos = NbtStructureManager.findOrGenerateDesertSanctuary(world, player.getBlockPos());
                }
                data.setTargetWaypoint(desertPos);
            }
            case CRAFT_SNOW_ELEMENT, FORGE_FROSTWAKE_PICK -> {
                BlockPos snowPos = saveData.getSnowBiomeLocation();
                if (snowPos == null) {
                    snowPos = NbtStructureManager.findNearestSnowBiome(world, player.getBlockPos());
                }
                data.setTargetWaypoint(snowPos);
            }
            case CRAFT_TREE_ELEMENT, FORGE_ROOTBOUND_AXE -> {
                BlockPos forestPos = saveData.getForestBiomeLocation();
                if (forestPos == null) {
                    forestPos = NbtStructureManager.findNearestForestBiome(world, player.getBlockPos());
                }
                data.setTargetWaypoint(forestPos);
            }
            case LOCATE_EVERLIVING_ARENA, DEFEAT_EVERLIVING_KNIGHT -> {
                BlockPos arenaPos = saveData.getEverlivingArenaLocation();
                if (arenaPos == null) {
                    arenaPos = NbtStructureManager.findOrGenerateEverlivingArena(world, player.getBlockPos());
                }
                data.setTargetWaypoint(arenaPos);
            }
            case QUEST_COMPLETE -> {
                data.setTargetWaypoint(null);
            }
        }
    }

    public static void handleSelectWeaponQuest(ServerPlayerEntity player, String weaponId) {
        if ("frostwake_pick".equalsIgnoreCase(weaponId)) {
            setQuestState(player, QuestState.CRAFT_SNOW_ELEMENT);
            player.sendMessage(Text.translatable("quest.elemental.frostwake_pick_assigned").formatted(Formatting.AQUA, Formatting.BOLD), false);
            for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        } else if ("rootbound_axe".equalsIgnoreCase(weaponId)) {
            setQuestState(player, QuestState.CRAFT_TREE_ELEMENT);
            player.sendMessage(Text.translatable("quest.elemental.rootbound_axe_assigned").formatted(Formatting.GREEN, Formatting.BOLD), false);
            for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        } else if ("sunforged_scimitar".equalsIgnoreCase(weaponId)) {
            PlayerQuestData data = getPlayerData(player);
            if (data.getState().getId() < QuestState.CRAFT_DRY_HEAT_ELEMENT.getId()) {
                setQuestState(player, QuestState.CRAFT_DRY_HEAT_ELEMENT);
            }
            player.sendMessage(Text.translatable("quest.elemental.sunforged_scimitar_assigned").formatted(Formatting.GOLD, Formatting.BOLD), false);
            for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
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

        // Only count blueprints when actively in relevant quest phases
        boolean needsBlueprintCheck = (state == QuestState.SALVAGE_MODULES || state == QuestState.LOCATE_SKYFORGE)
                || (state.getId() < QuestState.RETURN_TO_AERON.getId() && !data.hasClaimedJetpack() && data.getBlueprintsFound() < 4);

        int blueprintsCount = 0;
        boolean hasAllBlueprints = false;

        if (needsBlueprintCheck) {
            blueprintsCount = BlueprintMaps.countUniqueBlueprintsCombined(player.server.getPlayerManager().getPlayerList());
            hasAllBlueprints = blueprintsCount >= BlueprintMaps.Type.values().length;

            if (state.getId() < QuestState.RETURN_TO_AERON.getId() && !data.hasClaimedJetpack()) {
                if (blueprintsCount > data.getBlueprintsFound()) {
                    data.setBlueprintsFound(blueprintsCount);
                    updated = true;
                }
            }
        } else if (data.getBlueprintsFound() < 4 && (state.getId() >= QuestState.RETURN_TO_AERON.getId() || data.hasClaimedJetpack())) {
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

        // 3. In SALVAGE_MODULES: advance to RETURN_TO_AERON once all 4 recipe maps are collected
        if (state == QuestState.SALVAGE_MODULES) {
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

        // 4. Jetpack Task 1: Check if player has crafted/obtained Pneumatic Piston
        if (state == QuestState.CRAFT_PNEUMATIC_PISTON) {
            if (hasItemOrWorn(player, ModItems.PNEUMATIC_PISTON)) {
                data.setState(QuestState.CRAFT_AEROGEL_CORE);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.pneumatic_piston_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("dialogue.elemental.aeron.task2_assigned").formatted(Formatting.YELLOW), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Jetpack Task 2: Check if player has crafted/obtained Aerogel Core
        if (state == QuestState.CRAFT_AEROGEL_CORE) {
            if (hasItemOrWorn(player, ModItems.AEROGEL_CORE)) {
                data.setState(QuestState.CRAFT_ELEMENT_CORE);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.aerogel_core_crafted").formatted(Formatting.AQUA, Formatting.BOLD), false);
                // When 3rd task pops, Aeron tells player that element core is a commonly used resource, so make a handful of them
                player.server.getPlayerManager().broadcast(Text.translatable("dialogue.elemental.aeron.task3_element_core_handful").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Jetpack Task 3: Check if player has crafted/obtained Element Core
        if (state == QuestState.CRAFT_ELEMENT_CORE) {
            if (hasItemOrWorn(player, ModItems.ELEMENT_CORE)) {
                data.setState(QuestState.CRAFT_JETPACK);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.element_core_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("dialogue.elemental.aeron.task4_assigned").formatted(Formatting.YELLOW), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Jetpack Task 4: Check if player has crafted/obtained Elemental Jetpack
        if (state == QuestState.CRAFT_JETPACK) {
            if (hasItemOrWorn(player, ModItems.ELEMENTAL_JETPACK)) {
                data.setClaimedJetpack(true);
                BlockPos desertPos = NbtStructureManager.findOrGenerateDesertSanctuary(player.getServerWorld(), player.getBlockPos());
                data.setTargetWaypoint(desertPos);
                data.setState(QuestState.DESERT_EXPEDITION);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.jetpack_assembled").formatted(Formatting.GREEN, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("dialogue.elemental.aeron.jetpack_completed").formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("message.elemental.desert_coords", desertPos.getX(), desertPos.getZ()).formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.QUEST_COMPLETED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 5. Proximity check for DESERT_EXPEDITION -> arrival at Desert Village collectively
        if (state == QuestState.DESERT_EXPEDITION) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 36 * 36) {
                data.setState(QuestState.TALK_TO_WIZARD);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.desert_village_reached").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 6. Task 1: Check if player has crafted/obtained Weaponer
        if (state == QuestState.CRAFT_WEAPONER) {
            if (hasItemOrWorn(player, com.elemental.block.ModBlocks.WEAPONER.asItem())) {
                data.setState(QuestState.CRAFT_DRY_HEAT_ELEMENT);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.weaponer_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 7. Task 2: Check if player has crafted/obtained Dry Heat Element
        if (state == QuestState.CRAFT_DRY_HEAT_ELEMENT) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.DRY_HEAT_ELEMENT)) {
                data.setState(QuestState.FORGE_SUNFORGED_SCIMITAR);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.dry_heat_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 8. Task 3: Check if player has crafted/obtained Sunforged Scimitar
        if (state == QuestState.FORGE_SUNFORGED_SCIMITAR) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.SUNFORGED_SCIMITAR)) {
                QuestNetworking.sendOpenWeaponDetail(player, "sunforged_scimitar");
                data.setState(QuestState.CRAFT_SNOW_ELEMENT);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.sunforged_scimitar_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.sunforged_scimitar_next").formatted(Formatting.AQUA, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 9. Craft Snow Element
        if (state == QuestState.CRAFT_SNOW_ELEMENT) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.SNOW_ELEMENT)) {
                data.setState(QuestState.FORGE_FROSTWAKE_PICK);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.snow_element_crafted").formatted(Formatting.AQUA, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 10. Forge Frostwake Pick
        if (state == QuestState.FORGE_FROSTWAKE_PICK) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.FROSTWAKE_PICK)) {
                QuestNetworking.sendOpenWeaponDetail(player, "frostwake_pick");
                data.setState(QuestState.CRAFT_TREE_ELEMENT);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.frostwake_pick_crafted").formatted(Formatting.AQUA, Formatting.BOLD), false);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.frostwake_pick_next").formatted(Formatting.GREEN, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 11. Craft Tree Element
        if (state == QuestState.CRAFT_TREE_ELEMENT) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.TREE_ELEMENT)) {
                data.setState(QuestState.FORGE_ROOTBOUND_AXE);
                updateWaypointForState(player, data);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.tree_element_crafted").formatted(Formatting.GREEN, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 12. Forge Rootbound Axe -> Wizard teleports to player, announces boss fight!
        if (state == QuestState.FORGE_ROOTBOUND_AXE) {
            if (hasItemOrWorn(player, com.elemental.item.ModItems.ROOTBOUND_AXE)) {
                data.setState(QuestState.LOCATE_EVERLIVING_ARENA);
                updateWaypointForState(player, data);
                QuestNetworking.sendOpenWeaponDetail(player, "rootbound_axe");
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.rootbound_axe_crafted").formatted(Formatting.GREEN, Formatting.BOLD), false);

                // Teleport wizard directly to player!
                com.elemental.entity.ElementalWizardEntity wizard = NbtStructureManager.teleportWizardToPlayer(player.getServerWorld(), player);
                if (wizard != null) {
                    com.elemental.entity.ElementalWizardEntity.startEverlivingBossDialogue(player);
                }

                // Clean up any duplicate wizard entities if multiple were spawned nearby
                List<com.elemental.entity.ElementalWizardEntity> nearbyWizards = player.getServerWorld().getEntitiesByClass(
                        com.elemental.entity.ElementalWizardEntity.class,
                        new net.minecraft.util.math.Box(player.getBlockPos()).expand(32.0),
                        e -> true);
                if (nearbyWizards.size() > 1) {
                    for (int i = 1; i < nearbyWizards.size(); i++) {
                        nearbyWizards.get(i).discard();
                    }
                }

                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // 13. Proximity to Everliving Arena -> Advance to DEFEAT_EVERLIVING_KNIGHT
        if (state == QuestState.LOCATE_EVERLIVING_ARENA) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 32 * 32) {
                data.setState(QuestState.DEFEAT_EVERLIVING_KNIGHT);
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.everliving_arena_reached").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Ensure Aeron's camp has exactly one guide when player is in proximity
        BlockPos campPos = QuestSaveData.getServerState(player.server).getAeronsTentLocation();
        if (campPos != null && player.squaredDistanceTo(campPos.getX(), campPos.getY(), campPos.getZ()) <= 64 * 64) {
            NbtStructureManager.ensureGuideAtCamp(player.getServerWorld(), campPos, QuestSaveData.getServerState(player.server));
        }

        // Ensure Desert Village has the Elemental Wizard when player is in proximity
        BlockPos desertPos = QuestSaveData.getServerState(player.server).getDesertLocation();
        if (desertPos != null && player.squaredDistanceTo(desertPos.getX(), desertPos.getY(), desertPos.getZ()) <= 64 * 64) {
            NbtStructureManager.ensureWizardAtVillage(player.getServerWorld(), desertPos, QuestSaveData.getServerState(player.server));
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

    public static int countItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean hasItemOrWorn(ServerPlayerEntity player, net.minecraft.item.Item item) {
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
