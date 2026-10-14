package com.elemental.entity;

import com.elemental.item.SunforgedScimitarItem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SolarArcEntity extends PersistentProjectileEntity implements GeoEntity {
    // Base travel speed of the solar arc projectile
    public static final float BASE_SPEED = 1.5f;
    // Additional speed per Solar Core upgrade level
    public static final float CORE_SPEED_BONUS = 0.5f;
    // Base maximum lifetime of the projectile in ticks (40 ticks = 2 seconds)
    public static final int BASE_LIFETIME = 40;
    // Additional lifetime in ticks per Solar Core upgrade level
    public static final int CORE_LIFETIME_BONUS = 20;
    // Base magic damage dealt by the projectile on hit
    public static final float BASE_DAMAGE = 5.0f;
    // Damage multiplier applied when projectile or caster is in rain or water (50% reduction)
    public static final float RAIN_DAMAGE_MULTIPLIER = 0.5f;
    // Base duration in seconds the target is set on fire
    public static final int BASE_FIRE_SECONDS = 4;
    // Fire duration in seconds when hit in rain or water
    public static final int RAIN_FIRE_SECONDS = 1;
    // Additional seconds of fire applied per Solar Core upgrade level
    public static final int CORE_FIRE_BONUS_SECONDS = 2;

    private static final RawAnimation TRAVEL_ANIM = RawAnimation.begin().thenLoop("animation.solar_arc.travel");
    private static final RawAnimation IMPACT_ANIM = RawAnimation.begin().thenPlay("animation.solar_arc.impact");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int solarCoreLevel = 0;
    private int lifetimeTicks = 0;
    private boolean weakenedByWeather = false;
    private float damage = BASE_DAMAGE;

    public SolarArcEntity(EntityType<? extends PersistentProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
        this.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
    }

    public SolarArcEntity(World world, LivingEntity owner) {
        this(world, owner, ItemStack.EMPTY);
    }

    public SolarArcEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.SOLAR_ARC, owner, world);
        this.setNoGravity(true);
        this.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;

        if (!stack.isEmpty() && stack.getItem() instanceof SunforgedScimitarItem) {
            this.solarCoreLevel = SunforgedScimitarItem.getSolarCoreLevel(stack);
        } else if (owner instanceof PlayerEntity player) {
            ItemStack held = player.getMainHandStack();
            if (held.getItem() instanceof SunforgedScimitarItem) {
                this.solarCoreLevel = SunforgedScimitarItem.getSolarCoreLevel(held);
            }
        }

        if (owner.isTouchingWater() || (world.isRaining() && world.isSkyVisible(owner.getBlockPos()))) {
            this.weakenedByWeather = true;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            return state.setAndContinue(TRAVEL_ANIM);
        }).triggerableAnim("impact", IMPACT_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void tick() {
        super.tick();
        this.lifetimeTicks++;
        if (!this.getWorld().isClient() && this.lifetimeTicks >= this.getMaxLifetime()) {
            this.discard();
            return;
        }

        if (this.getWorld().isClient()) {
            this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    public int getMaxLifetime() {
        return BASE_LIFETIME + (this.solarCoreLevel * CORE_LIFETIME_BONUS);
    }

    @Override
    protected boolean canHit(net.minecraft.entity.Entity entity) {
        if (entity == this.getOwner()) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!this.getWorld().isClient()) {
            net.minecraft.entity.Entity hitEntity = entityHitResult.getEntity();
            if (hitEntity == this.getOwner()) {
                return;
            }

            if (hitEntity instanceof LivingEntity entity) {
                boolean wetConditions = this.weakenedByWeather
                        || this.isTouchingWater()
                        || (this.getWorld().isRaining() && this.getWorld().isSkyVisible(this.getBlockPos()));
                float dmg = wetConditions ? (this.damage * RAIN_DAMAGE_MULTIPLIER) : this.damage;
                int fireSecs = wetConditions ? RAIN_FIRE_SECONDS : (BASE_FIRE_SECONDS + this.solarCoreLevel * CORE_FIRE_BONUS_SECONDS);

                if (entity instanceof EverlivingKnightEntity knight && this.getOwner() instanceof PlayerEntity player) {
                    knight.applySolarArcReaction(player);
                } else {
                    entity.damage(this.getDamageSources().magic(), dmg);
                    entity.setOnFireFor(fireSecs);
                }
            }

            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
                serverWorld.spawnParticles(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                serverWorld.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            }

            // Spawn solar burst particles and sound on impact
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);
            this.discard();
        }
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!this.getWorld().isClient()) {
            if (this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                serverWorld.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            }
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.8f);
            this.discard();
        }
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("SolarCoreLevel", this.solarCoreLevel);
        nbt.putInt("LifetimeTicks", this.lifetimeTicks);
        nbt.putBoolean("WeakenedByWeather", this.weakenedByWeather);
        nbt.putFloat("Damage", this.damage);
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.solarCoreLevel = nbt.getInt("SolarCoreLevel");
        this.lifetimeTicks = nbt.getInt("LifetimeTicks");
        this.weakenedByWeather = nbt.getBoolean("WeakenedByWeather");
        this.damage = nbt.getFloat("Damage");
    }

    public float getProjectileDamage() {
        return this.damage;
    }

    public void setProjectileDamage(float damage) {
        this.damage = damage;
    }
}
