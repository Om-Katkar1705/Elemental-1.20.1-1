package com.elemental.client;

import com.elemental.client.renderer.AshenRiftBladeRenderer;
import com.elemental.client.renderer.EverlivingKnightRenderer;
import com.elemental.client.renderer.RockyRenderer;
import com.elemental.client.renderer.FrostwakePickRenderer;
import com.elemental.client.renderer.RootboundAxeRenderer;
import com.elemental.client.renderer.SolarArcRenderer;
import com.elemental.client.renderer.StormcallerHammerRenderer;
import com.elemental.client.renderer.SunforgedScimitarRenderer;
import com.elemental.client.renderer.TidebreakerBubbleEntityRenderer;
import com.elemental.client.renderer.TidebreakerTridentEntityRenderer;
import com.elemental.client.renderer.TidebreakerTridentRenderer;
import com.elemental.client.screen.WeaponerScreen;
import com.elemental.entity.ModEntities;
import com.elemental.item.AshenRiftBladeItem;
import com.elemental.item.FrostwakePickItem;
import com.elemental.item.ModItems;
import com.elemental.item.RootboundAxeItem;
import com.elemental.item.StormcallerHammerItem;
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
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;

import com.elemental.client.renderer.JetpackFeatureRenderer;
import com.elemental.client.renderer.JetpackModel;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.EntityType;

public class ElementalClient implements ClientModInitializer {
	private static KeyBinding dashKey;
	private static KeyBinding toggleJetpackKey;
	private static int groundWarmupTicks = 0;
	private static boolean wasJumpPressedLastTick = false;
	private static boolean isTakeoffSpooling = false;
	private static com.elemental.client.sound.JetpackFlightSoundInstance currentFlightSound = null;

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

		// Register GeckoLib custom renderer for the Ashen Rift Blade item
		AshenRiftBladeItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private AshenRiftBladeRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new AshenRiftBladeRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Register GeckoLib custom renderer for the Stormcaller Hammer item
		StormcallerHammerItem.RENDER_PROVIDER_CONSUMER = (consumer) -> {
			consumer.accept(new RenderProvider() {
				private StormcallerHammerRenderer renderer;

				@Override
				public BuiltinModelItemRenderer getCustomRenderer() {
					if (this.renderer == null) {
						this.renderer = new StormcallerHammerRenderer();
					}
					return this.renderer;
				}
			});
		};

		// Initialize Stormcaller Hammer PlayerAnimator handler
		com.elemental.client.animation.StormcallerHammerAnimationHandler.initClient();

		// Trigger Stormcaller Hammer melee swing immediately when attacking an entity on client
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand == net.minecraft.util.Hand.MAIN_HAND && player.getMainHandStack().isOf(ModItems.STORMCALLER_HAMMER)) {
				if (world.isClient()) {
					com.elemental.client.animation.StormcallerHammerAnimationHandler.triggerMeleeSwing();
				}
			}
			return net.minecraft.util.ActionResult.PASS;
		});

		// Register GeckoLib animated renderer for the Solar Arc projectile
		EntityRendererRegistry.register(ModEntities.SOLAR_ARC, SolarArcRenderer::new);

		// Register GeckoLib animated renderer for Sir Solvane, the Everliving Knight
		EntityRendererRegistry.register(ModEntities.EVERLIVING_KNIGHT, EverlivingKnightRenderer::new);

		// Register Tidebreaker projectile and bubble entity renderers
		EntityRendererRegistry.register(ModEntities.TIDEBREAKER_TRIDENT_PROJECTILE, TidebreakerTridentEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TIDEBREAKER_BUBBLE, TidebreakerBubbleEntityRenderer::new);

		// Register Chronicle Guide entity renderer (Aeron, the Last Skywright)
		EntityRendererRegistry.register(ModEntities.CHRONICLE_GUIDE, com.elemental.client.renderer.ChronicleGuideRenderer::new);

		// Register GeckoLib animated renderer for Rocky (Project Hail Mary alien companion)
		EntityRendererRegistry.register(ModEntities.ROCKY, RockyRenderer::new);

		// Register Seismic Colossus boss, minion, and projectile renderers
		EntityRendererRegistry.register(ModEntities.SEISMIC_COLOSSUS, com.elemental.client.renderer.SeismicColossusRenderer::new);
		EntityRendererRegistry.register(ModEntities.COLOSSUS_MINI, com.elemental.client.renderer.ColossusMiniRenderer::new);
		EntityRendererRegistry.register(ModEntities.SEISMIC_ROCK, com.elemental.client.renderer.SeismicRockRenderer::new);
		EntityRendererRegistry.register(ModEntities.SEISMIC_THROWN_BLOCK, com.elemental.client.renderer.SeismicThrownBlockRenderer::new);

		// Register Screen Shake Client Receiver
		ClientPlayNetworking.registerGlobalReceiver(com.elemental.network.SeismicColossusNetworking.S2C_SCREEN_SHAKE, (client, handler, buf, responseSender) -> {
			float intensity = buf.readFloat();
			int duration = buf.readInt();
			client.execute(() -> {
				com.elemental.client.util.CameraShakeManager.addShake(intensity, duration);
			});
		});

		// Tick Camera Shake Manager
		ClientTickEvents.END_CLIENT_TICK.register(client -> com.elemental.client.util.CameraShakeManager.clientTick());

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

		// Register Jetpack On/Off toggle keybinding (` key)
		toggleJetpackKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.elemental.jetpack_toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_GRAVE_ACCENT,
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

		// Register Extra Jetpack Slot Sync Client Receiver (replicated to all players)
		ClientPlayNetworking.registerGlobalReceiver(com.elemental.network.QuestNetworking.EXTRA_SLOT_SYNC_PACKET_ID, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			ItemStack stack = buf.readItemStack();
			boolean enabled = buf.readBoolean();
			client.execute(() -> {
				com.elemental.client.util.ClientJetpackSync.update(entityId, stack, enabled);
				net.minecraft.entity.Entity entity = null;
				if (client.world != null) {
					entity = client.world.getEntityById(entityId);
				}
				if (entity == null && client.player != null && client.player.getId() == entityId) {
					entity = client.player;
				}
				if (entity instanceof com.elemental.util.JetpackSlotHolder holder) {
					holder.elemental$setJetpackSlot(stack);
					holder.elemental$setJetpackEnabled(enabled);
				}
			});
		});

		// Register Player Fist Bump Animation Client Receiver
		ClientPlayNetworking.registerGlobalReceiver(com.elemental.network.QuestNetworking.PLAYER_FIST_BUMP_PACKET_ID, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			int durationTicks = buf.readInt();
			client.execute(() -> {
				com.elemental.client.util.ClientFistBumpSync.startAnimation(entityId, durationTicks);
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			com.elemental.client.util.ClientFistBumpSync.clientTick();
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
					com.elemental.client.animation.StormcallerHammerAnimationHandler.triggerGroundSmash();
				} else if (main.isOf(ModItems.ASHEN_RIFT_BLADE) || off.isOf(ModItems.ASHEN_RIFT_BLADE)) {
					PacketByteBuf buf = PacketByteBufs.create();
					ClientPlayNetworking.send(com.elemental.network.AshenRiftNetworking.RIFT_COLLAPSE_PACKET_ID, buf);
				}
			}

			// Attack / Swing animation for custom weapons on left-click swing
			if (client.player.handSwinging && client.player.handSwingTicks == 0) {
				ItemStack main = client.player.getMainHandStack();
				if (main.isOf(ModItems.STORMCALLER_HAMMER)) {
					com.elemental.client.animation.StormcallerHammerAnimationHandler.triggerMeleeSwing();
				} else if (main.getItem() instanceof GeoItem geoItem) {
					long id = GeoItem.getId(main);
					if (id != Long.MAX_VALUE) {
						if (main.isOf(ModItems.SUNFORGED_SCIMITAR)) {
							geoItem.triggerAnim(client.player, id, "controller", "attack");
						} else if (main.isOf(ModItems.FROSTWAKE_PICK)) {
							geoItem.triggerAnim(client.player, id, "controller", "swing");
						} else if (main.isOf(ModItems.ROOTBOUND_AXE)) {
							geoItem.triggerAnim(client.player, id, "controller", "wide_sweep");
						} else if (main.isOf(ModItems.TIDEBREAKER_TRIDENT)) {
							geoItem.triggerAnim(client.player, id, "controller", "swing");
						} else if (main.isOf(ModItems.ASHEN_RIFT_BLADE)) {
							geoItem.triggerAnim(client.player, id, "controller", "attack");
						}
					}
				}
			}

			// Jetpack guidance toggle
			while (guidanceKey.wasPressed()) {
				ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_TOGGLE_GUIDANCE_PACKET_ID, PacketByteBufs.empty());
			}

			// Jetpack toggle on/off (` key)
			while (toggleJetpackKey.wasPressed()) {
				boolean newState = !com.elemental.util.JetpackSlotHolder.isJetpackEnabled(client.player);
				if (client.player instanceof com.elemental.util.JetpackSlotHolder holder) {
					holder.elemental$setJetpackEnabled(newState);
				}
				com.elemental.client.util.ClientJetpackSync.update(client.player.getId(), com.elemental.util.JetpackSlotHolder.getJetpackStack(client.player), newState);

				client.player.playSound(
						newState ? net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value() : net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
						0.8f,
						newState ? 1.4f : 0.7f
				);
				client.player.sendMessage(
						net.minecraft.text.Text.translatable(newState ? "message.elemental.jetpack_enabled" : "message.elemental.jetpack_disabled")
								.formatted(newState ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.RED),
						true
				);

				ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_TOGGLE_PACKET_ID, PacketByteBufs.empty());
			}

			// Jetpack flight controls
			ItemStack extra = com.elemental.util.JetpackSlotHolder.getJetpackStack(client.player);
			boolean isWearingJetpack = extra.getItem() instanceof com.elemental.item.ElementalJetpackItem;
			boolean isJetpackEnabled = com.elemental.util.JetpackSlotHolder.isJetpackEnabled(client.player);

			if (isWearingJetpack && isJetpackEnabled) {
				boolean isJumpPressed = client.options.jumpKey.isPressed();
				boolean isTouchingWater = client.player.isTouchingWater();
				boolean isCreativeFlying = client.player.getAbilities().flying;

				if (isJumpPressed && !isTouchingWater && !isCreativeFlying) {
					// Check initial press of jump key
					if (!wasJumpPressedLastTick) {
						if (client.player.isOnGround()) {
							// Grounded when Space first pressed -> start 2-second takeoff spooling
							isTakeoffSpooling = true;
							groundWarmupTicks = 0;
						} else {
							// Already in mid-air when Space pressed -> thrust activates immediately with NO delay!
							isTakeoffSpooling = false;
							groundWarmupTicks = 0;
						}
						wasJumpPressedLastTick = true;
					}

					boolean canThrust = false;

					if (isTakeoffSpooling) {
						groundWarmupTicks++;

						// Suppress vanilla jumping hop so the player stays planted on the ground while engines spool up
						net.minecraft.util.math.Vec3d curVel = client.player.getVelocity();
						if (curVel.y > 0.0) {
							client.player.setVelocity(curVel.x, 0.0, curVel.z);
						}

						// Warm-up feedback: sparks/smoke sputtering from exhaust nozzles
						net.minecraft.util.math.Vec3d[] nozzles = com.elemental.util.JetpackParticleHelper.getThrusterExhaustPositions(client.player);
						for (net.minecraft.util.math.Vec3d nozzle : nozzles) {
							client.particleManager.addParticle(
									net.minecraft.particle.ParticleTypes.SMOKE,
									nozzle.x + (Math.random() - 0.5) * 0.04,
									nozzle.y,
									nozzle.z + (Math.random() - 0.5) * 0.04,
									(Math.random() - 0.5) * 0.02,
									-0.03,
									(Math.random() - 0.5) * 0.02
							);
							if (groundWarmupTicks % 3 == 0) {
								client.particleManager.addParticle(
										net.minecraft.particle.ParticleTypes.SMALL_FLAME,
										nozzle.x,
										nozzle.y,
										nozzle.z,
										0.0,
										-0.02,
										0.0
								);
							}
						}

						// Rising pitch warm-up sound
						if (groundWarmupTicks % 8 == 1) {
							float warmupPitch = 0.7f + (groundWarmupTicks / 40.0f) * 0.7f;
							client.player.playSound(com.elemental.sound.ModSounds.JETPACK_WARMUP, 0.25f, warmupPitch);
						}

						// UI indicator in actionbar
						if (groundWarmupTicks < 40) {
							float secondsRemaining = Math.max(0.1f, (40 - groundWarmupTicks) / 20.0f);
							client.player.sendMessage(
									net.minecraft.text.Text.translatable("message.elemental.jetpack_spooling", String.format(java.util.Locale.ROOT, "%.1fs", secondsRemaining))
											.formatted(net.minecraft.util.Formatting.GOLD),
									true
							);
						} else {
							// 40 ticks (2.0s) reached! Launch!
							isTakeoffSpooling = false;
							canThrust = true;
							client.player.sendMessage(
									net.minecraft.text.Text.translatable("message.elemental.jetpack_engaged")
											.formatted(net.minecraft.util.Formatting.AQUA),
									true
							);
						}
					} else {
						// Airborne or finished takeoff spooling: instant thrust
						canThrust = true;
					}

					if (canThrust) {
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

						// Visual flame and smoke particles trailing accurately from the jetpack's twin exhaust nozzles
						net.minecraft.util.math.Vec3d[] nozzles = com.elemental.util.JetpackParticleHelper.getThrusterExhaustPositions(client.player);
						for (net.minecraft.util.math.Vec3d nozzle : nozzles) {
							double pvx = hasForward ? (-look.x * 0.25 + (Math.random() - 0.5) * 0.04) : ((Math.random() - 0.5) * 0.04);
							double pvy = hasForward ? (-look.y * 0.25 - 0.05) : -0.25;
							double pvz = hasForward ? (-look.z * 0.25 + (Math.random() - 0.5) * 0.04) : ((Math.random() - 0.5) * 0.04);

							client.particleManager.addParticle(
									net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
									nozzle.x,
									nozzle.y,
									nozzle.z,
									pvx,
									pvy,
									pvz
							);
							client.particleManager.addParticle(
									net.minecraft.particle.ParticleTypes.CLOUD,
									nozzle.x,
									nozzle.y,
									nozzle.z,
									pvx * 0.5,
									pvy * 0.5,
									pvz * 0.5
							);
						}

						// Start looping flight sound using custom thruster audio
						if (currentFlightSound == null || currentFlightSound.isDone()) {
							currentFlightSound = new com.elemental.client.sound.JetpackFlightSoundInstance(client.player);
							client.getSoundManager().play(currentFlightSound);
						}
					} else {
						// While still warming up on ground: stop flight sound
						if (currentFlightSound != null) {
							currentFlightSound.stopSound();
							currentFlightSound = null;
						}
					}
				} else {
					// Input released: stop flight sound and reset warmup / spooling state immediately (anti-exploit)
					if (currentFlightSound != null) {
						currentFlightSound.stopSound();
						currentFlightSound = null;
					}
					wasJumpPressedLastTick = false;
					isTakeoffSpooling = false;
					groundWarmupTicks = 0;

					// Sneak key held while falling -> gliding descent
					if (client.options.sneakKey.isPressed() && client.player.getVelocity().y < -0.15) {
						ClientPlayNetworking.send(com.elemental.network.QuestNetworking.JETPACK_GLIDE_PACKET_ID, PacketByteBufs.empty());
						client.player.setVelocity(client.player.getVelocity().x, -0.15, client.player.getVelocity().z);
						client.player.fallDistance = 0.0f;
					}
				}
			} else {
				// Jetpack unequipped or disabled via toggle key: stop flight sound and reset immediately
				if (currentFlightSound != null) {
					currentFlightSound.stopSound();
					currentFlightSound = null;
				}
				wasJumpPressedLastTick = false;
				isTakeoffSpooling = false;
				groundWarmupTicks = 0;
			}
		});
	}
}