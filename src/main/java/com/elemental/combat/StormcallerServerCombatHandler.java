package com.elemental.combat;

import com.elemental.item.StormcallerHammerItem;
import com.elemental.network.StormcallerNetworking;
import com.elemental.util.StormcallerLightningScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative combat handler for the Stormcaller Hammer.
 * Synchronizes animation windows, hit checks, lightning timing, ground smash AOE,
 * knockback, and network broadcasts matching Blockbench animation timings.
 */
public class StormcallerServerCombatHandler {

    // ==========================================
    // DETERMINISTIC TIMING CONSTANTS (TICKS)
    // 20 ticks = 1.0 second
    // ==========================================

    /** Duration of melee swing: 0.7s = 14 ticks */
    public static final int MELEE_SWING_DURATION_TICKS = 14;
    /** Start of melee damage window: 0.30s = 6 ticks */
    public static final int MELEE_HIT_START_TICK = 6;
    /** Exact keyframe event marker "melee_hit": 0.35s = 7 ticks */
    public static final int MELEE_HIT_TICK = 7;
    /** End of melee damage window: 0.45s = 9 ticks */
    public static final int MELEE_HIT_END_TICK = 9;

    /** Duration of lightning release animation: 0.6s = 12 ticks */
    public static final int LIGHTNING_RELEASE_DURATION_TICKS = 12;
    /** Exact keyframe event marker "lightning_strike": 0.25s = 5 ticks (midpoint of downward motion) */
    public static final int LIGHTNING_STRIKE_TICK = 5;

    /** Duration of two-handed ground smash animation: 1.25s = 25 ticks */
    public static final int GROUND_SMASH_DURATION_TICKS = 25;
    /** Exact keyframe event marker "ground_impact": 0.80s = 16 ticks (impact frame) */
    public static final int GROUND_IMPACT_TICK = 16;

    public enum AttackType {
        MELEE,
        LIGHTNING_RELEASE,
        GROUND_SMASH
    }

    public static class ActiveAttack {
        public final AttackType type;
        public int currentTick;
        public final int maxTicks;
        public boolean hasTriggeredImpact;
        public int targetEntityId = -1;
        public float chargeProgress = 1.0f;
        public final Set<UUID> damagedEntities = new HashSet<>();

        public ActiveAttack(AttackType type, int maxTicks) {
            this.type = type;
            this.currentTick = 0;
            this.maxTicks = maxTicks;
            this.hasTriggeredImpact = false;
        }
    }

    private static final Map<UUID, ActiveAttack> ACTIVE_ATTACKS = new ConcurrentHashMap<>();
    private static final Set<UUID> AIMING_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StormcallerServerCombatHandler::tick);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (ACTIVE_ATTACKS.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveAttack>> it = ACTIVE_ATTACKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveAttack> entry = it.next();
            UUID playerUuid = entry.getKey();
            ActiveAttack attack = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);

            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            ItemStack stack = player.getMainHandStack();
            if (!(stack.getItem() instanceof StormcallerHammerItem)) {
                stack = player.getOffHandStack();
                if (!(stack.getItem() instanceof StormcallerHammerItem)) {
                    it.remove();
                    continue;
                }
            }

            attack.currentTick++;
            ServerWorld world = player.getServerWorld();

            switch (attack.type) {
                case MELEE -> handleMeleeTick(world, player, stack, attack);
                case LIGHTNING_RELEASE -> handleLightningReleaseTick(world, player, stack, attack);
                case GROUND_SMASH -> handleGroundSmashTick(world, player, stack, attack);
            }

            if (attack.currentTick >= attack.maxTicks) {
                it.remove();
            }
        }
    }

    public static boolean isAttackActive(PlayerEntity player) {
        return ACTIVE_ATTACKS.containsKey(player.getUuid());
    }

    // ==========================================
    // 1. MELEE ATTACK
    // ==========================================

    public static boolean startMeleeSwing(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof StormcallerHammerItem)) return false;

        ServerWorld world = player.getServerWorld();
        if (StormcallerHammerItem.isCoolingDown(world, stack)) {
            // Can still swing with reduced damage
        }

        if (isAttackActive(player)) return false;

        ActiveAttack attack = new ActiveAttack(AttackType.MELEE, MELEE_SWING_DURATION_TICKS);
        ACTIVE_ATTACKS.put(player.getUuid(), attack);

        // Broadcast player animation to client & tracking players
        StormcallerNetworking.broadcastPlayAnimation(player, "animation.stormcaller_hammer.melee_swing");

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 0.8f);

        return true;
    }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        if (player != null) {
            ACTIVE_ATTACKS.remove(player.getUuid());
            AIMING_PLAYERS.remove(player.getUuid());
        }
    }

    public static boolean isAiming(ServerPlayerEntity player) {
        return player != null && AIMING_PLAYERS.contains(player.getUuid());
    }

    public static ActiveAttack getActiveAttack(ServerPlayerEntity player) {
        return player != null ? ACTIVE_ATTACKS.get(player.getUuid()) : null;
    }

    private static void handleMeleeTick(ServerWorld world, ServerPlayerEntity player, ItemStack stack, ActiveAttack attack) {
        // Active damage window: ticks 6 to 9 (0.30s to 0.45s, centered on keyframe tick 7)
        if (attack.currentTick >= MELEE_HIT_START_TICK && attack.currentTick <= MELEE_HIT_END_TICK) {
            Vec3d eyePos = player.getEyePos();
            Vec3d lookDir = player.getRotationVec(1.0f);
            double reach = 4.25;
            Vec3d sweepCenter = eyePos.add(lookDir.multiply(reach * 0.5));

            Box sweepBox = new Box(sweepCenter.x - 2.0, sweepCenter.y - 1.5, sweepCenter.z - 2.0,
                    sweepCenter.x + 2.0, sweepCenter.y + 1.5, sweepCenter.z + 2.0);

            List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, sweepBox, e ->
                    e != player && e.isAlive() && !e.isSpectator() && !attack.damagedEntities.contains(e.getUuid())
            );

            for (LivingEntity target : targets) {
                if (eyePos.distanceTo(target.getEyePos()) > reach && player.distanceTo(target) > reach) {
                    continue;
                }
                Vec3d toTarget = target.getEyePos().subtract(eyePos).normalize();
                double dot = lookDir.dotProduct(toTarget);
                // Horizontal sweep arc in front of player
                if (dot > 0.3) {
                    attack.damagedEntities.add(target.getUuid());

                    boolean onCooldown = StormcallerHammerItem.isCoolingDown(world, stack);
                    float damage = onCooldown ? 5.0f : 10.0f;

                    target.damage(world.getDamageSources().playerAttack(player), damage);

                    // Heavy hammer knockback
                    double kbStrength = StormcallerHammerItem.BONUS_KNOCKBACK;
                    Vec3d kbVec = lookDir.multiply(kbStrength * 0.5).add(0, 0.35, 0);
                    target.addVelocity(kbVec.x, kbVec.y, kbVec.z);
                    target.velocityModified = true;
                    if (target instanceof ServerPlayerEntity targetPlayer) {
                        targetPlayer.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(targetPlayer));
                    }

                    // Effects
                    world.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.7f, 1.3f);
                    world.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.4f, 1.8f);
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(),
                            10, 0.25, 0.3, 0.25, 0.1);
                    world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.8, target.getZ(),
                            8, 0.3, 0.2, 0.3, 0.05);
                }
            }
        }
    }

    // ==========================================
    // 2. LIGHTNING AIM & RELEASE
    // ==========================================

    public static void startLightningAim(ServerPlayerEntity player, boolean aiming) {
        if (player == null || !player.isAlive()) return;

        if (aiming) {
            AIMING_PLAYERS.add(player.getUuid());
            StormcallerNetworking.broadcastPlayAnimation(player, "animation.stormcaller_hammer.lightning_aim_idle");
        } else {
            AIMING_PLAYERS.remove(player.getUuid());
            if (!ACTIVE_ATTACKS.containsKey(player.getUuid())) {
                StormcallerNetworking.broadcastPlayAnimation(player, "stop");
            }
        }
    }

    public static boolean releaseLightningStrike(ServerPlayerEntity player, int targetEntityId, float chargeProgress) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof StormcallerHammerItem)) return false;

        AIMING_PLAYERS.remove(player.getUuid());

        ServerWorld world = player.getServerWorld();

        // Validate or resolve target
        LivingEntity target = null;
        if (targetEntityId != -1) {
            Entity ent = world.getEntityById(targetEntityId);
            if (ent instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                target = living;
            }
        }
        if (target == null) {
            target = StormcallerHammerItem.findTargetedMob(player, 36.0);
        }

        // If no target, cancel safely without starting attack
        if (target == null || !target.isAlive()) {
            player.sendMessage(Text.literal("§7⚡ No valid mob targeted — attack cancelled.").formatted(Formatting.GRAY), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.5f);
            StormcallerNetworking.broadcastPlayAnimation(player, "stop");
            return false;
        }

        ActiveAttack attack = new ActiveAttack(AttackType.LIGHTNING_RELEASE, LIGHTNING_RELEASE_DURATION_TICKS);
        attack.targetEntityId = target.getId();
        attack.chargeProgress = chargeProgress;
        ACTIVE_ATTACKS.put(player.getUuid(), attack);

        // Broadcast release animation
        StormcallerNetworking.broadcastPlayAnimation(player, "animation.stormcaller_hammer.lightning_release");

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.2f);

        return true;
    }

    private static void handleLightningReleaseTick(ServerWorld world, ServerPlayerEntity player, ItemStack stack, ActiveAttack attack) {
        // Exact keyframe strike tick: tick 5 (0.25s, midpoint of downward hammer swing)
        if (attack.currentTick == LIGHTNING_STRIKE_TICK && !attack.hasTriggeredImpact) {
            attack.hasTriggeredImpact = true;

            Entity ent = world.getEntityById(attack.targetEntityId);
            LivingEntity target = null;
            if (ent instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                target = living;
            } else {
                target = StormcallerHammerItem.findTargetedMob(player, 36.0);
            }

            if (target != null && target.isAlive()) {
                float charge = attack.chargeProgress;
                int strikeCount;
                if (charge < 0.25f) {
                    strikeCount = 1;
                } else {
                    int maxStrikes = 15 + world.random.nextInt(6); // 15 to 20
                    strikeCount = Math.max(1, Math.round(1 + (maxStrikes - 1) * charge));
                }

                boolean onCooldown = StormcallerHammerItem.isCoolingDown(world, stack);
                float damagePerStrike;
                if (strikeCount == 1) {
                    damagePerStrike = 14.0f;
                } else {
                    // Total damage scales with charge: 22.0f at low charge up to 36.0f at full charge
                    // Deals enough damage to kill a zombie (20 HP) or equivalent in one charge attack
                    float totalDamage = 22.0f + (14.0f * charge);
                    damagePerStrike = totalDamage / strikeCount;
                }
                if (onCooldown) {
                    damagePerStrike *= 0.55f;
                }

                int intervalTicks = (strikeCount > 10) ? 1 : 2;
                StormcallerLightningScheduler.scheduleBarrage(world, player, target, strikeCount, damagePerStrike, intervalTicks);

                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.2f, 1.0f);

                // Apply cooldown and tool damage
                StormcallerHammerItem.applyCooldown(world, player, stack, StormcallerHammerItem.COOLDOWN_TICKS);
                stack.damage(2, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));

                if (onCooldown) {
                    player.sendMessage(Text.literal("§6⚡ Charged Lightning Strike! §c(Reduced damage during cooldown)").formatted(Formatting.YELLOW), true);
                } else {
                    player.sendMessage(Text.literal("§6⚡ Charged Lightning Strike unleashed! (" + strikeCount + " strikes)").formatted(Formatting.GOLD), true);
                }
            }
        }
    }

    // ==========================================
    // 3. GROUND SMASH
    // ==========================================

    public static boolean startGroundSmash(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof StormcallerHammerItem)) {
            stack = player.getOffHandStack();
            if (!(stack.getItem() instanceof StormcallerHammerItem)) return false;
        }

        ServerWorld world = player.getServerWorld();

        if (StormcallerHammerItem.isCoolingDown(world, stack)) {
            int remainingSec = StormcallerHammerItem.getRemainingCooldownSeconds(world, stack);
            player.sendMessage(Text.literal("§c⚡ Ground Smash cooling down! (" + remainingSec + "s remaining)").formatted(Formatting.RED), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.4f);
            return false;
        }

        if (isAttackActive(player)) return false;

        ActiveAttack attack = new ActiveAttack(AttackType.GROUND_SMASH, GROUND_SMASH_DURATION_TICKS);
        ACTIVE_ATTACKS.put(player.getUuid(), attack);

        // Broadcast player animation
        StormcallerNetworking.broadcastPlayAnimation(player, "animation.stormcaller_hammer.ground_smash");

        // Wind-up audio cue
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 0.9f, 0.6f);

        return true;
    }

    private static void handleGroundSmashTick(ServerWorld world, ServerPlayerEntity player, ItemStack stack, ActiveAttack attack) {
        // Ground impact frame: tick 16 (0.80s, exactly when hammer contacts the floor)
        if (attack.currentTick == GROUND_IMPACT_TICK && !attack.hasTriggeredImpact) {
            attack.hasTriggeredImpact = true;

            // Broadcast ground impact packet for client screenshake / camera downward snap
            StormcallerNetworking.broadcastGroundImpact(world, player.getX(), player.getY(), player.getZ());

            // Heavy sound impact
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4f, 0.6f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.2f, 0.8f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.2f, 0.5f);

            // Impact particles (shockwave ring)
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(),
                    70, 2.5, 0.2, 2.5, 0.03);
            world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(),
                    50, 2.2, 0.1, 2.2, 0.04);
            world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.3, player.getZ(),
                    70, 2.5, 0.3, 2.5, 0.08);
            world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.6, 0.2, 0.6, 0.0);
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 0.5, player.getZ(),
                    1, 0, 0, 0, 0);

            // Area of effect damage and knockback in 5-block radius
            double radius = 5.0;
            Box box = new Box(player.getX() - radius, player.getY() - 2.0, player.getZ() - radius,
                    player.getX() + radius, player.getY() + 3.0, player.getZ() + radius);

            List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e ->
                    e != player && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(player) <= (radius * radius)
            );

            for (LivingEntity target : targets) {
                boolean heavy = StormcallerHammerItem.isHeavyMob(target);
                double vy = heavy ? 0.45 : 0.95;

                Vec3d diff = target.getPos().subtract(player.getPos());
                Vec3d horiz = new Vec3d(diff.x, 0, diff.z);
                double vx = 0;
                double vz = 0;
                if (horiz.lengthSquared() > 0.001) {
                    Vec3d norm = horiz.normalize().multiply(heavy ? 0.30 : 0.65);
                    vx = norm.x;
                    vz = norm.z;
                }

                target.setVelocity(vx, vy, vz);
                target.velocityModified = true;
                if (target instanceof ServerPlayerEntity targetPlayer) {
                    targetPlayer.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(targetPlayer));
                }

                // Impact damage: applied ONLY at ground impact tick
                float damage = (target instanceof PlayerEntity) ? 6.0f : 12.0f;
                target.damage(world.getDamageSources().playerAttack(player), damage);
                world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.5, target.getZ(),
                        12, 0.2, 0.3, 0.2, 0.1);
            }

            // Apply 15s cooldown and durability loss
            StormcallerHammerItem.applyCooldown(world, player, stack, StormcallerHammerItem.COOLDOWN_TICKS);
            stack.damage(2, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
            player.sendMessage(Text.literal("§6⚡ Ground Smash! §cStormcaller cooling down (15s)").formatted(Formatting.GOLD), true);
        }
    }
}
