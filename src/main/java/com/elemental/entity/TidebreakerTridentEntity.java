package com.elemental.entity;

import com.elemental.item.ModItems;
import com.elemental.item.TidebreakerTridentItem;
import net.minecraft.enchantment.EnchantmentHelper;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TidebreakerTridentEntity extends PersistentProjectileEntity implements GeoEntity {

    private static final RawAnimation TRAVEL_ANIM = RawAnimation.begin().thenLoop("animation.tidebreaker_trident.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final TrackedData<Byte> LOYALTY = DataTracker.registerData(TidebreakerTridentEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Boolean> ENCHANTED = DataTracker.registerData(TidebreakerTridentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private ItemStack tridentStack = new ItemStack(ModItems.TIDEBREAKER_TRIDENT);
    private boolean dealtDamage;
    public int returnTimer;
    public int returnDelay = 0;
    public java.util.UUID stuckEntityUuid = null;
    public Vec3d stuckOffset = Vec3d.ZERO;

    public void stickToEntity(Entity entity, Vec3d offset, int delayTicks) {
        this.stuckEntityUuid = entity.getUuid();
        this.stuckOffset = offset;
        this.returnDelay = delayTicks;
        this.dealtDamage = true;
    }

    public TidebreakerTridentEntity(EntityType<? extends TidebreakerTridentEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            return state.setAndContinue(TRAVEL_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public TidebreakerTridentEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.TIDEBREAKER_TRIDENT_PROJECTILE, owner, world);
        this.tridentStack = stack.copy();
        this.dataTracker.set(LOYALTY, (byte) 2);
        this.dataTracker.set(ENCHANTED, stack.hasGlint());
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LOYALTY, (byte) 2);
        this.dataTracker.startTracking(ENCHANTED, false);
    }

    @Override
    public void tick() {
        if (this.returnDelay > 0) {
            this.returnDelay--;
            if (this.stuckEntityUuid != null && this.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                Entity stuckTarget = serverWorld.getEntity(this.stuckEntityUuid);
                if (stuckTarget != null && stuckTarget.isAlive()) {
                    Vec3d targetPos = stuckTarget.getPos().add(this.stuckOffset);
                    this.setPos(targetPos.x, targetPos.y, targetPos.z);
                    this.setVelocity(Vec3d.ZERO);
                }
            }
            super.tick();
            return;
        }

        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity owner = this.getOwner();
        int loyalty = this.dataTracker.get(LOYALTY);
        if (loyalty > 0 && (this.dealtDamage || this.isNoClip()) && owner != null) {
            if (!this.isOwnerAlive()) {
                if (!this.getWorld().isClient() && this.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED) {
                    this.dropStack(this.asItemStack(), 0.1f);
                }
                this.discard();
            } else {
                this.setNoClip(true);
                Vec3d ownerEyes = owner.getEyePos().subtract(this.getPos());
                this.setPos(this.getX(), this.getY() + ownerEyes.y * 0.015 * (double) loyalty, this.getZ());
                if (this.getWorld().isClient()) {
                    this.lastRenderY = this.getY();
                }

                double speed = 0.05 * (double) loyalty;
                this.setVelocity(this.getVelocity().multiply(0.95).add(ownerEyes.normalize().multiply(speed)));

                if (this.returnTimer == 0) {
                    this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0f, 1.0f);
                }
                this.returnTimer++;

                // Return to owner on arrival
                if (!this.getWorld().isClient() && this.squaredDistanceTo(owner) < 2.25) {
                    if (owner instanceof PlayerEntity player) {
                        boolean inserted = player.getInventory().insertStack(this.asItemStack());
                        if (!inserted && this.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED) {
                            player.dropItem(this.asItemStack(), false);
                        }
                        this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
                        this.discard();
                        return;
                    }
                }
            }
        }

        super.tick();

        // Visible in flight: spawn water bubble and splash particles
        if (this.getWorld().isClient() && !this.inGround && !this.isNoClip()) {
            this.getWorld().addParticle(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.getWorld().addParticle(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    private boolean isOwnerAlive() {
        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            return !(owner instanceof ServerPlayerEntity player) || !player.isSpectator();
        }
        return false;
    }

    @Override
    protected ItemStack asItemStack() {
        return this.tridentStack.copy();
    }

    public boolean isEnchanted() {
        return this.dataTracker.get(ENCHANTED);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity target = entityHitResult.getEntity();
        float damage = TidebreakerTridentItem.PROJECTILE_BASE_DAMAGE;

        // Apply biome bonus damage
        damage += TidebreakerTridentItem.getBiomeMeleeBonus(this);

        Entity owner = this.getOwner();
        DamageSource source = this.getDamageSources().trident(this, (owner == null ? this : owner));
        this.dealtDamage = true;

        if (target.damage(source, damage)) {
            if (target.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (target instanceof LivingEntity living) {
                if (owner instanceof LivingEntity livingOwner) {
                    EnchantmentHelper.onUserDamaged(living, livingOwner);
                    EnchantmentHelper.onTargetDamaged(livingOwner, living);
                }
                this.onHit(living);

                // Water Bubble Levitation special ability trigger
                if (!this.getWorld().isClient() && owner instanceof PlayerEntity player) {
                    TidebreakerTridentItem.tryApplyBubbleAbility(player, living, this.tridentStack);
                }
            }
        }

        this.setVelocity(this.getVelocity().multiply(-0.01, -0.1, -0.01));
        this.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.dealtDamage = true;
        this.playSound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1.0f, 1.0f);
    }

    @Override
    protected SoundEvent getHitSound() {
        return SoundEvents.ITEM_TRIDENT_HIT_GROUND;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Trident", 10)) {
            this.tridentStack = ItemStack.fromNbt(nbt.getCompound("Trident"));
        }
        this.dealtDamage = nbt.getBoolean("DealtDamage");
        this.dataTracker.set(LOYALTY, (byte) 2);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Trident", this.tridentStack.writeNbt(new NbtCompound()));
        nbt.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void age() {
        int loyalty = this.dataTracker.get(LOYALTY);
        if (this.pickupType != PersistentProjectileEntity.PickupPermission.ALLOWED || loyalty <= 0) {
            super.age();
        }
    }

    @Override
    protected float getDragInWater() {
        return 0.99f;
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return super.shouldRender(cameraX, cameraY, cameraZ);
    }
}
