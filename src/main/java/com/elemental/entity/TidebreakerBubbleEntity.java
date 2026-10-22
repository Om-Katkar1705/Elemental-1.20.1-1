package com.elemental.entity;

import com.elemental.item.TidebreakerTridentItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class TidebreakerBubbleEntity extends Entity {

    // ==================== CONFIGURATION VARIABLES ====================
    public static double UPWARD_SPEED = 0.28;
    public static float NETHER_DURATION_MULTIPLIER = 0.5f;

    private static final TrackedData<Integer> TARGET_ID = DataTracker.registerData(TidebreakerBubbleEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int targetId = -1;
    private UUID ownerUuid = null;
    private Vec3d originPos;
    private int lifetimeTicks = 0;
    private int maxDurationTicks = TidebreakerTridentItem.BASE_BUBBLE_DURATION;

    public TidebreakerBubbleEntity(EntityType<? extends TidebreakerBubbleEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = true;
    }

    public TidebreakerBubbleEntity(World world, LivingEntity target, PlayerEntity owner) {
        this(ModEntities.TIDEBREAKER_BUBBLE, world);
        this.targetId = target.getId();
        this.dataTracker.set(TARGET_ID, this.targetId);
        this.ownerUuid = owner.getUuid();
        this.originPos = target.getPos();
        this.setPosition(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());

        int duration = TidebreakerTridentItem.BASE_BUBBLE_DURATION;
        double multiplier = TidebreakerTridentItem.getBiomeBubbleMultiplier(target);
        duration = (int) (duration * multiplier);

        // Ultrawarm dimensions (such as the Nether): duration reduction
        if (world.getDimension().ultrawarm()) {
            duration = (int) (duration * NETHER_DURATION_MULTIPLIER);
        }
        this.maxDurationTicks = duration;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(TARGET_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        this.lifetimeTicks++;

        if (this.getWorld().isClient()) {
            return;
        }

        Entity targetEntity = this.getWorld().getEntityById(this.targetId);
        if (!(targetEntity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
            this.discard();
            return;
        }

        // Owner validity check
        if (this.ownerUuid != null) {
            PlayerEntity owner = this.getWorld().getPlayerByUuid(this.ownerUuid);
            if (owner == null || !owner.isAlive()) {
                this.discard();
                return;
            }
        }

        // Maximum distance from origin check
        if (this.originPos != null && target.squaredDistanceTo(this.originPos) > 2025.0) {
            this.discard();
            return;
        }

        // Expiration check or maximum lift height check
        double heightGained = this.originPos != null ? target.getY() - this.originPos.y : 0.0;
        if (this.lifetimeTicks >= this.maxDurationTicks || heightGained >= TidebreakerTridentItem.BUBBLE_LIFT_HEIGHT) {
            popBubble(target);
            return;
        }

        // Controlled upward motion (lifts smoothly ~30-40 blocks across duration)
        double upwardSpeed = UPWARD_SPEED;
        target.setVelocity(target.getVelocity().x * 0.2, upwardSpeed, target.getVelocity().z * 0.2);
        target.velocityModified = true;
        target.fallDistance = 0.0f;
        if (target instanceof ServerPlayerEntity targetPlayer) {
            targetPlayer.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(targetPlayer));
        }

        // Position bubble at target center
        this.setPosition(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());

        // Spawn particles around the bubble
        ServerWorld serverWorld = (ServerWorld) this.getWorld();
        boolean isUltrawarm = serverWorld.getDimension().ultrawarm();
        double radius = Math.max(0.8, target.getWidth() * 0.85);

        // Water bubbles and upward current (every 2 ticks to cut packet rate in half while maintaining smooth visuals)
        if (this.lifetimeTicks % 2 == 0) {
            serverWorld.spawnParticles(ParticleTypes.BUBBLE, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                    4, radius, target.getHeight() * 0.4, radius, 0.02);
            serverWorld.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, target.getX(), target.getY(), target.getZ(),
                    2, radius * 0.5, 0.1, radius * 0.5, 0.05);

            if (isUltrawarm) {
                // Nether / ultrawarm mix: dry smoke and flame particles along with water particles
                serverWorld.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                        2, radius, target.getHeight() * 0.4, radius, 0.02);
                serverWorld.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                        1, radius * 0.5, target.getHeight() * 0.3, radius * 0.5, 0.01);
            }
        }
        if (!isUltrawarm && this.lifetimeTicks % 4 == 0) {
            serverWorld.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                    3, radius, target.getHeight() * 0.3, radius, 0.05);
        }
    }

    private void popBubble(LivingEntity target) {
        if (!this.getWorld().isClient()) {
            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            // Water bubble blast visual & audio
            serverWorld.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                    35, 0.8, 0.8, 0.8, 0.2);
            serverWorld.spawnParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(),
                    20, 0.6, 0.6, 0.6, 0.1);
            serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.2f);
            serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.PLAYERS, 1.0f, 1.0f);

            // Pop damage
            PlayerEntity owner = this.ownerUuid != null ? serverWorld.getPlayerByUuid(this.ownerUuid) : null;
            if (owner != null) {
                target.damage(serverWorld.getDamageSources().playerAttack(owner), TidebreakerTridentItem.BUBBLE_EXPLOSION_DAMAGE);
            } else {
                target.damage(serverWorld.getDamageSources().magic(), TidebreakerTridentItem.BUBBLE_EXPLOSION_DAMAGE);
            }

            if (target instanceof ServerPlayerEntity targetPlayer) {
                targetPlayer.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(targetPlayer));
            }
        }
        this.discard();
    }

    public int getTargetId() {
        return this.dataTracker.get(TARGET_ID);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.targetId = nbt.getInt("TargetId");
        this.dataTracker.set(TARGET_ID, this.targetId);
        if (nbt.containsUuid("OwnerUuid")) {
            this.ownerUuid = nbt.getUuid("OwnerUuid");
        }
        this.lifetimeTicks = nbt.getInt("LifetimeTicks");
        this.maxDurationTicks = nbt.getInt("MaxDurationTicks");
        if (nbt.contains("OriginX")) {
            this.originPos = new Vec3d(nbt.getDouble("OriginX"), nbt.getDouble("OriginY"), nbt.getDouble("OriginZ"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("TargetId", this.targetId);
        if (this.ownerUuid != null) {
            nbt.putUuid("OwnerUuid", this.ownerUuid);
        }
        nbt.putInt("LifetimeTicks", this.lifetimeTicks);
        nbt.putInt("MaxDurationTicks", this.maxDurationTicks);
        if (this.originPos != null) {
            nbt.putDouble("OriginX", this.originPos.x);
            nbt.putDouble("OriginY", this.originPos.y);
            nbt.putDouble("OriginZ", this.originPos.z);
        }
    }
}
