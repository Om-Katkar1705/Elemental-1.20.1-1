package com.elemental.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sir Solvane, the Everliving Knight
 * ID: elemental:everliving_knight
 */
public class EverlivingKnightEntity extends HostileEntity implements GeoEntity {

    // ==================== BASE ATTRIBUTES ====================
    public static final float MAX_HEALTH = 250.0f; // Tuned so normal weapons feel impactful
    public static final float BASE_ATTACK_DAMAGE = 14.0f;
    public static final float ENRAGED_ATTACK_DAMAGE = 21.0f;
    public static final int BASE_ARMOR = 8;
    public static final float BASE_ARMOR_TOUGHNESS = 2.0f;
    public static final double BASE_KNOCKBACK_RESISTANCE = 0.9;
    public static final double BASE_MOVEMENT_SPEED = 0.25;
    public static final double FOLLOW_RANGE = 48.0;

    // Initial AI Chase Speeds (authentic specifications)
    public static final double CHASE_SPEED_NORMAL = 1.35;
    public static final double CHASE_SPEED_PHASE3 = 1.60;

    // Regeneration
    public static final float BASE_REGEN_PER_SECOND = 5.0f; // 2% max HP per second (5 HP/s)
    public static final float BASE_REGEN_PER_TICK = BASE_REGEN_PER_SECOND / 20.0f; // 0.25 HP/tick
    public static final float THORN_CAGE_REGEN_MULTIPLIER = 0.40f; // 2.0 HP/s
    public static final int FROSTBURST_LOCK_TICKS = 300; // 15 seconds
    public static final int THORN_CAGE_DURATION_TICKS = 100; // 5 seconds

    // 3 Cores System (100 -> 66 -> 32 -> 0)
    public static final int MAX_CORE_INTEGRITY = 100;
    public static final int CORE_DAMAGE_PER_THORN_CAGE = 34; // 3 hits break the 3 cores
    public static final int CORE_DAMAGE_PER_EXPOSED_HIT = 34; // 1 Rootbound Axe strike during core exposure destroys a core

    // Core Exposure & Stagger
    public static final int CORE_EXPOSURE_DURATION = 90; // 3 seconds exposure after Core Pulse
    public static final int STAGGER_DURATION = 100; // 5 seconds vulnerability after all cores break
    public static final int ENRAGE_COUNTDOWN_TICKS = 600; // 30 seconds

    // Arena boundary (10 block radius center fighting zone)
    public static final double ARENA_RADIUS = 10.0;

    // ==================== ATTACK ENUMS ====================
    public enum AttackType {
        NONE,
        SLASH,
        SHIELD_BASH,
        CHARGE,
        CORE_PULSE
    }

    public enum AttackPhase {
        IDLE,
        WINDUP,
        ACTIVE,
        RECOVERY
    }

    // ==================== DATA TRACKERS ====================
    private static final TrackedData<Integer> DATA_PHASE =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DATA_CORE_INTEGRITY =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DATA_CORE_BROKEN =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DATA_REGEN_FROZEN_TICKS =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> DATA_REGEN_MULTIPLIER =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> DATA_STAGGERED =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DATA_ENRAGE_TIMER =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DATA_ENRAGED =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> DATA_CORE_EXPOSED_ACTIVE =
            DataTracker.registerData(EverlivingKnightEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ==================== GECKOLIB ANIMATIONS ====================
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.everliving_knight.idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.everliving_knight.walk");
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("animation.everliving_knight.run");
    private static final RawAnimation STAGGER_LOOP = RawAnimation.begin().thenLoop("animation.everliving_knight.stagger");
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("animation.everliving_knight.death");
    private static final RawAnimation CORE_EXPOSED_ANIM = RawAnimation.begin().thenPlay("animation.everliving_knight.core_exposed");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossBar bossBar;

    // ==================== STATE FIELDS ====================
    private BlockPos arenaCenter = null;

    // Regeneration & Status Timers
    private int frostFrozenTicks = 0;
    private int thornCagedTicks = 0;
    private int staggerTicks = 0;
    private int coreExposureTicks = 0;
    private int enrageCountdown = ENRAGE_COUNTDOWN_TICKS;
    private boolean enrageStarted = false;

    // Initial Attack State Machine
    private AttackType currentAttack = AttackType.NONE;
    private AttackPhase currentAttackPhase = AttackPhase.IDLE;
    private int attackTimer = 0;
    private final Set<UUID> activeDamagedEntities = new HashSet<>();

    // Initial Attack Cooldowns (authentic mechanics)
    private int slashCooldown = 0;
    private int shieldBashCooldown = 0;
    private int chargeCooldown = 0;
    private int corePulseCooldown = 0;

    // Charge trajectory lock
    private Vec3d chargeTargetPos = null;
    private Vec3d chargeDirection = null;

    // Healing Flames visual effect counter
    private int flameParticleTick = 0;

    // Cached BossBar state to prevent flooding network packets every tick
    private String lastBossBarName = null;
    private BossBar.Color lastBossBarColor = null;
    private float lastBossBarPercent = -1.0f;

    public EverlivingKnightEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 5000;
        this.bossBar = new ServerBossBar(
                Text.literal("Sir Solvane, the Everliving Knight"),
                BossBar.Color.RED,
                BossBar.Style.PROGRESS
        );
        this.setStepHeight(1.0f);
    }

    public static DefaultAttributeContainer.Builder createKnightAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
                .add(EntityAttributes.GENERIC_ARMOR, BASE_ARMOR)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, BASE_ARMOR_TOUGHNESS)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new KnightMeleeSlashGoal(this));
        this.goalSelector.add(3, new KnightShieldBashGoal(this));
        this.goalSelector.add(4, new KnightChargeGoal(this));
        this.goalSelector.add(5, new KnightCorePulseGoal(this));
        this.goalSelector.add(6, new KnightChaseTargetGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8, 40));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));

        this.targetSelector.add(1, new KnightTargetGoal(this));
        this.targetSelector.add(2, new RevengeGoal(this));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(DATA_PHASE, 1);
        this.dataTracker.startTracking(DATA_CORE_INTEGRITY, MAX_CORE_INTEGRITY);
        this.dataTracker.startTracking(DATA_CORE_BROKEN, false);
        this.dataTracker.startTracking(DATA_REGEN_FROZEN_TICKS, 0);
        this.dataTracker.startTracking(DATA_REGEN_MULTIPLIER, 1.0f);
        this.dataTracker.startTracking(DATA_STAGGERED, false);
        this.dataTracker.startTracking(DATA_ENRAGE_TIMER, 0);
        this.dataTracker.startTracking(DATA_ENRAGED, false);
        this.dataTracker.startTracking(DATA_CORE_EXPOSED_ACTIVE, false);
    }

    // ==================== GECKOLIB CONTROLLERS ====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, state -> {
            if (this.isDead()) {
                return state.setAndContinue(DEATH_ANIM);
            }
            if (this.isStaggered()) {
                return state.setAndContinue(STAGGER_LOOP);
            }
            if (this.isCoreExposed()) {
                return state.setAndContinue(CORE_EXPOSED_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(this.isEnraged() || this.getPhase() == 3 ? RUN_ANIM : WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        })
        .triggerableAnim("idle", IDLE_ANIM)
        .triggerableAnim("slash_windup", RawAnimation.begin().thenPlay("animation.everliving_knight.slash_windup"))
        .triggerableAnim("slash", RawAnimation.begin().thenPlay("animation.everliving_knight.slash"))
        .triggerableAnim("combo", RawAnimation.begin().thenPlay("animation.everliving_knight.combo"))
        .triggerableAnim("shield_bash_windup", RawAnimation.begin().thenPlay("animation.everliving_knight.shield_bash_windup"))
        .triggerableAnim("shield_bash", RawAnimation.begin().thenPlay("animation.everliving_knight.shield_bash"))
        .triggerableAnim("charge_windup", RawAnimation.begin().thenPlay("animation.everliving_knight.charge_windup"))
        .triggerableAnim("charge", RawAnimation.begin().thenPlay("animation.everliving_knight.charge"))
        .triggerableAnim("core_pulse_windup", RawAnimation.begin().thenPlay("animation.everliving_knight.core_pulse_windup"))
        .triggerableAnim("core_pulse", RawAnimation.begin().thenPlay("animation.everliving_knight.core_pulse"))
        .triggerableAnim("core_exposed", CORE_EXPOSED_ANIM)
        .triggerableAnim("stagger", RawAnimation.begin().thenPlay("animation.everliving_knight.stagger"))
        .triggerableAnim("phase_two", RawAnimation.begin().thenPlay("animation.everliving_knight.phase_two"))
        .triggerableAnim("phase_three", RawAnimation.begin().thenPlay("animation.everliving_knight.phase_three"))
        .triggerableAnim("enrage", RawAnimation.begin().thenPlay("animation.everliving_knight.enrage"))
        .triggerableAnim("death", RawAnimation.begin().thenPlay("animation.everliving_knight.death"))
        .triggerableAnim("frostburst_reaction", RawAnimation.begin().thenPlay("animation.everliving_knight.frostburst_reaction"))
        .triggerableAnim("thorn_cage_reaction", RawAnimation.begin().thenPlay("animation.everliving_knight.thorn_cage_reaction"))
        .triggerableAnim("solar_arc_reaction", RawAnimation.begin().thenPlay("animation.everliving_knight.solar_arc_reaction")));
    }

    public void triggerAnimation(String animName) {
        if (!this.getWorld().isClient()) {
            this.triggerAnim("controller", animName);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ==================== GETTERS & SETTERS ====================
    public int getPhase() {
        return this.dataTracker.get(DATA_PHASE);
    }

    public void setPhase(int phase) {
        this.dataTracker.set(DATA_PHASE, phase);
    }

    public int getCoreIntegrity() {
        return this.dataTracker.get(DATA_CORE_INTEGRITY);
    }

    public void setCoreIntegrity(int integrity) {
        this.dataTracker.set(DATA_CORE_INTEGRITY, MathHelper.clamp(integrity, 0, MAX_CORE_INTEGRITY));
    }

    public boolean isCoreBroken() {
        return this.dataTracker.get(DATA_CORE_BROKEN);
    }

    public void setCoreBroken(boolean broken) {
        this.dataTracker.set(DATA_CORE_BROKEN, broken);
    }

    public boolean isStaggered() {
        return this.dataTracker.get(DATA_STAGGERED);
    }

    public boolean isCoreExposed() {
        return this.dataTracker.get(DATA_CORE_EXPOSED_ACTIVE) || (isCoreBroken() && isStaggered());
    }

    public boolean isEnraged() {
        return this.dataTracker.get(DATA_ENRAGED);
    }

    public void setEnraged(boolean enraged) {
        this.dataTracker.set(DATA_ENRAGED, enraged);
        if (enraged) {
            this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(ENRAGED_ATTACK_DAMAGE);
        }
    }

    public int getEnrageTimer() {
        return this.dataTracker.get(DATA_ENRAGE_TIMER);
    }

    public BlockPos getArenaCenter() {
        return this.arenaCenter;
    }

    public void setArenaCenter(BlockPos center) {
        this.arenaCenter = center;
    }

    public boolean isBusy() {
        return this.currentAttackPhase != AttackPhase.IDLE || this.isStaggered() || this.coreExposureTicks > 0;
    }

    public AttackType getCurrentAttack() {
        return this.currentAttack;
    }

    public AttackPhase getCurrentAttackPhase() {
        return this.currentAttackPhase;
    }

    public int getSlashCooldown() {
        return this.slashCooldown;
    }

    public int getShieldBashCooldown() {
        return this.shieldBashCooldown;
    }

    public int getChargeCooldown() {
        return this.chargeCooldown;
    }

    public int getCorePulseCooldown() {
        return this.corePulseCooldown;
    }

    // ==================== ARENA BOUNDARY ====================
    private void enforceArenaBoundary() {
        if (this.arenaCenter == null) {
            this.arenaCenter = this.getBlockPos();
            return;
        }
        if (this.getWorld().isClient()) return;

        double distSq = this.squaredDistanceTo(Vec3d.ofCenter(this.arenaCenter));
        if (distSq > ARENA_RADIUS * ARENA_RADIUS) {
            Vec3d toCenter = Vec3d.ofCenter(this.arenaCenter).subtract(this.getPos()).normalize();
            this.setVelocity(this.getVelocity().add(toCenter.multiply(0.12)));
            this.velocityModified = true;
        }
    }

    // ==================== REGENERATION SYSTEM ====================
    private void tickRegeneration() {
        if (this.getWorld().isClient()) return;

        // Decrement frozen ticks
        if (this.frostFrozenTicks > 0) {
            this.frostFrozenTicks--;
            this.dataTracker.set(DATA_REGEN_FROZEN_TICKS, this.frostFrozenTicks);
        }

        // Decrement thorn cage ticks
        if (this.thornCagedTicks > 0) {
            this.thornCagedTicks--;
        }

        // Calculate regeneration multiplier
        float multiplier;
        if (isCoreBroken()) {
            multiplier = 0.0f;
        } else if (this.frostFrozenTicks > 0) {
            multiplier = 0.0f;
        } else if (this.thornCagedTicks > 0) {
            multiplier = THORN_CAGE_REGEN_MULTIPLIER;
        } else {
            multiplier = 1.0f;
        }
        this.dataTracker.set(DATA_REGEN_MULTIPLIER, multiplier);

        // Apply regeneration (2% max HP/s = 5 HP/s = 0.25 HP/tick)
        if (multiplier > 0.0f && this.getHealth() < this.getMaxHealth()) {
            float healAmount = BASE_REGEN_PER_TICK * multiplier;
            // Phase 2 Healing Flames bonus (+2.5 HP/s if flames active)
            if (getPhase() == 2 && !isCoreBroken()) {
                healAmount += (2.5f / 20.0f) * multiplier;
            }
            this.heal(healAmount);
        }

        // Handle staggered countdown
        if (this.staggerTicks > 0) {
            this.staggerTicks--;
            if (this.staggerTicks <= 0) {
                this.dataTracker.set(DATA_STAGGERED, false);
            }
        }

        // Handle Core Exposure countdown
        if (this.coreExposureTicks > 0) {
            this.coreExposureTicks--;
            this.getNavigation().stop();
            this.setVelocity(0, this.getVelocity().y, 0);

            if (this.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.4, this.getZ(),
                        2, 0.25, 0.25, 0.25, 0.03);
                if (this.coreExposureTicks % 8 == 0) {
                    world.spawnParticles(ParticleTypes.GLOW, this.getX(), this.getY() + 1.3, this.getZ(),
                            6, 0.35, 0.35, 0.35, 0.05);
                }
            }

            if (this.coreExposureTicks <= 0) {
                this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, false);
            }
        }
    }

    // ==================== CORE EXPOSURE & DAMAGE ====================
    /**
     * Exposes the radiant core for a given duration, opening a window to destroy it.
     */
    public void startCoreExposure(int duration) {
        if (this.getWorld().isClient() || isCoreBroken()) return;

        this.coreExposureTicks = duration;
        this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, true);
        this.getNavigation().stop();
        this.setVelocity(0, this.getVelocity().y, 0);

        triggerAnimation("core_exposed");

        if (this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.2f, 0.8f);
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.0f, 1.1f);
            world.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.4, this.getZ(),
                    2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.4, this.getZ(),
                    30, 0.5, 0.5, 0.5, 0.08);
        }
    }

    /**
     * Damages the radiant core integrity and triggers visual/sound feedback.
     */
    public void damageCore(int amount, PlayerEntity player) {
        if (isCoreBroken()) return;

        int newIntegrity = Math.max(0, getCoreIntegrity() - amount);
        setCoreIntegrity(newIntegrity);

        if (this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2f, 1.3f);
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.HOSTILE, 1.5f, 0.85f);
            world.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.5, this.getZ(),
                    35, 0.4, 0.4, 0.4, 0.15);
            world.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.4, this.getZ(),
                    2, 0.1, 0.1, 0.1, 0.0);
        }

        if (newIntegrity <= 0) {
            breakCore();
        }
    }

    // ==================== ELEMENTAL REACTION API ====================
    /**
     * Applies the Thorn Cage elemental reaction.
     * Slows regeneration to 40% for 100 ticks (5s).
     * If Sir Solvane's radiant core is actively exposed, directly shreds a core (34 integrity).
     * If the core is guarded/unexposed, armor deflects core damage.
     */
    public void applyThornCageReaction() {
        applyThornCageReaction(null);
    }

    public void applyThornCageReaction(PlayerEntity player) {
        if (this.getWorld().isClient()) return;

        this.thornCagedTicks = THORN_CAGE_DURATION_TICKS;
        triggerAnimation("thorn_cage_reaction");

        // The Rootbound Axe only destroys the core when it is actively exposed!
        if (this.coreExposureTicks > 0 && !isCoreBroken()) {
            damageCore(CORE_DAMAGE_PER_THORN_CAGE, player);
            this.coreExposureTicks = 0;
            this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, false);
        } else {
            // Armor deflects core damage
            if (this.getWorld() instanceof ServerWorld world) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.3f, 0.8f);
                world.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.2, this.getZ(),
                        15, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    /**
     * Applies the Frostburst elemental reaction.
     * Freezes regeneration to 0% for 300 ticks (15s) and exposes the core.
     */
    public void applyFrostburstReaction() {
        if (this.getWorld().isClient()) return;

        this.frostFrozenTicks = FROSTBURST_LOCK_TICKS;
        this.dataTracker.set(DATA_REGEN_FROZEN_TICKS, this.frostFrozenTicks);
        triggerAnimation("frostburst_reaction");

        // Freezing exposes the brittle core for 40 ticks
        if (!isCoreBroken()) {
            startCoreExposure(40);
        }
    }

    /**
     * Applies the Solar Arc weapon reaction.
     */
    public void applySolarArcReaction(PlayerEntity player) {
        if (this.getWorld().isClient()) return;

        float magicDmg = 18.0f; // Magic damage
        this.damage(this.getDamageSources().magic(), magicDmg);
        triggerAnimation("solar_arc_reaction");
    }

    /**
     * Breaks all cores permanently, exposing the core, disabling regeneration,
     * staggering the boss, and making him vulnerable to lethal defeat.
     */
    public void breakCore() {
        setCoreBroken(true);
        setCoreIntegrity(0);
        this.frostFrozenTicks = 0;
        this.thornCagedTicks = 0;
        this.coreExposureTicks = 0;
        this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, false);
        this.dataTracker.set(DATA_REGEN_FROZEN_TICKS, 0);
        this.dataTracker.set(DATA_REGEN_MULTIPLIER, 0.0f);

        this.staggerTicks = STAGGER_DURATION;
        this.dataTracker.set(DATA_STAGGERED, true);

        resetAttackState();

        triggerAnimation("core_exposed");
        triggerAnimation("stagger");

        if (this.getWorld() instanceof ServerWorld world) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.HOSTILE, 1.8f, 0.6f);
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 1.2f, 0.8f);
            world.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.5, this.getZ(),
                    50, 0.6, 0.8, 0.6, 0.2);
        }
    }

    // ==================== PHASE SYSTEM ====================
    private void tickPhaseSystem() {
        if (this.getWorld().isClient()) return;

        float hpRatio = this.getHealth() / this.getMaxHealth();
        int current = getPhase();

        if (current == 1 && hpRatio < 0.70f) {
            setPhase(2);
            triggerAnimation("phase_two");
            if (this.getWorld() instanceof ServerWorld world) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.5f, 0.8f);
            }
        } else if (current == 2 && hpRatio < 0.35f) {
            setPhase(3);
            this.enrageStarted = true;
            this.enrageCountdown = ENRAGE_COUNTDOWN_TICKS;
            triggerAnimation("phase_three");
            if (this.getWorld() instanceof ServerWorld world) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 0.75f);
            }
        }

        // Phase 2 Healing Flames visual particles
        if (getPhase() == 2 && !isCoreBroken()) {
            this.flameParticleTick++;
            if (this.flameParticleTick % 5 == 0 && this.getWorld() instanceof ServerWorld world) {
                double angle1 = (this.flameParticleTick * 0.1);
                double angle2 = angle1 + Math.PI;
                double r = 1.8;
                world.spawnParticles(ParticleTypes.FLAME,
                        this.getX() + Math.cos(angle1) * r, this.getY() + 0.8, this.getZ() + Math.sin(angle1) * r,
                        2, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + Math.cos(angle2) * r, this.getY() + 0.8, this.getZ() + Math.sin(angle2) * r,
                        2, 0.05, 0.05, 0.05, 0.01);
            }
        }

        // Phase 3 Enrage timer
        if (getPhase() == 3 && this.enrageStarted && !isEnraged()) {
            this.enrageCountdown--;
            this.dataTracker.set(DATA_ENRAGE_TIMER, this.enrageCountdown);
            if (this.enrageCountdown <= 0) {
                setEnraged(true);
                triggerAnimation("enrage");
                if (this.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.6f);
                    world.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.5, this.getZ(),
                            2, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }
    }

    // ==================== ATTACK STATE MACHINE (INITIAL AI) ====================
    private void tickAttackStateMachine() {
        if (this.getWorld().isClient()) return;

        // Decrement cooldowns
        if (this.slashCooldown > 0) this.slashCooldown--;
        if (this.shieldBashCooldown > 0) this.shieldBashCooldown--;
        if (this.chargeCooldown > 0) this.chargeCooldown--;
        if (this.corePulseCooldown > 0) this.corePulseCooldown--;

        if (this.currentAttack == AttackType.NONE || this.currentAttackPhase == AttackPhase.IDLE) {
            return;
        }

        this.attackTimer--;

        LivingEntity target = this.getTarget();

        switch (this.currentAttack) {
            case SLASH -> handleSlashTick(target);
            case SHIELD_BASH -> handleShieldBashTick(target);
            case CHARGE -> handleChargeTick();
            case CORE_PULSE -> handleCorePulseTick();
            default -> resetAttackState();
        }
    }

    private void handleSlashTick(LivingEntity target) {
        if (this.currentAttackPhase == AttackPhase.WINDUP) {
            if (target != null) {
                this.getLookControl().lookAt(target, 45.0f, 45.0f);
            }
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.ACTIVE;
                this.attackTimer = 3;
                this.activeDamagedEntities.clear();
                triggerAnimation("slash");
                if (this.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 0.8f);
                }
            }
        } else if (this.currentAttackPhase == AttackPhase.ACTIVE) {
            damageTargetsInRadius(5.5, 16.0f, false);
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.RECOVERY;
                this.attackTimer = 20;
            }
        } else if (this.currentAttackPhase == AttackPhase.RECOVERY) {
            if (this.attackTimer <= 0) {
                this.slashCooldown = 30; // Initial 30 ticks cooldown
                resetAttackState();
            }
        }
    }

    private void handleShieldBashTick(LivingEntity target) {
        if (this.currentAttackPhase == AttackPhase.WINDUP) {
            if (target != null) {
                this.getLookControl().lookAt(target, 45.0f, 45.0f);
            }
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.ACTIVE;
                this.attackTimer = 5;
                this.activeDamagedEntities.clear();
                triggerAnimation("shield_bash");
                if (this.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.5f, 0.7f);
                }
            }
        } else if (this.currentAttackPhase == AttackPhase.ACTIVE) {
            // 4-block directional cone (~45 degrees)
            Vec3d lookDir = this.getRotationVec(1.0f).normalize();
            Box area = this.getBoundingBox().expand(4.0);
            List<LivingEntity> list = this.getWorld().getNonSpectatingEntities(LivingEntity.class, area);
            for (LivingEntity e : list) {
                if (e != this && !this.activeDamagedEntities.contains(e.getUuid())) {
                    Vec3d toEntity = e.getPos().subtract(this.getPos()).normalize();
                    double dot = lookDir.dotProduct(toEntity);
                    double dist = this.distanceTo(e);
                    if (dist <= 4.0 && dot >= 0.707) { // 45-degree cone
                        this.activeDamagedEntities.add(e.getUuid());
                        e.damage(this.getDamageSources().mobAttack(this), 10.0f);
                        // Heavy directional knockback
                        e.addVelocity(lookDir.x * 1.6, 0.25, lookDir.z * 1.6);
                        e.velocityModified = true;
                        // Slowness X for 10 ticks
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 9, false, false, true));
                    }
                }
            }

            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.RECOVERY;
                this.attackTimer = 15;
            }
        } else if (this.currentAttackPhase == AttackPhase.RECOVERY) {
            if (this.attackTimer <= 0) {
                this.shieldBashCooldown = 80; // Initial 80 ticks cooldown
                resetAttackState();
            }
        }
    }

    private void handleChargeTick() {
        if (this.currentAttackPhase == AttackPhase.WINDUP) {
            if (this.chargeTargetPos != null) {
                this.getLookControl().lookAt(this.chargeTargetPos.x, this.chargeTargetPos.y, this.chargeTargetPos.z, 50.0f, 50.0f);
            }
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.ACTIVE;
                this.attackTimer = 12;
                this.activeDamagedEntities.clear();
                triggerAnimation("charge");
                if (this.chargeTargetPos != null) {
                    Vec3d diff = this.chargeTargetPos.subtract(this.getPos());
                    this.chargeDirection = new Vec3d(diff.x, 0, diff.z).normalize();
                } else {
                    this.chargeDirection = this.getRotationVec(1.0f).normalize();
                }
            }
        } else if (this.currentAttackPhase == AttackPhase.ACTIVE) {
            // Dash at locked direction (approx 2.5x speed vector)
            if (this.chargeDirection != null) {
                this.setVelocity(this.chargeDirection.x * 1.35, this.getVelocity().y, this.chargeDirection.z * 1.35);
                this.velocityModified = true;

                // Damage intercepted entities along path
                Box pathBox = this.getBoundingBox().expand(1.2);
                List<LivingEntity> list = this.getWorld().getNonSpectatingEntities(LivingEntity.class, pathBox);
                for (LivingEntity e : list) {
                    if (e != this && !this.activeDamagedEntities.contains(e.getUuid())) {
                        this.activeDamagedEntities.add(e.getUuid());
                        e.damage(this.getDamageSources().mobAttack(this), 20.0f);
                        // Knockback: 1.5x horizontal, +0.5 vertical
                        e.addVelocity(this.chargeDirection.x * 1.5, 0.5, this.chargeDirection.z * 1.5);
                        e.velocityModified = true;
                    }
                }
            }

            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.RECOVERY;
                this.attackTimer = 30;
                this.setVelocity(0, this.getVelocity().y, 0);
            }
        } else if (this.currentAttackPhase == AttackPhase.RECOVERY) {
            if (this.attackTimer <= 0) {
                this.chargeCooldown = 120; // Initial 120 ticks cooldown
                resetAttackState();
            }
        }
    }

    private void handleCorePulseTick() {
        if (this.currentAttackPhase == AttackPhase.WINDUP) {
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.ACTIVE;
                this.attackTimer = 4;
                this.activeDamagedEntities.clear();
                triggerAnimation("core_pulse");

                // Execute 6-block radial blast
                if (this.getWorld() instanceof ServerWorld world) {
                    world.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.85f);
                    world.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.2, this.getZ(),
                            2, 0.1, 0.1, 0.1, 0.0);
                    world.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.2, this.getZ(),
                            45, 2.5, 0.6, 2.5, 0.15);
                }

                Box blastBox = this.getBoundingBox().expand(6.0);
                List<LivingEntity> list = this.getWorld().getNonSpectatingEntities(LivingEntity.class, blastBox);
                for (LivingEntity e : list) {
                    if (e != this && this.squaredDistanceTo(e) <= 36.0 && !this.activeDamagedEntities.contains(e.getUuid())) {
                        this.activeDamagedEntities.add(e.getUuid());
                        // 10 magic damage (bypasses normal armor)
                        e.damage(this.getDamageSources().magic(), 10.0f);
                        // Radial outward knockback
                        Vec3d dir = e.getPos().subtract(this.getPos()).normalize();
                        e.addVelocity(dir.x * 1.5, 0.45, dir.z * 1.5);
                        e.velocityModified = true;
                    }
                }
            }
        } else if (this.currentAttackPhase == AttackPhase.ACTIVE) {
            if (this.attackTimer <= 0) {
                this.currentAttackPhase = AttackPhase.RECOVERY;
                this.attackTimer = 20;
            }
        } else if (this.currentAttackPhase == AttackPhase.RECOVERY) {
            if (this.attackTimer <= 0) {
                this.corePulseCooldown = 100; // Initial 100 ticks cooldown
                resetAttackState();

                // Core Pulse exhausts the knight and EXPOSES the core for 3 seconds to be destroyed!
                if (!isCoreBroken()) {
                    startCoreExposure(CORE_EXPOSURE_DURATION);
                }
            }
        }
    }

    private void damageTargetsInRadius(double reach, float damage, boolean magic) {
        double reachSq = reach * reach;
        Box area = this.getBoundingBox().expand(reach);
        List<LivingEntity> list = this.getWorld().getNonSpectatingEntities(LivingEntity.class, area);
        for (LivingEntity e : list) {
            if (e != this && this.squaredDistanceTo(e) <= reachSq && !this.activeDamagedEntities.contains(e.getUuid())) {
                this.activeDamagedEntities.add(e.getUuid());
                if (magic) {
                    e.damage(this.getDamageSources().magic(), damage);
                } else {
                    e.damage(this.getDamageSources().mobAttack(this), damage);
                }
            }
        }
    }

    public void startSlash() {
        this.currentAttack = AttackType.SLASH;
        this.currentAttackPhase = AttackPhase.WINDUP;
        this.attackTimer = 12; // Initial 12 ticks windup
        this.activeDamagedEntities.clear();
        triggerAnimation("slash_windup");
    }

    public void startShieldBash() {
        this.currentAttack = AttackType.SHIELD_BASH;
        this.currentAttackPhase = AttackPhase.WINDUP;
        this.attackTimer = 10; // Initial 10 ticks windup
        this.activeDamagedEntities.clear();
        triggerAnimation("shield_bash_windup");
    }

    public void startCharge(Vec3d targetPos) {
        this.currentAttack = AttackType.CHARGE;
        this.currentAttackPhase = AttackPhase.WINDUP;
        this.attackTimer = 18; // Initial 18 ticks windup
        this.chargeTargetPos = targetPos;
        this.activeDamagedEntities.clear();
        triggerAnimation("charge_windup");
    }

    public void startCorePulse() {
        this.currentAttack = AttackType.CORE_PULSE;
        this.currentAttackPhase = AttackPhase.WINDUP;
        this.attackTimer = 20; // Initial 20 ticks windup
        this.activeDamagedEntities.clear();
        triggerAnimation("core_pulse_windup");
    }

    public void resetAttackState() {
        this.currentAttack = AttackType.NONE;
        this.currentAttackPhase = AttackPhase.IDLE;
        this.attackTimer = 0;
        this.chargeTargetPos = null;
        this.chargeDirection = null;
        this.activeDamagedEntities.clear();
    }

    // ==================== DAMAGE & IMMORTAL CORE ====================
    @Override
    public boolean damage(DamageSource source, float amount) {
        // When core is exposed to be destroyed, incoming player attacks damage core integrity ONLY with Rootbound Axe!
        if (this.coreExposureTicks > 0 && !isCoreBroken() && source.getAttacker() instanceof PlayerEntity player) {
            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            boolean isHoldingRootboundAxe = (main != null && main.getItem() instanceof com.elemental.item.RootboundAxeItem)
                    || (off != null && off.getItem() instanceof com.elemental.item.RootboundAxeItem);

            if (isHoldingRootboundAxe) {
                damageCore(CORE_DAMAGE_PER_THORN_CAGE, player);
                this.coreExposureTicks = 0;
                this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, false);
            }
        }

        // Immortal Core mechanic: cannot die while core is unbroken!
        if (!isCoreBroken()) {
            float health = this.getHealth();
            if (health - amount < 1.0f) {
                float allowed = Math.max(0.0f, health - 1.0f);
                if (allowed <= 0.0f) {
                    this.setHealth(1.0f);
                    return false;
                }
                amount = allowed;
            }
        }

        boolean damaged = super.damage(source, amount);
        if (damaged) {
            updateBossBar();
        }
        return damaged;
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!isCoreBroken()) {
            this.setHealth(1.0f);
            return;
        }

        triggerAnimation("death");
        this.bossBar.clearPlayers();
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossBar.clearPlayers();
    }

    // ==================== BOSS BAR COLOR INDICATOR ====================
    private void updateBossBar() {
        float percent = MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f);
        if (Math.abs(percent - this.lastBossBarPercent) > 0.005f) {
            this.bossBar.setPercent(percent);
            this.lastBossBarPercent = percent;
        }

        BossBar.Color targetColor;
        String targetName;

        if (isCoreBroken() || getCoreIntegrity() <= 0) {
            // All 3 cores destroyed: cycle through all 3 colors (Red, Orange/Yellow, Green) together!
            long time = this.getWorld().getTime();
            int cycle = (int) ((time / 8) % 3);
            if (cycle == 0) {
                targetColor = BossBar.Color.RED;
            } else if (cycle == 1) {
                targetColor = BossBar.Color.YELLOW;
            } else {
                targetColor = BossBar.Color.GREEN;
            }

            if (isStaggered()) {
                targetName = "Sir Solvane - §c[Core 1] §6[Core 2] §a[Core 3] §f[ALL SHATTERED - STAGGERED]";
            } else {
                targetName = "Sir Solvane - §c[Core 1] §6[Core 2] §a[Core 3] §f[ALL SHATTERED]";
            }
        } else if (this.frostFrozenTicks > 0) {
            // Blue for frozen regen
            targetColor = BossBar.Color.BLUE;
            String exposedStatus = isCoreExposed() ? " §e[CORE EXPOSED - DESTROY IT!]§r" : "";
            targetName = "Sir Solvane - §9[FROZEN REGEN (" + (this.frostFrozenTicks / 20) + "s)]§r" + exposedStatus;
        } else {
            // Active Core Color Indicators:
            // Red: First core (100 - 67 integrity)
            // Orange (Yellow in Minecraft): 2nd core (66 - 34 integrity)
            // Green: 3rd core (33 - 1 integrity)
            int integrity = getCoreIntegrity();
            String exposedStatus = isCoreExposed() ? " §e[CORE EXPOSED - DESTROY IT!]§r" : "";

            if (integrity > 66) {
                targetColor = BossBar.Color.RED;
                targetName = "Sir Solvane - §c[Core 1/3 Active]§r" + exposedStatus;
            } else if (integrity > 33) {
                targetColor = BossBar.Color.YELLOW; // Minecraft Yellow renders as Orange/Gold
                targetName = "Sir Solvane - §6[Core 2/3 Active]§r" + exposedStatus;
            } else {
                targetColor = BossBar.Color.GREEN;
                targetName = "Sir Solvane - §a[Core 3/3 Active]§r" + exposedStatus;
            }
        }

        if (targetColor != this.lastBossBarColor) {
            this.bossBar.setColor(targetColor);
            this.lastBossBarColor = targetColor;
        }

        if (!targetName.equals(this.lastBossBarName)) {
            this.bossBar.setName(Text.literal(targetName));
            this.lastBossBarName = targetName;
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    // ==================== TICK ====================
    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient()) {
            enforceArenaBoundary();
            tickRegeneration();
            tickPhaseSystem();
            tickAttackStateMachine();
            updateBossBar();
        }
    }

    // ==================== NBT PERSISTENCE ====================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Phase", getPhase());
        nbt.putInt("CoreIntegrity", getCoreIntegrity());
        nbt.putBoolean("CoreBroken", isCoreBroken());
        nbt.putInt("FrostFrozenTicks", this.frostFrozenTicks);
        nbt.putInt("ThornCagedTicks", this.thornCagedTicks);
        nbt.putInt("StaggerTicks", this.staggerTicks);
        nbt.putInt("CoreExposureTicks", this.coreExposureTicks);
        nbt.putInt("EnrageCountdown", this.enrageCountdown);
        nbt.putBoolean("EnrageStarted", this.enrageStarted);
        nbt.putBoolean("Enraged", isEnraged());
        nbt.putInt("SlashCooldown", this.slashCooldown);
        nbt.putInt("ShieldBashCooldown", this.shieldBashCooldown);
        nbt.putInt("ChargeCooldown", this.chargeCooldown);
        nbt.putInt("CorePulseCooldown", this.corePulseCooldown);
        if (this.arenaCenter != null) {
            nbt.putLong("ArenaCenter", this.arenaCenter.asLong());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Phase")) {
            setPhase(nbt.getInt("Phase"));
        }
        if (nbt.contains("CoreIntegrity")) {
            setCoreIntegrity(nbt.getInt("CoreIntegrity"));
        }
        if (nbt.contains("CoreBroken")) {
            setCoreBroken(nbt.getBoolean("CoreBroken"));
        }
        if (nbt.contains("FrostFrozenTicks")) {
            this.frostFrozenTicks = nbt.getInt("FrostFrozenTicks");
            this.dataTracker.set(DATA_REGEN_FROZEN_TICKS, this.frostFrozenTicks);
        }
        if (nbt.contains("ThornCagedTicks")) {
            this.thornCagedTicks = nbt.getInt("ThornCagedTicks");
        }
        if (nbt.contains("StaggerTicks")) {
            this.staggerTicks = nbt.getInt("StaggerTicks");
            this.dataTracker.set(DATA_STAGGERED, this.staggerTicks > 0);
        }
        if (nbt.contains("CoreExposureTicks")) {
            this.coreExposureTicks = nbt.getInt("CoreExposureTicks");
            this.dataTracker.set(DATA_CORE_EXPOSED_ACTIVE, this.coreExposureTicks > 0);
        }
        if (nbt.contains("EnrageCountdown")) {
            this.enrageCountdown = nbt.getInt("EnrageCountdown");
        }
        if (nbt.contains("EnrageStarted")) {
            this.enrageStarted = nbt.getBoolean("EnrageStarted");
        }
        if (nbt.contains("Enraged")) {
            setEnraged(nbt.getBoolean("Enraged"));
        }
        if (nbt.contains("SlashCooldown")) this.slashCooldown = nbt.getInt("SlashCooldown");
        if (nbt.contains("ShieldBashCooldown")) this.shieldBashCooldown = nbt.getInt("ShieldBashCooldown");
        if (nbt.contains("ChargeCooldown")) this.chargeCooldown = nbt.getInt("ChargeCooldown");
        if (nbt.contains("CorePulseCooldown")) this.corePulseCooldown = nbt.getInt("CorePulseCooldown");
        if (nbt.contains("ArenaCenter")) {
            this.arenaCenter = BlockPos.fromLong(nbt.getLong("ArenaCenter"));
        }
    }

    // ==================== DEDICATED AI GOALS (INITIAL AI) ====================

    /**
     * Targeting goal: Detects players within 48 blocks.
     * Prioritizes Survival/Adventure players, but tracks Creative players for testing.
     */
    public static class KnightTargetGoal extends Goal {
        private final EverlivingKnightEntity knight;

        public KnightTargetGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.TARGET));
        }

        @Override
        public boolean canStart() {
            LivingEntity currentTarget = this.knight.getTarget();
            if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isSpectator()
                    && this.knight.squaredDistanceTo(currentTarget) <= FOLLOW_RANGE * FOLLOW_RANGE) {
                return false;
            }

            PlayerEntity chosen = null;
            double chosenDistSq = FOLLOW_RANGE * FOLLOW_RANGE;

            for (PlayerEntity player : this.knight.getWorld().getPlayers()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                double distSq = this.knight.squaredDistanceTo(player);
                if (distSq <= FOLLOW_RANGE * FOLLOW_RANGE) {
                    if (!player.isCreative()) {
                        if (chosen == null || chosen.isCreative() || distSq < chosenDistSq) {
                            chosen = player;
                            chosenDistSq = distSq;
                        }
                    } else if (chosen == null || (chosen.isCreative() && distSq < chosenDistSq)) {
                        chosen = player;
                        chosenDistSq = distSq;
                    }
                }
            }

            if (chosen != null) {
                this.knight.setTarget(chosen);
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;
            return this.knight.squaredDistanceTo(target) <= (FOLLOW_RANGE + 16.0) * (FOLLOW_RANGE + 16.0);
        }
    }

    /**
     * Pursuit goal: Navigates toward target at 1.35x speed (1.6x in Phase 3).
     * Yields control when in attack range or busy.
     */
    public static class KnightChaseTargetGoal extends Goal {
        private final EverlivingKnightEntity knight;
        private int updatePathDelay = 0;

        public KnightChaseTargetGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;
            if (this.knight.isBusy()) return false;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;
            return !this.knight.isBusy();
        }

        @Override
        public void start() {
            this.updatePathDelay = 0;
        }

        @Override
        public void stop() {
            this.knight.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.knight.getTarget();
            if (target == null) return;

            this.knight.getLookControl().lookAt(target, 30.0f, 30.0f);

            if (--this.updatePathDelay <= 0) {
                this.updatePathDelay = 4 + this.knight.random.nextInt(6);
                double speed = this.knight.getPhase() == 3 || this.knight.isEnraged()
                        ? CHASE_SPEED_PHASE3 : CHASE_SPEED_NORMAL;
                this.knight.getNavigation().startMovingTo(target, speed);
            }
        }
    }

    /**
     * Melee Slash Attack Goal
     * Trigger: Target between 4.0 and 5.5 blocks, slashCooldown == 0.
     */
    public static class KnightMeleeSlashGoal extends Goal {
        private final EverlivingKnightEntity knight;

        public KnightMeleeSlashGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.knight.isBusy() || this.knight.getSlashCooldown() > 0) return false;
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;

            double dist = this.knight.distanceTo(target);
            return dist >= 4.0 && dist <= 5.5;
        }

        @Override
        public void start() {
            this.knight.getNavigation().stop();
            this.knight.startSlash();
        }

        @Override
        public boolean shouldContinue() {
            return this.knight.getCurrentAttack() == AttackType.SLASH
                    && this.knight.getCurrentAttackPhase() != AttackPhase.IDLE;
        }
    }

    /**
     * Shield Bash Attack Goal
     * Trigger: Target within 4.0 blocks, ~35% chance per tick when ready.
     */
    public static class KnightShieldBashGoal extends Goal {
        private final EverlivingKnightEntity knight;

        public KnightShieldBashGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.knight.isBusy() || this.knight.getShieldBashCooldown() > 0) return false;
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;

            double dist = this.knight.distanceTo(target);
            return dist <= 4.0 && this.knight.random.nextFloat() < 0.35f;
        }

        @Override
        public void start() {
            this.knight.getNavigation().stop();
            this.knight.startShieldBash();
        }

        @Override
        public boolean shouldContinue() {
            return this.knight.getCurrentAttack() == AttackType.SHIELD_BASH
                    && this.knight.getCurrentAttackPhase() != AttackPhase.IDLE;
        }
    }

    /**
     * Charge / Rush Attack Goal
     * Trigger: Target between 4.0 and 18.0 blocks, chargeCooldown == 0.
     */
    public static class KnightChargeGoal extends Goal {
        private final EverlivingKnightEntity knight;

        public KnightChargeGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.knight.isBusy() || this.knight.getChargeCooldown() > 0) return false;
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;

            double dist = this.knight.distanceTo(target);
            return dist >= 4.0 && dist <= 18.0;
        }

        @Override
        public void start() {
            this.knight.getNavigation().stop();
            LivingEntity target = this.knight.getTarget();
            if (target != null) {
                this.knight.startCharge(target.getPos());
            }
        }

        @Override
        public boolean shouldContinue() {
            return this.knight.getCurrentAttack() == AttackType.CHARGE
                    && this.knight.getCurrentAttackPhase() != AttackPhase.IDLE;
        }
    }

    /**
     * Radiant Core Pulse Attack Goal
     * Probability: Phase 1: 8%, Phase 2: 12%, Phase 3: 20%.
     */
    public static class KnightCorePulseGoal extends Goal {
        private final EverlivingKnightEntity knight;

        public KnightCorePulseGoal(EverlivingKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.knight.isBusy() || this.knight.getCorePulseCooldown() > 0) return false;
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive() || target.isSpectator()) return false;

            float chance = switch (this.knight.getPhase()) {
                case 3 -> 0.20f;
                case 2 -> 0.12f;
                default -> 0.08f;
            };

            return this.knight.random.nextFloat() < chance;
        }

        @Override
        public void start() {
            this.knight.getNavigation().stop();
            this.knight.startCorePulse();
        }

        @Override
        public boolean shouldContinue() {
            return this.knight.getCurrentAttack() == AttackType.CORE_PULSE
                    && this.knight.getCurrentAttackPhase() != AttackPhase.IDLE;
        }
    }
}
