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
            case BIOME_EXPEDITION_1 -> updateWaypointForBiomeLineup(player, data, 0);
            case REACH_BIOME_2, FORGE_WEAPON_2, BIOME_EXPEDITION_2 -> updateWaypointForBiomeLineup(player, data, 1);
            case REACH_BIOME_3, FORGE_WEAPON_3, BIOME_EXPEDITION_3 -> updateWaypointForBiomeLineup(player, data, 2);
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
                    snowPos = NbtStructureManager.findNearestSnowVillageOrBiome(world, player.getBlockPos());
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

    public static void initializeBiomeLineup(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        BlockPos desertPos = NbtStructureManager.findOrGenerateDesertSanctuary(world, playerPos);
        BlockPos snowPos = NbtStructureManager.findNearestSnowVillageOrBiome(world, playerPos);
        BlockPos forestPos = NbtStructureManager.findNearestForestBiome(world, playerPos);

        double distDesert = playerPos.getSquaredDistance(desertPos);
        double distSnow = playerPos.getSquaredDistance(snowPos);
        double distForest = playerPos.getSquaredDistance(forestPos);

        class TargetInfo {
            final String id;
            final String name;
            final BlockPos pos;
            final double distSq;

            TargetInfo(String id, String name, BlockPos pos, double distSq) {
                this.id = id;
                this.name = name;
                this.pos = pos;
                this.distSq = distSq;
            }
        }

        List<TargetInfo> list = new java.util.ArrayList<>();
        list.add(new TargetInfo("DESERT", "Desert Village", desertPos, distDesert));
        list.add(new TargetInfo("SNOW", "Snow Village", snowPos, distSnow));
        list.add(new TargetInfo("FOREST", "Dark Forest", forestPos, distForest));

        list.sort(java.util.Comparator.comparingDouble(t -> t.distSq));

        saveData.setBiomeOrder(list.get(0).id, list.get(1).id, list.get(2).id);
        BlockPos closestPos = list.get(0).pos;
        saveData.setWizardLocation(closestPos);
        saveData.markDirty();

        NbtStructureManager.ensureWizardAtLocation(world, closestPos, saveData);

        PlayerQuestData data = getPlayerData(player);
        data.setState(QuestState.BIOME_EXPEDITION_1);
        data.setTargetWaypoint(closestPos);

        // Unlock crafting recipes for all players
        for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
            com.elemental.entity.ChronicleGuideEntity.unlockJetpackRecipes(p);
            com.elemental.entity.ElementalWizardEntity.unlockElementCoreRecipes(p);
        }

        int d1 = (int) Math.sqrt(list.get(0).distSq);
        int d2 = (int) Math.sqrt(list.get(1).distSq);
        int d3 = (int) Math.sqrt(list.get(2).distSq);

        player.server.getPlayerManager().broadcast(Text.literal("§a§l[Task 4 Complete] §eElemental Jetpack assembled!").formatted(Formatting.GREEN, Formatting.BOLD), false);
        player.server.getPlayerManager().broadcast(Text.literal("§6[Aeron] §eMagnificent flight capability unlocked! Long-range sensors have located Eldrin, the ancient Elemental Wizard!").formatted(Formatting.GOLD), false);
        player.server.getPlayerManager().broadcast(Text.literal("§6[Aeron] §eEldrin is waiting at the closest elemental source: §6" + list.get(0).name + " §b[X: " + list.get(0).pos.getX() + ", Z: " + list.get(0).pos.getZ() + "] §a(~" + d1 + "m away)§e!").formatted(Formatting.GOLD), false);
        player.server.getPlayerManager().broadcast(Text.literal("§6[Aeron] §eFly there with your jetpack to meet the Wizard. He will grant you the sacred tasks to master each element!").formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.server.getPlayerManager().broadcast(Text.literal("§7[Expedition Plan] 1. " + list.get(0).name + " (~" + d1 + "m - Meet Wizard) → 2. " + list.get(1).name + " (~" + d2 + "m) → 3. " + list.get(2).name + " (~" + d3 + "m)."), false);

        for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
            p.playSound(ModSounds.QUEST_COMPLETED, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    public static void updateWaypointForBiomeLineup(ServerPlayerEntity player, PlayerQuestData data, int stageIndex) {
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);
        ServerWorld world = player.getServerWorld();

        if (saveData.getFirstBiome() == null) {
            initializeBiomeLineup(player);
            return;
        }

        List<String> order = List.of(saveData.getFirstBiome(), saveData.getSecondBiome(), saveData.getThirdBiome());
        List<String> remaining = new java.util.ArrayList<>();
        for (String b : order) {
            if ("DESERT".equals(b) && !saveData.isScimitarObtained()) remaining.add(b);
            else if ("SNOW".equals(b) && !saveData.isPickObtained()) remaining.add(b);
            else if ("FOREST".equals(b) && !saveData.isAxeObtained()) remaining.add(b);
        }

        if (remaining.isEmpty()) {
            BlockPos arena = saveData.getEverlivingArenaLocation();
            if (arena == null) {
                arena = NbtStructureManager.findOrGenerateEverlivingArena(world, player.getBlockPos());
            }
            data.setTargetWaypoint(arena);
            return;
        }

        String targetBiome = remaining.get(0);
        BlockPos targetPos = switch (targetBiome) {
            case "DESERT" -> {
                BlockPos p = saveData.getDesertLocation();
                yield p != null ? p : NbtStructureManager.findOrGenerateDesertSanctuary(world, player.getBlockPos());
            }
            case "SNOW" -> {
                BlockPos p = saveData.getSnowBiomeLocation();
                yield p != null ? p : NbtStructureManager.findNearestSnowVillageOrBiome(world, player.getBlockPos());
            }
            case "FOREST" -> {
                BlockPos p = saveData.getForestBiomeLocation();
                yield p != null ? p : NbtStructureManager.findNearestForestBiome(world, player.getBlockPos());
            }
            default -> player.getBlockPos();
        };

        data.setTargetWaypoint(targetPos);
    }

    public static String getBiomeDisplayName(String biomeId) {
        if ("DESERT".equals(biomeId)) return "Desert Village";
        if ("SNOW".equals(biomeId)) return "Snow Village";
        if ("FOREST".equals(biomeId)) return "Dark Forest";
        return "Elemental Biome";
    }

    public static String getBiomeElementName(String biomeId) {
        if ("DESERT".equals(biomeId)) return "Dry Heat Element";
        if ("SNOW".equals(biomeId)) return "Snow Element";
        if ("FOREST".equals(biomeId)) return "Tree Element";
        return "Element";
    }

    public static String getBiomeWeaponName(String biomeId) {
        if ("DESERT".equals(biomeId)) return "Sunforged Scimitar";
        if ("SNOW".equals(biomeId)) return "Frostwake Pick";
        if ("FOREST".equals(biomeId)) return "Rootbound Axe";
        return "Primal Weapon";
    }

    public static boolean isPlayerInTargetBiome(ServerWorld world, BlockPos pos, String biomeId) {
        var biome = world.getBiome(pos);
        if ("DESERT".equals(biomeId)) {
            return biome.matchesKey(BiomeKeys.DESERT) || biome.isIn(BiomeTags.IS_BADLANDS);
        } else if ("SNOW".equals(biomeId)) {
            return biome.matchesKey(BiomeKeys.SNOWY_PLAINS) || biome.matchesKey(BiomeKeys.SNOWY_SLOPES)
                    || biome.matchesKey(BiomeKeys.FROZEN_PEAKS) || biome.matchesKey(BiomeKeys.SNOWY_TAIGA)
                    || biome.matchesKey(BiomeKeys.ICE_SPIKES) || biome.matchesKey(BiomeKeys.GROVE);
        } else if ("FOREST".equals(biomeId)) {
            return biome.matchesKey(BiomeKeys.DARK_FOREST) || biome.isIn(BiomeTags.IS_FOREST);
        }
        return false;
    }

    public static void handleSelectWeaponQuest(ServerPlayerEntity player, String weaponId) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);
        PlayerQuestData data = getPlayerData(player);

        if ("frostwake_pick".equalsIgnoreCase(weaponId)) {
            BlockPos snowPos = saveData.getSnowBiomeLocation();
            if (snowPos == null) {
                snowPos = NbtStructureManager.findNearestSnowVillageOrBiome(world, player.getBlockPos());
            }
            data.setTargetWaypoint(snowPos);
            player.sendMessage(Text.literal("§b✦ Waypoint updated: Snow Village / Biome [X: " + snowPos.getX() + ", Z: " + snowPos.getZ() + "]").formatted(Formatting.AQUA, Formatting.BOLD), false);
            player.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            syncToClient(player);
        } else if ("rootbound_axe".equalsIgnoreCase(weaponId)) {
            BlockPos forestPos = saveData.getForestBiomeLocation();
            if (forestPos == null) {
                forestPos = NbtStructureManager.findNearestForestBiome(world, player.getBlockPos());
            }
            data.setTargetWaypoint(forestPos);
            player.sendMessage(Text.literal("§a✦ Waypoint updated: Dark Forest [X: " + forestPos.getX() + ", Z: " + forestPos.getZ() + "]").formatted(Formatting.GREEN, Formatting.BOLD), false);
            player.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            syncToClient(player);
        } else if ("sunforged_scimitar".equalsIgnoreCase(weaponId)) {
            BlockPos desertPos = saveData.getDesertLocation();
            if (desertPos == null) {
                desertPos = NbtStructureManager.findOrGenerateDesertSanctuary(world, player.getBlockPos());
            }
            data.setTargetWaypoint(desertPos);
            player.sendMessage(Text.literal("§6✦ Waypoint updated: Desert Village [X: " + desertPos.getX() + ", Z: " + desertPos.getZ() + "]").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            syncToClient(player);
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
                initializeBiomeLineup(player);
                syncToAll(player.server);
                updated = true;
            }
        }

        // Biome expeditions & 3 weapons tracking
        if (state == QuestState.BIOME_EXPEDITION_1 || state == QuestState.REACH_BIOME_2 || state == QuestState.FORGE_WEAPON_2
                || state == QuestState.REACH_BIOME_3 || state == QuestState.FORGE_WEAPON_3
                || state == QuestState.BIOME_EXPEDITION_2 || state == QuestState.BIOME_EXPEDITION_3
                || state == QuestState.DESERT_EXPEDITION || state == QuestState.TALK_TO_WIZARD || state == QuestState.CRAFT_WEAPONER
                || state == QuestState.CRAFT_DRY_HEAT_ELEMENT || state == QuestState.FORGE_SUNFORGED_SCIMITAR
                || state == QuestState.CRAFT_SNOW_ELEMENT || state == QuestState.FORGE_FROSTWAKE_PICK
                || state == QuestState.CRAFT_TREE_ELEMENT || state == QuestState.FORGE_ROOTBOUND_AXE) {

            QuestSaveData saveData = QuestSaveData.getServerState(player.server);
            boolean hadScimitar = saveData.isScimitarObtained();
            boolean hadPick = saveData.isPickObtained();
            boolean hadAxe = saveData.isAxeObtained();

            boolean currentScimitar = hadScimitar || hasItemOrWorn(player, ModItems.SUNFORGED_SCIMITAR);
            boolean currentPick = hadPick || hasItemOrWorn(player, ModItems.FROSTWAKE_PICK);
            boolean currentAxe = hadAxe || hasItemOrWorn(player, ModItems.ROOTBOUND_AXE);

            if (currentScimitar && !hadScimitar) {
                saveData.setScimitarObtained(true);
                saveData.markDirty();
                QuestNetworking.sendOpenWeaponDetail(player, "sunforged_scimitar");
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.sunforged_scimitar_crafted").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                updated = true;
            }

            if (currentPick && !hadPick) {
                saveData.setPickObtained(true);
                saveData.markDirty();
                QuestNetworking.sendOpenWeaponDetail(player, "frostwake_pick");
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.frostwake_pick_crafted").formatted(Formatting.AQUA, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                updated = true;
            }

            if (currentAxe && !hadAxe) {
                saveData.setAxeObtained(true);
                saveData.markDirty();
                QuestNetworking.sendOpenWeaponDetail(player, "rootbound_axe");
                player.server.getPlayerManager().broadcast(Text.translatable("quest.elemental.rootbound_axe_crafted").formatted(Formatting.GREEN, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                updated = true;
            }

            int count = (currentScimitar ? 1 : 0) + (currentPick ? 1 : 0) + (currentAxe ? 1 : 0);

            if (count >= 3) {
                // All 3 weapons obtained! Locate Everliving Knight Arena and teleport wizard to player
                data.setState(QuestState.LOCATE_EVERLIVING_ARENA);
                BlockPos arenaPos = NbtStructureManager.findOrGenerateEverlivingArena(player.getServerWorld(), player.getBlockPos());
                data.setTargetWaypoint(arenaPos);

                // Teleport wizard directly to player!
                com.elemental.entity.ElementalWizardEntity wizard = NbtStructureManager.teleportWizardToPlayer(player.getServerWorld(), player);
                if (wizard != null) {
                    com.elemental.entity.ElementalWizardEntity.startEverlivingBossDialogue(player);
                }

                // Clean up any duplicate wizard entities nearby
                List<com.elemental.entity.ElementalWizardEntity> nearbyWizards = player.getServerWorld().getEntitiesByClass(
                        com.elemental.entity.ElementalWizardEntity.class,
                        new net.minecraft.util.math.Box(player.getBlockPos()).expand(32.0),
                        e -> true);
                if (nearbyWizards.size() > 1) {
                    for (int i = 1; i < nearbyWizards.size(); i++) {
                        nearbyWizards.get(i).discard();
                    }
                }

                player.server.getPlayerManager().broadcast(Text.literal("§6§l[!] All 3 Primal Weapons Obtained! Sir Solvane awaits in the Everliving Coliseum!").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            } else if (count == 2) {
                if (state != QuestState.REACH_BIOME_3 && state != QuestState.FORGE_WEAPON_3) {
                    data.setState(QuestState.REACH_BIOME_3);
                    updateWaypointForBiomeLineup(player, data, 2);
                    String thirdBiomeId = saveData.getThirdBiome();
                    String bName = getBiomeDisplayName(thirdBiomeId);
                    int d3 = data.getTargetWaypoint() != null ? (int) Math.sqrt(player.getBlockPos().getSquaredDistance(data.getTargetWaypoint())) : 0;
                    player.server.getPlayerManager().broadcast(Text.literal("§6[Eldrin] §b§lRemarkable power! §eTwo primal weapons mastered! Your next destination is the far source: §a" + bName + " §e(~" + d3 + "m away). Reach that region first before forging its weapon!").formatted(Formatting.AQUA), false);
                    for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                        p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                    syncToAll(player.server);
                    updated = true;
                }
            } else if (count == 1) {
                if (state != QuestState.REACH_BIOME_2 && state != QuestState.FORGE_WEAPON_2) {
                    data.setState(QuestState.REACH_BIOME_2);
                    updateWaypointForBiomeLineup(player, data, 1);
                    String secondBiomeId = saveData.getSecondBiome();
                    String bName = getBiomeDisplayName(secondBiomeId);
                    int d2 = data.getTargetWaypoint() != null ? (int) Math.sqrt(player.getBlockPos().getSquaredDistance(data.getTargetWaypoint())) : 0;
                    player.server.getPlayerManager().broadcast(Text.literal("§6[Eldrin] §a§lSplendid craftsmanship! §eFirst weapon mastered! Your next destination is the 2nd closest source: §b" + bName + " §e(~" + d2 + "m away). Reach that region first before crafting its element!").formatted(Formatting.GOLD), false);
                    for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                        p.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                    syncToAll(player.server);
                    updated = true;
                }
            } else {
                if (state != QuestState.BIOME_EXPEDITION_1) {
                    data.setState(QuestState.BIOME_EXPEDITION_1);
                    updateWaypointForBiomeLineup(player, data, 0);
                    syncToAll(player.server);
                    updated = true;
                }
            }
        }

        // Proximity arrival check for REACH_BIOME_2 -> FORGE_WEAPON_2
        if (state == QuestState.REACH_BIOME_2) {
            BlockPos target2 = data.getTargetWaypoint();
            QuestSaveData sData = QuestSaveData.getServerState(player.server);
            String secondBiomeId = sData.getSecondBiome();
            boolean arrived = false;
            if (target2 != null && player.squaredDistanceTo(target2.getX(), player.getY(), target2.getZ()) <= 48 * 48) {
                arrived = true;
            } else if (secondBiomeId != null && isPlayerInTargetBiome(player.getServerWorld(), player.getBlockPos(), secondBiomeId)
                    && (target2 == null || player.squaredDistanceTo(target2.getX(), player.getY(), target2.getZ()) <= 150 * 150)) {
                arrived = true;
            }
            if (arrived) {
                data.setState(QuestState.FORGE_WEAPON_2);
                String biomeName = getBiomeDisplayName(secondBiomeId);
                String elemName = getBiomeElementName(secondBiomeId);
                String weapName = getBiomeWeaponName(secondBiomeId);
                player.server.getPlayerManager().broadcast(Text.literal("§6§l[!] Arrived at " + biomeName + "! §eNow gather regional materials to craft the §b" + elemName + "§e and forge the §f" + weapName + "§e!").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Proximity arrival check for REACH_BIOME_3 -> FORGE_WEAPON_3
        if (state == QuestState.REACH_BIOME_3) {
            BlockPos target3 = data.getTargetWaypoint();
            QuestSaveData sData = QuestSaveData.getServerState(player.server);
            String thirdBiomeId = sData.getThirdBiome();
            boolean arrived = false;
            if (target3 != null && player.squaredDistanceTo(target3.getX(), player.getY(), target3.getZ()) <= 48 * 48) {
                arrived = true;
            } else if (thirdBiomeId != null && isPlayerInTargetBiome(player.getServerWorld(), player.getBlockPos(), thirdBiomeId)
                    && (target3 == null || player.squaredDistanceTo(target3.getX(), player.getY(), target3.getZ()) <= 150 * 150)) {
                arrived = true;
            }
            if (arrived) {
                data.setState(QuestState.FORGE_WEAPON_3);
                String biomeName = getBiomeDisplayName(thirdBiomeId);
                String elemName = getBiomeElementName(thirdBiomeId);
                String weapName = getBiomeWeaponName(thirdBiomeId);
                player.server.getPlayerManager().broadcast(Text.literal("§6§l[!] Arrived at " + biomeName + "! §eNow gather regional materials to craft the §a" + elemName + "§e and forge the §f" + weapName + "§e!").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (ServerPlayerEntity p : player.server.getPlayerManager().getPlayerList()) {
                    p.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                syncToAll(player.server);
                updated = true;
            }
        }

        // Proximity auto-trigger for Wizard introduction at closest biome
        if (state == QuestState.BIOME_EXPEDITION_1) {
            QuestSaveData sData = QuestSaveData.getServerState(player.server);
            if (!sData.isWizardIntroDone()) {
                BlockPos wPos = sData.getWizardLocation();
                if (wPos != null && player.squaredDistanceTo(wPos.getX(), player.getY(), wPos.getZ()) <= 16 * 16) {
                    if (!com.elemental.quest.DialogueManager.isInConversation(player) && !com.elemental.quest.DialogueManager.isAnyConversationActive()) {
                        com.elemental.entity.ElementalWizardEntity.startWizardIntroduction(player);
                    }
                }
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

        // Ensure the Elemental Wizard is stationed at wizardLocation when player is in proximity
        BlockPos wizardLoc = QuestSaveData.getServerState(player.server).getWizardLocation();
        if (wizardLoc != null && player.squaredDistanceTo(wizardLoc.getX(), wizardLoc.getY(), wizardLoc.getZ()) <= 64 * 64) {
            NbtStructureManager.ensureWizardAtLocation(player.getServerWorld(), wizardLoc, QuestSaveData.getServerState(player.server));
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
