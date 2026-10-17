package com.elemental.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Utility helper to accurately position jetpack exhaust particles at the twin thruster nozzles.
 */
public class JetpackParticleHelper {

    /**
     * Calculates the world coordinates of the left and right jetpack exhaust nozzles.
     * The nozzles are mounted on the lower back of the jetpack backpack:
     * - Height: lower torso level (~0.85 blocks above entity feet)
     * - Back offset: ~0.34 blocks behind the player's torso backplate
     * - Lateral offset: +/- 0.28 blocks for left (+X) and right (-X) thrusters
     */
    public static Vec3d[] getThrusterExhaustPositions(LivingEntity entity) {
        float bodyYaw = entity.bodyYaw;
        float yawRad = (float) Math.toRadians(bodyYaw);

        // Vector pointing directly behind the player
        double backX = Math.sin(yawRad);
        double backZ = -Math.cos(yawRad);

        // Vector pointing to the player's left side
        double leftX = Math.cos(yawRad);
        double leftZ = Math.sin(yawRad);

        double baseY = entity.getY() + 0.85;
        double backDist = 0.34;
        double sideDist = 0.28;

        // Center point at the bottom-back of the jetpack
        double centerX = entity.getX() + backX * backDist;
        double centerZ = entity.getZ() + backZ * backDist;

        // Left nozzle
        Vec3d leftNozzle = new Vec3d(
                centerX + leftX * sideDist,
                baseY,
                centerZ + leftZ * sideDist
        );

        // Right nozzle
        Vec3d rightNozzle = new Vec3d(
                centerX - leftX * sideDist,
                baseY,
                centerZ - leftZ * sideDist
        );

        return new Vec3d[]{leftNozzle, rightNozzle};
    }

    /**
     * Spawns active flight thrust particles from both exhaust nozzles on the server.
     */
    public static void spawnFlightParticlesServer(ServerWorld world, LivingEntity entity, boolean hasForwardInput) {
        Vec3d[] nozzles = getThrusterExhaustPositions(entity);
        Vec3d look = entity.getRotationVec(1.0f);

        for (Vec3d nozzle : nozzles) {
            double velX;
            double velY;
            double velZ;

            if (hasForwardInput) {
                // Exhaust jets backward opposite to flight direction
                velX = -look.x * 0.35 + (Math.random() - 0.5) * 0.05;
                velY = -look.y * 0.35 - 0.05;
                velZ = -look.z * 0.35 + (Math.random() - 0.5) * 0.05;
            } else {
                // Vertical climb: exhaust streams directly downward
                velX = (Math.random() - 0.5) * 0.05;
                velY = -0.30;
                velZ = (Math.random() - 0.5) * 0.05;
            }

            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, nozzle.x, nozzle.y, nozzle.z, 1, velX * 0.25, velY * 0.25, velZ * 0.25, 0.02);
            world.spawnParticles(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z, 1, velX * 0.15, velY * 0.15, velZ * 0.15, 0.01);
        }
    }
}
