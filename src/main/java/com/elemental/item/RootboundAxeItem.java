package com.elemental.item;

import com.elemental.Elemental;
import com.elemental.entity.ModEntityTags;
import com.elemental.util.RootboundScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
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
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RootboundAxeItem extends AxeItem implements GeoItem {

    // Durability (diamond-tier equivalent: 1561, netherite-tier: 2031; balanced around diamond)
    public static final int DURABILITY = 1800;

    // Base attack damage (diamond axe = 6, netherite axe = 7; with material base 5 = diamond+ tier)
    public static final int BASE_ATTACK_DAMAGE = 3;

    // Attack speed modifier (axes are slower: -3.0 base + (-0.2) = 0.8 attacks/sec)
    public static final float BASE_ATTACK_SPEED = -0.2f;

    // Fast woodcutting speed multiplier on logs/wood
    public static final float WOOD_MINING_SPEED = 10.0f;

    // Sweep attack configuration
    public static final float SWEEP_ATTACK_RADIUS = 6.0f;
    public static final float SWEEP_ANGLE = 180.0f; // degrees (vanilla axe is ~120)

    // Passive: Healing Root configuration
    public static final double PASSIVE_TRIGGER_CHANCE = 0.15; // 15% chance on hit
    public static final double CRIT_TRIGGER_CHANCE = 0.50; // 50% chance on crit
    public static final int HEALING_ROOT_DURATION_BASE = 200; // 10 seconds in ticks
    public static final int HEALING_ROOT_DURATION_FOREST_BONUS = 100; // +5 seconds in forest/jungle
    public static final double HEALING_ROOT_RADIUS_BASE = 3.0;
    public static final double HEALING_ROOT_RADIUS_FOREST_BONUS = 1.5;
    public static final int REGENERATION_DURATION_BASE = 60; // 3 seconds
    public static final int REGENERATION_DURATION_FOREST_BONUS = 40; // +2 seconds
    public static final int REGENERATION_AMPLIFIER_BASE = 0; // Regeneration I
    public static final int REGENERATION_AMPLIFIER_FOREST_BONUS = 1; // Regeneration II

    // Active: Thorn Cage configuration
    public static final int BASE_COOLDOWN_TICKS = 300; // 15 seconds
    public static final int FOREST_COOLDOWN_TICKS = 300; // 15 seconds in forest/jungle
    public static final double THORN_CAGE_RADIUS = 10.0;
    public static final int THORN_CAGE_DURATION = 100; // 5 seconds
    public static final int THORN_CAGE_DAMAGE_TICK_INTERVAL = 10; // every 0.5 seconds
    public static final float THORN_CAGE_DAMAGE_PER_TICK = 2.0f;
    public static final int THORN_CAGE_SLOW_LEVEL = 1; // Slowness II (amplifier 1)
    public static final int THORN_CAGE_SLOW_DURATION = 20; // 1 second per tick

    // NBT keys
    private static final String LAST_THORN_CAGE_TIME_KEY = "LastThornCageTime";
    private static final String ACTIVE_THORN_CAGE_KEY = "ActiveThornCage";

    private static final Random RANDOM = new Random();

    // Biome tag for forest/jungle biomes (stronger roots)
    private static final TagKey<Biome> FOREST_JUNGLE_BIOMES = TagKey.of(
            RegistryKeys.BIOME,
            new Identifier("minecraft", "is_forest")
    );

    // GeckoLib animation definitions
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.rootbound_axe.idle");
    private static final RawAnimation WIDE_SWEEP_ANIM = RawAnimation.begin().thenPlay("animation.rootbound_axe.wide_sweep");
    private static final RawAnimation THORN_CAGE_ANIM = RawAnimation.begin().thenPlay("animation.rootbound_axe.thorn_cage");
    private static final RawAnimation HEALING_ROOT_ANIM = RawAnimation.begin().thenPlay("animation.rootbound_axe.healing_root");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.rootbound_axe.cooldown");
    private static final RawAnimation EQUIP_ANIM = RawAnimation.begin().thenPlay("animation.rootbound_axe.equip");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    // Client renderer hook
    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    public RootboundAxeItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public RootboundAxeItem(Settings settings) {
        super(RootboundAxeToolMaterial.INSTANCE, BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
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
            if (stack != null && entity instanceof PlayerEntity player) {
                if (player.getItemCooldownManager().isCoolingDown(this)) {
                    return state.setAndContinue(COOLDOWN_ANIM);
                }
            }
            return state.setAndContinue(IDLE_ANIM);
        })
                .triggerableAnim("wide_sweep", WIDE_SWEEP_ANIM)
                .triggerableAnim("thorn_cage", THORN_CAGE_ANIM)
                .triggerableAnim("healing_root", HEALING_ROOT_ANIM)
                .triggerableAnim("cooldown", COOLDOWN_ANIM)
                .triggerableAnim("idle", IDLE_ANIM)
                .triggerableAnim("equip", EQUIP_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        if (isLogOrWood(state)) {
            return WOOD_MINING_SPEED;
        }
        return super.getMiningSpeedMultiplier(stack, state);
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        if (isLogOrWood(state)) {
            return true;
        }
        return super.isSuitableFor(state);
    }

    /**
     * Checks if the block state is a log or wood type block.
     */
    private boolean isLogOrWood(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isOf(Blocks.STRIPPED_OAK_LOG) || state.isOf(Blocks.STRIPPED_SPRUCE_LOG)
                || state.isOf(Blocks.STRIPPED_BIRCH_LOG) || state.isOf(Blocks.STRIPPED_JUNGLE_LOG)
                || state.isOf(Blocks.STRIPPED_ACACIA_LOG) || state.isOf(Blocks.STRIPPED_DARK_OAK_LOG)
                || state.isOf(Blocks.STRIPPED_MANGROVE_LOG) || state.isOf(Blocks.STRIPPED_CHERRY_LOG)
                || state.isOf(Blocks.STRIPPED_BAMBOO_BLOCK);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        World world = attacker.getWorld();

        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(attacker, id, "controller", "wide_sweep");
            }
        } else if (world instanceof ServerWorld serverWorld) {
            // Perform sweep attack
            performSweepAttack(serverWorld, attacker, stack, target);

            // Passive: Healing root on hit/crit
            attemptHealingRoot(serverWorld, attacker, target, stack);
        }
        return true;
    }

    /**
     * Performs a wide sweep attack similar to vanilla axe sweep but with larger angle/radius.
     */
    private void performSweepAttack(ServerWorld world, LivingEntity attacker, ItemStack stack, LivingEntity primaryTarget) {
        if (!(attacker instanceof PlayerEntity)) return;

        triggerAnim(attacker, GeoItem.getOrAssignId(stack, world), "controller", "wide_sweep");

        float attackAngle = SWEEP_ANGLE;
        float attackRadius = SWEEP_ATTACK_RADIUS;

        // Get attacker's look direction for sweep cone
        Vec3d lookVec = attacker.getRotationVec(1.0F).normalize();
        Vec3d attackerPos = attacker.getPos();

        // Create sweep area box in front of attacker
        Box sweepBox = new Box(
                attackerPos.x - attackRadius, attackerPos.y - 1.0, attackerPos.z - attackRadius,
                attackerPos.x + attackRadius, attackerPos.y + 1.5, attackerPos.z + attackRadius
        ).expand(attackRadius);

        // Find entities in sweep area
        for (Entity entity : world.getOtherEntities(attacker, sweepBox)) {
            if (entity == primaryTarget || !(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity) continue;

            LivingEntity living = (LivingEntity) entity;

            // Check if entity is within the sweep cone angle
            Vec3d toEntity = living.getPos().subtract(attackerPos).normalize();
            double dot = lookVec.dotProduct(toEntity);
            double angleCos = Math.cos(Math.toRadians(attackAngle / 2.0));

            if (dot >= angleCos) {
                // Apply sweep damage (50% of base attack damage)
                float sweepDamage = (BASE_ATTACK_DAMAGE + RootboundAxeToolMaterial.INSTANCE.getAttackDamage()) * 0.5f;
                living.damage(attacker.getDamageSources().mobAttack(attacker), sweepDamage);

                // Apply knockback
                Vec3d knockback = toEntity.multiply(0.5).add(0, 0.1, 0);
                living.addVelocity(knockback.x, knockback.y, knockback.z);

                // Root particle effect along sweep arc
                spawnRootParticles(world, living.getPos());
            }
        }
    }

    /**
     * Attempts to spawn a healing root at the target's location on hit/crit.
     */
    private void attemptHealingRoot(ServerWorld world, LivingEntity attacker, LivingEntity target, ItemStack stack) {
        // Determine chance based on crit (simplified: fall distance > 0 and not on ground)
        boolean isCrit = attacker.fallDistance > 0.0f && !attacker.isOnGround() && !attacker.isClimbing()
                && !attacker.isTouchingWater() && !attacker.hasStatusEffect(StatusEffects.BLINDNESS);

        double chance = isCrit ? CRIT_TRIGGER_CHANCE : PASSIVE_TRIGGER_CHANCE;

        if (RANDOM.nextDouble() < chance) {
            spawnHealingRoot(world, target.getBlockPos(), attacker, stack);
        }
    }

    /**
     * Spawns a healing root at the given position.
     */
    private void spawnHealingRoot(ServerWorld world, BlockPos pos, LivingEntity source, ItemStack stack) {
        triggerAnim(source, GeoItem.getOrAssignId(stack, world), "controller", "healing_root");

        // Check biome for forest/jungle bonus
        boolean isForestJungle = world.getBiome(pos).isIn(FOREST_JUNGLE_BIOMES);

        int duration = HEALING_ROOT_DURATION_BASE + (isForestJungle ? HEALING_ROOT_DURATION_FOREST_BONUS : 0);
        double radius = HEALING_ROOT_RADIUS_BASE + (isForestJungle ? HEALING_ROOT_RADIUS_FOREST_BONUS : 0);
        int regenDuration = REGENERATION_DURATION_BASE + (isForestJungle ? REGENERATION_DURATION_FOREST_BONUS : 0);
        int regenAmplifier = REGENERATION_AMPLIFIER_BASE + (isForestJungle ? REGENERATION_AMPLIFIER_FOREST_BONUS : 0);

        // Register server-authoritative healing root zone
        RootboundScheduler.scheduleHealingRoot(world, source.getUuid(), pos, radius, duration, regenDuration, regenAmplifier);

        // Visual and sound effects (using GLOW and SMALL_FLAME particles available in 1.20.1)
        world.spawnParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                20, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0.02);
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /**
     * Spawns root particles at the given position for visual feedback.
     */
    private void spawnRootParticles(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5, pos.z,
                5, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y + 0.5, pos.z,
                3, 0.2, 0.2, 0.2, 0.01);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Check cooldown
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            if (world.isClient()) {
                long id = GeoItem.getId(stack);
                if (id != Long.MAX_VALUE) {
                    triggerAnim(user, id, "controller", "cooldown");
                }
            } else if (world instanceof ServerWorld serverWorld) {
                long id = GeoItem.getOrAssignId(stack, serverWorld);
                triggerAnim(user, id, "controller", "cooldown");
            }
            return TypedActionResult.fail(stack);
        }

        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(user, id, "controller", "thorn_cage");
            }
            return TypedActionResult.consume(stack);
        }

        // Perform active ability: Thorn Cage
        performThornCage((ServerWorld) world, user, stack, hand);
        return TypedActionResult.consume(stack);
    }

    /**
     * Performs the active Thorn Cage ability.
     */
    private void performThornCage(ServerWorld world, PlayerEntity user, ItemStack stack, Hand hand) {
        triggerAnim(user, GeoItem.getOrAssignId(stack, world), "controller", "thorn_cage");

        Vec3d center = user.getPos();
        BlockPos centerPos = user.getBlockPos();

        // Check biome for cooldown reduction
        boolean isForestJungle = world.getBiome(centerPos).isIn(FOREST_JUNGLE_BIOMES);
        int cooldown = isForestJungle ? FOREST_COOLDOWN_TICKS : BASE_COOLDOWN_TICKS;

        // Apply effects to entities in radius
        Box area = new Box(
                center.x - THORN_CAGE_RADIUS, center.y - THORN_CAGE_RADIUS, center.z - THORN_CAGE_RADIUS,
                center.x + THORN_CAGE_RADIUS, center.y + THORN_CAGE_RADIUS, center.z + THORN_CAGE_RADIUS
        );

        for (Entity entity : world.getOtherEntities(user, area)) {
            if (entity instanceof LivingEntity living) {
                if (living instanceof com.elemental.entity.EverlivingKnightEntity knight) {
                    knight.applyThornCageReaction(user);
                } else {
                    // Apply piercing damage to regular entities
                    living.damage(user.getDamageSources().thorns(user), THORN_CAGE_DAMAGE_PER_TICK);

                    // Apply slowness
                    living.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, THORN_CAGE_SLOW_DURATION, THORN_CAGE_SLOW_LEVEL - 1, false, false, true));
                }
            }
        }

        // Spawn thorn/vine particles
        world.spawnParticles(ParticleTypes.COMPOSTER, center.x, center.y, center.z,
                40, THORN_CAGE_RADIUS * 0.5, 1.0, THORN_CAGE_RADIUS * 0.5, 0.1);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.5, center.z,
                30, THORN_CAGE_RADIUS * 0.4, 1.5, THORN_CAGE_RADIUS * 0.4, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, center.x, center.y + 1.0, center.z,
                15, THORN_CAGE_RADIUS * 0.3, 1.0, THORN_CAGE_RADIUS * 0.3, 0.1);

        // Sound effects
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.BLOCK_VINE_PLACE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.0f);

        // Set cooldown
        user.getItemCooldownManager().set(this, cooldown);

        // Schedule server-authoritative ticking Thorn Cage zone (5 seconds duration, ticks damage every 10 ticks = 0.5s)
        if (user instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            RootboundScheduler.scheduleThornCage(world, serverPlayer, center, THORN_CAGE_RADIUS, THORN_CAGE_DURATION, THORN_CAGE_DAMAGE_TICK_INTERVAL, THORN_CAGE_DAMAGE_PER_TICK);
        }

        // Damage the item
        Hand finalHand = hand;
        stack.damage(3, user, (p) -> p.sendToolBreakStatus(finalHand));

        long endTime = world.getTime() + THORN_CAGE_DURATION;
        stack.getOrCreateNbt().putLong(ACTIVE_THORN_CAGE_KEY, endTime);
        stack.getOrCreateNbt().putLong(LAST_THORN_CAGE_TIME_KEY, world.getTime());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world instanceof ServerWorld serverWorld) {
            GeoItem.getOrAssignId(stack, serverWorld);
        }
    }

    /**
     * Checks if entity is a boss (uses custom boss tag).
     */
    private boolean isBoss(LivingEntity entity) {
        return entity.getType().isIn(ModEntityTags.BOSSES);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);

        tooltip.add(Text.literal("Wide sweep attack").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Chance to create healing roots (stronger in forests and jungles)")
                .formatted(Formatting.GREEN));
        tooltip.add(Text.literal("Active: Thorn cage - damages and slows enemies")
                .formatted(Formatting.RED));
        tooltip.add(Text.literal("Roots are destroyed by fire").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Cooldown: " + (BASE_COOLDOWN_TICKS / 20) + "s (Forest/Jungle: " + (FOREST_COOLDOWN_TICKS / 20) + "s)")
                .formatted(Formatting.DARK_GRAY));
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 1;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }
}