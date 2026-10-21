package com.elemental.entity;

import com.elemental.network.SeismicColossusNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SeismicThrownBlockEntity extends ProjectileEntity {

    private static final TrackedData<Integer> BLOCK_STATE_ID = DataTracker.registerData(SeismicThrownBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private float impactDamage = 12.0f;
    private int lifeTicks = 0;

    public SeismicThrownBlockEntity(EntityType<? extends SeismicThrownBlockEntity> entityType, World world) {
        super(entityType, world);
    }

    public SeismicThrownBlockEntity(World world, LivingEntity owner, BlockState blockState, float damage) {
        super(ModEntities.SEISMIC_THROWN_BLOCK, world);
        this.setOwner(owner);
        this.setBlockState(blockState);
        this.impactDamage = damage;
        this.setPos(owner.getX(), owner.getY() + 3.0, owner.getZ());
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(BLOCK_STATE_ID, Block.getRawIdFromState(Blocks.COBBLESTONE.getDefaultState()));
    }

    public BlockState getBlockState() {
        int id = this.dataTracker.get(BLOCK_STATE_ID);
        BlockState state = Block.getStateFromRawId(id);
        return state != null ? state : Blocks.COBBLESTONE.getDefaultState();
    }

    public void setBlockState(BlockState state) {
        this.dataTracker.set(BLOCK_STATE_ID, Block.getRawIdFromState(state != null ? state : Blocks.COBBLESTONE.getDefaultState()));
    }

    @Override
    public void tick() {
        super.tick();
        this.lifeTicks++;

        if (this.lifeTicks > 200) {
            this.discard();
            return;
        }

        Vec3d velocity = this.getVelocity();
        HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onCollision(hitResult);
            return;
        }

        double nextX = this.getX() + velocity.x;
        double nextY = this.getY() + velocity.y;
        double nextZ = this.getZ() + velocity.z;

        this.setPos(nextX, nextY, nextZ);

        // Apply gravity and drag
        if (!this.hasNoGravity()) {
            this.setVelocity(velocity.x * 0.99, velocity.y - 0.045, velocity.z * 0.99);
        }

        // Flight dust particles
        if (this.getWorld().isClient()) {
            this.getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, getBlockState()),
                    this.getX(), this.getY(), this.getZ(),
                    (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        Entity target = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        if (target == owner) return;

        DamageSource source = this.getDamageSources().thrown(this, owner != null ? owner : this);
        target.damage(source, this.impactDamage);

        // Knockback away from projectile trajectory
        Vec3d vel = this.getVelocity().normalize().multiply(0.8);
        target.addVelocity(vel.x, 0.4, vel.z);
        target.velocityModified = true;

        this.triggerImpact();
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.triggerImpact();
        this.discard();
    }

    private void triggerImpact() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            BlockState state = getBlockState();
            serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    this.getX(), this.getY(), this.getZ(),
                    35, 0.5, 0.5, 0.5, 0.15);
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(),
                    1, 0, 0, 0, 0);

            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.8f);
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.8f, 1.2f);

            // Screen shake nearby
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 16.0, 1.2f, 10);
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("BlockStateId", this.dataTracker.get(BLOCK_STATE_ID));
        nbt.putFloat("ImpactDamage", this.impactDamage);
        nbt.putInt("LifeTicks", this.lifeTicks);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BlockStateId")) {
            this.dataTracker.set(BLOCK_STATE_ID, nbt.getInt("BlockStateId"));
        }
        if (nbt.contains("ImpactDamage")) {
            this.impactDamage = nbt.getFloat("ImpactDamage");
        }
        this.lifeTicks = nbt.getInt("LifeTicks");
    }
}
