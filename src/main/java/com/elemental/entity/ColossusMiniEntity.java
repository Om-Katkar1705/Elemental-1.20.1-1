package com.elemental.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ColossusMiniEntity extends HostileEntity implements GeoEntity {

    // ==================== CONFIGURATION VARIABLES ====================
    public static double MAX_HEALTH = 10.0;
    public static double ATTACK_DAMAGE = 2.0;
    public static double MOVEMENT_SPEED = 0.30;
    public static double ARMOR = 4.0;
    public static double KNOCKBACK_RESISTANCE = 1.0;
    public static double FOLLOW_RANGE = 32.0;
    public static int EXPERIENCE_POINTS = 3;
    public static double ATTACK_CHASE_SPEED = 1.25;
    public static double WANDER_SPEED = 0.8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.colossus_mini.idle");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.colossus_mini.walk");
    protected static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.colossus_mini.attack");

    public ColossusMiniEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = EXPERIENCE_POINTS;
    }

    public static DefaultAttributeContainer.Builder createMiniAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, ATTACK_CHASE_SPEED, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, WANDER_SPEED));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 0.5, this.getZ(),
                    20, 0.3, 0.3, 0.3, 0.1);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, state -> {
            if (this.handSwinging) {
                return state.setAndContinue(ATTACK_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
