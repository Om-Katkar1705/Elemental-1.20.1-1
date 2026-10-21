package com.elemental.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class StormcallerLightningScheduler {

    private static class Barrage {
        final UUID targetUuid;
        final UUID attackerUuid;
        final RegistryKey<World> worldKey;
        int strikesRemaining;
        final int intervalTicks;
        int nextStrikeDelay;
        final float damagePerStrike;

        Barrage(UUID targetUuid, UUID attackerUuid, RegistryKey<World> worldKey, int strikesRemaining, int intervalTicks, float damagePerStrike) {
            this.targetUuid = targetUuid;
            this.attackerUuid = attackerUuid;
            this.worldKey = worldKey;
            this.strikesRemaining = strikesRemaining;
            this.intervalTicks = intervalTicks;
            this.nextStrikeDelay = 0; // First strike fires immediately
            this.damagePerStrike = damagePerStrike;
        }
    }

    private static final List<Barrage> ACTIVE_BARRAGES = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StormcallerLightningScheduler::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE_BARRAGES.clear());
    }

    public static void scheduleBarrage(ServerWorld world, ServerPlayerEntity attacker, LivingEntity target, int totalStrikes, float damagePerStrike, int intervalTicks) {
        if (world == null || target == null || totalStrikes <= 0) return;
        UUID attackerUuid = attacker != null ? attacker.getUuid() : null;
        ACTIVE_BARRAGES.add(new Barrage(target.getUuid(), attackerUuid, world.getRegistryKey(), totalStrikes, intervalTicks, damagePerStrike));
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE_BARRAGES.isEmpty()) return;

        Iterator<Barrage> iterator = ACTIVE_BARRAGES.iterator();
        while (iterator.hasNext()) {
            Barrage barrage = iterator.next();
            ServerWorld world = server.getWorld(barrage.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }

            net.minecraft.entity.Entity entity = world.getEntity(barrage.targetUuid);
            // Edge case: target is missing, dead, removed, or chunk is unloaded during barrage
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved() || !world.isChunkLoaded(entity.getBlockX() >> 4, entity.getBlockZ() >> 4)) {
                iterator.remove();
                continue;
            }

            ServerPlayerEntity attacker = barrage.attackerUuid != null ? server.getPlayerManager().getPlayer(barrage.attackerUuid) : null;

            if (barrage.nextStrikeDelay > 0) {
                barrage.nextStrikeDelay--;
                continue;
            }

            // Spawn cosmetic lightning bolt to avoid block fires or uncontrolled AOE on player
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
                lightning.setCosmetic(true);
                world.spawnEntity(lightning);
            }

            // Deal controlled damage directly to targeted mob
            DamageSource damageSource = (attacker != null && attacker.isAlive())
                    ? world.getDamageSources().playerAttack(attacker)
                    : world.getDamageSources().lightningBolt();

            net.minecraft.util.math.Vec3d prevVel = target.getVelocity();
            target.timeUntilRegen = 0;
            target.damage(damageSource, barrage.damagePerStrike);
            // Pin the target with lightning rather than flinging them away out of the barrage
            target.setVelocity(prevVel.x * 0.2, Math.max(prevVel.y * 0.5, -0.1), prevVel.z * 0.2);
            target.velocityModified = true;

            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(),
                    10, 0.3, 0.4, 0.3, 0.1);

            barrage.strikesRemaining--;
            barrage.nextStrikeDelay = barrage.intervalTicks;

            if (barrage.strikesRemaining <= 0) {
                iterator.remove();
            }
        }
    }
}
