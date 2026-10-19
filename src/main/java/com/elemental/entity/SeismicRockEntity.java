package com.elemental.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public class SeismicRockEntity extends PersistentProjectileEntity implements FlyingItemEntity {

    private float rockDamage = 4.0f;

    public SeismicRockEntity(EntityType<? extends SeismicRockEntity> type, World world) {
        super(type, world);
    }

    public SeismicRockEntity(World world, LivingEntity owner, float damage) {
        super(ModEntities.SEISMIC_ROCK, owner, world);
        this.rockDamage = damage;
    }

    @Override
    public ItemStack getStack() {
        return new ItemStack(Items.COBBLESTONE);
    }

    @Override
    protected ItemStack asItemStack() {
        return new ItemStack(Items.COBBLESTONE);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity target = entityHitResult.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.getDamageSources().thrown(this, owner != null ? owner : this);
        target.damage(source, this.rockDamage);
        this.spawnImpactParticles();
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.spawnImpactParticles();
        this.discard();
    }

    private void spawnImpactParticles() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBBLESTONE)),
                    this.getX(), this.getY(), this.getZ(),
                    15, 0.2, 0.2, 0.2, 0.05
            );
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.0f, 1.2f);
        }
    }
}
