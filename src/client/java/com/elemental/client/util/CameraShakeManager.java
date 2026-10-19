package com.elemental.client.util;

import java.util.Random;

public class CameraShakeManager {
    private static final Random RANDOM = new Random();
    private static float currentIntensity = 0.0f;
    private static int remainingTicks = 0;
    private static float pitchOffset = 0.0f;
    private static float yawOffset = 0.0f;

    public static void addShake(float intensity, int ticks) {
        currentIntensity = Math.max(currentIntensity, intensity);
        remainingTicks = Math.max(remainingTicks, ticks);
    }

    public static void clientTick() {
        if (remainingTicks > 0) {
            float factor = Math.min(1.0f, (float) remainingTicks / 10.0f);
            pitchOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * currentIntensity * factor;
            yawOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * currentIntensity * factor;
            remainingTicks--;
            if (remainingTicks <= 0) {
                currentIntensity = 0.0f;
                pitchOffset = 0.0f;
                yawOffset = 0.0f;
            }
        } else {
            pitchOffset = 0.0f;
            yawOffset = 0.0f;
        }
    }

    public static float getPitchOffset() {
        return pitchOffset;
    }

    public static float getYawOffset() {
        return yawOffset;
    }
}
