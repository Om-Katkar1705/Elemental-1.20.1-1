package com.elemental.entity;

import com.elemental.enchantment.ModEnchantments;
import com.elemental.item.SunforgedScimitarItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class SolarArcEntity extends PersistentProjectileEntity implements GeoEntity {

    // ==================== CONFIGURATION VARIABLES ====================
    // Base travel speed of the solar arc projectile
    public static float BASE_SPEED = 1.5f;
    // Additional speed per Solar Core upgrade level
    public static float CORE_SPEED_BONUS = 0.5f;
    // Base maximum lifetime of the projectile in ticks (40 ticks = 2 seconds)
    public static int BASE_LIFETIME = 40;
    // Additional lifetime in ticks per Solar Core upgrade level
    public static int CORE_LIFETIME_BONUS = 20;
    // Base damage dealt by the projectile on hit at full charge (deals 6 hearts through full diamond armor, 13 hearts unarmored)
    public static float BASE_DAMAGE = 26.0f;
    // Damage multiplier applied when projectile or caster is in rain or water (50% reduction)
    public static float RAIN_DAMAGE_MULTIPLIER = 0.5f;
    // Base duration in seconds the target is set on fire
    public static int BASE_FIRE_SECONDS = 4;
    // Fire duration in seconds when hit in rain or water
    public static int RAIN_FIRE_SECONDS = 1;
    // Additional seconds of fire applied per Solar Core upgrade level
    public static int CORE_FIRE_BONUS_SECONDS = 2;

    private static final TrackedData<Boolean> SOLAR_BLAST = DataTracker.registerData(SolarArcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation TRAVEL_ANIM = RawAnimation.begin().thenLoop("animation.solar_arc.travel");
    private static final RawAnimation IMPACT_ANIM = RawAnimation.begin().thenPlay("animation.solar_arc.impact");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int solarCoreLevel = 0;
    private int solarBlastLevel = 0;
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
            this.solarBlastLevel = ModEnchantments.getSolarBlastLevel(stack);
        } else if (owner instanceof PlayerEntity player) {
            ItemStack held = player.getMainHandStack();
            if (held.getItem() instanceof SunforgedScimitarItem) {
                this.solarCoreLevel = SunforgedScimitarItem.getSolarCoreLevel(held);
                this.solarBlastLevel = ModEnchantments.getSolarBlastLevel(held);
            }
        }

        if (this.solarBlastLevel > 0) {
            float procChance = ModEnchantments.getSolarBlastProcChance(this.solarBlastLevel);
            if (this.random.nextFloat() < procChance) {
                this.setSolarBlast(true);
            }
        }

        if (owner.isTouchingWater() || (world.isRaining() && world.isSkyVisible(owner.getBlockPos()))) {
            this.weakenedByWeather = true;
        }
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SOLAR_BLAST, false);
    }

    public boolean isSolarBlast() {
        return this.dataTracker.get(SOLAR_BLAST);
    }

    public void setSolarBlast(boolean blast) {
        this.dataTracker.set(SOLAR_BLAST, blast);
    }

    public int getSolarBlastLevel() {
        return this.solarBlastLevel;
    }

    public void setSolarBlastLevel(int level) {
        this.solarBlastLevel = level;
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
            if (this.isSolarBlast()) {
                this.getWorld().addParticle(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.03, 0.0);
                this.getWorld().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            } else {
                this.getWorld().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    public int getMaxLifetime() {
        return BASE_LIFETIME + (this.solarCoreLevel * CORE_LIFETIME_BONUS);
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (entity == this.getOwner()) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!this.getWorld().isClient()) {
            Entity hitEntity = entityHitResult.getEntity();
            if (hitEntity == this.getOwner()) {
                return;
            }

            if (hitEntity instanceof LivingEntity entity) {
                boolean wetConditions = this.weakenedByWeather
                        || this.isTouchingWater()
                        || (this.getWorld().isRaining() && this.getWorld().isSkyVisible(this.getBlockPos()));
                float baseDmg = wetConditions ? (this.damage * RAIN_DAMAGE_MULTIPLIER) : this.damage;
                // Solar Blast bonus direct damage: +6.0 per level
                float blastBonus = this.isSolarBlast() ? (6.0f * Math.max(1, this.solarBlastLevel)) : 0.0f;
                float finalDamage = baseDmg + blastBonus;

                int fireSecs = wetConditions ? RAIN_FIRE_SECONDS : (BASE_FIRE_SECONDS + this.solarCoreLevel * CORE_FIRE_BONUS_SECONDS + (this.isSolarBlast() ? (this.solarBlastLevel * 2) : 0));

                if (entity instanceof EverlivingKnightEntity knight && this.getOwner() instanceof PlayerEntity player) {
                    knight.applySolarArcReaction(player);
                } else {
                    DamageSource source = (this.getOwner() instanceof PlayerEntity player)
                            ? this.getDamageSources().playerAttack(player)
                            : (this.getOwner() instanceof LivingEntity living)
                            ? this.getDamageSources().mobAttack(living)
                            : this.getDamageSources().generic();
                    entity.damage(source, finalDamage);
                    entity.setOnFireFor(fireSecs);
                }
            }

            if (this.isSolarBlast()) {
                triggerSolarBlastDetonation(this.getWorld(), this.getPos(), hitEntity);
            } else {
                if (this.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
                    serverWorld.spawnParticles(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    serverWorld.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                }
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 1.6f);
            }
            this.discard();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (!this.getWorld().isClient()) {
            if (this.isSolarBlast()) {
                triggerSolarBlastDetonation(this.getWorld(), this.getPos(), null);
            } else {
                if (this.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                    serverWorld.spawnParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                }
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.8f);
            }
            this.discard();
        }
    }

    /**
     * Executes the explosive Solar Blast AOE detonation, damaging and igniting surrounding mobs in the blast radius.
     */
    private void triggerSolarBlastDetonation(World world, Vec3d center, @Nullable Entity directTarget) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) return;

        double radius = 2.5 + (0.75 * Math.max(1, this.solarBlastLevel));
        double radiusSq = radius * radius;
        Box box = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        float aoeDamage = 8.0f + (3.5f * Math.max(1, this.solarBlastLevel));
        int fireDurationSec = 3 + (this.solarBlastLevel * 2);

        Entity ownerEntity = this.getOwner();
        DamageSource source = (ownerEntity instanceof PlayerEntity player)
                ? world.getDamageSources().playerAttack(player)
                : (ownerEntity instanceof LivingEntity living)
                ? world.getDamageSources().mobAttack(living)
                : world.getDamageSources().generic();

        List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box, e ->
                e != ownerEntity && e != directTarget && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= radiusSq
        );

        for (LivingEntity mob : nearby) {
            if (ownerEntity instanceof PlayerEntity player && player.isTeammate(mob)) {
                continue;
            }
            mob.damage(source, aoeDamage);
            mob.setOnFireFor(fireDurationSec);

            // Outward blast shockwave knockback
            Vec3d diff = mob.getPos().subtract(center);
            double dist = diff.length();
            if (dist > 0.001) {
                Vec3d push = diff.normalize().multiply(0.45 + 0.1 * this.solarBlastLevel).add(0, 0.25, 0);
                mob.addVelocity(push.x, push.y, push.z);
                mob.velocityModified = true;
            }
        }

        // Intense solar explosion VFX
        serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        serverWorld.spawnParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 30, radius * 0.4, 0.4, radius * 0.4, 0.15);
        serverWorld.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 50, radius * 0.5, 0.5, radius * 0.5, 0.2);
        serverWorld.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0.1, 0.1, 0.1, 0);

        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.0f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.1f, 0.7f);
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("SolarCoreLevel", this.solarCoreLevel);
        nbt.putInt("SolarBlastLevel", this.solarBlastLevel);
        nbt.putBoolean("IsSolarBlast", this.isSolarBlast());
        nbt.putInt("LifetimeTicks", this.lifetimeTicks);
        nbt.putBoolean("WeakenedByWeather", this.weakenedByWeather);
        nbt.putFloat("Damage", this.damage);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.solarCoreLevel = nbt.getInt("SolarCoreLevel");
        this.solarBlastLevel = nbt.getInt("SolarBlastLevel");
        this.setSolarBlast(nbt.getBoolean("IsSolarBlast"));
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
