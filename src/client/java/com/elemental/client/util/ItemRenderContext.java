package com.elemental.client.util;

import net.minecraft.entity.LivingEntity;

public class ItemRenderContext {
    private static final ThreadLocal<LivingEntity> CURRENT_ENTITY = new ThreadLocal<>();

    public static void set(LivingEntity entity) {
        CURRENT_ENTITY.set(entity);
    }

    public static void clear() {
        CURRENT_ENTITY.remove();
    }

    public static LivingEntity get() {
        return CURRENT_ENTITY.get();
    }
}
