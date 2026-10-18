package com.elemental;

import com.elemental.block.ModBlocks;
import com.elemental.block.entity.ModBlockEntities;
import com.elemental.entity.ModEntities;
import com.elemental.item.ModItemGroup;
import com.elemental.item.ModItems;
import com.elemental.network.AshenRiftNetworking;
import com.elemental.network.StormcallerNetworking;
import com.elemental.network.TidebreakerNetworking;
import com.elemental.recipe.WeaponerRecipes;
import com.elemental.screen.ModScreenHandlers;
import com.elemental.util.AshenRiftScheduler;
import com.elemental.util.RootboundScheduler;
import com.elemental.util.StormcallerLightningScheduler;
import com.elemental.util.SunforgedHeatHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elemental.command.ElementalQuestCommand;
import com.elemental.config.ElementalConfig;
import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.network.QuestNetworking;
import com.elemental.quest.QuestManager;
import com.elemental.quest.QuestSaveData;
import com.elemental.sound.ModSounds;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class Elemental implements ModInitializer {
	public static final String MOD_ID = "elemental";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Elemental mod...");
		ElementalConfig.load();
		ModSounds.registerModSounds();
		ModBlocks.registerModBlocks();
		ModBlockEntities.registerBlockEntities();
		ModScreenHandlers.registerScreenHandlers();
		WeaponerRecipes.init();
		ModItems.registerModItems();
		ModItemGroup.registerItemGroups();
		ModEntities.registerModEntities();
		SunforgedHeatHandler.register();
		RootboundScheduler.register();
		TidebreakerNetworking.registerServerReceivers();
		StormcallerNetworking.registerServerReceivers();
		StormcallerLightningScheduler.register();
		com.elemental.combat.StormcallerServerCombatHandler.register();
		AshenRiftNetworking.registerServerReceivers();
		AshenRiftScheduler.register();
		QuestNetworking.registerServerReceivers();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ElementalQuestCommand.register(dispatcher);
		});

		// First world spawn and village placement logic
		java.util.Map<java.util.UUID, Integer> pendingSpawnDelays = new java.util.HashMap<>();

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();

			// Ensure player state is clean (e.g. if disconnected during rift teleport)
			player.setInvisible(false);
			player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);

			// Queue village initialization & Aeron spawn after 5 ticks of connection
			pendingSpawnDelays.put(player.getUuid(), 5);

			// Sync quest state to connecting player
			QuestManager.syncToClient(player);

			// Sync extra jetpack slot to connecting player
			QuestNetworking.sendExtraSlotSync(player);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			pendingSpawnDelays.remove(player.getUuid());
			com.elemental.combat.StormcallerServerCombatHandler.handlePlayerDisconnect(player);
			com.elemental.quest.DialogueManager.cancelConversation(player);
		});

		// Server tick event for dialogues, village spawn, and quest objectives
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// 1. Tick active timed 2-way NPC conversations
			com.elemental.quest.DialogueManager.tick(server);

			// 2. Process delayed initial world / village spawns
			if (!pendingSpawnDelays.isEmpty()) {
				java.util.Iterator<java.util.Map.Entry<java.util.UUID, Integer>> it = pendingSpawnDelays.entrySet().iterator();
				while (it.hasNext()) {
					var entry = it.next();
					int remaining = entry.getValue() - 1;
					if (remaining <= 0) {
						ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
						if (player != null && !player.isDisconnected()) {
							com.elemental.world.NbtStructureManager.initializeWorld(player);
						}
						it.remove();
					} else {
						entry.setValue(remaining);
					}
				}
			}

			// 3. Periodic quest objective monitoring
			tickCounter++;
			if (tickCounter % 20 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					QuestManager.tickPlayer(player);
				}
			}
		});
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
