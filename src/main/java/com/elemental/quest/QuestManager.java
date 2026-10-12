package com.elemental.quest;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import com.elemental.item.ModItems;
import com.elemental.network.QuestNetworking;
import com.elemental.sound.ModSounds;
import com.elemental.world.SkyforgeAirshipGenerator;
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
        syncToClient(player);
    }

    public static void updateWaypointForState(ServerPlayerEntity player, PlayerQuestData data) {
        ServerWorld world = player.getServerWorld();
        QuestSaveData saveData = QuestSaveData.getServerState(player.server);

        switch (data.getState()) {
            case CRAFT_STONE_TOOLS -> {
                data.setTargetWaypoint(null);
            }
            case FIND_AERON, RETURN_TO_AERON -> {
                BlockPos campPos = saveData.getAeronsTentLocation();
                data.setTargetWaypoint(campPos != null ? campPos : player.getBlockPos());
            }
            case LOCATE_SKYFORGE, SALVAGE_MODULES -> {
                BlockPos airshipPos = SkyforgeAirshipGenerator.findOrGenerateAirship(world, player.getBlockPos());
                data.setTargetWaypoint(airshipPos);
            }
            case CRAFT_JETPACK -> {
                BlockPos nearestWeaponer = findNearestWeaponer(world, player.getBlockPos(), 64);
                if (nearestWeaponer != null) {
                    data.setTargetWaypoint(nearestWeaponer);
                } else if (saveData.getAeronsTentLocation() != null) {
                    data.setTargetWaypoint(saveData.getAeronsTentLocation());
                } else {
                    data.setTargetWaypoint(player.getBlockPos());
                }
            }
            case DESERT_EXPEDITION -> {
                BlockPos desertPos = saveData.getDesertLocation();
                if (desertPos == null) {
                    desertPos = findNearestDesert(world, player.getBlockPos());
                    saveData.setDesertLocation(desertPos);
                    saveData.markDirty();
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

    private static BlockPos findNearestWeaponer(ServerWorld world, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -10, -radius), center.add(radius, 10, radius))) {
            if (world.getBlockState(pos).isOf(ModBlocks.WEAPONER)) {
                double distSq = pos.getSquaredDistance(center);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = pos.toImmutable();
                }
            }
        }
        return best;
    }

    /**
     * Periodic server-side check for inventory items, proximity, and objective advancement.
     */
    public static void tickPlayer(ServerPlayerEntity player) {
        PlayerQuestData data = getPlayerData(player);
        QuestState state = data.getState();

        boolean updated = false;

        // 1. Initial State: Craft any Stone Tool -> advance to FIND_AERON
        if (state == QuestState.CRAFT_STONE_TOOLS) {
            if (hasStoneTool(player)) {
                data.setState(QuestState.FIND_AERON);
                updateWaypointForState(player, data);
                player.sendMessage(Text.translatable("message.elemental.stone_tool_crafted").formatted(Formatting.GOLD), false);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                updated = true;
            }
        }

        // Count quest items in player inventory
        int pistonCount = countItem(player, ModItems.PNEUMATIC_PISTON);
        int aerogelCount = countItem(player, ModItems.AEROGEL_CORE);
        boolean hasBlueprint = countItem(player, ModItems.JETPACK_BLUEPRINT) > 0;
        boolean hasJetpack = hasItemOrWorn(player, ModItems.ELEMENTAL_JETPACK);

        if (pistonCount != data.getPistonsSalvaged()) {
            data.setPistonsSalvaged(pistonCount);
            updated = true;
        }
        if (aerogelCount != data.getAerogelExtracted()) {
            data.setAerogelExtracted(aerogelCount);
            updated = true;
        }
        if (hasBlueprint != data.isBlueprintFound()) {
            data.setBlueprintFound(hasBlueprint);
            updated = true;
        }

        // 2. Proximity check for LOCATE_SKYFORGE -> SALVAGE_MODULES
        if (state == QuestState.LOCATE_SKYFORGE) {
            BlockPos target = data.getTargetWaypoint();
            if (target != null && player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) <= 28 * 28) {
                data.setState(QuestState.SALVAGE_MODULES);
                player.sendMessage(Text.translatable("quest.elemental.airship_reached").formatted(Formatting.GOLD), false);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
                updated = true;
            }
        }

        // 3. Transition from SALVAGE_MODULES to CRAFT_JETPACK
        if (state == QuestState.SALVAGE_MODULES) {
            if (pistonCount >= 4 && aerogelCount >= 2 && hasBlueprint) {
                data.setState(QuestState.CRAFT_JETPACK);
                updateWaypointForState(player, data);
                player.sendMessage(Text.translatable("quest.elemental.all_components_salvaged").formatted(Formatting.AQUA), false);
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.2f);
                updated = true;
            }
        }

        // 4. Transition to RETURN_TO_AERON upon crafting/acquiring jetpack
        if (state == QuestState.CRAFT_JETPACK && hasJetpack) {
            data.setState(QuestState.RETURN_TO_AERON);
            updateWaypointForState(player, data);
            player.sendMessage(Text.translatable("quest.elemental.jetpack_assembled").formatted(Formatting.GREEN, Formatting.BOLD), false);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.1f);
            updated = true;
        }

        if (updated) {
            QuestSaveData.getServerState(player.server).markDirty();
            syncToClient(player);
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
}
