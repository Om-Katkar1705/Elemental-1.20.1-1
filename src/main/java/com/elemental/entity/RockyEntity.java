package com.elemental.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import com.elemental.network.QuestNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Rocky — an intelligent alien pet inspired by Project Hail Mary.
 * A dense, faceless, five-legged rock-like companion that communicates in musical chords
 * and loves minerals, metals, and friendly fist bumps.
 */
public class RockyEntity extends TameableEntity implements GeoEntity {

    // ==================== CONFIGURATION VARIABLES ====================
    public static double MAX_HEALTH = 60.0;
    public static double MOVEMENT_SPEED = 0.28;
    public static double ARMOR = 10.0;
    public static double ARMOR_TOUGHNESS = 4.0;
    public static double KNOCKBACK_RESISTANCE = 0.8;
    public static double ATTACK_DAMAGE = 7.0;
    public static double FOLLOW_RANGE = 32.0;
    public static int EXPERIENCE_POINTS = 5;
    public static double ATTACK_CHASE_SPEED = 1.25;
    public static double FOLLOW_OWNER_SPEED = 1.2;
    public static double WANDER_SPEED = 0.75;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ==================== GECKOLIB ANIMATIONS ====================
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.rocky.idle");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.rocky.walk");
    protected static final RawAnimation SIT_ANIM = RawAnimation.begin().thenLoop("animation.rocky.sit");
    protected static final RawAnimation FOLLOW_ANIM = RawAnimation.begin().thenLoop("animation.rocky.follow");
    protected static final RawAnimation HAPPY_ANIM = RawAnimation.begin().thenPlay("animation.rocky.happy");
    protected static final RawAnimation COMM_ANIM = RawAnimation.begin().thenPlay("animation.rocky.communication");
    protected static final RawAnimation FIST_BUMP_ANIM = RawAnimation.begin().thenPlay("animation.rocky.fist_bump");
    protected static final RawAnimation HURT_ANIM = RawAnimation.begin().thenPlay("animation.rocky.hurt");
    protected static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("animation.rocky.death");

    private int communicationCooldown = 0;
    private UUID fistBumpPartnerUuid = null;
    private int fistBumpState = 0; // 0 = IDLE, 1 = APPROACHING, 2 = PERFORMING
    private int fistBumpTimer = 0;

    public RockyEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = EXPERIENCE_POINTS;
    }

    public static DefaultAttributeContainer.Builder createRockyAttributes() {
        return TameableEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new SitGoal(this));
        this.goalSelector.add(3, new MeleeAttackGoal(this, ATTACK_CHASE_SPEED, true));
        this.goalSelector.add(4, new FollowOwnerGoal(this, FOLLOW_OWNER_SPEED, 5.0f, 2.0f, false));
        this.goalSelector.add(5, new AnimalMateGoal(this, 1.0));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, WANDER_SPEED));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
        this.targetSelector.add(2, new AttackWithOwnerGoal(this));
        this.targetSelector.add(3, new RevengeGoal(this).setGroupRevenge());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.communicationCooldown > 0) {
            this.communicationCooldown--;
        }

        if (!this.getWorld().isClient()) {
            tickFistBump();
        }
    }

    @Override
    public net.minecraft.world.EntityView method_48926() {
        return this.getWorld();
    }

    // ==================== IMMUNITIES & RESISTANCES ====================
    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheInWater() {
        return true;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        // High density carapace absorbs most fall damage
        return super.handleFallDamage(Math.max(0.0f, fallDistance - 5.0f), damageMultiplier * 0.5f, damageSource);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Rocky's rock carapace is impervious to suffocation/cramming in wall
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.CRAMMING) || source.isOf(DamageTypes.CACTUS)) {
            return false;
        }

        boolean damaged = super.damage(source, amount);
        if (damaged && !this.getWorld().isClient() && this.isAlive()) {
            this.triggerAnimation("hurt");
            playChimeChord(0.8f, 0.6f);
        }
        return damaged;
    }

    // ==================== INTERACTION & TAMING ====================
    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // 1. Mineral diet check (Raw Iron, Iron Ingot, Amethyst Shard, Raw Gold, Gold Ingot, Diamond, Netherite)
        if (isMineralFood(stack)) {
            if (!this.isTamed()) {
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }

                if (!this.getWorld().isClient()) {
                    // 1 in 3 taming chance
                    if (this.random.nextInt(3) == 0) {
                        this.setOwner(player);
                        this.navigation.stop();
                        this.setTarget(null);
                        this.setSitting(false);
                        this.getWorld().sendEntityStatus(this, (byte) 7); // Heart particles
                        this.triggerAnimation("happy");
                        playHappyChimes();
                        player.sendMessage(Text.literal("§6Rocky: §b*♫ happy harmonic chords ♫* §e(Amaze! Amaze! Amaze!)"), true);
                    } else {
                        this.getWorld().sendEntityStatus(this, (byte) 6); // Smoke particles
                        playChimeChord(1.0f, 1.0f);
                    }
                }
                return ActionResult.success(this.getWorld().isClient());
            }

            // Tamed: Healing
            if (this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                this.heal(12.0f);
                if (!this.getWorld().isClient()) {
                    this.getWorld().sendEntityStatus(this, (byte) 7);
                    this.triggerAnimation("happy");
                    playHappyChimes();
                }
                return ActionResult.success(this.getWorld().isClient());
            }
        }

        // 2. Owner interaction
        if (this.isTamed() && this.isOwner(player)) {
            // Shift + Right Click with empty hand: THE ICONIC FIST BUMP!
            if (player.isSneaking()) {
                if (stack.isEmpty() && this.communicationCooldown <= 0 && !this.isFistBumping()) {
                    if (!this.getWorld().isClient()) {
                        startFistBump(player);
                    }
                    return ActionResult.success(this.getWorld().isClient());
                }
                return super.interactMob(player, hand);
            }

            // Normal Right Click with empty hand: Toggle Sitting/Standing
            if (stack.isEmpty()) {
                if (!this.getWorld().isClient()) {
                    boolean sitting = !this.isSitting();
                    this.setSitting(sitting);
                    this.setInSittingPose(sitting);
                    this.jumping = false;
                    this.navigation.stop();
                    this.setTarget(null);

                    if (sitting) {
                        playChimeChord(0.9f, 0.7f);
                        player.sendMessage(Text.literal("§6Rocky §7sits down like a sturdy boulder."), true);
                    } else {
                        playChimeChord(1.1f, 1.3f);
                        player.sendMessage(Text.literal("§6Rocky §7stands and readies his 5 legs."), true);
                    }
                }
                return ActionResult.success(this.getWorld().isClient());
            }
        }

        return super.interactMob(player, hand);
    }

    public static boolean isMineralFood(ItemStack stack) {
        return stack.isOf(Items.RAW_IRON) ||
                stack.isOf(Items.IRON_INGOT) ||
                stack.isOf(Items.AMETHYST_SHARD) ||
                stack.isOf(Items.RAW_GOLD) ||
                stack.isOf(Items.GOLD_INGOT) ||
                stack.isOf(Items.DIAMOND) ||
                stack.isOf(Items.NETHERITE_SCRAP);
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return isMineralFood(stack);
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        RockyEntity baby = ModEntities.ROCKY.create(world);
        if (baby != null && this.getOwnerUuid() != null) {
            baby.setOwnerUuid(this.getOwnerUuid());
            baby.setTamed(true);
        }
        return baby;
    }

    // ==================== MUSICAL CHORD AUDIO ====================
    public void playChimeChord(float pitch1, float pitch2) {
        this.getWorld().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                SoundCategory.NEUTRAL,
                0.85f,
                pitch1
        );
        this.getWorld().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.NEUTRAL,
                0.6f,
                pitch2
        );
    }

    public void playHappyChimes() {
        playChimeChord(1.2f, 1.5f);
        this.getWorld().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                SoundCategory.NEUTRAL,
                0.7f,
                1.4f
        );
    }

    public void playFistBumpChimes() {
        playChimeChord(1.0f, 1.25f);
        this.getWorld().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
                SoundCategory.NEUTRAL,
                0.65f,
                1.6f
        );
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        // Soft occasional melodic chimes
        if (this.random.nextFloat() < 0.4f) {
            playChimeChord(1.0f + this.random.nextFloat() * 0.4f, 1.2f);
        }
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_DEEPSLATE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_DEEPSLATE_BREAK;
    }

    // ==================== GECKOLIB CONTROLLERS ====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, state -> {
            if (this.isDead()) {
                return state.setAndContinue(DEATH_ANIM);
            }
            if (this.isInSittingPose()) {
                return state.setAndContinue(SIT_ANIM);
            }
            if (state.isMoving()) {
                // If moving fast or following closely, use follow trot
                if (this.isNavigating() && (this.getVelocity().horizontalLengthSquared() > 0.04 || this.isSprinting())) {
                    return state.setAndContinue(FOLLOW_ANIM);
                }
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        })
        .triggerableAnim("happy", HAPPY_ANIM)
        .triggerableAnim("communication", COMM_ANIM)
        .triggerableAnim("fist_bump", FIST_BUMP_ANIM)
        .triggerableAnim("hurt", HURT_ANIM)
        .triggerableAnim("death", DEATH_ANIM));
    }

    public void triggerAnimation(String animName) {
        if (!this.getWorld().isClient()) {
            this.triggerAnim("controller", animName);
        }
    }

    // ==================== FIST BUMP POSITIONING & PERFORMANCE ====================
    public boolean isFistBumping() {
        return this.fistBumpState != 0;
    }

    public void startFistBump(PlayerEntity player) {
        if (this.isFistBumping()) return;

        // If sitting, stand up to reposition
        if (this.isSitting()) {
            this.setSitting(false);
            this.setInSittingPose(false);
        }

        this.fistBumpPartnerUuid = player.getUuid();
        this.fistBumpState = 1; // APPROACHING
        this.fistBumpTimer = 0;
        this.communicationCooldown = 60;

        playChimeChord(1.2f, 1.4f);
        player.sendMessage(Text.literal("§6Rocky §7scuttles into position for a fist bump..."), true);

        // Immediate pathing towards target spot
        Vec3d target = getFistBumpTargetPos(player);
        this.getNavigation().startMovingTo(target.x, target.y, target.z, 1.25);
    }

    private Vec3d getFistBumpTargetPos(PlayerEntity player) {
        double rad = Math.toRadians(player.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);

        // 1.25 blocks in front of player, offset 0.2 blocks to player's right (where player's right hand extends)
        return new Vec3d(
                player.getX() + fwdX * 1.25 + rightX * 0.2,
                player.getY(),
                player.getZ() + fwdZ * 1.25 + rightZ * 0.2
        );
    }

    private void tickFistBump() {
        if (this.fistBumpState == 0) return;

        if (this.fistBumpPartnerUuid == null) {
            cancelFistBump();
            return;
        }

        PlayerEntity partner = this.getWorld().getPlayerByUuid(this.fistBumpPartnerUuid);
        if (partner == null || !partner.isAlive() || this.squaredDistanceTo(partner) > 100.0) {
            cancelFistBump();
            return;
        }

        if (this.fistBumpState == 1) { // APPROACHING
            this.fistBumpTimer++;

            // Rocky keeps focus and looks directly at partner
            this.getLookControl().lookAt(partner, 30.0f, 30.0f);

            Vec3d target = getFistBumpTargetPos(partner);

            // Re-path periodically if player shifts
            if (this.fistBumpTimer % 5 == 0) {
                this.getNavigation().startMovingTo(target.x, target.y, target.z, 1.25);
            }

            double distSqToTarget = this.squaredDistanceTo(target.x, target.y, target.z);
            double distToPartner = Math.sqrt(this.squaredDistanceTo(partner));

            // Position reached check: close to target, or in ideal distance range (0.85m - 1.45m), or 60-tick timeout
            if (distSqToTarget < 0.25 || (distToPartner >= 0.85 && distToPartner <= 1.45) || this.fistBumpTimer >= 60) {
                beginFistBumpPerformance(partner);
            }
        } else if (this.fistBumpState == 2) { // PERFORMING
            this.fistBumpTimer++;

            // Lock Rocky firmly in place during performance
            this.getNavigation().stop();
            this.setVelocity(Vec3d.ZERO);

            // Face partner directly
            double dx = partner.getX() - this.getX();
            double dz = partner.getZ() - this.getZ();
            float facingYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            this.setYaw(facingYaw);
            this.setBodyYaw(facingYaw);
            this.setHeadYaw(facingYaw);
            this.getLookControl().lookAt(partner, 180.0f, 180.0f);

            // Impact moment at tick 20 (1.0s in): fists collide!
            if (this.fistBumpTimer == 20) {
                playFistBumpChimes();

                if (this.getWorld() instanceof ServerWorld serverWorld) {
                    double midX = (this.getX() + partner.getX()) * 0.5;
                    double midY = (this.getY() + 0.8 + partner.getY() + 1.1) * 0.5;
                    double midZ = (this.getZ() + partner.getZ()) * 0.5;

                    serverWorld.spawnParticles(ParticleTypes.NOTE, midX, midY, midZ, 6, 0.25, 0.2, 0.25, 0.15);
                    serverWorld.spawnParticles(ParticleTypes.CRIT, midX, midY, midZ, 8, 0.2, 0.2, 0.2, 0.05);
                }

                partner.sendMessage(Text.literal("§6Rocky: §d*♫ resonant chords ♫* §b(Fist my bump!)"), true);
            }

            // Animation completion at tick 40 (2.0s)
            if (this.fistBumpTimer >= 40) {
                finishFistBump();
            }
        }
    }

    private void beginFistBumpPerformance(PlayerEntity partner) {
        this.fistBumpState = 2; // PERFORMING
        this.fistBumpTimer = 0;
        this.getNavigation().stop();
        this.setVelocity(Vec3d.ZERO);

        // Snap orientation to face partner directly
        double dx = partner.getX() - this.getX();
        double dz = partner.getZ() - this.getZ();
        float facingYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        this.setYaw(facingYaw);
        this.setBodyYaw(facingYaw);
        this.setHeadYaw(facingYaw);

        // Trigger animations for both Rocky and the player
        this.triggerAnimation("fist_bump");
        this.triggerAnimation("communication");

        if (partner instanceof ServerPlayerEntity serverPlayer) {
            QuestNetworking.sendPlayerFistBump(serverPlayer, 40);
        }
    }

    private void finishFistBump() {
        this.fistBumpState = 0;
        this.fistBumpTimer = 0;
        this.fistBumpPartnerUuid = null;
        this.communicationCooldown = 40;
    }

    private void cancelFistBump() {
        this.fistBumpState = 0;
        this.fistBumpTimer = 0;
        this.fistBumpPartnerUuid = null;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
