package com.elemental.item;

import com.elemental.entity.ModEntityTags;
import com.elemental.util.RootboundScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
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
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
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

    // Sweep attack configuration (generalized to 4.25 blocks range)
    public static final float SWEEP_ATTACK_RADIUS = 4.25f;
    public static final float SWEEP_ANGLE = 135.0f; // degrees

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
        // No GeckoLib animations registered per user request
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

        if (world instanceof ServerWorld serverWorld) {
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

        float attackAngle = SWEEP_ANGLE;
        float attackRadius = SWEEP_ATTACK_RADIUS;
        double attackRadiusSq = attackRadius * attackRadius;

        // Get attacker's look direction for sweep cone
        Vec3d lookVec = attacker.getRotationVec(1.0F).normalize();
        Vec3d attackerPos = attacker.getPos();
        double angleCos = Math.cos(Math.toRadians(attackAngle / 2.0));

        // Create sweep area box in front of attacker
        Box sweepBox = new Box(
                attackerPos.x - attackRadius, attackerPos.y - 1.0, attackerPos.z - attackRadius,
                attackerPos.x + attackRadius, attackerPos.y + 1.5, attackerPos.z + attackRadius
        );

        float sweepDamage = (BASE_ATTACK_DAMAGE + RootboundAxeToolMaterial.INSTANCE.getAttackDamage()) * 0.5f;

        // Query only living entities directly from chunk sections
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, sweepBox, e ->
                e != primaryTarget && !(e instanceof PlayerEntity) && e.isAlive() && e.squaredDistanceTo(attackerPos) <= attackRadiusSq
        );

        for (LivingEntity living : targets) {
            Vec3d toEntity = living.getPos().subtract(attackerPos).normalize();
            double dot = lookVec.dotProduct(toEntity);

            if (dot >= angleCos) {
                // Apply sweep damage (50% of base attack damage)
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
            return TypedActionResult.fail(stack);
        }

        if (world.isClient()) {
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
        double radiusSq = THORN_CAGE_RADIUS * THORN_CAGE_RADIUS;

        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, area, living ->
                living != user && !(living instanceof PlayerEntity) && living.isAlive() && living.squaredDistanceTo(center) <= radiusSq
        );

        for (LivingEntity living : targets) {
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
    public static boolean isBoss(LivingEntity entity) {
        return entity != null && entity.getType().isIn(ModEntityTags.BOSSES);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("§a► [Right-Click] §fThorn Cage"));
        tooltip.add(Text.literal("§2► [R] §fTimber Bastion"));
        tooltip.add(Text.literal("§8[Open Weapon Detail Menu for Lore & Stats]").formatted(Formatting.DARK_GRAY));
    }

    public static void performTimberBastion(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;

        ItemStack stack = null;
        if (player.getMainHandStack().getItem() instanceof RootboundAxeItem) {
            stack = player.getMainHandStack();
        } else if (player.getOffHandStack().getItem() instanceof RootboundAxeItem) {
            stack = player.getOffHandStack();
        }

        if (stack == null) return;

        int bastionLevel = com.elemental.enchantment.ModEnchantments.getTimberBastionLevel(stack);
        if (bastionLevel <= 0) {
            player.sendMessage(Text.literal("§7✦ Your Rootbound Axe requires the Timber Bastion enchantment to summon a log wall.").formatted(Formatting.GRAY), true);
            return;
        }

        ServerWorld world = player.getServerWorld();
        if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            player.sendMessage(Text.literal("§c✦ Timber Bastion is on cooldown!").formatted(Formatting.RED), true);
            return;
        }

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d horizontalLook = new Vec3d(look.x, 0, look.z).normalize();
        Vec3d right = new Vec3d(-horizontalLook.z, 0, horizontalLook.x).normalize();

        Vec3d wallCenter = player.getPos().add(horizontalLook.multiply(1.8));
        BlockPos baseCenterPos = new BlockPos((int) Math.floor(wallCenter.x), (int) Math.floor(wallCenter.y), (int) Math.floor(wallCenter.z));

        java.util.Map<BlockPos, BlockState> originalBlocks = new java.util.HashMap<>();
        for (int w = -1; w <= 2; w++) {
            BlockPos colPos = baseCenterPos.add((int) Math.round(right.x * w), 0, (int) Math.round(right.z * w));
            for (int h = 0; h < 3; h++) {
                BlockPos targetPos = colPos.up(h);
                if (world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
                    BlockState current = world.getBlockState(targetPos);
                    if (current.isAir() || current.isReplaceable() || current.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) || current.isIn(net.minecraft.registry.tag.BlockTags.FIRE)) {
                        originalBlocks.put(targetPos, current);
                        world.setBlockState(targetPos, Blocks.OAK_LOG.getDefaultState());
                        world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LOG.getDefaultState()),
                                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
        }

        int durationTicks = com.elemental.enchantment.ModEnchantments.getTimberBastionDurationTicks(bastionLevel);
        RootboundScheduler.scheduleTimberWall(world, originalBlocks, durationTicks);

        float regenChance = com.elemental.enchantment.ModEnchantments.getTimberBastionRegenChance(bastionLevel);
        if (world.getRandom().nextFloat() < regenChance) {
            int regenDuration = bastionLevel >= 3 ? 100 : 80;
            int regenAmp = bastionLevel >= 3 ? 1 : 0;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, regenDuration, regenAmp, false, false, true));
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.4, 0.5, 0.4, 0.05);
            player.sendMessage(Text.literal("§a✦ Timber Bastion granted Nature's Vitality!").formatted(Formatting.GREEN), true);
        }

        world.playSound(null, wallCenter.x, wallCenter.y, wallCenter.z, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.PLAYERS, 1.2f, 0.8f);
        world.playSound(null, wallCenter.x, wallCenter.y, wallCenter.z, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1.4f);

        player.getItemCooldownManager().set(stack.getItem(), 240);
        stack.damage(2, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
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