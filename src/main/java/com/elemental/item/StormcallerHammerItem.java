package com.elemental.item;

import com.elemental.entity.ModEntityTags;
import com.elemental.util.StormcallerLightningScheduler;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.particle.ParticleTypes;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StormcallerHammerItem extends PickaxeItem implements GeoItem {

    public static final int DURABILITY = 1500;
    public static final float BASE_ATTACK_DAMAGE = 6.0f;
    public static final float BASE_ATTACK_SPEED = -3.3f;
    public static final double BONUS_KNOCKBACK = 1.5;

    public static final int COOLDOWN_TICKS = 300; // 15 seconds
    public static final String NBT_COOLDOWN_END = "StormcallerCooldownEnd";
    public static final String NBT_COOLDOWN_NOTIFIED = "StormcallerNotifiedCooldown";

    public static final UUID REDUCED_DAMAGE_MODIFIER_UUID = UUID.fromString("e5d2630a-9d32-475a-b68e-73c38cb51921");
    public static final EntityAttributeModifier REDUCED_DAMAGE_MODIFIER = new EntityAttributeModifier(
            REDUCED_DAMAGE_MODIFIER_UUID,
            "Stormcaller Cooldown Reduced Damage",
            -5.0, // Deals less damage than normal during cooldown
            EntityAttributeModifier.Operation.ADDITION
    );

    // GeckoLib animation definitions
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.stormcaller_hammer.idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.stormcaller_hammer.attack");
    private static final RawAnimation THUNDERCLAP_ANIM = RawAnimation.begin().thenPlay("animation.stormcaller_hammer.thunderclap").thenLoop("animation.stormcaller_hammer.cooldown");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop("animation.stormcaller_hammer.charge");
    private static final RawAnimation RELEASE_ANIM = RawAnimation.begin().thenPlay("animation.stormcaller_hammer.release").thenLoop("animation.stormcaller_hammer.cooldown");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.stormcaller_hammer.cooldown");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public StormcallerHammerItem(Settings settings) {
        super(StormcallerHammerToolMaterial.INSTANCE, (int) BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 10.0, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", BASE_ATTACK_SPEED, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, new EntityAttributeModifier(
                UUID.fromString("9352e071-70e2-4bd5-b28e-5b12da6f23a8"), "Weapon knockback modifier", BONUS_KNOCKBACK, EntityAttributeModifier.Operation.ADDITION));
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
                if (isCoolingDown(entity.getWorld(), stack)) {
                    return state.setAndContinue(COOLDOWN_ANIM);
                }
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("attack", ATTACK_ANIM)
          .triggerableAnim("thunderclap", THUNDERCLAP_ANIM)
          .triggerableAnim("charge", CHARGE_ANIM)
          .triggerableAnim("release", RELEASE_ANIM)
          .triggerableAnim("cooldown", COOLDOWN_ANIM)
          .triggerableAnim("idle", IDLE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("Stormcaller Hammer");
    }

    @Override
    public Text getName() {
        return Text.literal("Stormcaller Hammer");
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.attributeModifiers;
        }
        return super.getAttributeModifiers(slot);
    }

    // ==========================================
    // COOLDOWN & REDUCED DAMAGE HANDLING
    // ==========================================

    public static void applyCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int ticks) {
        long endTick = world.getTime() + ticks;
        stack.getOrCreateNbt().putLong(NBT_COOLDOWN_END, endTick);
        stack.getOrCreateNbt().putBoolean(NBT_COOLDOWN_NOTIFIED, true);
    }

    public static boolean isCoolingDown(World world, ItemStack stack) {
        if (!stack.hasNbt()) return false;
        long endTick = stack.getNbt().getLong(NBT_COOLDOWN_END);
        return world.getTime() < endTick;
    }

    public static int getRemainingCooldownSeconds(World world, ItemStack stack) {
        if (!stack.hasNbt()) return 0;
        long endTick = stack.getNbt().getLong(NBT_COOLDOWN_END);
        long remaining = endTick - world.getTime();
        return remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (!world.isClient() && entity instanceof PlayerEntity player) {
            boolean isHolding = selected || player.getOffHandStack() == stack;
            boolean coolingDown = isCoolingDown(world, stack);
            boolean shouldReduceDamage = isHolding && coolingDown;

            EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (attr != null) {
                boolean hasMod = attr.hasModifier(REDUCED_DAMAGE_MODIFIER);
                if (shouldReduceDamage && !hasMod) {
                    attr.addTemporaryModifier(REDUCED_DAMAGE_MODIFIER);
                } else if (!shouldReduceDamage && hasMod) {
                    attr.removeModifier(REDUCED_DAMAGE_MODIFIER);
                    // Notify player on cooldown expiry
                    if (isHolding && stack.hasNbt() && stack.getNbt().getBoolean(NBT_COOLDOWN_NOTIFIED)) {
                        stack.getNbt().remove(NBT_COOLDOWN_NOTIFIED);
                        player.sendMessage(Text.literal("§a⚡ Stormcaller Hammer fully recharged!").formatted(Formatting.GREEN, Formatting.BOLD), true);
                        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
                        if (player instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
                            triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, serverWorld), "controller", "idle");
                        }
                    }
                }
            }
        }
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
        } else {
            if (attacker instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
                triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, serverWorld), "controller", "attack");
            }
            boolean onCooldown = isCoolingDown(world, stack);
            if (onCooldown) {
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.9f, 0.9f);
            } else {
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 1.2f);
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.4f, 1.6f);
                if (world instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(),
                            12, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }
        return true;
    }

    // ==========================================
    // ABILITY 1: THUNDERCLAP (PRESS R)
    // ==========================================

    public static void performThunderclap(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        Hand hand = Hand.MAIN_HAND;
        if (!(stack.getItem() instanceof StormcallerHammerItem)) {
            stack = player.getOffHandStack();
            hand = Hand.OFF_HAND;
            if (!(stack.getItem() instanceof StormcallerHammerItem)) {
                return;
            }
        }

        ServerWorld world = player.getServerWorld();

        StormcallerHammerItem hammer = (StormcallerHammerItem) stack.getItem();

        // Edge case: repeated ability use while cooling down
        if (isCoolingDown(world, stack)) {
            int remainingSec = getRemainingCooldownSeconds(world, stack);
            player.sendMessage(Text.literal("§c⚡ Thunderclap is cooling down! (" + remainingSec + "s remaining)").formatted(Formatting.RED), true);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.4f);
            hammer.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "controller", "cooldown");
            return;
        }

        // Trigger GeckoLib Thunderclap smash animation
        hammer.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "controller", "thunderclap");

        // Audio impact
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.6f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0f, 0.5f);

        // Visual earthquake impact particles in 4-block radius without modifying terrain/blocks
        for (int r = 1; r <= 4; r++) {
            int points = r * 12;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double px = player.getX() + r * Math.cos(angle);
                double pz = player.getZ() + r * Math.sin(angle);
                double py = player.getY() + 0.1;
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 1, 0.05, 0.08, 0.05, 0.02);
                world.spawnParticles(ParticleTypes.POOF, px, py, pz, 1, 0.08, 0.05, 0.08, 0.03);
                world.spawnParticles(ParticleTypes.CRIT, px, py + 0.2, pz, 1, 0.1, 0.15, 0.1, 0.05);
            }
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.5, 0.2, 0.5, 0.0);
        world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);

        // Affect mobs inside 4-block radius
        Box box = new Box(player.getX() - 4.0, player.getY() - 2.0, player.getZ() - 4.0,
                player.getX() + 4.0, player.getY() + 3.0, player.getZ() + 4.0);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity target : targets) {
            // Do NOT launch or affect the player
            if (target == player || target instanceof PlayerEntity) {
                continue;
            }

            // Ensure strictly inside 4-block radius
            if (target.squaredDistanceTo(player) > 16.0) {
                continue;
            }

            boolean heavy = isHeavyMob(target);
            // Heavy mobs receive reduced launch effect but must still be affected
            double vy = heavy ? 0.38 : 0.88;

            Vec3d diff = target.getPos().subtract(player.getPos());
            Vec3d horiz = new Vec3d(diff.x, 0, diff.z);
            double vx = 0;
            double vz = 0;
            if (horiz.lengthSquared() > 0.001) {
                Vec3d norm = horiz.normalize().multiply(heavy ? 0.20 : 0.45);
                vx = norm.x;
                vz = norm.z;
            }

            target.setVelocity(vx, vy, vz);
            target.velocityModified = true;

            // Controlled earthquake damage
            target.damage(world.getDamageSources().playerAttack(player), 7.0f);
            world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.2, 0.3, 0.2, 0.1);
        }

        // Apply 15-second cooldown and durability loss
        applyCooldown(world, player, stack, COOLDOWN_TICKS);
        Hand finalHand = hand;
        stack.damage(2, player, p -> p.sendToolBreakStatus(finalHand));
        player.sendMessage(Text.literal("§6⚡ Thunderclap! §cStormcaller cooling down (15s) - Damage reduced").formatted(Formatting.GOLD), true);
    }

    public static boolean isHeavyMob(LivingEntity entity) {
        if (entity instanceof net.minecraft.entity.boss.WitherEntity
                || entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity
                || entity instanceof net.minecraft.entity.mob.WardenEntity
                || entity instanceof net.minecraft.entity.mob.RavagerEntity
                || entity instanceof net.minecraft.entity.passive.IronGolemEntity) {
            return true;
        }
        if (entity.getType().isIn(ModEntityTags.BOSSES)) {
            return true;
        }
        double knockbackRes = entity.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockbackRes >= 0.4) {
            return true;
        }
        return (entity.getWidth() * entity.getHeight() >= 2.5f) || entity.getMaxHealth() >= 50.0f;
    }

    // ==========================================
    // ABILITY 2: CHARGED LIGHTNING STRIKE (HOLD RIGHT CLICK)
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
        user.setCurrentHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 0.8f, 0.7f);
        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(user, id, "controller", "charge");
            }
        } else if (user instanceof ServerPlayerEntity serverPlayer) {
            triggerAnim(serverPlayer, GeoItem.getOrAssignId(stack, (ServerWorld) world), "controller", "charge");
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Visual electric sparks around player during charge
        if (chargeTicks % 4 == 0) {
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    user.getX() + (world.random.nextDouble() - 0.5) * 1.2,
                    user.getY() + 0.8 + (world.random.nextDouble() - 0.5) * 0.8,
                    user.getZ() + (world.random.nextDouble() - 0.5) * 1.2,
                    0, 0.05, 0);
        }

        if (chargeTicks % 10 == 0) {
            float pitch = Math.min(2.0f, 0.6f + (chargeTicks / 40.0f) * 0.8f);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.35f, pitch);
        }

        if (chargeTicks == 40 && user instanceof PlayerEntity player) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.8f);
            if (world.isClient()) {
                player.sendMessage(Text.literal("§e⚡ FULL CHARGE READY!").formatted(Formatting.YELLOW, Formatting.BOLD), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()) {
            if (user instanceof PlayerEntity player) {
                long id = GeoItem.getId(stack);
                if (id != Long.MAX_VALUE) {
                    int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;
                    if (chargeTicks >= 5) {
                        triggerAnim(player, id, "controller", "release");
                    } else {
                        triggerAnim(player, id, "controller", isCoolingDown(world, stack) ? "cooldown" : "idle");
                    }
                }
            }
            return;
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return;
        }

        ServerWorld serverWorld = player.getServerWorld();
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Minimum charge threshold
        if (chargeTicks < 5) {
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", isCoolingDown(serverWorld, stack) ? "cooldown" : "idle");
            return;
        }

        // Aim raycast to find targeted mob
        LivingEntity target = findTargetedMob(player, 36.0);

        // Edge case: If no valid mob is targeted, safely cancel the ability without triggering a cooldown
        if (target == null || !target.isAlive() || target.isRemoved()) {
            player.sendMessage(Text.literal("§7⚡ No valid mob targeted — ability safely cancelled.").formatted(Formatting.GRAY), true);
            serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.5f);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", isCoolingDown(serverWorld, stack) ? "cooldown" : "idle");
            return;
        }

        // Calculate charge progress and strike count:
        // Minimum charge: 1 lightning strike
        // Full charge: 15–20 lightning strikes on target
        float chargeProgress = Math.min(1.0f, (float) chargeTicks / 40.0f);
        int strikeCount;
        if (chargeProgress < 0.25f) {
            strikeCount = 1;
        } else {
            int maxStrikes = 15 + serverWorld.random.nextInt(6); // 15 to 20
            strikeCount = Math.max(1, Math.round(1 + (maxStrikes - 1) * chargeProgress));
        }

        // Damage scaling: during cooldown, weapon deals less damage than normal
        boolean onCooldown = isCoolingDown(serverWorld, stack);
        float damagePerStrike = (strikeCount == 1) ? 9.0f : (38.0f / strikeCount);
        if (onCooldown) {
            // At full charge during cooldown, the weapon deals less damage than normal
            damagePerStrike *= 0.55f;
        }

        // Spread strikes over short duration
        int intervalTicks = (strikeCount > 10) ? 1 : 2;
        StormcallerLightningScheduler.scheduleBarrage(serverWorld, player, target, strikeCount, damagePerStrike, intervalTicks);

        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.2f, 1.0f);

        // Trigger GeckoLib release animation
        triggerAnim(player, GeoItem.getOrAssignId(stack, serverWorld), "controller", "release");

        // Apply 15-second cooldown and durability
        applyCooldown(serverWorld, player, stack, COOLDOWN_TICKS);
        stack.damage(2, player, p -> p.sendToolBreakStatus(player.getActiveHand()));

        if (onCooldown) {
            player.sendMessage(Text.literal("§6⚡ Charged Lightning Strike! §c(Reduced damage during cooldown)").formatted(Formatting.YELLOW), true);
        } else {
            player.sendMessage(Text.literal("§6⚡ Charged Lightning Strike unleashed! (" + strikeCount + " strikes)").formatted(Formatting.GOLD), true);
        }
    }

    public static LivingEntity findTargetedMob(ServerPlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookDir.multiply(maxDistance));

        ServerWorld world = player.getServerWorld();

        // Check line of sight against solid blocks
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            endPos = blockHit.getPos();
        }

        Box searchBox = new Box(eyePos, endPos).expand(1.2);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, searchBox, e -> {
            // Target only valid mobs for lightning; never target or strike the player
            return e != player && !(e instanceof PlayerEntity) && e.isAlive() && !e.isSpectator();
        });

        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (LivingEntity entity : list) {
            Box box = entity.getBoundingBox().expand(0.5);
            Optional<Vec3d> hit = box.raycast(eyePos, endPos);
            if (hit.isPresent()) {
                double distSq = eyePos.squaredDistanceTo(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = entity;
                }
            }
        }

        return closest;
    }

    // ==========================================
    // TOOLTIPS
    // ==========================================

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        if (world != null) {
            if (isCoolingDown(world, stack)) {
                int remaining = getRemainingCooldownSeconds(world, stack);
                tooltip.add(Text.literal("⚡ Cooldown: " + remaining + "s (Damage Reduced)").formatted(Formatting.RED));
            } else {
                tooltip.add(Text.literal("⚡ Ability: Ready").formatted(Formatting.GREEN));
            }
        }
        tooltip.add(Text.literal("► Press [R]: Thunderclap (4m shockwave launch)").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("► Hold [Right Click]: Charged Lightning Strike").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("  - Min Charge: 1 Strike | Full Charge: 15–20 Strikes").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("  - 15s Cooldown applies after use").formatted(Formatting.GRAY));
    }
}
