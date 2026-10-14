package com.elemental.item;

import com.elemental.entity.ModEntityTags;
import com.elemental.entity.TidebreakerBubbleEntity;
import com.elemental.entity.TidebreakerTridentEntity;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TidebreakerTridentItem extends Item implements GeoItem {

    // Configurable balance constants
    public static final int MAX_DURABILITY = 650;
    public static final float MELEE_DAMAGE = 8.0f;
    public static final float ATTACK_SPEED = -2.9f;

    public static final float UNDERWATER_MINING_SPEED = 5.0f;
    public static final float UNDERWATER_BLOCK_MINING_SPEED = 7.5f;

    public static final double UNDERWATER_ATTACK_SPEED_BONUS = 0.25; // +25% underwater attack speed

    public static final int DASH_COOLDOWN_TICKS = 80; // 4 seconds
    public static final int FAILED_DASH_COOLDOWN_TICKS = 40; // 2 seconds failed dash cooldown
    public static final int THROW_COOLDOWN_TICKS = 60; // 3 seconds throw cooldown
    public static final double BASE_DASH_SPEED = 1.95; // 10 to 15 blocks
    public static final int WATER_SEARCH_RADIUS = 10;

    public static final int BUBBLE_COOLDOWN_TICKS = 160; // 8 seconds
    public static final int BASE_BUBBLE_DURATION = 120; // 6 seconds (100 to 140 ticks)
    public static final double BUBBLE_MAX_RANGE = 30.0;
    public static final double BUBBLE_LIFT_HEIGHT = 35.0; // 30 to 40 blocks
    public static final float BUBBLE_EXPLOSION_DAMAGE = 8.0f;
    public static final float PROJECTILE_BASE_DAMAGE = 8.0f;

    // Biome multipliers and bonuses
    public static final float OCEAN_MELEE_BONUS = 2.0f;
    public static final float RIVER_MELEE_BONUS = 1.0f;
    public static final double OCEAN_DASH_MULTIPLIER = 1.20;
    public static final double RIVER_DASH_MULTIPLIER = 1.10;
    public static final double OCEAN_BUBBLE_MULTIPLIER = 1.20;
    public static final double RIVER_BUBBLE_MULTIPLIER = 1.10;

    // Attribute modifier UUIDs
    private static final UUID UNDERWATER_ATTACK_SPEED_UUID = UUID.fromString("4c62f270-e455-4da0-96aa-2144fa734e56");
    private static final EntityAttributeModifier UNDERWATER_ATTACK_SPEED_MOD = new EntityAttributeModifier(
            UNDERWATER_ATTACK_SPEED_UUID,
            "Tidebreaker underwater attack speed",
            UNDERWATER_ATTACK_SPEED_BONUS,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL
    );

    // GeckoLib animations
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.tidebreaker_trident.idle");
    private static final RawAnimation SWING_ANIM = RawAnimation.begin().thenPlay("animation.tidebreaker_trident.swing");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop("animation.tidebreaker_trident.charge");
    private static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlay("animation.tidebreaker_trident.throw");
    private static final RawAnimation DASH_ANIM = RawAnimation.begin().thenPlay("animation.tidebreaker_trident.dash");
    private static final RawAnimation FAILED_DASH_ANIM = RawAnimation.begin().thenPlay("animation.tidebreaker_trident.failed_dash");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.tidebreaker_trident.cooldown");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public TidebreakerTridentItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", MELEE_DAMAGE, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", ATTACK_SPEED, EntityAttributeModifier.Operation.ADDITION));
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
        controllers.add(new AnimationController<>(this, "controller", 2, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            Entity entity = state.getData(DataTickets.ENTITY);
            if (stack != null && entity instanceof PlayerEntity player) {
                long now = entity.getWorld().getTime();
                long lastBubble = stack.hasNbt() ? stack.getNbt().getLong("LastBubbleTime") : 0;
                boolean bubbleCd = (now - lastBubble < BUBBLE_COOLDOWN_TICKS);
                if (player.getItemCooldownManager().isCoolingDown(this) || bubbleCd) {
                    return state.setAndContinue(COOLDOWN_ANIM);
                }
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("swing", SWING_ANIM)
          .triggerableAnim("charge", CHARGE_ANIM)
          .triggerableAnim("throw", THROW_ANIM)
          .triggerableAnim("dash", DASH_ANIM)
          .triggerableAnim("failed_dash", FAILED_DASH_ANIM)
          .triggerableAnim("cooldown", COOLDOWN_ANIM)
          .triggerableAnim("idle", IDLE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.attributeModifiers;
        }
        return super.getAttributeModifiers(slot);
    }

    // ==========================================
    // TOOL FUNCTION: UNDERWATER MINING
    // ==========================================

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        if (state.isIn(BlockTags.CORAL_BLOCKS)
                || state.isOf(Blocks.PRISMARINE)
                || state.isOf(Blocks.PRISMARINE_BRICKS)
                || state.isOf(Blocks.DARK_PRISMARINE)
                || state.isOf(Blocks.SEA_LANTERN)
                || state.isOf(Blocks.SPONGE)
                || state.isOf(Blocks.WET_SPONGE)
                || state.isIn(BlockTags.SAND)) {
            return UNDERWATER_BLOCK_MINING_SPEED;
        }
        return UNDERWATER_MINING_SPEED;
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        return true;
    }

    // ==========================================
    // PASSIVE ABILITY: UNDERWATER ATTACK SPEED
    // ==========================================

    // Per-player bubble cooldown tracking to prevent hotbar switching exploits
    private static final java.util.Map<UUID, Long> LAST_BUBBLE_BY_PLAYER = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world instanceof ServerWorld serverWorld) {
            GeoItem.getOrAssignId(stack, serverWorld);
        }
        if (!world.isClient() && entity instanceof PlayerEntity player) {
            EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED);
            if (attr != null) {
                boolean isHolding = selected || player.getOffHandStack() == stack;
                boolean inWater = (player.isSubmergedIn(FluidTags.WATER) || player.isSwimming());
                boolean shouldHave = isHolding && inWater;
                boolean hasMod = attr.hasModifier(UNDERWATER_ATTACK_SPEED_MOD);

                if (shouldHave && !hasMod) {
                    attr.addTemporaryModifier(UNDERWATER_ATTACK_SPEED_MOD);
                } else if (!shouldHave && hasMod) {
                    attr.removeModifier(UNDERWATER_ATTACK_SPEED_MOD);
                }
            }
        }
    }

    // ==========================================
    // MELEE COMBAT
    // ==========================================

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        World world = attacker.getWorld();
        if (!world.isClient()) {
            float bonus = getBiomeMeleeBonus(attacker);
            if (bonus > 0.0f && attacker instanceof PlayerEntity player) {
                target.damage(attacker.getDamageSources().playerAttack(player), bonus);
            }
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1.0, target.getZ(),
                        15, 0.3, 0.3, 0.3, 0.1);
                long id = GeoItem.getOrAssignId(stack, serverWorld);
                triggerAnim(attacker, id, "controller", "swing");
            }
        } else {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(attacker, id, "controller", "swing");
            }
        }
        return true;
    }

    // ==========================================
    // THROWN ATTACK (RIGHT-CLICK CHARGE & THROW)
    // ==========================================

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // Check throw cooldown
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        // Prevent throwing if insufficient durability
        if (stack.getDamage() >= stack.getMaxDamage() - 1) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);

        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(user, id, "controller", "charge");
            }
        } else if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            triggerAnim(user, id, "controller", "charge");
        }

        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        int chargeTime = this.getMaxUseTime(stack) - remainingUseTicks;
        if (chargeTime >= 10) {
            if (!world.isClient()) {
                stack.damage(1, player, p -> p.sendToolBreakStatus(user.getActiveHand()));

                TidebreakerTridentEntity entity = new TidebreakerTridentEntity(world, player, stack);
                entity.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, 2.5f, 1.0f);

                if (player.getAbilities().creativeMode) {
                    entity.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                }

                world.spawnEntity(entity);
                world.playSoundFromEntity(null, entity, SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);

                if (!player.getAbilities().creativeMode) {
                    player.getInventory().removeOne(stack);
                }

                // Apply cooldown to trident throw
                player.getItemCooldownManager().set(this, THROW_COOLDOWN_TICKS);

                if (world instanceof ServerWorld serverWorld) {
                    long id = GeoItem.getOrAssignId(stack, serverWorld);
                    triggerAnim(player, id, "controller", "throw");
                }
            } else {
                long id = GeoItem.getId(stack);
                if (id != Long.MAX_VALUE) {
                    triggerAnim(player, id, "controller", "throw");
                }
            }
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        } else {
            // Cancelled charge
            if (world.isClient()) {
                long id = GeoItem.getId(stack);
                if (id != Long.MAX_VALUE) {
                    triggerAnim(player, id, "controller", "idle");
                }
            } else if (world instanceof ServerWorld serverWorld) {
                long id = GeoItem.getOrAssignId(stack, serverWorld);
                triggerAnim(player, id, "controller", "idle");
            }
        }
    }

    // ==========================================
    // ACTIVE ABILITY: WATER DASH
    // ==========================================

    public static void performDash(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return;
        }

        ItemStack stack = null;
        Hand hand = Hand.MAIN_HAND;
        if (player.getMainHandStack().getItem() instanceof TidebreakerTridentItem) {
            stack = player.getMainHandStack();
            hand = Hand.MAIN_HAND;
        } else if (player.getOffHandStack().getItem() instanceof TidebreakerTridentItem) {
            stack = player.getOffHandStack();
            hand = Hand.OFF_HAND;
        }

        if (stack == null) return;

        long now = player.getWorld().getTime();
        long lastDash = stack.getOrCreateNbt().getLong("LastDashTime");
        if (player.getItemCooldownManager().isCoolingDown(ModItems.TIDEBREAKER_TRIDENT) || now - lastDash < DASH_COOLDOWN_TICKS) {
            return; // Cooldown active
        }

        ServerWorld world = player.getServerWorld();

        // Check if water is nearby
        if (!isWaterNearby(player)) {
            // Failed dash: push forward 2 blocks in the direction the player is looking at
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d push = look.multiply(0.65).add(0, 0.1, 0);
            player.setVelocity(push.x, push.y, push.z);
            player.velocityModified = true;
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));

            world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 1.5f);

            // Add cooldown for failed dash
            player.getItemCooldownManager().set(ModItems.TIDEBREAKER_TRIDENT, FAILED_DASH_COOLDOWN_TICKS);
            stack.getOrCreateNbt().putLong("LastDashTime", now);

            if (stack.getItem() instanceof GeoItem geoItem) {
                long id = GeoItem.getOrAssignId(stack, world);
                geoItem.triggerAnim(player, id, "controller", "failed_dash");
            }
            return;
        }

        // Successful dash
        double multiplier = getBiomeDashMultiplier(player);
        double speed = BASE_DASH_SPEED * multiplier;
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d dashVec = look.multiply(speed);

        player.setVelocity(dashVec.x, dashVec.y * 0.85 + 0.15, dashVec.z);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));

        // Visual and sound effects
        world.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(),
                35, 0.6, 0.6, 0.6, 0.2);
        world.spawnParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.5, player.getZ(),
                25, 0.6, 0.6, 0.6, 0.1);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TRIDENT_RIPTIDE_2, SoundCategory.PLAYERS, 0.9f, 1.4f);

        // Record cooldown and consume durability
        player.getItemCooldownManager().set(ModItems.TIDEBREAKER_TRIDENT, DASH_COOLDOWN_TICKS);
        stack.getOrCreateNbt().putLong("LastDashTime", now);
        Hand finalHand = hand;
        stack.damage(1, player, p -> p.sendToolBreakStatus(finalHand));

        if (stack.getItem() instanceof GeoItem geoItem) {
            long id = GeoItem.getOrAssignId(stack, world);
            geoItem.triggerAnim(player, id, "controller", "dash");
        }
    }

    public static boolean isWaterNearby(ServerPlayerEntity player) {
        if (player.isTouchingWater() || player.isSwimming() || player.isSubmergedIn(FluidTags.WATER)) {
            return true;
        }
        ServerWorld world = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();
        int r = WATER_SEARCH_RADIUS;
        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, r, r, r)) {
            if (pos.isWithinDistance(playerPos, r)) {
                if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==========================================
    // SPECIAL ABILITY: WATER BUBBLE LEVITATION
    // ==========================================

    public static void tryApplyBubbleAbility(PlayerEntity owner, LivingEntity target, ItemStack tridentStack) {
        if (owner.getWorld().isClient()) return;
        if (target == owner) return;
        if (owner.isTeammate(target)) return;
        if (target.getType().isIn(ModEntityTags.BOSSES)) return;

        if (owner.distanceTo(target) > BUBBLE_MAX_RANGE) return;

        long now = owner.getWorld().getTime();
        Long lastPlayerBubble = LAST_BUBBLE_BY_PLAYER.get(owner.getUuid());
        if (lastPlayerBubble != null && now - lastPlayerBubble < BUBBLE_COOLDOWN_TICKS) {
            return;
        }
        long lastBubble = tridentStack.getOrCreateNbt().getLong("LastBubbleTime");
        if (now - lastBubble < BUBBLE_COOLDOWN_TICKS) {
            return;
        }

        // Spawn bubble entity on target
        TidebreakerBubbleEntity bubble = new TidebreakerBubbleEntity(owner.getWorld(), target, owner);
        owner.getWorld().spawnEntity(bubble);

        LAST_BUBBLE_BY_PLAYER.put(owner.getUuid(), now);
        tridentStack.getOrCreateNbt().putLong("LastBubbleTime", now);
        tridentStack.damage(1, owner, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
    }

    // ==========================================
    // BIOME HELPERS
    // ==========================================

    public static boolean isInOcean(Entity entity) {
        return entity.getWorld().getBiome(entity.getBlockPos()).isIn(BiomeTags.IS_OCEAN);
    }

    public static boolean isInRiver(Entity entity) {
        return entity.getWorld().getBiome(entity.getBlockPos()).isIn(BiomeTags.IS_RIVER);
    }

    public static double getBiomePowerMultiplier(Entity entity) {
        if (isInOcean(entity)) return 1.20;
        if (isInRiver(entity)) return 1.10;
        return 1.0;
    }

    public static float getBiomeMeleeBonus(Entity entity) {
        if (isInOcean(entity)) return OCEAN_MELEE_BONUS;
        if (isInRiver(entity)) return RIVER_MELEE_BONUS;
        return 0.0f;
    }

    public static double getBiomeDashMultiplier(Entity entity) {
        if (isInOcean(entity)) return OCEAN_DASH_MULTIPLIER;
        if (isInRiver(entity)) return RIVER_DASH_MULTIPLIER;
        return 1.0;
    }

    public static double getBiomeBubbleMultiplier(Entity entity) {
        if (isInOcean(entity)) return OCEAN_BUBBLE_MULTIPLIER;
        if (isInRiver(entity)) return RIVER_BUBBLE_MULTIPLIER;
        return 1.0;
    }

    // ==========================================
    // TOOLTIPS
    // ==========================================

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("Passive: +25% Attack Speed underwater").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Tool: Accelerated underwater mining").formatted(Formatting.DARK_AQUA));
        tooltip.add(Text.literal("Active [R]: Tidebreaker Dash (4s CD; requires water within 10 blocks, 2s on fail)").formatted(Formatting.BLUE));
        tooltip.add(Text.literal("Throw: Water Bubble Levitation on entity impact (3s CD)").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("Empowered in Oceans (+20%) and Rivers (+10%)").formatted(Formatting.GRAY));
    }
}
