package com.elemental.util;

import com.elemental.entity.EverlivingKnightEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class RootboundScheduler {

    public static class ThornCageZone {
        final RegistryKey<World> worldKey;
        final UUID casterUuid;
        final Vec3d center;
        final double radius;
        final int maxDuration;
        final int interval;
        final float damagePerTick;
        int elapsedTicks = 0;

        public ThornCageZone(RegistryKey<World> worldKey, UUID casterUuid, Vec3d center, double radius, int maxDuration, int interval, float damagePerTick) {
            this.worldKey = worldKey;
            this.casterUuid = casterUuid;
            this.center = center;
            this.radius = radius;
            this.maxDuration = maxDuration;
            this.interval = interval;
            this.damagePerTick = damagePerTick;
        }
    }

    public static class HealingRootZone {
        final RegistryKey<World> worldKey;
        final UUID casterUuid;
        final BlockPos centerPos;
        final double radius;
        final int maxDuration;
        final int regenDuration;
        final int regenAmplifier;
        int elapsedTicks = 0;

        public HealingRootZone(RegistryKey<World> worldKey, UUID casterUuid, BlockPos centerPos, double radius, int maxDuration, int regenDuration, int regenAmplifier) {
            this.worldKey = worldKey;
            this.casterUuid = casterUuid;
            this.centerPos = centerPos;
            this.radius = radius;
            this.maxDuration = maxDuration;
            this.regenDuration = regenDuration;
            this.regenAmplifier = regenAmplifier;
        }
    }

    private static final List<ThornCageZone> ACTIVE_CAGES = new ArrayList<>();
    private static final List<HealingRootZone> ACTIVE_ROOTS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RootboundScheduler::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ACTIVE_CAGES.clear();
            ACTIVE_ROOTS.clear();
        });
    }

    public static void scheduleThornCage(ServerWorld world, ServerPlayerEntity caster, Vec3d center, double radius, int duration, int interval, float damagePerTick) {
        if (world == null || center == null) return;
        UUID uuid = caster != null ? caster.getUuid() : null;
        ACTIVE_CAGES.add(new ThornCageZone(world.getRegistryKey(), uuid, center, radius, duration, interval, damagePerTick));
    }

    public static void scheduleHealingRoot(ServerWorld world, UUID casterUuid, BlockPos centerPos, double radius, int duration, int regenDuration, int regenAmplifier) {
        if (world == null || centerPos == null) return;
        ACTIVE_ROOTS.add(new HealingRootZone(world.getRegistryKey(), casterUuid, centerPos, radius, duration, regenDuration, regenAmplifier));
    }

    private static void tick(MinecraftServer server) {
        tickCages(server);
        tickRoots(server);
    }

    private static void tickCages(MinecraftServer server) {
        if (ACTIVE_CAGES.isEmpty()) return;

        Iterator<ThornCageZone> iterator = ACTIVE_CAGES.iterator();
        while (iterator.hasNext()) {
            ThornCageZone zone = iterator.next();
            ServerWorld world = server.getWorld(zone.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }

            zone.elapsedTicks++;

            // Every damage interval (e.g. 10 ticks = 0.5s)
            if (zone.elapsedTicks % zone.interval == 0) {
                ServerPlayerEntity caster = zone.casterUuid != null ? server.getPlayerManager().getPlayer(zone.casterUuid) : null;
                Box box = new Box(zone.center.x - zone.radius, zone.center.y - zone.radius, zone.center.z - zone.radius,
                        zone.center.x + zone.radius, zone.center.y + zone.radius, zone.center.z + zone.radius);

                List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box, e ->
                        e.isAlive() && !e.isSpectator() && !(e instanceof PlayerEntity) && e.squaredDistanceTo(zone.center) <= zone.radius * zone.radius
                );

                for (LivingEntity target : entities) {
                    if (target instanceof EverlivingKnightEntity) {
                        // Boss core interaction already handled on cage activation
                        continue;
                    }
                    if (caster != null && caster.isAlive()) {
                        target.damage(world.getDamageSources().thorns(caster), zone.damagePerTick);
                    } else {
                        target.damage(world.getDamageSources().magic(), zone.damagePerTick);
                    }
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1, false, false, true));
                }

                // Ambient cage particles
                world.spawnParticles(ParticleTypes.COMPOSTER, zone.center.x, zone.center.y + 0.5, zone.center.z,
                        12, zone.radius * 0.4, 0.4, zone.radius * 0.4, 0.05);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, zone.center.x, zone.center.y + 0.5, zone.center.z,
                        6, zone.radius * 0.3, 0.5, zone.radius * 0.3, 0.02);
            }

            if (zone.elapsedTicks >= zone.maxDuration) {
                iterator.remove();
            }
        }
    }

    private static void tickRoots(MinecraftServer server) {
        if (ACTIVE_ROOTS.isEmpty()) return;

        Iterator<HealingRootZone> iterator = ACTIVE_ROOTS.iterator();
        while (iterator.hasNext()) {
            HealingRootZone zone = iterator.next();
            ServerWorld world = server.getWorld(zone.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }

            // Check if destroyed by fire or lava
            BlockState state = world.getBlockState(zone.centerPos);
            if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.LAVA)) {
                world.playSound(null, zone.centerPos.getX(), zone.centerPos.getY(), zone.centerPos.getZ(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.2f);
                world.spawnParticles(ParticleTypes.SMOKE, zone.centerPos.getX() + 0.5, zone.centerPos.getY() + 0.5, zone.centerPos.getZ() + 0.5,
                        15, 0.3, 0.3, 0.3, 0.05);
                iterator.remove();
                continue;
            }

            zone.elapsedTicks++;

            // Every 20 ticks (1s), grant regeneration to players in zone
            if (zone.elapsedTicks % 20 == 0) {
                Box box = new Box(
                        zone.centerPos.getX() - zone.radius, zone.centerPos.getY() - 1.5, zone.centerPos.getZ() - zone.radius,
                        zone.centerPos.getX() + zone.radius, zone.centerPos.getY() + 2.5, zone.centerPos.getZ() + zone.radius
                );

                List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, box, p ->
                        p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(zone.centerPos.toCenterPos()) <= zone.radius * zone.radius
                );

                for (PlayerEntity player : players) {
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.REGENERATION, zone.regenDuration, zone.regenAmplifier, false, false, true
                    ));
                }

                // Visual pulses
                world.spawnParticles(ParticleTypes.GLOW, zone.centerPos.getX() + 0.5, zone.centerPos.getY() + 0.2, zone.centerPos.getZ() + 0.5,
                        8, zone.radius * 0.4, 0.3, zone.radius * 0.4, 0.02);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, zone.centerPos.getX() + 0.5, zone.centerPos.getY() + 0.3, zone.centerPos.getZ() + 0.5,
                        5, zone.radius * 0.3, 0.3, zone.radius * 0.3, 0.01);
            }

            if (zone.elapsedTicks >= zone.maxDuration) {
                iterator.remove();
            }
        }
    }
}
