package com.elemental.client.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active fist bump animation states for local and remote players on the client.
 */
public class ClientFistBumpSync {

    public static class AnimationState {
        public int currentTick = 0;
        public final int maxTicks;

        public AnimationState(int maxTicks) {
            this.maxTicks = maxTicks;
        }
    }

    private static final Map<Integer, AnimationState> ACTIVE_ANIMATIONS = new ConcurrentHashMap<>();

    public static void startAnimation(int entityId, int durationTicks) {
        ACTIVE_ANIMATIONS.put(entityId, new AnimationState(durationTicks));
    }

    public static boolean isAnimating(int entityId) {
        return ACTIVE_ANIMATIONS.containsKey(entityId);
    }

    public static float getAnimationTick(int entityId, float tickDelta) {
        AnimationState state = ACTIVE_ANIMATIONS.get(entityId);
        if (state == null) return 0.0f;
        return Math.min(state.currentTick + tickDelta, (float) state.maxTicks);
    }

    public static void clientTick() {
        ACTIVE_ANIMATIONS.entrySet().removeIf(entry -> {
            entry.getValue().currentTick++;
            return entry.getValue().currentTick >= entry.getValue().maxTicks;
        });
    }

    public static void clear(int entityId) {
        ACTIVE_ANIMATIONS.remove(entityId);
    }
}
