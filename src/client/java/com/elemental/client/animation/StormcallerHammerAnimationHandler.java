package com.elemental.client.animation;

import com.elemental.combat.StormcallerServerCombatHandler;
import com.elemental.item.StormcallerHammerItem;
import com.elemental.network.StormcallerNetworking;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client animation and camera synchronization controller for the Stormcaller Hammer.
 * Integrates directly with PlayerAnimator (Player Animation Library) to play animations,
 * enables first-person animation visibility, and controls frame-accurate camera movement
 * during the ground smash attack.
 */
public class StormcallerHammerAnimationHandler {

    public static final Identifier LAYER_ID = new Identifier("elemental", "stormcaller_layer");

    // Per-player animation layer mapping
    private static final Map<UUID, ModifierLayer<IAnimation>> PLAYER_LAYERS = new ConcurrentHashMap<>();

    // Local cached animations fallback
    private static final Map<String, KeyframeAnimation> ANIMATION_CACHE = new HashMap<>();
    private static boolean animationsLoaded = false;

    // First-person camera animation state
    private static boolean isSmashCameraActive = false;
    private static int smashCameraTick = 0;
    private static float prevSmashCameraTick = 0;

    // Screen shake state
    private static float screenShakeIntensity = 0.0f;

    public static void initClient() {
        // Register animation layer factory for all player entities (local client and other players in multiplayer)
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                1000,
                player -> {
                    ModifierLayer<IAnimation> layer = new ModifierLayer<>();
                    PLAYER_LAYERS.put(player.getUuid(), layer);
                    return layer;
                }
        );

        // Fallback registration event
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, animationStack) -> {
            ModifierLayer<IAnimation> layer = PLAYER_LAYERS.computeIfAbsent(player.getUuid(), k -> {
                ModifierLayer<IAnimation> l = new ModifierLayer<>();
                animationStack.addAnimLayer(1000, l);
                return l;
            });
            try {
                PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
            } catch (Exception ignored) {}
        });

        // S2C Play Animation Receiver
        ClientPlayNetworking.registerGlobalReceiver(StormcallerNetworking.S2C_PLAY_ANIMATION, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String animName = buf.readString();
            client.execute(() -> {
                if (client.world != null) {
                    Entity entity = client.world.getEntityById(entityId);
                    if (entity instanceof AbstractClientPlayerEntity player) {
                        if ("stop".equals(animName)) {
                            stopPlayerAnimation(player);
                        } else {
                            // If local player already has this animation playing from local prediction, avoid restarting
                            if (client.player != null && client.player.getId() == entityId) {
                                ModifierLayer<IAnimation> layer = getPlayerLayer(player);
                                if (layer != null && layer.isActive()) {
                                    return;
                                }
                            }
                            playPlayerAnimation(player, animName);
                        }
                    }
                }
            });
        });

        // S2C Ground Impact Receiver (spawns screen shake and downward camera impulse)
        ClientPlayNetworking.registerGlobalReceiver(StormcallerNetworking.S2C_GROUND_IMPACT, (client, handler, buf, responseSender) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            client.execute(() -> triggerGroundImpactClient(x, y, z));
        });

        // Connect StormcallerHammerItem client callbacks
        StormcallerHammerItem.CLIENT_AIM_START_CALLBACK = player -> startLightningAim();
        StormcallerHammerItem.CLIENT_AIM_STOP_CALLBACK = player -> stopLightningAim();
        StormcallerHammerItem.CLIENT_RELEASE_CALLBACK = (player, targetId, charge) -> releaseLightningStrike(targetId, charge);

        // Client Tick for Camera & Shake
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickClientCamera());
    }

    public static void setPlayerLayer(UUID uuid, ModifierLayer<IAnimation> layer) {
        PLAYER_LAYERS.put(uuid, layer);
    }

    @SuppressWarnings("unchecked")
    public static ModifierLayer<IAnimation> getPlayerLayer(AbstractClientPlayerEntity player) {
        if (player == null) return null;
        ModifierLayer<IAnimation> layer = null;
        try {
            layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        } catch (Exception ignored) {}

        if (layer == null) {
            layer = PLAYER_LAYERS.get(player.getUuid());
        }

        if (layer == null) {
            try {
                var stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
                if (stack != null) {
                    layer = new ModifierLayer<>();
                    stack.addAnimLayer(1000, layer);
                    PLAYER_LAYERS.put(player.getUuid(), layer);
                    try {
                        PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return layer;
    }

    private static void ensureAnimationsLoaded() {
        if (animationsLoaded) return;
        animationsLoaded = true;

        try {
            InputStream is = StormcallerHammerAnimationHandler.class.getResourceAsStream("/assets/elemental/player_animation/stormcaller_hammer.json");
            if (is == null) {
                is = StormcallerHammerAnimationHandler.class.getResourceAsStream("/assets/elemental/animations/player/stormcaller_hammer.animation.json");
            }
            if (is != null) {
                List<KeyframeAnimation> list = AnimationSerializing.deserializeAnimation(is);
                for (KeyframeAnimation anim : list) {
                    String name = (String) anim.extraData.get("name");
                    if (name != null) {
                        String lower = name.toLowerCase(Locale.ROOT);
                        ANIMATION_CACHE.put(lower, anim);
                        ANIMATION_CACHE.put("animation.stormcaller_hammer." + lower, anim);
                        if (lower.startsWith("animation.stormcaller_hammer.")) {
                            String shortName = lower.substring("animation.stormcaller_hammer.".length());
                            ANIMATION_CACHE.put(shortName, anim);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static KeyframeAnimation getAnimation(String animName) {
        ensureAnimationsLoaded();

        String lower = animName.toLowerCase(Locale.ROOT);
        // 1. Try PlayerAnimationRegistry
        try {
            KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(new Identifier("elemental", lower));
            if (anim != null) return anim;
            anim = PlayerAnimationRegistry.getAnimation(new Identifier("elemental", "stormcaller_hammer." + lower));
            if (anim != null) return anim;
            for (Map.Entry<Identifier, KeyframeAnimation> entry : PlayerAnimationRegistry.getAnimations().entrySet()) {
                if (entry.getKey().getNamespace().equals("elemental") && entry.getKey().getPath().contains("ground_smash") && lower.contains("ground_smash")) {
                    return entry.getValue();
                }
            }
        } catch (Exception ignored) {}

        // 2. Try Cache
        KeyframeAnimation anim = ANIMATION_CACHE.get(lower);
        if (anim != null) return anim;

        if (lower.startsWith("animation.stormcaller_hammer.")) {
            String shortName = lower.substring("animation.stormcaller_hammer.".length());
            anim = ANIMATION_CACHE.get(shortName);
            if (anim != null) return anim;
        } else {
            anim = ANIMATION_CACHE.get("animation.stormcaller_hammer." + lower);
            if (anim != null) return anim;
        }

        for (Map.Entry<String, KeyframeAnimation> entry : ANIMATION_CACHE.entrySet()) {
            if (entry.getKey().contains("ground_smash") && lower.contains("ground_smash")) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Plays a PlayerAnimator animation on the given player.
     * Disabled per user request to preserve regular vanilla player animations.
     */
    public static void playPlayerAnimation(AbstractClientPlayerEntity player, String animName) {
        // No-op: custom player animations removed per user request
    }

    public static void stopPlayerAnimation(AbstractClientPlayerEntity player) {
        // No-op: custom player animations removed per user request
    }

    // ==========================================
    // CLIENT INPUT ACTIONS & PREDICTION
    // ==========================================

    public static void triggerMeleeSwing() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        // Send C2S packet
        ClientPlayNetworking.send(StormcallerNetworking.C2S_MELEE_SWING, PacketByteBufs.empty());

        // Instant local prediction
        playPlayerAnimation(player, "animation.stormcaller_hammer.melee_swing");
    }

    public static void startLightningAim() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(true);
        ClientPlayNetworking.send(StormcallerNetworking.C2S_LIGHTNING_AIM, buf);

        playPlayerAnimation(player, "animation.stormcaller_hammer.lightning_aim_idle");
    }

    public static void stopLightningAim() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(false);
        ClientPlayNetworking.send(StormcallerNetworking.C2S_LIGHTNING_AIM, buf);

        stopPlayerAnimation(player);
    }

    public static void releaseLightningStrike(int targetId, float chargeProgress) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(targetId);
        buf.writeFloat(chargeProgress);
        ClientPlayNetworking.send(StormcallerNetworking.C2S_LIGHTNING_RELEASE, buf);

        playPlayerAnimation(player, "animation.stormcaller_hammer.lightning_release");
    }

    public static void triggerGroundSmash() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        ClientPlayNetworking.send(StormcallerNetworking.C2S_GROUND_SMASH, PacketByteBufs.empty());

        // Prediction
        playPlayerAnimation(player, "animation.stormcaller_hammer.ground_smash");
        startSmashCameraAnimation();
    }

    // ==========================================
    // FIRST-PERSON CAMERA MOTION SYNCHRONIZATION
    // ==========================================

    private static void startSmashCameraAnimation() {
        isSmashCameraActive = true;
        smashCameraTick = 0;
        prevSmashCameraTick = 0;
    }

    private static void tickClientCamera() {
        prevSmashCameraTick = smashCameraTick;
        if (isSmashCameraActive) {
            smashCameraTick++;
            if (smashCameraTick > StormcallerServerCombatHandler.GROUND_SMASH_DURATION_TICKS) {
                isSmashCameraActive = false;
                smashCameraTick = 0;
                prevSmashCameraTick = 0;
            }
        }

        // Decay screen shake
        if (screenShakeIntensity > 0.01f) {
            screenShakeIntensity *= 0.82f;
        } else {
            screenShakeIntensity = 0.0f;
        }
    }

    public static void triggerGroundImpactClient(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        double distSq = mc.player.squaredDistanceTo(x, y, z);
        if (distSq < 256.0) { // Within 16 blocks
            float dist = (float) Math.sqrt(distSq);
            float factor = Math.max(0.0f, 1.0f - (dist / 16.0f));
            screenShakeIntensity = Math.max(screenShakeIntensity, factor * 3.5f);
        }
    }

    /**
     * Calculates the frame-interpolated first-person camera pitch offset.
     * Moves upward during hammer overhead wind-up, snaps sharply downward on impact (tick 16),
     * and decays smoothly back to normal.
     */
    public static float getCameraPitchOffset(float tickDelta) {
        if (!isSmashCameraActive && screenShakeIntensity <= 0.001f) {
            return 0.0f;
        }

        float offset = 0.0f;

        if (isSmashCameraActive) {
            float tick = MathHelper.lerp(tickDelta, prevSmashCameraTick, smashCameraTick);

            // Timing windows:
            // 0 to 14 ticks: Wind-up (overhead raise) -> Camera pitches upward (-4.5 degrees)
            // 15 to 17 ticks: Downward smash & impact at tick 16 -> Snaps downward (+7.0 degrees)
            // 18 to 25 ticks: Recovery -> Smoothly returns to 0
            if (tick <= 14.0f) {
                float progress = tick / 14.0f;
                // Smooth sine upward curve (negative pitch is looking up in Minecraft)
                offset = -MathHelper.sin(progress * (float) Math.PI * 0.5f) * 4.5f;
            } else if (tick <= 17.0f) {
                float progress = (tick - 14.0f) / 3.0f;
                // Fast snap from -4.5 up to +7.0
                offset = MathHelper.lerp(progress, -4.5f, 7.0f);
            } else {
                float progress = (tick - 17.0f) / 8.0f;
                float clampedProgress = MathHelper.clamp(progress, 0.0f, 1.0f);
                // Smooth decay from +7.0 back to 0.0
                offset = (1.0f - MathHelper.sin(clampedProgress * (float) Math.PI * 0.5f)) * 7.0f;
            }
        }

        // Add subtle high-frequency screen shake vibration
        if (screenShakeIntensity > 0.01f) {
            long time = System.currentTimeMillis();
            float shake = (float) Math.sin(time * 0.06) * screenShakeIntensity * 0.7f;
            offset += shake;
        }

        return offset;
    }
}
