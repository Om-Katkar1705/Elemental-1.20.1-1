package com.elemental.client.util;

import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores replicated extra jetpack slot state for local and remote entities on the client.
 * Guarantees that remote players' jetpacks render immediately even if network packets arrive
 * slightly before or after entity instantiation.
 */
public class ClientJetpackSync {
    private static final Map<Integer, ItemStack> SLOTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> ENABLED_STATES = new ConcurrentHashMap<>();

    public static void update(int entityId, ItemStack stack, boolean enabled) {
        if (stack == null || stack.isEmpty()) {
            SLOTS.remove(entityId);
        } else {
            SLOTS.put(entityId, stack.copy());
        }
        ENABLED_STATES.put(entityId, enabled);
    }

    public static ItemStack getStack(int entityId) {
        return SLOTS.getOrDefault(entityId, ItemStack.EMPTY);
    }

    public static boolean isEnabled(int entityId) {
        return ENABLED_STATES.getOrDefault(entityId, true);
    }

    public static void clear(int entityId) {
        SLOTS.remove(entityId);
        ENABLED_STATES.remove(entityId);
    }
}
