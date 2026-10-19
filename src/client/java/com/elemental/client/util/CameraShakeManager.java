package com.elemental.client.util;

import java.util.Random;

public class CameraShakeManager {
    private static final Random RANDOM = new Random();
    private static float currentIntensity = 0.0f;
    private static int remainingTicks = 0;
    private static int totalTicks = 0;
    private static float prevPitchOffset = 0.0f;
    private static float pitchOffset = 0.0f;
    private static float prevYawOffset = 0.0f;
    private static float yawOffset = 0.0f;

    public static void addShake(float intensity, int ticks) {
        currentIntensity = Math.max(currentIntensity, intensity);
        remainingTicks = Math.max(remainingTicks, ticks);
        totalTicks = Math.max(totalTicks, ticks);
    }

    public static void clientTick() {
        prevPitchOffset = pitchOffset;
        prevYawOffset = yawOffset;

        if (remainingTicks > 0) {
            float progress = totalTicks > 0 ? (float) remainingTicks / (float) totalTicks : 1.0f;
            float factor = (float) Math.pow(progress, 1.1);
            float magnitude = currentIntensity * factor;
            pitchOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * magnitude;
            yawOffset = (RANDOM.nextFloat() * 2.0f - 1.0f) * magnitude;
            remainingTicks--;
            if (remainingTicks <= 0) {
                currentIntensity = 0.0f;
                totalTicks = 0;
                pitchOffset = 0.0f;
                yawOffset = 0.0f;
            }
        } else {
            pitchOffset = 0.0f;
            yawOffset = 0.0f;
        }
    }

    public static float getPitchOffset(float tickDelta) {
        return prevPitchOffset + (pitchOffset - prevPitchOffset) * tickDelta;
    }

    public static float getYawOffset(float tickDelta) {
        return prevYawOffset + (yawOffset - prevYawOffset) * tickDelta;
    }

    public static float getPitchOffset() {
        return pitchOffset;
    }

    public static float getYawOffset() {
        return yawOffset;
    }
}
