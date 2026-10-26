package com.elemental.item;

import com.elemental.entity.ModEntityTags;
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
import software.bernie.geckolib.core.object.PlayState;
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

    public static final int COOLDOWN_THUNDERCLAP_TICKS = 300; // 15 seconds
    public static final int COOLDOWN_THUNDERSTORM_TICKS = 300; // 15 seconds
    public static final int COOLDOWN_TICKS = 300; // 15 seconds (backwards compatibility alias)

    public static final String NBT_THUNDERCLAP_COOLDOWN_END = "StormcallerThunderclapCooldownEnd";
    public static final String NBT_THUNDERSTORM_COOLDOWN_END = "StormcallerThunderstormCooldownEnd";
    public static final String NBT_THUNDERCLAP_NOTIFIED = "StormcallerThunderclapNotified";
    public static final String NBT_THUNDERSTORM_NOTIFIED = "StormcallerThunderstormNotified";

    public static final String NBT_COOLDOWN_END = "StormcallerCooldownEnd";
    public static final String NBT_COOLDOWN_NOTIFIED = "StormcallerNotifiedCooldown";

    public static final UUID REDUCED_DAMAGE_MODIFIER_UUID = UUID.fromString("e5d2630a-9d32-475a-b68e-73c38cb51921");
    public static final EntityAttributeModifier REDUCED_DAMAGE_MODIFIER = new EntityAttributeModifier(
            REDUCED_DAMAGE_MODIFIER_UUID,
            "Stormcaller Cooldown Reduced Damage",
            -5.0, // Deals less damage than normal during cooldown
            EntityAttributeModifier.Operation.ADDITION);

    // GeckoLib animation definitions (weapon model idle and cooldown ONLY; player
    // animations handled by PlayerAnimator)
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.stormcaller_hammer.idle");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin()
            .thenLoop("animation.stormcaller_hammer.cooldown");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    @FunctionalInterface
    public interface ReleaseCallback {
        void onRelease(PlayerEntity player, int targetEntityId, float chargeProgress);
    }

    public static Consumer<PlayerEntity> CLIENT_AIM_START_CALLBACK = null;
    public static Consumer<PlayerEntity> CLIENT_AIM_STOP_CALLBACK = null;
    public static ReleaseCallback CLIENT_RELEASE_CALLBACK = null;

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public StormcallerHammerItem(Settings settings) {
        super(StormcallerHammerToolMaterial.INSTANCE, (int) BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 10.0, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", BASE_ATTACK_SPEED,
                EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, new EntityAttributeModifier(
                UUID.fromString("9352e071-70e2-4bd5-b28e-5b12da6f23a8"), "Weapon knockback modifier", BONUS_KNOCKBACK,
                EntityAttributeModifier.Operation.ADDITION));
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
            if (stack == null || !(entity instanceof PlayerEntity player)) {
                return PlayState.STOP;
            }
            if (isThunderclapCoolingDown(player.getWorld(), stack)
                    && isThunderstormCoolingDown(player.getWorld(), stack)) {
                return state.setAndContinue(COOLDOWN_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("cooldown", COOLDOWN_ANIM)
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

    public static boolean isThunderclapCoolingDown(World world, ItemStack stack) {
        if (!stack.hasNbt())
            return false;
        long endTick = stack.getNbt().getLong(NBT_THUNDERCLAP_COOLDOWN_END);
        return world.getTime() < endTick;
    }

    public static int getRemainingThunderclapSeconds(World world, ItemStack stack) {
        if (!stack.hasNbt())
            return 0;
        long endTick = stack.getNbt().getLong(NBT_THUNDERCLAP_COOLDOWN_END);
        long remaining = endTick - world.getTime();
        return remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
    }

    public static void applyThunderclapCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack,
            int ticks) {
        long endTick = world.getTime() + ticks;
        stack.getOrCreateNbt().putLong(NBT_THUNDERCLAP_COOLDOWN_END, endTick);
        stack.getOrCreateNbt().putBoolean(NBT_THUNDERCLAP_NOTIFIED, true);
        // Do NOT call player.getItemCooldownManager().set(...) so right-click
        // Thunderstorm is not blocked!
    }

    public static boolean isThunderstormCoolingDown(World world, ItemStack stack) {
        if (!stack.hasNbt())
            return false;
        long endTick = stack.getNbt().getLong(NBT_THUNDERSTORM_COOLDOWN_END);
        return world.getTime() < endTick;
    }

    public static int getRemainingThunderstormSeconds(World world, ItemStack stack) {
        if (!stack.hasNbt())
            return 0;
        long endTick = stack.getNbt().getLong(NBT_THUNDERSTORM_COOLDOWN_END);
        long remaining = endTick - world.getTime();
        return remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
    }

    public static void applyThunderstormCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack,
            int ticks) {
        long endTick = world.getTime() + ticks;
        stack.getOrCreateNbt().putLong(NBT_THUNDERSTORM_COOLDOWN_END, endTick);
        stack.getOrCreateNbt().putBoolean(NBT_THUNDERSTORM_NOTIFIED, true);
        player.getItemCooldownManager().set(stack.getItem(), ticks);
    }

    public static boolean isCoolingDown(World world, ItemStack stack) {
        return isThunderclapCoolingDown(world, stack) || isThunderstormCoolingDown(world, stack);
    }

    public static int getRemainingCooldownSeconds(World world, ItemStack stack) {
        return Math.max(getRemainingThunderclapSeconds(world, stack), getRemainingThunderstormSeconds(world, stack));
    }

    public static void applyCooldown(ServerWorld world, ServerPlayerEntity player, ItemStack stack, int ticks) {
        applyThunderstormCooldown(world, player, stack, ticks);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world instanceof ServerWorld serverWorld) {
            GeoItem.getOrAssignId(stack, serverWorld);
        }
        if (!world.isClient() && entity instanceof PlayerEntity player) {
            boolean isHolding = selected || player.getOffHandStack() == stack;
            boolean thunderclapCooling = isThunderclapCoolingDown(world, stack);
            boolean thunderstormCooling = isThunderstormCoolingDown(world, stack);
            boolean shouldReduceDamage = isHolding && (thunderclapCooling && thunderstormCooling);

            EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (attr != null) {
                boolean hasMod = attr.hasModifier(REDUCED_DAMAGE_MODIFIER);
                if (shouldReduceDamage && !hasMod) {
                    attr.addTemporaryModifier(REDUCED_DAMAGE_MODIFIER);
                } else if (!shouldReduceDamage && hasMod) {
                    attr.removeModifier(REDUCED_DAMAGE_MODIFIER);
                }
            }

            if (isHolding && stack.hasNbt()) {
                if (!thunderclapCooling && stack.getNbt().getBoolean(NBT_THUNDERCLAP_NOTIFIED)) {
                    stack.getNbt().remove(NBT_THUNDERCLAP_NOTIFIED);
                    player.sendMessage(
                            Text.literal("§a⚡ Thunderclap recharged!").formatted(Formatting.GREEN, Formatting.BOLD),
                            true);
                    player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
                }
                if (!thunderstormCooling && stack.getNbt().getBoolean(NBT_THUNDERSTORM_NOTIFIED)) {
                    stack.getNbt().remove(NBT_THUNDERSTORM_NOTIFIED);
                    player.sendMessage(
                            Text.literal("§a⚡ Thunderstorm recharged!").formatted(Formatting.GREEN, Formatting.BOLD),
                            true);
                    player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.8f);
                }
                // Backwards compatibility clean-up
                if (stack.getNbt().contains(NBT_COOLDOWN_NOTIFIED)) {
                    stack.getNbt().remove(NBT_COOLDOWN_NOTIFIED);
                }
            }
        }
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        World world = attacker.getWorld();
        if (!world.isClient()) {
            if (attacker instanceof ServerPlayerEntity serverPlayer) {
                com.elemental.combat.StormcallerServerCombatHandler.startMeleeSwing(serverPlayer);
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
                    serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0,
                            target.getZ(),
                            12, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }
        return true;
    }

    // ==========================================
    // ==========================================
    // ABILITY 1: GROUND SMASH / THUNDERCLAP (PRESS R)
    // ==========================================

    public static void performThunderclap(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        com.elemental.combat.StormcallerServerCombatHandler.startGroundSmash(player);
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
        return UseAction.NONE;
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
        if (isThunderstormCoolingDown(world, stack)) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                int remaining = getRemainingThunderstormSeconds(world, stack);
                serverPlayer
                        .sendMessage(Text.literal("§c⚡ Thunderstorm is cooling down! (" + remaining + "s remaining)")
                                .formatted(Formatting.RED), true);
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.PLAYERS, 0.6f, 1.4f);
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START,
                SoundCategory.PLAYERS, 0.8f, 0.7f);
        if (world.isClient()) {
            if (CLIENT_AIM_START_CALLBACK != null) {
                CLIENT_AIM_START_CALLBACK.accept(user);
            }
        } else if (user instanceof ServerPlayerEntity serverPlayer) {
            com.elemental.combat.StormcallerServerCombatHandler.startLightningAim(serverPlayer, true);
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
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    SoundCategory.PLAYERS, 0.35f, pitch);
        }

        if (chargeTicks == 40 && user instanceof PlayerEntity player) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.PLAYERS, 0.8f, 1.8f);
            if (world.isClient()) {
                player.sendMessage(Text.literal("§e⚡ FULL CHARGE READY!").formatted(Formatting.YELLOW, Formatting.BOLD),
                        true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;
        float chargeProgress = Math.min(1.0f, (float) chargeTicks / 40.0f);

        if (world.isClient()) {
            if (user instanceof PlayerEntity player) {
                if (chargeTicks >= 5) {
                    LivingEntity target = findTargetedMob(player, 36.0);
                    int targetId = target != null ? target.getId() : -1;
                    if (CLIENT_RELEASE_CALLBACK != null) {
                        CLIENT_RELEASE_CALLBACK.onRelease(player, targetId, chargeProgress);
                    }
                } else {
                    if (CLIENT_AIM_STOP_CALLBACK != null) {
                        CLIENT_AIM_STOP_CALLBACK.accept(player);
                    }
                }
            }
            return;
        }

        if (!(user instanceof ServerPlayerEntity player)) {
            return;
        }

        if (chargeTicks < 5) {
            com.elemental.combat.StormcallerServerCombatHandler.startLightningAim(player, false);
            return;
        }

        LivingEntity target = findTargetedMob(player, 36.0);
        int targetId = target != null ? target.getId() : -1;
        com.elemental.combat.StormcallerServerCombatHandler.releaseLightningStrike(player, targetId, chargeProgress);
    }

    public static LivingEntity findTargetedMob(ServerPlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookDir.multiply(maxDistance));

        ServerWorld world = player.getServerWorld();

        // Check line of sight against solid blocks
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
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

    public static LivingEntity findTargetedMob(PlayerEntity player, double maxDistance) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookDir = player.getRotationVec(1.0F);
        Vec3d endPos = eyePos.add(lookDir.multiply(maxDistance));
        World world = player.getWorld();

        // Check line of sight against solid blocks
        BlockHitResult blockHit = world.raycast(new RaycastContext(
                eyePos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            endPos = blockHit.getPos();
        }

        Box searchBox = new Box(eyePos, endPos).expand(1.2);
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e != player && !(e instanceof PlayerEntity) && e.isAlive() && !e.isSpectator());

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
        tooltip.add(Text.literal("§e► [Hold Right-Click] §fThunderstorm"));
        tooltip.add(Text.literal("§b► [R] §fThunderclap"));
        tooltip.add(Text.literal("§8[Open Weapon Detail Menu for Lore & Stats]").formatted(Formatting.DARK_GRAY));
    }
}
