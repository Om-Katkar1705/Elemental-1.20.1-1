package com.elemental.item;

import com.elemental.entity.SolarArcEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import software.bernie.geckolib.constant.DataTickets;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
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
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SunforgedScimitarItem extends SwordItem implements GeoItem {

    // Durability of the Sunforged Scimitar (balanced between iron and diamond)
    public static final int DURABILITY = 300;

    // Bonus attack damage (yields diamond-tier 7-8 total damage with ScimitarToolMaterial base of 3)
    public static final int BASE_ATTACK_DAMAGE = 4;

    // Attack speed modifier (4.0 base + (-2.2) = 1.8 attacks per second, faster than standard swords)
    public static final float BASE_ATTACK_SPEED = -2.2f;

    // Fast mining speed multiplier when digging sand, red sand, or suspicious sand
    public static final float SAND_MINING_SPEED = 8.0f;

    // Bonus magic damage dealt to undead targets when in direct sunlight
    public static final float BONUS_UNDEAD_DAMAGE = 3.0f;

    // Base cooldown in ticks for the Solar Arc projectile ability (240 ticks = 12 seconds)
    public static final int BASE_COOLDOWN_TICKS = 240;

    // Charge time in ticks to reach 100% full charge (40 ticks = 2.0 seconds)
    public static final int FULL_CHARGE_TICKS = 40;
    // Minimum charge time before projectile can fire
    public static final int MIN_CHARGE_TICKS = 5;

    // Cooldown reduction percentage applied when in desert biomes (25% reduction)
    public static final double DESERT_COOLDOWN_REDUCTION = 0.25;

    // Desert cooldown in ticks (240 ticks * 0.75 = 180 ticks = 9 seconds)
    public static final int DESERT_COOLDOWN_TICKS = 180;

    // Maximum heat level capacity
    public static final int MAX_HEAT = 150;

    // Heat gained per 20 ticks (1 second) in hot biomes under direct sunlight
    public static final int HEAT_GAIN_RATE = 1;

    // Heat lost per 20 ticks outside hot biomes or sunlight
    public static final int HEAT_DECAY_RATE = 1;

    // Heat level needed to trigger bonus fire damage on attack
    public static final int HEAT_DAMAGE_THRESHOLD = 100;

    // Bonus fire damage to targets when weapon heat is at or above the threshold
    public static final float HEAT_BONUS_DAMAGE = 1.0f;

    // Controls whether high heat causes self-damage to the holder (configurable risk/reward)
    public static boolean SELF_DAMAGE_ENABLED = false;

    // Amount of self-damage dealt to the player when heat threshold is exceeded (if enabled)
    public static final float HEAT_SELF_DAMAGE = 1.0f;

    // GeckoLib animation definitions
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.sunforged.idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.sunforged.attack");
    private static final RawAnimation HEAT_ANIM = RawAnimation.begin().thenPlay("animation.sunforged.heat");
    private static final RawAnimation CHARGE_ANIM = RawAnimation.begin().thenLoop("animation.sunforged.charge");
    private static final RawAnimation RELEASE_ANIM = RawAnimation.begin().thenPlay("animation.sunforged.release");
    private static final RawAnimation COOLDOWN_ANIM = RawAnimation.begin().thenLoop("animation.sunforged.cooldown");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    // Client renderer hook
    public static Consumer<Consumer<Object>> RENDER_PROVIDER_CONSUMER;

    public SunforgedScimitarItem(Settings settings) {
        super(ScimitarToolMaterial.INSTANCE, BASE_ATTACK_DAMAGE, BASE_ATTACK_SPEED, settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public SunforgedScimitarItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
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
            if (stack == null || !(entity instanceof PlayerEntity player)) {
                return PlayState.STOP;
            }
            if (player.getItemCooldownManager().isCoolingDown(this)) {
                return state.setAndContinue(COOLDOWN_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }).triggerableAnim("attack", ATTACK_ANIM)
          .triggerableAnim("charge", CHARGE_ANIM)
          .triggerableAnim("release", RELEASE_ANIM)
          .triggerableAnim("cooldown", COOLDOWN_ANIM)
          .triggerableAnim("heat", HEAT_ANIM)
          .triggerableAnim("idle", IDLE_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.SUSPICIOUS_SAND)) {
            return SAND_MINING_SPEED;
        }
        return super.getMiningSpeedMultiplier(stack, state);
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.SUSPICIOUS_SAND)) {
            return true;
        }
        return super.isSuitableFor(state);
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

            // Passive sunlight vs undead bonus damage
            if (target.getGroup() == EntityGroup.UNDEAD) {
                if (world.isDay() && !world.isRaining() && world.isSkyVisible(attacker.getBlockPos())
                        && world.getLightLevel(LightType.SKY, attacker.getBlockPos()) >= 12) {
                    target.damage(attacker.getDamageSources().magic(), BONUS_UNDEAD_DAMAGE);
                }
            }

            // Passive heat bonus damage at high heat threshold
            int heat = getHeatLevel(stack);
            if (heat >= HEAT_DAMAGE_THRESHOLD) {
                target.damage(attacker.getDamageSources().onFire(), HEAT_BONUS_DAMAGE);
            }

            // Dawn's Siphon Enchantment: Siphons solar heat into bonus fire damage, durability mending, and fire immunity
            int siphonLevel = com.elemental.enchantment.ModEnchantments.getDawnsSiphonLevel(stack);
            if (siphonLevel > 0) {
                boolean inSun = world.isDay() && !world.isRaining() && world.isSkyVisible(attacker.getBlockPos());
                if (inSun || heat >= 50) {
                    target.damage(attacker.getDamageSources().onFire(), 1.0f * siphonLevel);
                    target.setOnFireFor(siphonLevel + 1);

                    // Shield attacker with brief Fire Resistance
                    attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, siphonLevel * 30, 0, false, false, true));

                    // Durability siphon chance
                    float repairChance = com.elemental.enchantment.ModEnchantments.getDawnsSiphonRepairChance(siphonLevel);
                    if (world.getRandom().nextFloat() < repairChance && stack.getDamage() > 0) {
                        int restore = siphonLevel >= 4 ? 2 : 1;
                        stack.setDamage(Math.max(0, stack.getDamage() - restore));
                        serverWorld.spawnParticles(ParticleTypes.WAX_OFF, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
                        world.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, 1.8f);
                    }

                    // Siphon trail particles
                    serverWorld.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.8, target.getZ(), 8, 0.2, 0.2, 0.2, 0.04);
                }
            }
        }
        return true;
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
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);
        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;

        // Visual sun and flame particles while gathering solar energy
        if (chargeTicks % 4 == 0) {
            world.addParticle(ParticleTypes.FLAME,
                    user.getX() + (world.random.nextDouble() - 0.5) * 1.0,
                    user.getY() + 0.6 + (world.random.nextDouble() - 0.5) * 0.6,
                    user.getZ() + (world.random.nextDouble() - 0.5) * 1.0,
                    0, 0.04, 0);
        }

        if (chargeTicks % 10 == 0 && chargeTicks < FULL_CHARGE_TICKS) {
            float pitch = 0.8f + (chargeTicks / (float) FULL_CHARGE_TICKS) * 0.8f;
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.35f, pitch);
        }

        if (chargeTicks == FULL_CHARGE_TICKS && user instanceof PlayerEntity player) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.8f);
            if (world.isClient()) {
                player.sendMessage(Text.literal("§6☀ FULL SOLAR CHARGE READY!").formatted(Formatting.GOLD, Formatting.BOLD), true);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player) || !player.isAlive() || player.isSpectator()) return;

        int chargeTicks = getMaxUseTime(stack) - remainingUseTicks;
        if (chargeTicks < MIN_CHARGE_TICKS) {
            // Cancelled charge / released too early
            if (world.isClient()) {
                long id = GeoItem.getId(stack);
                if (id != Long.MAX_VALUE) {
                    triggerAnim(player, id, "controller", player.getItemCooldownManager().isCoolingDown(this) ? "cooldown" : "idle");
                }
            } else if (world instanceof ServerWorld serverWorld) {
                long id = GeoItem.getOrAssignId(stack, serverWorld);
                triggerAnim(player, id, "controller", player.getItemCooldownManager().isCoolingDown(this) ? "cooldown" : "idle");
            }
            return;
        }

        if (world.isClient()) {
            long id = GeoItem.getId(stack);
            if (id != Long.MAX_VALUE) {
                triggerAnim(player, id, "controller", "release");
            }
            return;
        }

        float chargeRatio = Math.min(1.0f, (float) chargeTicks / FULL_CHARGE_TICKS);

        // Spawn solar arc projectile with speed scaled by charge and Solar Core upgrade level
        SolarArcEntity projectile = new SolarArcEntity(world, player, stack);
        net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0F);
        projectile.setPosition(player.getX() + look.x * 0.5, player.getEyeY() - 0.1 + look.y * 0.5, player.getZ() + look.z * 0.5);
        float speed = (SolarArcEntity.BASE_SPEED + (SolarArcEntity.CORE_SPEED_BONUS * getSolarCoreLevel(stack))) * (0.6f + chargeRatio * 0.4f);
        projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, speed, 0.0F);
        world.spawnEntity(projectile);

        // Scale damage by charge:
        // At full charge (chargeRatio == 1.0f): deals 26.0f raw damage.
        // Against full diamond armor (20 armor, 8 toughness), this deals exactly 12.0 HP (6.0 hearts).
        // Against an unarmored target, it deals 26.0 HP (13.0 hearts).
        float calculatedDamage = 8.0f + (18.0f * chargeRatio) + (2.0f * getSolarCoreLevel(stack));
        projectile.setProjectileDamage(calculatedDamage);

        // Sound effect - pitch varies with charge
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8F, 1.0f + chargeRatio * 0.4f);

        // Trigger GeckoLib release animation
        if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            triggerAnim(player, id, "controller", "release");
        }

        // Consume 1 durability
        stack.damage(1, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));

        // Desert cooldown reduction
        int cooldown = BASE_COOLDOWN_TICKS;
        if (world.getBiome(player.getBlockPos()).matchesKey(BiomeKeys.DESERT)) {
            cooldown = DESERT_COOLDOWN_TICKS;
        }

        player.getItemCooldownManager().set(this, cooldown);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    /**
     * Retrieves the Solar Core upgrade level from item NBT (defaults to 0).
     */
    public static int getSolarCoreLevel(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("SolarCoreLevel")) {
            return stack.getNbt().getInt("SolarCoreLevel");
        }
        return 0;
    }

    /**
     * Sets the Solar Core upgrade level on item NBT.
     */
    public static void setSolarCoreLevel(ItemStack stack, int level) {
        stack.getOrCreateNbt().putInt("SolarCoreLevel", Math.max(0, level));
    }

    /**
     * Retrieves the current environmental Heat level from item NBT.
     */
    public static int getHeatLevel(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("Heat")) {
            return stack.getNbt().getInt("Heat");
        }
        return 0;
    }

    /**
     * Sets the current environmental Heat level on item NBT.
     */
    public static void setHeatLevel(ItemStack stack, int heat) {
        stack.getOrCreateNbt().putInt("Heat", Math.max(0, Math.min(MAX_HEAT, heat)));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("§6► [Right-Click] §fSolar Arc Flame Strike"));
        tooltip.add(Text.literal("§8[Open Weapon Detail Menu for Lore & Stats]").formatted(Formatting.DARK_GRAY));
    }
}
