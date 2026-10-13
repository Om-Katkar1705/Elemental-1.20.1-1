package com.elemental.util;

import com.elemental.block.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AshenRiftScheduler {

    // Global protection map so overlapping rifts never overwrite true original terrain with Air/Lava
    private static final Map<BlockPos, BlockState> GLOBAL_ORIGINAL_BLOCKS = new ConcurrentHashMap<>();

    private static class HoleSpec {
        final UUID targetUuid;
        final BlockPos centerPos;
        final int radius;
        final int depth;
        final int surfaceY;
        final Set<BlockPos> holePositions = new HashSet<>();
        final Set<BlockPos> lavaPositions = new HashSet<>();

        HoleSpec(UUID targetUuid, BlockPos centerPos, int radius, int depth, int surfaceY) {
            this.targetUuid = targetUuid;
            this.centerPos = centerPos;
            this.radius = radius;
            this.depth = depth;
            this.surfaceY = surfaceY;
        }
    }

    private static class RiftCollapseSession {
        final RegistryKey<World> worldKey;
        final UUID playerUuid;
        final List<UUID> targetUuids;
        final List<HoleSpec> holes = new ArrayList<>();
        final Map<BlockPos, BlockState> sessionOriginalBlocks = new HashMap<>();
        int ticks = 0;

        RiftCollapseSession(RegistryKey<World> worldKey, UUID playerUuid, List<UUID> targetUuids) {
            this.worldKey = worldKey;
            this.playerUuid = playerUuid;
            this.targetUuids = targetUuids;
        }
    }

    private static class RiftTeleportTask {
        final UUID playerUuid;
        final RegistryKey<World> worldKey;
        final Vec3d initialPos;
        final Vec3d destPos;
        int ticks = 0;

        RiftTeleportTask(UUID playerUuid, RegistryKey<World> worldKey, Vec3d initialPos, Vec3d destPos) {
            this.playerUuid = playerUuid;
            this.worldKey = worldKey;
            this.initialPos = initialPos;
            this.destPos = destPos;
        }
    }

    private static final List<RiftCollapseSession> ACTIVE_SESSIONS = new ArrayList<>();
    private static final List<RiftTeleportTask> ACTIVE_TELEPORTS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AshenRiftScheduler::tick);
    }

    public static void startRiftCollapse(ServerWorld world, ServerPlayerEntity player, List<LivingEntity> targets) {
        if (world == null || targets.isEmpty()) return;
        List<UUID> targetUuids = new ArrayList<>();
        for (LivingEntity target : targets) {
            if (target != null && target.isAlive()) {
                targetUuids.add(target.getUuid());
            }
        }
        if (targetUuids.isEmpty()) return;

        ACTIVE_SESSIONS.add(new RiftCollapseSession(world.getRegistryKey(), player != null ? player.getUuid() : null, targetUuids));
    }

    public static void scheduleTeleport(ServerWorld world, ServerPlayerEntity player, Vec3d initialPos, Vec3d destPos) {
        if (world == null || player == null || destPos == null) return;
        ACTIVE_TELEPORTS.add(new RiftTeleportTask(player.getUuid(), world.getRegistryKey(), initialPos, destPos));
    }

    public static boolean isProtectedBlock(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.getHardness(world, pos) < 0) return true; // Bedrock, end portal frame, barrier
        if (world.getBlockEntity(pos) != null) return true; // Containers, chests, tile entities
        if (state.isOf(ModBlocks.WEAPONER) || state.isOf(ModBlocks.EVERLIVING_ALTAR)) return true;
        if (state.isOf(Blocks.NETHER_PORTAL) || state.isOf(Blocks.END_PORTAL)) return true;
        return false;
    }

    private static void tick(MinecraftServer server) {
        tickCollapses(server);
        tickTeleports(server);
    }

    private static void tickCollapses(MinecraftServer server) {
        if (ACTIVE_SESSIONS.isEmpty()) return;

        Iterator<RiftCollapseSession> iterator = ACTIVE_SESSIONS.iterator();
        while (iterator.hasNext()) {
            RiftCollapseSession session = iterator.next();
            ServerWorld world = server.getWorld(session.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }

            session.ticks++;

            // Tick 5 (~0.25s): After initial rebound knockback, create a hole beneath each affected mob
            if (session.ticks == 5) {
                for (UUID targetId : session.targetUuids) {
                    net.minecraft.entity.Entity entity = world.getEntity(targetId);
                    if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
                        continue;
                    }

                    float width = target.getWidth();
                    float height = target.getHeight();

                    // Slightly wider than hitbox
                    int radius = Math.max(1, (int) Math.ceil((width + 0.3) / 2.0));
                    // Deeper than hitbox
                    int depth = Math.max(3, (int) Math.ceil(height) + 2);

                    // Find ground surface beneath target's current position
                    BlockPos groundPos = target.getBlockPos();
                    while (groundPos.getY() > world.getBottomY() && world.getBlockState(groundPos).isAir()) {
                        groundPos = groundPos.down();
                    }
                    int surfaceY = groundPos.getY();

                    HoleSpec hole = new HoleSpec(targetId, groundPos, radius, depth, surfaceY);

                    // Excavate hole
                    for (int y = surfaceY; y >= surfaceY - depth + 1 && y > world.getBottomY(); y--) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                BlockPos pos = new BlockPos(groundPos.getX() + dx, y, groundPos.getZ() + dz);
                                BlockState state = world.getBlockState(pos);

                                if (isProtectedBlock(world, pos, state)) {
                                    continue;
                                }

                                // Store original state if not already tracked by an active hole
                                if (!GLOBAL_ORIGINAL_BLOCKS.containsKey(pos)) {
                                    GLOBAL_ORIGINAL_BLOCKS.put(pos, state);
                                }
                                session.sessionOriginalBlocks.putIfAbsent(pos, state);
                                hole.holePositions.add(pos);

                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            }
                        }
                    }

                    // Pull mob downwards into hole
                    target.setVelocity(0, -0.7, 0);
                    target.velocityModified = true;

                    // Hole opening visual and audio effects
                    world.playSound(null, groundPos.getX(), surfaceY, groundPos.getZ(),
                            SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.2f, 0.7f);
                    world.playSound(null, groundPos.getX(), surfaceY, groundPos.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.8f, 0.6f);
                    world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, groundPos.getX() + 0.5, surfaceY + 0.5, groundPos.getZ() + 0.5,
                            18, radius * 0.5, 0.2, radius * 0.5, 0.04);
                    world.spawnParticles(ParticleTypes.ASH, groundPos.getX() + 0.5, surfaceY + 0.5, groundPos.getZ() + 0.5,
                            25, radius * 0.5, 0.2, radius * 0.5, 0.05);

                    session.holes.add(hole);
                }
            }

            // Tick 45 (2.0s after hole opened): Spawn lava inside hole so trapped mobs burn to death
            if (session.ticks == 45) {
                for (HoleSpec hole : session.holes) {
                    int bottomY = hole.surfaceY - hole.depth + 1;
                    int bottomY2 = hole.surfaceY - hole.depth + 2;

                    for (int dx = -hole.radius; dx <= hole.radius; dx++) {
                        for (int dz = -hole.radius; dz <= hole.radius; dz++) {
                            BlockPos pos1 = new BlockPos(hole.centerPos.getX() + dx, bottomY, hole.centerPos.getZ() + dz);
                            if (hole.holePositions.contains(pos1) && world.getBlockState(pos1).isAir()) {
                                world.setBlockState(pos1, Blocks.LAVA.getDefaultState(), Block.NOTIFY_ALL);
                                hole.lavaPositions.add(pos1);
                            }
                            if (hole.depth >= 4) {
                                BlockPos pos2 = new BlockPos(hole.centerPos.getX() + dx, bottomY2, hole.centerPos.getZ() + dz);
                                if (hole.holePositions.contains(pos2) && world.getBlockState(pos2).isAir()) {
                                    world.setBlockState(pos2, Blocks.LAVA.getDefaultState(), Block.NOTIFY_ALL);
                                    hole.lavaPositions.add(pos2);
                                }
                            }
                        }
                    }

                    world.playSound(null, hole.centerPos.getX(), bottomY, hole.centerPos.getZ(),
                            SoundEvents.ITEM_BUCKET_EMPTY_LAVA, SoundCategory.BLOCKS, 1.0f, 0.9f);
                    world.playSound(null, hole.centerPos.getX(), bottomY, hole.centerPos.getZ(),
                            SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.2f);
                    world.spawnParticles(ParticleTypes.LAVA, hole.centerPos.getX() + 0.5, bottomY + 1.0, hole.centerPos.getZ() + 0.5,
                            15, hole.radius * 0.4, 0.5, hole.radius * 0.4, 0.1);
                    world.spawnParticles(ParticleTypes.FLAME, hole.centerPos.getX() + 0.5, bottomY + 1.0, hole.centerPos.getZ() + 0.5,
                            20, hole.radius * 0.4, 0.5, hole.radius * 0.4, 0.05);

                    // Ensure target mob takes fire damage even if suspended
                    net.minecraft.entity.Entity entity = world.getEntity(hole.targetUuid);
                    if (entity instanceof LivingEntity target && target.isAlive()) {
                        target.setOnFireFor(8);
                        target.damage(world.getDamageSources().lava(), 6.0f);
                    }
                }
            }

            // Tick 105 (5.0s total): Refill each hole by copying surrounding blocks back into place, restoring terrain
            if (session.ticks >= 105) {
                // Remove placed lava first
                for (HoleSpec hole : session.holes) {
                    for (BlockPos lavaPos : hole.lavaPositions) {
                        if (world.getBlockState(lavaPos).isOf(Blocks.LAVA)) {
                            world.setBlockState(lavaPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }

                // Restore recorded blocks
                for (Map.Entry<BlockPos, BlockState> entry : session.sessionOriginalBlocks.entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockState origState = entry.getValue();

                    // If not in another active hole, restore
                    boolean activeInOther = false;
                    for (RiftCollapseSession other : ACTIVE_SESSIONS) {
                        if (other != session && other.sessionOriginalBlocks.containsKey(pos)) {
                            activeInOther = true;
                            break;
                        }
                    }

                    if (!activeInOther) {
                        BlockState restoreState = origState;
                        // If original block was air inside underground cavity, copy surrounding block
                        if (restoreState == null || restoreState.isAir()) {
                            restoreState = findSurroundingGroundState(world, pos);
                        }
                        world.setBlockState(pos, restoreState, Block.NOTIFY_ALL);
                        GLOBAL_ORIGINAL_BLOCKS.remove(pos);
                    }
                }

                // Restoration audio and visual feedback
                for (HoleSpec hole : session.holes) {
                    world.playSound(null, hole.centerPos.getX(), hole.surfaceY, hole.centerPos.getZ(),
                            SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f);
                    world.playSound(null, hole.centerPos.getX(), hole.surfaceY, hole.centerPos.getZ(),
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 0.7f, 1.5f);
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, hole.centerPos.getX() + 0.5, hole.surfaceY + 0.5, hole.centerPos.getZ() + 0.5,
                            20, hole.radius * 0.5, 0.3, hole.radius * 0.5, 0.05);
                }

                iterator.remove();
            }
        }
    }

    private static BlockState findSurroundingGroundState(ServerWorld world, BlockPos pos) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighbor);
            if (!neighborState.isAir() && !neighborState.isOf(Blocks.LAVA) && !neighborState.isOf(Blocks.WATER)) {
                return neighborState;
            }
        }
        BlockState belowState = world.getBlockState(pos.down());
        if (!belowState.isAir() && !belowState.isOf(Blocks.LAVA) && !belowState.isOf(Blocks.WATER)) {
            return belowState;
        }
        return Blocks.AIR.getDefaultState();
    }

    private static void tickTeleports(MinecraftServer server) {
        if (ACTIVE_TELEPORTS.isEmpty()) return;

        Iterator<RiftTeleportTask> iterator = ACTIVE_TELEPORTS.iterator();
        while (iterator.hasNext()) {
            RiftTeleportTask task = iterator.next();
            ServerWorld world = server.getWorld(task.worldKey);
            if (world == null) {
                iterator.remove();
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.playerUuid);
            if (player == null || !player.isAlive() || player.isDisconnected()) {
                iterator.remove();
                continue;
            }

            task.ticks++;

            // Ticks 1-5: Player falling into initial portal beneath legs
            if (task.ticks < 6) {
                player.setVelocity(0, -0.55, 0);
                player.velocityModified = true;
                player.fallDistance = 0.0f;

                world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.1, player.getZ(),
                        12, 0.35, 0.1, 0.35, 0.05);
                world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.2, player.getZ(),
                        8, 0.25, 0.1, 0.25, 0.02);
            }
            // Tick 6: Teleport player 5 blocks above destination and spawn falling portal animation
            else if (task.ticks == 6) {
                // Closing portal at initial position
                world.playSound(null, task.initialPos.x, task.initialPos.y, task.initialPos.z,
                        SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.8f, 1.4f);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, task.initialPos.x, task.initialPos.y + 0.2, task.initialPos.z,
                        25, 0.4, 0.2, 0.4, 0.08);

                // Teleport to destination
                player.teleport(world, task.destPos.x, task.destPos.y, task.destPos.z, player.getYaw(), player.getPitch());
                player.fallDistance = 0.0f;

                // Portal appearance sound at destination
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 1.0f, 0.9f);
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.8f);

                // Spawn falling portal animation at destination
                for (int i = 0; i < 36; i++) {
                    double angle = 2 * Math.PI * i / 36.0;
                    double rx = 0.9 * Math.cos(angle);
                    double rz = 0.9 * Math.sin(angle);
                    world.spawnParticles(ParticleTypes.PORTAL, task.destPos.x + rx, task.destPos.y + 1.8, task.destPos.z + rz,
                            2, 0, -0.2, 0, 0.1);
                    world.spawnParticles(ParticleTypes.ASH, task.destPos.x + rx * 0.7, task.destPos.y + 1.5, task.destPos.z + rz * 0.7,
                            2, 0.05, -0.1, 0.05, 0.03);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, task.destPos.x + rx * 0.5, task.destPos.y + 1.8, task.destPos.z + rz * 0.5,
                            1, 0, -0.1, 0, 0.02);
                }

                // Player falls downward out of portal
                player.setVelocity(0, -0.4, 0);
                player.velocityModified = true;
            }
            // Ticks 7-14: Trail falling portal animation as player falls out of it
            else if (task.ticks <= 14) {
                world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                        8, 0.3, 0.3, 0.3, 0.05);
                world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.5, player.getZ(),
                        6, 0.2, 0.2, 0.2, 0.02);
                player.fallDistance = 0.0f; // Prevent fall damage on landing
            }
            // Completed
            else {
                iterator.remove();
            }
        }
    }
}
