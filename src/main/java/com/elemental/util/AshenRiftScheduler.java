package com.elemental.util;

import com.elemental.block.ModBlocks;
import com.elemental.item.AshenRiftBladeItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AshenRiftScheduler {

    // Global protection map so overlapping rifts never overwrite true original terrain with Air/Lava
    private static final Map<BlockPos, BlockState> GLOBAL_ORIGINAL_BLOCKS = new ConcurrentHashMap<>();

    // Fall damage immunity tracking for teleporting players
    private static final Map<UUID, Long> FALL_IMMUNITY_MAP = new ConcurrentHashMap<>();

    public static void grantFallImmunity(UUID playerUuid, long expiryTick) {
        if (playerUuid != null) {
            FALL_IMMUNITY_MAP.put(playerUuid, expiryTick);
        }
    }

    public static boolean isFallImmune(UUID playerUuid, long currentTick) {
        if (playerUuid == null) return false;
        Long expiry = FALL_IMMUNITY_MAP.get(playerUuid);
        if (expiry == null) return false;
        if (currentTick > expiry) {
            FALL_IMMUNITY_MAP.remove(playerUuid);
            return false;
        }
        return true;
    }

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
        final Vec3d backDir;
        final float initialYaw;
        final float initialPitch;
        int ticks = 0;
        boolean teleported = false;
        boolean landed = false;
        int postLandTicks = 0;

        RiftTeleportTask(UUID playerUuid, RegistryKey<World> worldKey, Vec3d initialPos, Vec3d destPos, Vec3d lookDir, float initialYaw, float initialPitch) {
            this.playerUuid = playerUuid;
            this.worldKey = worldKey;
            this.initialPos = initialPos;
            this.destPos = destPos;
            this.initialYaw = initialYaw;
            this.initialPitch = initialPitch;
            Vec3d horiz = new Vec3d(-lookDir.x, 0, -lookDir.z);
            this.backDir = horiz.lengthSquared() > 0.001 ? horiz.normalize() : new Vec3d(0, 0, 1);
        }
    }

    private static final List<RiftCollapseSession> ACTIVE_SESSIONS = new ArrayList<>();
    private static final List<RiftTeleportTask> ACTIVE_TELEPORTS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AshenRiftScheduler::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (isFallImmune(player.getUuid(), player.getWorld().getTime())) {
                    if (source.isOf(DamageTypes.FALL) || source.isIn(DamageTypeTags.IS_FALL)) {
                        return false; // Guaranteed built-in fall damage immunity
                    }
                }
            }
            return true;
        });
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
        // Grant fall immunity right away
        grantFallImmunity(player.getUuid(), world.getTime() + 160);
        ACTIVE_TELEPORTS.add(new RiftTeleportTask(
                player.getUuid(),
                world.getRegistryKey(),
                initialPos,
                destPos,
                player.getRotationVec(1.0f),
                player.getYaw(),
                player.getPitch()
        ));
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
                if (player != null) {
                    player.setInvisible(false);
                    player.removeStatusEffect(StatusEffects.INVISIBILITY);
                    player.fallDistance = 0.0f;
                }
                iterator.remove();
                continue;
            }

            task.ticks++;

            // ==============================================================
            // PHASE 1 (Ticks 1-14): Entry Portal & Controlled Backward Fall
            // ==============================================================
            if (task.ticks <= 14) {
                // Ensure no fall damage during transition
                player.fallDistance = 0.0f;
                grantFallImmunity(player.getUuid(), world.getTime() + 100);

                // Controlled backward falling motion into floor rift
                double backSpeed = 0.14 * (1.0 - (task.ticks / 18.0));
                double fallY = -0.42;
                player.setVelocity(task.backDir.x * backSpeed, fallY, task.backDir.z * backSpeed);
                player.velocityModified = true;
                player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

                // Tilt player camera / pitch backward slightly
                player.setPitch(Math.max(-40.0f, player.getPitch() - 2.5f));

                // Portal ring beneath feet at initial position
                double t = task.ticks / 14.0;
                double radius = 0.35 + t * 0.75;
                int count = 16;
                for (int i = 0; i < count; i++) {
                    double angle = 2 * Math.PI * i / count + (task.ticks * 0.3);
                    double px = task.initialPos.x + radius * Math.cos(angle);
                    double pz = task.initialPos.z + radius * Math.sin(angle);
                    double py = task.initialPos.y + 0.05;
                    world.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, -0.05, 0, 0.04);
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, px, py, pz, 1, 0, 0.03, 0, 0.02);
                    if (i % 2 == 0) {
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0.01, 0, 0.01);
                    }
                    if (i % 3 == 0) {
                        world.spawnParticles(ParticleTypes.ASH, px, py + 0.1, pz, 1, 0.02, 0.04, 0.02, 0.01);
                    }
                }
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, task.initialPos.x, task.initialPos.y + 0.1, task.initialPos.z,
                        2, 0.1, 0.1, 0.1, 0.02);

                if (task.ticks == 8) {
                    world.playSound(null, task.initialPos.x, task.initialPos.y, task.initialPos.z,
                            SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.PLAYERS, 0.8f, 1.4f);
                }
            }
            // ==============================================================
            // PHASE 2 (Ticks 15-24): Disappearance & Destination Portal Opening
            // ==============================================================
            else if (task.ticks <= 24) {
                player.fallDistance = 0.0f;
                grantFallImmunity(player.getUuid(), world.getTime() + 100);

                // Entry portal snaps shut / collapses on tick 15
                if (task.ticks == 15) {
                    world.playSound(null, task.initialPos.x, task.initialPos.y, task.initialPos.z,
                            SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    world.playSound(null, task.initialPos.x, task.initialPos.y, task.initialPos.z,
                            SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, task.initialPos.x, task.initialPos.y + 0.2, task.initialPos.z,
                            35, 0.35, 0.15, 0.35, 0.1);
                    world.spawnParticles(ParticleTypes.ASH, task.initialPos.x, task.initialPos.y + 0.2, task.initialPos.z,
                            25, 0.25, 0.1, 0.25, 0.05);

                    // Player disappears into the portal
                    player.setInvisible(true);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, false, false, false));
                }

                // Suspend player movement while in rift transit
                player.setVelocity(0, 0, 0);
                player.velocityModified = true;
                player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

                // Destination portal begins opening 5 blocks above aimed block
                double prog = (task.ticks - 14) / 10.0;
                double portalRadius = 1.0 * prog;
                int pCount = 20;
                for (int i = 0; i < pCount; i++) {
                    double angle = 2 * Math.PI * i / pCount + (task.ticks * 0.25);
                    double px = task.destPos.x + portalRadius * Math.cos(angle);
                    double pz = task.destPos.z + portalRadius * Math.sin(angle);
                    double py = task.destPos.y + 1.2;
                    world.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0, -0.15, 0, 0.05);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, -0.05, 0, 0.02);
                    world.spawnParticles(ParticleTypes.ASH, px, py + 0.1, pz, 1, 0.02, -0.05, 0.02, 0.01);
                }

                if (task.ticks == 16) {
                    world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                            SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0f, 1.1f);
                    world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                            SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.9f, 1.2f);
                }
            }
            // ==============================================================
            // PHASE 3 (Tick 25): Emergence & Downward Exit Falling
            // ==============================================================
            else if (task.ticks == 25) {
                // Teleport player 5 blocks above destination
                player.teleport(world, task.destPos.x, task.destPos.y, task.destPos.z, task.initialYaw, task.initialPitch);
                player.setInvisible(false);
                player.removeStatusEffect(StatusEffects.INVISIBILITY);
                player.fallDistance = 0.0f;
                task.teleported = true;
                grantFallImmunity(player.getUuid(), world.getTime() + 100);

                // Exit portal sounds
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.2f, 1.3f);
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.8f);
                world.playSound(null, task.destPos.x, task.destPos.y, task.destPos.z,
                        SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.9f, 0.9f);

                // Exit portal burst
                for (int i = 0; i < 36; i++) {
                    double angle = 2 * Math.PI * i / 36.0;
                    double rx = 1.0 * Math.cos(angle);
                    double rz = 1.0 * Math.sin(angle);
                    world.spawnParticles(ParticleTypes.PORTAL, task.destPos.x + rx, task.destPos.y + 1.6, task.destPos.z + rz,
                            2, 0, -0.3, 0, 0.1);
                    world.spawnParticles(ParticleTypes.REVERSE_PORTAL, task.destPos.x + rx * 0.8, task.destPos.y + 1.4, task.destPos.z + rz * 0.8,
                            2, 0, -0.2, 0, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, task.destPos.x + rx * 0.5, task.destPos.y + 1.5, task.destPos.z + rz * 0.5,
                            1, 0, -0.15, 0, 0.03);
                    world.spawnParticles(ParticleTypes.DRAGON_BREATH, task.destPos.x + rx * 0.4, task.destPos.y + 1.3, task.destPos.z + rz * 0.4,
                            1, 0, -0.1, 0, 0.02);
                }

                // Trigger weapon emerging/falling animation
                ItemStack mainStack = player.getMainHandStack();
                if (mainStack.getItem() instanceof AshenRiftBladeItem bladeItem) {
                    bladeItem.triggerAnim(player, GeoItem.getOrAssignId(mainStack, world), "controller", "teleport_exit");
                }

                // Downward velocity falling out of portal
                player.setVelocity(0, -0.65, 0);
                player.velocityModified = true;
                player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            }
            // ==============================================================
            // PHASE 4 (Ticks 26+): Falling Descent & Safe Landing
            // ==============================================================
            else {
                player.fallDistance = 0.0f;
                grantFallImmunity(player.getUuid(), world.getTime() + 60);

                if (!task.landed) {
                    // Trailing rift particles as player falls towards ground
                    world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.2, player.getZ(),
                            6, 0.3, 0.25, 0.3, 0.04);
                    world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.6, player.getZ(),
                            4, 0.2, 0.2, 0.2, 0.02);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.8, player.getZ(),
                            2, 0.15, 0.15, 0.15, 0.01);

                    // Landing check
                    if (player.isOnGround() || task.ticks >= 55) {
                        task.landed = true;
                        player.fallDistance = 0.0f;
                        grantFallImmunity(player.getUuid(), world.getTime() + 60);

                        // Safe landing visual impact
                        for (int i = 0; i < 20; i++) {
                            double angle = 2 * Math.PI * i / 20.0;
                            double px = player.getX() + 0.75 * Math.cos(angle);
                            double pz = player.getZ() + 0.75 * Math.sin(angle);
                            world.spawnParticles(ParticleTypes.ASH, px, player.getY() + 0.1, pz,
                                    2, 0.1, 0.05, 0.1, 0.03);
                            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, player.getY() + 0.1, pz,
                                    1, 0.05, 0.02, 0.05, 0.01);
                            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, player.getY() + 0.1, pz,
                                    1, 0.02, 0.04, 0.02, 0.02);
                        }

                        // Safe landing audio feedback
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 0.7f, 1.8f);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_STONE_STEP, SoundCategory.PLAYERS, 1.0f, 0.8f);

                        // Trigger weapon recovery animation
                        ItemStack mainStack = player.getMainHandStack();
                        if (mainStack.getItem() instanceof AshenRiftBladeItem bladeItem) {
                            bladeItem.triggerAnim(player, GeoItem.getOrAssignId(mainStack, world), "controller", "teleport_recover");
                        }
                    }
                } else {
                    task.postLandTicks++;
                    if (task.postLandTicks >= 6) {
                        player.fallDistance = 0.0f;
                        iterator.remove();
                    }
                }
            }
        }
    }
}
