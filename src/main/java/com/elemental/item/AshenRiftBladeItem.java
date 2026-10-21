package com.elemental.item;

import com.elemental.entity.ModEntityTags;
import com.elemental.util.AshenRiftScheduler;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AshenRiftBladeItem extends SwordItem implements GeoItem {

    public static final int DURABILITY = 1561;
    public static final float BASE_ATTACK_DAMAGE = 4.0f;
    public static final float BASE_ATTACK_SPEED = -2.4f;

    public static final int COOLDOWN_COLLAPSE_TICKS = 300; // 15 seconds
    public static final int COOLDOWN_TELEPORT_TICKS = 120; // 6 seconds

    public static final String NBT_COLLAPSE_COOLDOWN_END = "AshenCollapseCooldownEnd";
    public static final String NBT_TELEPORT_COOLDOWN_END = "AshenTeleportCooldownEnd";

    // GeckoLib animation definitions
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.ashen_rift_blade.idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.attack");
    private static final RawAnimation SWING_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.swing");
    private static final RawAnimation COLLAPSE_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.collapse");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop("animation.ashen_rift_blade.charge");
    private static final RawAnimation RELEASE_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.release");
    private static final RawAnimation TELEPORT_ENTRY_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.teleport_entry");
    private static final RawAnimation TELEPORT_EXIT_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.teleport_exit");
    private static final RawAnimation TELEPORT_RECOVER_ANIM = RawAnimation.begin().thenPlay("animation.ashen_rift_blade.teleport_recover");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.ashen_rift_blade.cooldown");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public AshenRiftBladeItem(Settings settings) {
        super(AshenRiftBladeToolMaterial.INSTANCE, (int) BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 8.0, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", BASE_ATTACK_SPEED, EntityAttributeModifier.Operation.ADDITION));
        this.attributeModifiers = builder.build();
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        if (RENDER_PROVIDER_CONSUMER != null) {
            RENDER_PROVIDER_CONSUMER.accept(consumer);
        }
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            Entity entity = state.getData(DataTickets.ENTITY);
            if (stack != null && entity != null) {
                if (isCollapseCoolingDown(entity.getWorld(), stack) || isTeleportCoolingDown(entity.getWorld(), stack)) {
                    return state.setAndContinue(COOLDOWN_ANIM);
                }
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("attack", ATTACK_ANIM)
          .triggerableAnim("swing", SWING_ANIM)
          .triggerableAnim("collapse", COLLAPSE_ANIM)
          .triggerableAnim("charge", CHARGE_ANIM)
          .triggerableAnim("release", RELEASE_ANIM)
          .triggerableAnim("teleport_entry", TELEPORT_ENTRY_ANIM)
          .triggerableAnim("teleport_exit", TELEPORT_EXIT_ANIM)
          .triggerableAnim("teleport_recover", TELEPORT_RECOVER_ANIM)
          .triggerableAnim("cooldown", COOLDOWN_ANIM)
          .triggerableAnim("idle", IDLE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("Ashen Rift Blade");
    }

    @Override
    public Text getName() {
        return Text.literal("Ashen Rift Blade");
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.attributeModifiers;
        }
        return super.getAttributeModifiers(slot);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        World world = attacker.getWorld();
        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(attacker, id, "controller", "attack");
            }
        } else if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            triggerAnim(attacker, id, "controller", "attack");
            serverWorld.spawnParticles(ParticleTypes.ASH, target.getX(), target.getY() + 0.5, target.getZ(), 5, 0.15, 0.2, 0.15, 0.02);
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 0.5, target.getZ(), 3, 0.08, 0.12, 0.08, 0.01);
            serverWorld.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        }
        return true;
    }

    // ==========================================
    // COOLDOWN HELPERS
    // ==========================================

    public static boolean isCollapseCoolingDown(World world, ItemStack stack) {
        if (!stack.hasNbt()) return false;
        long endTick = stack.getNbt().getLong(NBT_COLLAPSE_COOLDOWN_END);
        return world.getTime() < endTick;
    }

    public static int getCollapseRemainingSeconds(World world, ItemStack stack) {
        if (!stack.hasNbt()) return 0;
        long endTick = stack.getNbt().getLong(NBT_COLLAPSE_COOLDOWN_END);
        long remaining = endTick - world.getTime();
        return remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
    }

    public static void applyCollapseCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int ticks) {
        long endTick = world.getTime() + ticks;
        stack.getOrCreateNbt().putLong(NBT_COLLAPSE_COOLDOWN_END, endTick);
        player.getItemCooldownManager().set(stack.getItem(), ticks);
    }

    public static boolean isTeleportCoolingDown(World world, ItemStack stack) {
        if (!stack.hasNbt()) return false;
        long endTick = stack.getNbt().getLong(NBT_TELEPORT_COOLDOWN_END);
        return world.getTime() < endTick;
    }

    public static int getTeleportRemainingSeconds(World world, ItemStack stack) {
        if (!stack.hasNbt()) return 0;
        long endTick = stack.getNbt().getLong(NBT_TELEPORT_COOLDOWN_END);
        long remaining = endTick - world.getTime();
        return remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
    }

    public static void applyTeleportCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int ticks) {
        long endTick = world.getTime() + ticks;
        stack.getOrCreateNbt().putLong(NBT_TELEPORT_COOLDOWN_END, endTick);
        player.getItemCooldownManager().set(stack.getItem(), ticks);
    }

    // ==========================================
    // MOB CLASSIFICATION & EXCLUSIONS
    // ==========================================

    public static boolean isFireProofMob(LivingEntity entity) {
        if (entity.isFireImmune()) return true;
        if (entity.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) return true;
        return false;
    }

    public static boolean isBossMob(LivingEntity entity) {
        if (entity.getType().isIn(ModEntityTags.BOSSES)) return true;
        if (entity instanceof net.minecraft.entity.boss.WitherEntity) return true;
        if (entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity) return true;
        if (entity instanceof net.minecraft.entity.mob.WardenEntity) return true;
        if (entity instanceof net.minecraft.entity.mob.ElderGuardianEntity) return true;
        return false;
    }

    public static boolean isFlyingMob(LivingEntity entity) {
        if (entity instanceof net.minecraft.entity.mob.FlyingEntity) return true; // Ghast, Phantom
        if (entity instanceof net.minecraft.entity.passive.BatEntity) return true;
        if (entity instanceof net.minecraft.entity.passive.BeeEntity) return true;
        if (entity instanceof net.minecraft.entity.passive.AllayEntity) return true;
        if (entity instanceof net.minecraft.entity.passive.ParrotEntity) return true;
        if (entity instanceof net.minecraft.entity.mob.VexEntity) return true;
        if (entity instanceof net.minecraft.entity.mob.BlazeEntity) return true;
        if (entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity) return true;
        if (entity instanceof net.minecraft.entity.boss.WitherEntity) return true;
        if (entity.isFallFlying()) return true;
        if (entity.hasNoGravity()) return true;
        return false;
    }

    public static boolean isValidCollapseTarget(LivingEntity target, PlayerEntity player) {
        // Exclude player
        if (target == null || target == player || target instanceof PlayerEntity) {
            return false;
        }
        if (!target.isAlive() || target.isRemoved() || target.isSpectator()) {
            return false;
        }
        // Exclude fire-proof mobs
        if (isFireProofMob(target)) {
            return false;
        }
        // Exclude boss mobs
        if (isBossMob(target)) {
            return false;
        }
        // Exclude flying mobs
        if (isFlyingMob(target)) {
            return false;
        }
        return true;
    }

    // ==========================================
    // ABILITY 1: RIFT COLLAPSE (PRESS R)
    // ==========================================

    public static void performRiftCollapse(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }

        ItemStack stack = player.getMainHandStack();
        Hand hand = Hand.MAIN_HAND;
        if (!(stack.getItem() instanceof AshenRiftBladeItem)) {
            stack = player.getOffHandStack();
            hand = Hand.OFF_HAND;
            if (!(stack.getItem() instanceof AshenRiftBladeItem)) {
                return;
            }
        }

        ServerWorld world = player.getServerWorld();

        // Edge case: repeated ability use while cooling down
        if (isCollapseCoolingDown(world, stack)) {
            int remainingSec = getCollapseRemainingSeconds(world, stack);
            player.sendMessage(Text.literal("§c✦ Rift Collapse is cooling down! (" + remainingSec + "s remaining)").formatted(Formatting.RED), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.4f);
            if (stack.getItem() instanceof AshenRiftBladeItem bladeItem) {
                bladeItem.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "controller", "cooldown");
            }
            return;
        }

        // Query mobs within a 6-block radius around the player
        Box box = new Box(player.getX() - 6.0, player.getY() - 3.0, player.getZ() - 6.0,
                player.getX() + 6.0, player.getY() + 4.0, player.getZ() + 6.0);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e ->
                isValidCollapseTarget(e, player) && e.squaredDistanceTo(player) <= 36.0
        );

        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("§7✦ No valid mobs in 6-block radius to collapse.").formatted(Formatting.GRAY), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.6f);
            return;
        }

        // Visual and auditory rift shockwave
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 1.2f, 0.6f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.4f, 1.8f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.8f, 0.7f);

        world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.1, player.getZ(), 80, 2.8, 0.2, 2.8, 0.02);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 60, 2.8, 0.2, 2.8, 0.01);

        // Sort mobs so closest mobs are processed first
        targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));

        // First, mobs closest to the player should be knocked back/rebounded away from the player
        for (LivingEntity target : targets) {
            Vec3d diff = target.getPos().subtract(player.getPos());
            Vec3d horiz = new Vec3d(diff.x, 0, diff.z);
            double dist = Math.sqrt(target.squaredDistanceTo(player));

            if (horiz.lengthSquared() < 0.001) {
                double randomAngle = world.random.nextDouble() * 2 * Math.PI;
                horiz = new Vec3d(Math.cos(randomAngle), 0, Math.sin(randomAngle));
            } else {
                horiz = horiz.normalize();
            }

            // Strong rebound impulse away from the player
            double pushSpeed = Math.max(0.65, (6.5 - dist) * 0.22 + 0.5);
            target.setVelocity(horiz.x * pushSpeed, 0.28, horiz.z * pushSpeed);
            target.velocityModified = true;

            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        }

        // Then, delegate creation of hole beneath each affected mob, lava spawn after 2s, and refill after 5s
        AshenRiftScheduler.startRiftCollapse(world, player, targets);

        // Trigger GeckoLib collapse animation
        if (stack.getItem() instanceof AshenRiftBladeItem bladeItem) {
            bladeItem.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "controller", "collapse");
        }

        // Apply 15-second cooldown and durability loss
        applyCollapseCooldown(world, player, stack, COOLDOWN_COLLAPSE_TICKS);
        Hand finalHand = hand;
        stack.damage(2, player, p -> p.sendToolBreakStatus(finalHand));
        player.sendMessage(Text.literal("§6✦ Rift Collapse unleashed! Ground shattering...").formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    // ==========================================
    // ABILITY 2: RIFT TELEPORT (HOLD RIGHT CLICK)
    // ==========================================

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (stack.getDamage() >= stack.getMaxDamage() - 1) {
            return TypedActionResult.fail(stack);
        }

        // Check teleport cooldown
        if (isTeleportCoolingDown(world, stack)) {
            if (!world.isClient()) {
                int remaining = getTeleportRemainingSeconds(world, stack);
                user.sendMessage(Text.literal("§c✦ Rift Teleport is cooling down! (" + remaining + "s remaining)").formatted(Formatting.RED), true);
                if (user instanceof ServerPlayerEntity serverPlayer) {
                    triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerWorld) world), "controller", "cooldown");
                }
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.4f);
            return TypedActionResult.fail(stack);
        }

        user.setCurrentHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.7f, 1.1f);
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerWorld) world), "controller", "charge");
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Visual rift portal particles swirling around player while holding
        if (chargeTicks % 3 == 0) {
            world.addParticle(ParticleTypes.PORTAL,
                    user.getX() + (world.random.nextDouble() - 0.5) * 1.0,
                    user.getY() + 0.1,
                    user.getZ() + (world.random.nextDouble() - 0.5) * 1.0,
                    0, 0.08, 0);
            world.addParticle(ParticleTypes.ASH,
                    user.getX() + (world.random.nextDouble() - 0.5) * 1.2,
                    user.getY() + 0.4,
                    user.getZ() + (world.random.nextDouble() - 0.5) * 1.2,
                    0, 0.05, 0);
        }

        if (chargeTicks % 12 == 0) {
            float pitch = Math.min(2.0f, 0.7f + (chargeTicks / 30.0f) * 0.6f);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.4f, pitch);
        }

        if (chargeTicks == 20 && user instanceof PlayerEntity player && world.isClient()) {
            player.sendMessage(Text.literal("§d✦ RIFT READY — Release to Teleport!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return;
        }

        if (!player.isAlive() || player.isSpectator()) {
            return;
        }

        ServerWorld serverWorld = player.getServerWorld();
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Minimum charge threshold (5 ticks = 0.25s) - canceled/early release
        if (chargeTicks < 5) {
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "idle");
            return;
        }

        // Raycast to aim at a targeted block (40 block maximum range)
        BlockHitResult blockHit = raycastAimedBlock(player, 40.0);

        // Edge case: no aimed block
        if (blockHit == null || blockHit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("§7✦ No block targeted — Rift Teleport cancelled.").formatted(Formatting.GRAY), true);
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.5f);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "idle");
            return;
        }

        BlockPos aimedBlockPos = blockHit.getBlockPos();

        // Edge case: invalid/unsafe destination, unloaded chunk, or inside active rift
        BlockPos safePos = findSafeDestination(serverWorld, aimedBlockPos);
        if (safePos == null) {
            player.sendMessage(Text.literal("§c✦ Unsafe rift destination! Teleport cancelled.").formatted(Formatting.RED), true);
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.2f);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "idle");
            return;
        }

        Vec3d initialPos = player.getPos();
        Vec3d destPos = new Vec3d(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);

        // Server-side distance check
        if (player.squaredDistanceTo(destPos) > 45.0 * 45.0) {
            player.sendMessage(Text.literal("§c✦ Destination out of range! Teleport cancelled.").formatted(Formatting.RED), true);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "idle");
            return;
        }

        // Spawn portal effect beneath player's legs at initial position
        serverWorld.spawnParticles(ParticleTypes.PORTAL, initialPos.x, initialPos.y + 0.05, initialPos.z, 64, 0.6, 0.05, 0.6, 0.1);
        serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, initialPos.x, initialPos.y + 0.05, initialPos.z, 32, 0.6, 0.05, 0.6, 0.05);
        serverWorld.spawnParticles(ParticleTypes.ASH, initialPos.x, initialPos.y + 0.1, initialPos.z, 40, 0.4, 0.05, 0.4, 0.02);

        serverWorld.playSound(null, initialPos.x, initialPos.y, initialPos.z,
                SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 1.0f, 1.2f);
        serverWorld.playSound(null, initialPos.x, initialPos.y, initialPos.z,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 0.6f);

        // Schedule falling into initial portal and emergence at destination 5 blocks above
        AshenRiftScheduler.scheduleTeleport(serverWorld, player, initialPos, destPos);

        // Trigger GeckoLib teleport entry animation
        triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "teleport_entry");

        // Apply cooldown and damage weapon durability
        applyTeleportCooldown(serverWorld, player, stack, COOLDOWN_TELEPORT_TICKS);
        stack.damage(2, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
        player.sendMessage(Text.literal("§5✦ Rift Teleport engaged!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
    }

    public static BlockHitResult raycastAimedBlock(ServerPlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookDir.multiply(maxDistance));

        return player.getServerWorld().raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
        ));
    }

    public static BlockPos findSafeDestination(ServerWorld world, BlockPos aimedPos) {
        if (aimedPos == null || !world.isChunkLoaded(aimedPos.getX() >> 4, aimedPos.getZ() >> 4)) return null;

        // Desired position: 5 blocks above aimed position
        BlockPos desiredPos = aimedPos.up(5);
        if (world.isChunkLoaded(desiredPos.getX() >> 4, desiredPos.getZ() >> 4)) {
            if (isPassable(world, desiredPos) && isPassable(world, desiredPos.up()) && !isHazard(world, desiredPos)
                    && !AshenRiftScheduler.isBlockInActiveRift(desiredPos) && !AshenRiftScheduler.isBlockInActiveRift(desiredPos.down())) {
                return desiredPos;
            }
        }

        // If obstructed (e.g. cavern ceiling), find highest clear clearance between +1 and +5
        for (int h = 5; h >= 1; h--) {
            BlockPos checkPos = aimedPos.up(h);
            if (world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) {
                if (isPassable(world, checkPos) && isPassable(world, checkPos.up()) && !isHazard(world, checkPos)
                        && !AshenRiftScheduler.isBlockInActiveRift(checkPos) && !AshenRiftScheduler.isBlockInActiveRift(checkPos.down())) {
                    return checkPos;
                }
            }
        }

        return null;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world instanceof ServerWorld serverWorld) {
            GeoItem.getOrAssignId(stack, serverWorld);
        }
    }

    private static boolean isPassable(World world, BlockPos pos) {
        if (!world.isInBuildLimit(pos)) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return true;
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isHazard(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return true;
        return world.getFluidState(pos).isIn(FluidTags.LAVA);
    }

    // ==========================================
    // TOOLTIPS
    // ==========================================

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("§7A dark blade forged from shattered spatial rifts."));
        tooltip.add(Text.literal("§6[R] Rift Collapse: §7Rebounds mobs, digs rifts, spawns lava, then refills.").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§d[Hold Right-Click] Rift Teleport: §7Fall into a rift and emerge 5 blocks above targeted block.").formatted(Formatting.LIGHT_PURPLE));

        if (world != null) {
            if (isCollapseCoolingDown(world, stack)) {
                int rem = getCollapseRemainingSeconds(world, stack);
                tooltip.add(Text.literal("§c✦ Rift Collapse Cooldown: " + rem + "s").formatted(Formatting.RED));
            } else {
                tooltip.add(Text.literal("§a✦ Rift Collapse: Ready").formatted(Formatting.GREEN));
            }

            if (isTeleportCoolingDown(world, stack)) {
                int rem = getTeleportRemainingSeconds(world, stack);
                tooltip.add(Text.literal("§c✦ Rift Teleport Cooldown: " + rem + "s").formatted(Formatting.RED));
            } else {
                tooltip.add(Text.literal("§a✦ Rift Teleport: Ready").formatted(Formatting.GREEN));
            }
        }
    }
}
