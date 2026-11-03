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
import com.elemental.network.QuestNetworking;
import com.elemental.quest.QuestManager;
import com.elemental.sound.ModSounds;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
		com.elemental.enchantment.ModEnchantments.registerModEnchantments();
		ModItemGroup.registerItemGroups();
		ModEntities.registerModEntities();
		SunforgedHeatHandler.register();
		RootboundScheduler.register();
		com.elemental.network.RootboundNetworking.registerServerReceivers();
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

		// Allow Upgrade Key to transform anvils immediately on right-click even without sneaking
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
			if (stack.isOf(ModItems.UPGRADE_KEY)) {
				net.minecraft.util.math.BlockPos pos = hitResult.getBlockPos();
				net.minecraft.block.BlockState state = world.getBlockState(pos);
				if (state.isIn(net.minecraft.registry.tag.BlockTags.ANVIL) && !state.isOf(ModBlocks.ELEMENTAL_ANVIL)) {
					return com.elemental.item.UpgradeKeyItem.transformAnvil(world, pos, state, player, stack);
				}
			}
			return net.minecraft.util.ActionResult.PASS;
		});

		// Right-click inspection and sync for Blueprint Maps
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
			if (com.elemental.item.BlueprintMaps.isBlueprint(stack)) {
				if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
					com.elemental.item.BlueprintMaps.ensureBlueprintMapInitialized(stack, world);
					Integer mapId = net.minecraft.item.FilledMapItem.getMapId(stack);
					if (mapId != null) {
						net.minecraft.item.map.MapState mapState = serverWorld.getMapState(net.minecraft.item.FilledMapItem.getMapName(mapId));
						if (mapState != null) {
							net.minecraft.item.map.MapState.UpdateData updateData = new net.minecraft.item.map.MapState.UpdateData(0, 0, 128, 128, mapState.colors);
							serverPlayer.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket(mapId, mapState.scale, mapState.locked, java.util.Collections.emptyList(), updateData));
						}
					}
					com.elemental.item.BlueprintMaps.Type type = com.elemental.item.BlueprintMaps.getBlueprintType(stack);
					if (type != null) {
						serverPlayer.sendMessage(net.minecraft.text.Text.literal("§d[Schematic] §e" + type.getTitle() + ": §f" + type.getRecipeDesc()), true);
						serverPlayer.getWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
								net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.1f);
					}
				}
				return net.minecraft.util.TypedActionResult.success(stack);
			}
			return net.minecraft.util.TypedActionResult.pass(stack);
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
