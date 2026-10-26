package com.elemental.item;

import com.elemental.Elemental;
import com.elemental.entity.ModEntityTags;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FrostwakePickItem extends PickaxeItem implements GeoItem {

    public static final int DURABILITY = 2000;

    public static final int BASE_ATTACK_DAMAGE = 6;
    public static final float BASE_ATTACK_SPEED = -2.8f;

    public static final float ICE_MINING_SPEED = 12.0f;
    public static final float SNOW_MINING_SPEED = 10.0f;

    public static final int BASE_COOLDOWN_TICKS = 300;
    public static final int SNOWY_COOLDOWN_TICKS = 300;

    public static final double BASE_RADIUS = 10.0;
    public static final double UPGRADED_RADIUS = 10.0;

    public static final int PASSIVE_SLOW_DURATION = 60;
    public static final int PASSIVE_STACK_DURATION = 15;
    public static final int MAX_PASSIVE_LEVEL = 2;

    public static final int ACTIVE_SLOW_DURATION = 100;
    public static final int ACTIVE_SLOW_LEVEL = 5;
    public static final int ACTIVE_SLOW_LEVEL_UPGRADED = 3;

    private static final String GLACIAL_CORE_KEY = "GlacialCoreInstalled";
    private static final String LAST_CHILL_TIME_KEY = "LastChillTime";
    private static final String CHILL_STACKS_KEY = "ChillStacks";

    private static final TagKey<Biome> COLD_SNOWY = TagKey.of(
            RegistryKeys.BIOME,
            new Identifier(Elemental.MOD_ID, "cold_snowy")
    );

    // GeckoLib animation definitions
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.frostwake_pick.idle");
    private static final RawAnimation SWING_ANIM = RawAnimation.begin().thenPlay("animation.frostwake_pick.swing");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.frostwake_pick.cooldown");
    private static final RawAnimation FROSTBURST_ANIM = RawAnimation.begin()
            .thenPlay("animation.frostwake_pick.frostburst");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    // Client renderer hook
    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    public FrostwakePickItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public FrostwakePickItem(Settings settings) {
        super(FrostwakePickToolMaterial.INSTANCE, BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
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
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            Entity entity = state.getData(DataTickets.ENTITY);
            if (stack == null || !(entity instanceof PlayerEntity player)) {
                return PlayState.STOP;
            }
            if (player.getItemCooldownManager().isCoolingDown(this)) {
                return state.setAndContinue(COOLDOWN_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("swing", SWING_ANIM)
          .triggerableAnim("cooldown", COOLDOWN_ANIM)
          .triggerableAnim("frostburst", FROSTBURST_ANIM)
          .triggerableAnim("idle", IDLE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)) {
            return ICE_MINING_SPEED;
        }
        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            return SNOW_MINING_SPEED;
        }
        return super.getMiningSpeedMultiplier(stack, state);
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)
                || state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            return true;
        }
        return super.isSuitableFor(state);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        World world = attacker.getWorld();

        if (!world.isClient() && !isBoss(target)) {
            applyPassiveChill(stack, target, world.getTime());
        }

        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(attacker, id, "controller", "swing");
            }
        } else if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            triggerAnim(attacker, id, "controller", "swing");
        }
        return true;
    }

    private void applyPassiveChill(ItemStack stack, LivingEntity target, long worldTime) {
        int currentStacks = getChillStacks(stack);
        long lastChillTime = getLastChillTime(stack);

        if (worldTime - lastChillTime > 100) {
            currentStacks = 0;
        }

        currentStacks = Math.min(currentStacks + 1, MAX_PASSIVE_LEVEL);
        int duration = PASSIVE_SLOW_DURATION + (currentStacks - 1) * PASSIVE_STACK_DURATION;
        int amplifier = currentStacks - 1;

        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, amplifier, false, false, true));

        setChillStacks(stack, currentStacks);
        setLastChillTime(stack, worldTime);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            GeoItem.getOrAssignId(stack, serverWorld);
        }
        super.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!user.isAlive() || user.isSpectator()) {
            return TypedActionResult.pass(stack);
        }

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
                triggerAnim(user, id, "controller", "frostburst");
            }
            return TypedActionResult.consume(stack);
        }

        performFrostburst((ServerWorld) world, user, stack);
        return TypedActionResult.consume(stack);
    }

    private void performFrostburst(ServerWorld world, PlayerEntity user, ItemStack stack) {
        long id = GeoItem.getOrAssignId(stack, world);
        triggerAnim(user, id, "controller", "frostburst");

        double radius = hasGlacialCore(stack) ? UPGRADED_RADIUS : BASE_RADIUS;
        double radiusSq = radius * radius;
        Vec3d center = user.getPos();

        Box area = new Box(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, area, e -> e != user && e.isAlive() && e.squaredDistanceTo(user) <= radiusSq)) {
            if (living instanceof com.elemental.entity.EverlivingKnightEntity knight) {
                knight.applyFrostburstReaction();
            } else {
                int slowLevel = hasGlacialCore(stack) ? ACTIVE_SLOW_LEVEL_UPGRADED : ACTIVE_SLOW_LEVEL;
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ACTIVE_SLOW_DURATION, slowLevel - 1, false, false, true));
            }
        }

        BlockPos centerPos = user.getBlockPos();
        int blockRadius = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.iterateOutwards(centerPos, blockRadius, blockRadius, blockRadius)) {
            if (pos.isWithinDistance(centerPos, radius) && world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                BlockState state = world.getBlockState(pos);
                if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()) {
                    world.setBlockState(pos, Blocks.ICE.getDefaultState());
                }
            }
        }

        world.spawnParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1, center.z, 30, radius * 0.5, 1.0, radius * 0.5, 0.1);
        world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, center.x, center.y + 1, center.z, 20, radius * 0.3, 1.0, radius * 0.3, 0.2);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 0.6f, 1.2f);

        // Spike Shockwave Enchantment: Erupts concentric ice spikes and launches mobs with knockback
        int spikeLevel = com.elemental.enchantment.ModEnchantments.getSpikeShockwaveLevel(stack);
        if (spikeLevel > 0) {
            int maxRadius = com.elemental.enchantment.ModEnchantments.getSpikeShockwaveMaxRadius(spikeLevel);
            performSpikeShockwave(world, user, center, maxRadius, spikeLevel);
        }

        int cooldown = BASE_COOLDOWN_TICKS;
        if (world.getBiome(user.getBlockPos()).isIn(COLD_SNOWY)) {
            cooldown = SNOWY_COOLDOWN_TICKS;
        }
        user.getItemCooldownManager().set(this, cooldown);

        stack.damage(2, user, (p) -> p.sendToolBreakStatus(Hand.MAIN_HAND));
    }

    private void performSpikeShockwave(ServerWorld world, PlayerEntity user, Vec3d center, int maxRadius, int level) {
        // Concentric circles from 4 blocks up to maxRadius (4, 6, 8, 10)
        for (int r = 4; r <= maxRadius; r++) {
            int points = (int) (r * 3.5);
            for (int i = 0; i < points; i++) {
                double angle = (2.0 * Math.PI * i) / points;
                double px = center.x + r * Math.cos(angle);
                double pz = center.z + r * Math.sin(angle);
                double py = center.y;
                world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                        px, py + 0.2, pz, 3, 0.1, 0.4, 0.1, 0.05);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, px, py + 0.4, pz, 2, 0.1, 0.2, 0.1, 0.02);
            }
        }

        Box spikeArea = new Box(center.x - maxRadius, center.y - 3.0, center.z - maxRadius,
                center.x + maxRadius, center.y + 4.0, center.z + maxRadius);
        double maxRadiusSq = (double) maxRadius * maxRadius;

        float spikeDamage = 4.0f + 2.5f * level;
        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, spikeArea,
                e -> e != user && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(center) <= maxRadiusSq)) {
            if (user.isTeammate(living)) continue;

            // Damage and preserved slowness
            living.damage(world.getDamageSources().playerAttack(user), spikeDamage);
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ACTIVE_SLOW_DURATION, Math.min(3, level), false, false, true));

            // Upward and outward knockback
            Vec3d diff = living.getPos().subtract(center);
            double dist = diff.length();
            if (dist > 0.01) {
                Vec3d push = diff.normalize().multiply(0.35 + 0.1 * level).add(0, 0.45 + 0.08 * level, 0);
                living.setVelocity(push);
                living.velocityModified = true;
            }
        }

        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    private boolean isBoss(LivingEntity entity) {
        return entity.getType().isIn(ModEntityTags.BOSSES);
    }

    public static boolean hasGlacialCore(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean(GLACIAL_CORE_KEY);
    }

    public static void setGlacialCore(ItemStack stack, boolean installed) {
        stack.getOrCreateNbt().putBoolean(GLACIAL_CORE_KEY, installed);
    }

    private static int getChillStacks(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains(CHILL_STACKS_KEY)) {
            return stack.getNbt().getInt(CHILL_STACKS_KEY);
        }
        return 0;
    }

    private static void setChillStacks(ItemStack stack, int stacks) {
        stack.getOrCreateNbt().putInt(CHILL_STACKS_KEY, stacks);
    }

    private static long getLastChillTime(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains(LAST_CHILL_TIME_KEY)) {
            return stack.getNbt().getLong(LAST_CHILL_TIME_KEY);
        }
        return 0;
    }

    private static void setLastChillTime(ItemStack stack, long time) {
        stack.getOrCreateNbt().putLong(LAST_CHILL_TIME_KEY, time);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("§b► [Right-Click] §fFrostburst"));
        tooltip.add(Text.literal("§d► [Passive] §fGlacial Chill"));
        tooltip.add(Text.literal("§8[Open Weapon Detail Menu for Lore & Stats]").formatted(Formatting.DARK_GRAY));
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