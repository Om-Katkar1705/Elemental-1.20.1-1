package com.elemental.client;

import com.elemental.client.renderer.EverlivingKnightRenderer;
import com.elemental.client.renderer.FrostwakePickRenderer;
import com.elemental.client.renderer.RootboundAxeRenderer;
import com.elemental.client.renderer.SolarArcRenderer;
import com.elemental.client.renderer.SunforgedScimitarRenderer;
import com.elemental.client.renderer.TidebreakerBubbleEntityRenderer;
import com.elemental.client.renderer.TidebreakerTridentEntityRenderer;
import com.elemental.client.renderer.TidebreakerTridentRenderer;
import com.elemental.client.screen.WeaponerScreen;
import com.elemental.entity.ModEntities;
import com.elemental.item.FrostwakePickItem;
import com.elemental.item.ModItems;
import com.elemental.item.RootboundAxeItem;
import com.elemental.item.SunforgedScimitarItem;
import com.elemental.item.TidebreakerTridentItem;
import com.elemental.network.TidebreakerNetworking;
import com.elemental.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.client.RenderProvider;

import com.elemental.client.renderer.JetpackFeatureRenderer;
import com.elemental.client.renderer.JetpackModel;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;

public class ElementalClient implements ClientModInitializer {
	private static KeyBinding dashKey;

	@Override
	public void onInitializeClient() {
		// Register GeckoLib custom renderer for the Sunforged Scimitar item
		SunforgedScimitarItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private SunforgedScimitarRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new SunforgedScimitarRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Register GeckoLib custom renderer for the Frostwake Pick item
		FrostwakePickItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private FrostwakePickRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new FrostwakePickRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Register GeckoLib custom renderer for the Rootbound Axe item
		RootboundAxeItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private RootboundAxeRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new RootboundAxeRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Register GeckoLib custom renderer for the Tidebreaker Trident item
		TidebreakerTridentItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private TidebreakerTridentRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new TidebreakerTridentRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Register GeckoLib animated renderer for the Solar Arc projectile
		EntityRendererRegistry.register(ModEntities.SOLAR_ARC, SolarArcRenderer::new);

		// Register GeckoLib animated renderer for Sir Solvane, the Everliving Knight
		EntityRendererRegistry.register(ModEntities.EVERLIVING_KNIGHT, EverlivingKnightRenderer::new);

		// Register Tidebreaker projectile and bubble entity renderers
		EntityRendererRegistry.register(ModEntities.TIDEBREAKER_TRIDENT_PROJECTILE, TidebreakerTridentEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TIDEBREAKER_BUBBLE, TidebreakerBubbleEntityRenderer::new);

		// Register Chronicle Guide entity renderer (Aeron, the Last Skywright)
		EntityRendererRegistry.register(ModEntities.CHRONICLE_GUIDE, com.elemental.client.renderer.ChronicleGuideRenderer::new);

		// Register Jetpack 3D model layer and player feature renderer
		EntityModelLayerRegistry.registerModelLayer(JetpackFeatureRenderer.JETPACK_LAYER, JetpackModel::getTexturedModelData);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
				registrationHelper.register(new JetpackFeatureRenderer(playerRenderer, context.getModelLoader()));
			}
		});

		// Register Weaponer screen GUI handler
		HandledScreens.register(ModScreenHandlers.WEAPONER_SCREEN_HANDLER, WeaponerScreen::new);

		// Register Quest Guidance HUD callback
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new com.elemental.client.gui.QuestGuidanceHud());

		// Register Tidebreaker Dash keybinding
		dashKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.elemental.tidebreaker_dash",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				"category.elemental"
		));

		// Register Jetpack Waypoint Guidance toggle keybinding
		KeyBinding guidanceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.elemental.jetpack_guidance",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				"category.elemental"
		));

		// Register Quest Sync Client Receiver
		ClientPlayNetworking.registerGlobalReceiver(com.elemental.network.QuestNetworking.QUEST_SYNC_PACKET_ID, (client, handler, buf, responseSender) -> {
			int stateId = buf.readInt();
			int pistons = buf.readInt();
			int aerogel = buf.readInt();
			int blueprints = buf.readInt();
			boolean guidance = buf.readBoolean();
			boolean hasTarget = buf.readBoolean();
			net.minecraft.util.math.BlockPos target = null;
			if (hasTarget) {
				target = new net.minecraft.util.math.BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
			}

			net.minecraft.util.math.BlockPos finalTarget = target;
			client.execute(() -> {
				com.elemental.client.quest.ClientQuestData.update(
						com.elemental.quest.QuestState.fromId(stateId),
						pistons,
						aerogel,
						blueprints,
						guidance,
						finalTarget
				);
			});
		});

		// Register Extra Jetpack Slot Sync Client Receiver
		ClientPlayNetworking.registerGlobalReceiver(com.elemental.network.QuestNetworking.EXTRA_SLOT_SYNC_PACKET_ID, (client, handler, buf, responseSender) -> {
			ItemStack stack = buf.readItemStack();
			client.execute(() -> {
				if (client.player instanceof com.elemental.util.JetpackSlotHolder holder) {
					holder.elemental$setJetpackSlot(stack);
				}
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			// Weapon R ability (Tidebreaker dash or Stormcaller thunderclap)
			while (dashKey.wasPressed()) {
				ItemStack main = client.player.getMainHandStack();
				ItemStack off = client.player.getOffHandStack();
				if (main.isOf(ModItems.TIDEBREAKER_TRIDENT) || off.isOf(ModItems.TIDEBREAKER_TRIDENT)) {
					if (!client.player.getItemCooldownManager().isCoolingDown(ModItems.TIDEBREAKER_TRIDENT)) {
						PacketByteBuf buf = PacketByteBufs.create();
						ClientPlayNetworking.send(TidebreakerNetworking.DASH_PACKET_ID, buf);
					}
				} else if (main.isOf(ModItems.STORMCALLER_HAMMER) || off.isOf(ModItems.STORMCALLER_HAMMER)) {
					PacketByteBuf buf = PacketByteBufs.create();
					ClientPlayNetworking.send(com.elemental.network.StormcallerNetworking.THUNDERCLAP_PACKET_ID, buf);
				} else if (main.isOf(ModItems.ASHEN_RIFT_BLADE) || off.isOf(ModItems.ASHEN_RIFT_BLADE)) {
					PacketByteBuf buf = PacketByteBufs.create();
					ClientPlayNetworking.send(com.elemental.network.AshenRiftNetworking.RIFT_COLLAPSE_PACKET_ID, buf);
				}
			}

			// Jetpack guidance toggle
			while (guidanceKey.wasPressed()) {
				ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_TOGGLE_GUIDANCE_PACKET_ID, PacketByteBufs.empty());
			}

			// Jetpack flight controls
			ItemStack extra = com.elemental.util.JetpackSlotHolder.getJetpackStack(client.player);
			boolean isWearingJetpack = extra.getItem() instanceof com.elemental.item.ElementalJetpackItem;

			if (isWearingJetpack) {
				// Jump key held airborne -> Elytra-speed flight or vertical climb
				if (client.options.jumpKey.isPressed() && !client.player.isTouchingWater() && !client.player.getAbilities().flying) {
					boolean hasForward = client.options.forwardKey.isPressed() || client.player.input.movementForward > 0;
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeBoolean(hasForward);
					ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_THRUST_PACKET_ID, buf);

					net.minecraft.util.math.Vec3d vel = client.player.getVelocity();
					net.minecraft.util.math.Vec3d look = client.player.getRotationVec(1.0f);

					double targetX;
					double targetY;
					double targetZ;

					if (hasForward) {
						// Elytra-speed flight along look direction (~1.45 blocks/tick = ~29 m/s)
						double targetSpeed = 1.45;
						net.minecraft.util.math.Vec3d targetVel = look.multiply(targetSpeed);
						targetX = vel.x * 0.70 + targetVel.x * 0.30;
						targetY = vel.y * 0.70 + targetVel.y * 0.30;
						targetZ = vel.z * 0.70 + targetVel.z * 0.30;
					} else {
						// Vertical climb
						targetY = Math.min(vel.y + 0.15, 0.75);
						targetX = vel.x * 0.85;
						targetZ = vel.z * 0.85;
					}

					client.player.setVelocity(targetX, targetY, targetZ);
					client.player.fallDistance = 0.0f;

					// Visual flame and smoke particles trailing behind player on client
					net.minecraft.util.math.Vec3d backOffset = look.multiply(-0.4);
					client.particleManager.addParticle(
							net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
							client.player.getX() + backOffset.x,
							client.player.getY() + 0.5,
							client.player.getZ() + backOffset.z,
							-look.x * 0.2 + (Math.random() - 0.5) * 0.05,
							-0.1,
							-look.z * 0.2 + (Math.random() - 0.5) * 0.05
					);
					client.particleManager.addParticle(
							net.minecraft.particle.ParticleTypes.CLOUD,
							client.player.getX() + backOffset.x,
							client.player.getY() + 0.5,
							client.player.getZ() + backOffset.z,
							-look.x * 0.1,
							-0.05,
							-look.z * 0.1
					);
				}
				// Sneak key held while falling -> gliding descent
				else if (client.options.sneakKey.isPressed() && client.player.getVelocity().y < -0.15) {
					ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_GLIDE_PACKET_ID, PacketByteBufs.empty());
					client.player.setVelocity(client.player.getVelocity().x, -0.15, client.player.getVelocity().z);
					client.player.fallDistance = 0.0f;
				}
			}
		});
	}
}