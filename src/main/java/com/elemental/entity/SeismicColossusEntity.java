package com.elemental.entity;

import com.elemental.config.ElementalConfig;
import com.elemental.item.AshenRiftBladeItem;
import com.elemental.item.FrostwakePickItem;
import com.elemental.item.ModItems;
import com.elemental.item.RootboundAxeItem;
import com.elemental.item.StormcallerHammerItem;
import com.elemental.item.SunforgedScimitarItem;
import com.elemental.network.SeismicColossusNetworking;
import com.elemental.sound.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seismic Colossus — A towering, ancient stone titan boss made from giant
 * stones.
 * Features 3 dramatic combat phases, terrain ripping/block-throwing, screen
 * shake,
 * and a deep weapon-sticking mechanic freed with the Stormcaller Hammer.
 */
public class SeismicColossusEntity extends HostileEntity implements GeoEntity {

    // ==================== CONFIGURATION VARIABLES ====================
    public static double ARMOR = 10.0;
    public static double ARMOR_TOUGHNESS = 3.0;
    public static double KNOCKBACK_RESISTANCE = 1.0;
    public static double FOLLOW_RANGE = 48.0;
    public static int EXPERIENCE_POINTS = 100;

    // Special Attack Damages (Nerfed for balanced combat)
    public static float SLAM_DAMAGE = 8.0f;
    public static float STOMP_DAMAGE = 5.0f;
    public static float FISSURE_DAMAGE = 8.0f;
    public static float WAR_ROAR_DAMAGE = 5.0f;
    public static float CHARGE_DAMAGE = 10.0f;
    public static float BREAKOUT_DAMAGE = 8.0f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ==================== GECKOLIB ANIMATIONS ====================
    protected static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.seismic_colossus.idle");
    protected static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.seismic_colossus.walk");
    protected static final RawAnimation PUNCH_ANIM = RawAnimation.begin().thenPlay("animation.seismic_colossus.punch");
    protected static final RawAnimation RAGE_ANIM = RawAnimation.begin().thenPlay("animation.seismic_colossus.rage");
    protected static final RawAnimation DEATH_ANIM = RawAnimation.begin()
            .thenPlayAndHold("animation.seismic_colossus.death");
    protected static final RawAnimation BREAKOUT_ANIM = RawAnimation.begin()
            .thenPlay("animation.seismic_colossus.breakout");
    protected static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlay("animation.seismic_colossus.slam");
    protected static final RawAnimation STOMP_ANIM = RawAnimation.begin().thenPlay("animation.seismic_colossus.stomp");
    protected static final RawAnimation THROW_ANIM = RawAnimation.begin().thenPlay("animation.seismic_colossus.throw");
    protected static final RawAnimation FISSURE_ANIM = RawAnimation.begin()
            .thenPlay("animation.seismic_colossus.fissure");
    protected static final RawAnimation WAR_ROAR_ANIM = RawAnimation.begin()
            .thenPlay("animation.seismic_colossus.war_roar");
    protected static final RawAnimation AVALANCHE_ANIM = RawAnimation.begin()
            .thenPlay("animation.seismic_colossus.avalanche");
    protected static final RawAnimation CHARGE_ANIM = RawAnimation.begin()
            .thenLoop("animation.seismic_colossus.charge");

    // ==================== TRACKED DATA ====================
    public static final TrackedData<Integer> PHASE = DataTracker.registerData(SeismicColossusEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Integer> ATTACK_STATE = DataTracker.registerData(SeismicColossusEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    public static final TrackedData<Boolean> INVULNERABLE_STATE = DataTracker.registerData(SeismicColossusEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    public static final TrackedData<NbtCompound> STUCK_WEAPONS_DATA = DataTracker
            .registerData(SeismicColossusEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    public static final TrackedData<Boolean> TRIDENT_STUCK = DataTracker.registerData(SeismicColossusEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);

    // Attack State constants
    public static final int STATE_IDLE = 0;
    public static final int STATE_PUNCH = 1;
    public static final int STATE_SLAM = 2;
    public static final int STATE_STOMP = 3;
    public static final int STATE_ROCK_THROW = 4;
    public static final int STATE_BLOCK_THROW = 5;
    public static final int STATE_PHASE_TRANSITION = 6;
    public static final int STATE_BREAKOUT = 7;
    public static final int STATE_FISSURE = 8;
    public static final int STATE_WAR_ROAR = 9;
    public static final int STATE_AVALANCHE = 10;
    public static final int STATE_CHARGE = 11;

    // ==================== BOSS BAR ====================
    private final ServerBossBar bossBar;

    // ==================== COMBAT TIMERS & COOLDOWNS ====================
    private int attackCooldownTicks = 0;
    private int actionStateTicks = 0;
    private int phaseTransitionTimer = 0;
    private int footstepTicker = 0;
    private int tridentStuckTicks = 0;
    private BlockState heldRipBlockState = null;

    // Bull Rush Charge tracking
    private Vec3d chargeVelocity = Vec3d.ZERO;
    private int chargeTicks = 0;

    // Earthshatter Breakout tracking (when trapped in pit, hole, or buried in solid
    // blocks)
    private int breakoutTimer = 0;
    private boolean isBreakoutAirborne = false;
    private int trappedTicks = 0;

    // Terrain restoration tracker
    private final Map<BlockPos, BlockState> pulledBlocks = new HashMap<>();

    // Continuous melee attack tracking (streak resets if player pauses > 50 ticks)
    private final Map<java.util.UUID, Integer> consecutiveMeleeHits = new HashMap<>();
    private final Map<java.util.UUID, Long> lastMeleeHitTime = new HashMap<>();
    private final Map<java.util.UUID, Integer> requiredHitsToStick = new HashMap<>();

    // Stuck Weapons representation
    public static class StuckWeapon {
        public ItemStack stack = ItemStack.EMPTY;
        public int hitsRemaining = 3;
        public String boneName = "Body";
        public double offsetX = 0.0;
        public double offsetY = 0.0;
        public double offsetZ = 0.0;

        public NbtCompound toNbt() {
            NbtCompound tag = new NbtCompound();
            tag.put("Item", this.stack.writeNbt(new NbtCompound()));
            tag.putInt("Hits", this.hitsRemaining);
            tag.putString("Bone", this.boneName);
            tag.putDouble("X", this.offsetX);
            tag.putDouble("Y", this.offsetY);
            tag.putDouble("Z", this.offsetZ);
            return tag;
        }

        public static StuckWeapon fromNbt(NbtCompound tag) {
            StuckWeapon sw = new StuckWeapon();
            if (tag.contains("Item")) {
                sw.stack = ItemStack.fromNbt(tag.getCompound("Item"));
            }
            sw.hitsRemaining = tag.getInt("Hits");
            sw.boneName = tag.getString("Bone");
            sw.offsetX = tag.getDouble("X");
            sw.offsetY = tag.getDouble("Y");
            sw.offsetZ = tag.getDouble("Z");
            // Fix any legacy floating coordinates: always anchor cleanly to the front stone
            // torso
            if (sw.offsetZ < -0.35 || sw.offsetY < 2.0 || sw.offsetY > 3.5 || !"Body".equals(sw.boneName)) {
                sw.boneName = "Body";
                sw.offsetX = 0.0;
                sw.offsetY = 2.6;
                sw.offsetZ = -0.20;
            }
            return sw;
        }
    }

    private final List<StuckWeapon> stuckWeapons = new ArrayList<>();

    public SeismicColossusEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = EXPERIENCE_POINTS;

        this.bossBar = new ServerBossBar(
                this.getDisplayName(),
                BossBar.Color.GREEN,
                BossBar.Style.NOTCHED_10);
        this.bossBar.setDarkenSky(true);
        this.bossBar.setDragonMusic(false);
    }

    public static DefaultAttributeContainer.Builder createColossusAttributes() {
        ElementalConfig config = ElementalConfig.get();
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, config.seismicColossusPhase1Health)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, config.seismicColossusSpeed)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, config.seismicColossusBaseDamage)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ARMOR_TOUGHNESS)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, 1);
        this.dataTracker.startTracking(ATTACK_STATE, STATE_IDLE);
        this.dataTracker.startTracking(INVULNERABLE_STATE, false);
        this.dataTracker.startTracking(STUCK_WEAPONS_DATA, new NbtCompound());
        this.dataTracker.startTracking(TRIDENT_STUCK, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new ColossusPhaseTransitionGoal(this));
        this.goalSelector.add(3, new ColossusCombatGoal(this));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ==================== PHASE & STATE GETTERS / SETTERS ====================
    public int getPhase() {
        return this.dataTracker.get(PHASE);
    }

    public void setPhase(int phase) {
        this.dataTracker.set(PHASE, phase);
        this.updateBossBarTitle();
    }

    public float getPhaseMaxHealth(int phase) {
        ElementalConfig config = ElementalConfig.get();
        if (phase == 2) {
            return config.seismicColossusPhase2Health;
        } else if (phase == 3) {
            return config.seismicColossusPhase3Health;
        }
        return config.seismicColossusPhase1Health;
    }

    public int getAttackState() {
        return this.dataTracker.get(ATTACK_STATE);
    }

    public void setAttackState(int state) {
        this.dataTracker.set(ATTACK_STATE, state);
    }

    public boolean isInvulnerablePhase() {
        return this.dataTracker.get(INVULNERABLE_STATE);
    }

    public void setInvulnerablePhase(boolean invuln) {
        this.dataTracker.set(INVULNERABLE_STATE, invuln);
    }

    public boolean isTridentStuck() {
        return this.dataTracker.get(TRIDENT_STUCK);
    }

    public void setTridentStuck(boolean stuck) {
        this.dataTracker.set(TRIDENT_STUCK, stuck);
    }

    public List<StuckWeapon> getStuckWeapons() {
        if (this.getWorld().isClient()) {
            List<StuckWeapon> list = new ArrayList<>();
            NbtCompound tag = this.dataTracker.get(STUCK_WEAPONS_DATA);
            if (tag.contains("List")) {
                NbtList nbtList = tag.getList("List", 10);
                for (int i = 0; i < nbtList.size(); i++) {
                    list.add(StuckWeapon.fromNbt(nbtList.getCompound(i)));
                }
            }
            return list;
        }
        return this.stuckWeapons;
    }

    public void syncStuckWeapons() {
        NbtCompound tag = new NbtCompound();
        NbtList list = new NbtList();
        for (StuckWeapon sw : this.stuckWeapons) {
            list.add(sw.toNbt());
        }
        tag.put("List", list);
        this.dataTracker.set(STUCK_WEAPONS_DATA, tag);
    }

    private void updateBossBarTitle() {
        int phase = getPhase();
        Formatting colorFormat = phase == 1 ? Formatting.DARK_GREEN
                : (phase == 2 ? Formatting.GOLD : Formatting.DARK_RED);
        String phaseName = phase == 1 ? "Stone Guardian" : (phase == 2 ? "Living Quarry" : "Seismic Rage");

        this.bossBar.setName(Text.literal("Seismic Colossus — " + phaseName).formatted(colorFormat, Formatting.BOLD));
        if (phase == 1) {
            this.bossBar.setColor(BossBar.Color.GREEN);
        } else if (phase == 2) {
            this.bossBar.setColor(BossBar.Color.YELLOW);
        } else {
            this.bossBar.setColor(BossBar.Color.RED);
        }
    }

    private float lastBossBarPercent = -1.0f;

    // ==================== TICKING & WALKING EFFECTS ====================
    @Override
    public void tick() {
        super.tick();

        // 1. Boss bar updates
        if (!this.getWorld().isClient()) {
            float percent = MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f);
            if (Math.abs(percent - this.lastBossBarPercent) > 0.005f) {
                this.bossBar.setPercent(percent);
                this.lastBossBarPercent = percent;
            }
        }

        // 2. Heavy Footsteps, Dust, & Screen Shake
        if (this.isOnGround() && this.getVelocity().horizontalLengthSquared() > 0.0008) {
            this.footstepTicker++;
            if (this.footstepTicker >= 22) { // heavy cadence
                this.footstepTicker = 0;
                this.triggerFootstep();
            }
        } else {
            this.footstepTicker = 0;
        }

        // 3. Trident stick countdown
        if (!this.getWorld().isClient() && isTridentStuck()) {
            if (this.tridentStuckTicks > 0) {
                this.tridentStuckTicks--;
                if (this.tridentStuckTicks <= 0) {
                    setTridentStuck(false);
                }
            }
        }

        // 4. Server-side Phase Transition & Snappy Regeneration (Prevents permanent
        // invulnerability bug)
        if (!this.getWorld().isClient()) {
            if (getAttackState() == STATE_PHASE_TRANSITION) {
                this.getNavigation().stop();
                this.phaseTransitionTimer++;
                float maxHp = this.getMaxHealth();
                float progress = Math.min(1.0f, (float) this.phaseTransitionTimer / 25.0f);
                this.setHealth(Math.max(1.0f, maxHp * progress));

                if (this.phaseTransitionTimer % 4 == 0 && this.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.WAX_OFF, this.getX(), this.getY() + 1.8, this.getZ(), 8, 0.8, 1.0,
                            0.8, 0.05);
                }

                if (this.phaseTransitionTimer >= 30) {
                    this.completePhaseTransition();
                }
            } else if (isInvulnerablePhase()) {
                // Safety failsafe: If not actively transitioning, invulnerability MUST be
                // turned off
                setInvulnerablePhase(false);
            }
        }

        // 5. Server-side Earthshatter Breakout (Pulverizes blocks and leaps out of rift
        // holes / pits)
        if (!this.getWorld().isClient()) {
            if (getAttackState() == STATE_BREAKOUT) {
                tickBreakoutSequence();
            } else if (getAttackState() != STATE_PHASE_TRANSITION) {
                checkIfTrappedAndTriggerBreakout();
            }
        }

        // 6. Bull Rush Charge Tick
        if (!this.getWorld().isClient() && getAttackState() == STATE_CHARGE) {
            tickChargeSequence();
        }

        // 7. Attack action ticker (never cancel phase transition, breakout, or charge)
        if (getAttackState() != STATE_PHASE_TRANSITION && getAttackState() != STATE_BREAKOUT
                && getAttackState() != STATE_CHARGE && this.actionStateTicks > 0) {
            this.actionStateTicks--;
            if (this.actionStateTicks <= 0) {
                setAttackState(STATE_IDLE);
            }
        }

        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }
    }

    private void triggerFootstep() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // Heavy footstep sounds
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_STEP, SoundCategory.HOSTILE, 1.6f, 0.6f);

            // Ground dust particles
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 0.1, this.getZ(),
                    25, 1.2, 0.1, 1.2, 0.1);
            serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX(), this.getY() + 0.1, this.getZ(),
                    4, 0.8, 0.05, 0.8, 0.01);

            // Screen shake nearby (22 block radius)
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 22.0, 1.5f, 8);
        }
    }

    // ==================== DAMAGE & WEAPON-STICKING ====================
    @Override
    public boolean damage(DamageSource source, float amount) {
        // Suffocation in wall -> Instant Earthshatter Breakout!
        if (source.isOf(DamageTypes.IN_WALL)) {
            if (getAttackState() != STATE_BREAKOUT && !this.getWorld().isClient()) {
                triggerBreakout();
            }
            return false;
        }

        // Safety failsafe: if not in active phase transition, force clear
        // invulnerability
        if (getAttackState() != STATE_PHASE_TRANSITION && isInvulnerablePhase()) {
            setInvulnerablePhase(false);
        }

        if (isInvulnerablePhase() && !source.isOf(DamageTypes.OUT_OF_WORLD)) {
            return false;
        }

        Entity attacker = source.getAttacker();

        // 1. Tidebreaker Trident check (Phase 2 & 3)
        if (source.getSource() instanceof TidebreakerTridentEntity trident) {
            if (getPhase() >= 2 && !isTridentStuck()) {
                int stickDuration = ElementalConfig.get().seismicColossusWeaponStickDuration;
                trident.stickToEntity(this, new Vec3d((Math.random() - 0.5) * 0.6, 2.2, -0.7), stickDuration);
                setTridentStuck(true);
                this.tridentStuckTicks = stickDuration;

                if (this.getWorld() instanceof ServerWorld sw) {
                    sw.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                            SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 2.0f, 0.7f);
                    sw.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 2.0, this.getZ(), 20, 0.3, 0.3,
                            0.3, 0.1);
                }
            }
        }

        // 2. Melee Weapon & Hammer interaction
        if (attacker instanceof PlayerEntity player && !this.getWorld().isClient()) {
            ItemStack held = player.getMainHandStack();

            // Hammer hit check -> dislodge stuck weapons!
            if (isStormcallerHammer(held)) {
                if (!this.stuckWeapons.isEmpty()) {
                    handleHammerStrike(player);
                }
            }
            // Stickable weapon check during Phase 2 & 3: Only when melee attacks are
            // CONTINUOUS!
            else if (getPhase() >= 2 && isStickableWeapon(held)) {
                long currentTick = this.getWorld().getTime();
                Long lastTick = this.lastMeleeHitTime.get(player.getUuid());
                int streak = 1;
                if (lastTick != null && (currentTick - lastTick) <= 50) {
                    streak = this.consecutiveMeleeHits.getOrDefault(player.getUuid(), 0) + 1;
                } else {
                    this.requiredHitsToStick.remove(player.getUuid());
                }
                this.consecutiveMeleeHits.put(player.getUuid(), streak);
                this.lastMeleeHitTime.put(player.getUuid(), currentTick);

                // Randomly requires between 3 and 6 consecutive attacks to stick
                int required = this.requiredHitsToStick.computeIfAbsent(player.getUuid(),
                        k -> 3 + this.random.nextInt(4)); // 3, 4, 5, or 6

                if (streak == required - 1) {
                    if (this.getWorld() instanceof ServerWorld sw) {
                        sw.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                                SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 1.4f, 1.2f);
                        sw.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.0, this.getZ(), 10, 0.2, 0.2,
                                0.2, 0.1);
                    }
                    player.sendMessage(
                            Text.literal("§6⚠ The stone hide is gripping your weapon! Step back or it will get stuck!"),
                            true);
                } else if (streak >= required) {
                    this.consecutiveMeleeHits.remove(player.getUuid());
                    this.lastMeleeHitTime.remove(player.getUuid());
                    this.requiredHitsToStick.remove(player.getUuid());
                    handleWeaponSticking(player, held);
                }
            }
        }

        // Check if lethal hit will trigger phase transition instead of normal death
        if (this.getHealth() - amount <= 0.0f) {
            int currentPhase = getPhase();
            if (currentPhase < 3) {
                this.startPhaseTransition(currentPhase + 1);
                return false;
            }
        }

        return super.damage(source, amount);
    }

    private boolean isStormcallerHammer(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        return stack.isOf(ModItems.STORMCALLER_HAMMER) || stack.getItem() instanceof StormcallerHammerItem;
    }

    private boolean isStickableWeapon(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        Item item = stack.getItem();
        if (item instanceof SunforgedScimitarItem || item instanceof FrostwakePickItem
                || item instanceof RootboundAxeItem || item instanceof AshenRiftBladeItem) {
            return true;
        }
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof PickaxeItem) {
            return true;
        }
        String path = Registries.ITEM.getId(item).getPath().toLowerCase();
        return path.contains("scimitar") || path.contains("pickaxe") || path.contains("axe")
                || path.contains("blade") || path.contains("sword");
    }

    private void handleWeaponSticking(PlayerEntity player, ItemStack weapon) {
        StuckWeapon sw = new StuckWeapon();
        sw.stack = weapon.copy();
        sw.hitsRemaining = 3;
        sw.boneName = "Body";

        // Stagger positions across the chest plate
        int count = this.stuckWeapons.size();
        double[] xOffsets = { -0.4, 0.4, -0.15, 0.15, -0.55, 0.55, 0.0 };
        double[] yOffsets = { 2.7, 2.5, 3.0, 2.3, 2.8, 2.6, 2.4 };
        int idx = count % xOffsets.length;

        sw.offsetX = xOffsets[idx] + (Math.random() - 0.5) * 0.1;
        sw.offsetY = yOffsets[idx] + (Math.random() - 0.5) * 0.1;
        sw.offsetZ = -0.20; // Embedded in the front stone chest plate!

        this.stuckWeapons.add(sw);
        this.syncStuckWeapons();

        // Disarm player main hand
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                    SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.HOSTILE, 1.8f, 0.7f);
            serverWorld.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.0, this.getZ(),
                    25, 0.4, 0.4, 0.4, 0.15);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 2.0, this.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        }

        player.sendMessage(Text.literal(
                "Your weapon got stuck into the Colossus's stone chest! Hit it with a Stormcaller Hammer to free it!")
                .formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    private void handleHammerStrike(PlayerEntity player) {
        if (this.stuckWeapons.isEmpty())
            return;

        StuckWeapon target = this.stuckWeapons.get(0);
        target.hitsRemaining--;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // Sparks & cracks feedback
            serverWorld.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 2.0, this.getZ(), 25, 0.3, 0.3,
                    0.3, 0.15);
            serverWorld.spawnParticles(ParticleTypes.WAX_OFF, this.getX(), this.getY() + 2.0, this.getZ(), 20, 0.3, 0.3,
                    0.3, 0.1);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRACKED_STONE_BRICKS.getDefaultState()),
                    this.getX(), this.getY() + 2.0, this.getZ(), 30, 0.4, 0.4, 0.4, 0.1);

            serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                    ModSounds.HAMMER_SPARK, SoundCategory.PLAYERS, 1.8f, 1.0f);
        }

        if (target.hitsRemaining <= 0) {
            // Weapon freed! Drop as ItemEntity
            this.stuckWeapons.remove(0);
            this.syncStuckWeapons();

            ItemEntity drop = new ItemEntity(this.getWorld(), this.getX(), this.getY() + 1.5, this.getZ(),
                    target.stack);
            drop.setVelocity((Math.random() - 0.5) * 0.3, 0.45, (Math.random() - 0.5) * 0.3);
            this.getWorld().spawnEntity(drop);

            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.playSound(null, this.getX(), this.getY() + 1.5, this.getZ(),
                        SoundEvents.BLOCK_ANVIL_BREAK, SoundCategory.PLAYERS, 1.4f, 1.4f);
            }
            player.sendMessage(
                    Text.literal("You dislodged a stuck weapon!").formatted(Formatting.GREEN, Formatting.BOLD), true);
        } else {
            player.sendMessage(Text.literal(
                    "Hammer strike cracked the stone! " + target.hitsRemaining + " hits remaining to free weapon!")
                    .formatted(Formatting.YELLOW), true);
        }
    }

    // ==================== PHASE TRANSITIONS ====================
    public void startPhaseTransition(int nextPhase) {
        this.phaseTransitionTimer = 0;
        setAttackState(STATE_PHASE_TRANSITION);
        this.actionStateTicks = 0;
        setInvulnerablePhase(true);
        this.setPhase(nextPhase);

        float maxHp = getPhaseMaxHealth(nextPhase);
        if (this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
        }
        this.setHealth(Math.max(5.0f, maxHp * 0.05f));

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // Roar sound
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_ROAR, SoundCategory.HOSTILE, 3.0f, 0.7f);

            // Falling rock / explosion particles
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 3.0, this.getZ(), 80, 2.0, 2.0, 2.0, 0.2);
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY() + 2.0, this.getZ(), 5, 1.0, 1.0, 1.0, 0.0);

            // Heavy screen shake
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 36.0, 4.5f, 30);

            if (nextPhase == 2) {
                // Purple crystal hand breaks off!
                serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                        SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 3.5f, 0.7f);
                serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                        SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.5f, 0.5f);
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.getDefaultState()),
                        this.getX() + 1.5, this.getY() + 2.0, this.getZ(), 100, 1.0, 1.2, 1.0, 0.25);
                serverWorld.spawnParticles(ParticleTypes.PORTAL,
                        this.getX() + 1.5, this.getY() + 2.0, this.getZ(), 40, 0.8, 1.0, 0.8, 0.1);
                serverWorld.getServer().getPlayerManager().broadcast(
                        Text.literal(
                                "§5§l[Seismic Colossus] §cThe purple crystal hand shatters and breaks off! Phase 2: Living Quarry has begun!"),
                        false);
            } else if (nextPhase == 3) {
                // Entire purple arm breaks off completely!
                serverWorld.playSound(null, this.getX(), this.getY() + 2.5, this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.0f, 0.6f);
                serverWorld.playSound(null, this.getX(), this.getY() + 2.5, this.getZ(),
                        SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.HOSTILE, 2.8f, 0.6f);
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.getDefaultState()),
                        this.getX() + 1.5, this.getY() + 2.5, this.getZ(), 120, 1.2, 1.5, 1.2, 0.3);
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.getDefaultState()),
                        this.getX() + 1.5, this.getY() + 2.5, this.getZ(), 80, 1.0, 1.2, 1.0, 0.2);
                serverWorld.getServer().getPlayerManager().broadcast(
                        Text.literal(
                                "§4§l[Seismic Colossus] §cThe entire purple arm breaks completely off! The titan enters an unstoppable Seismic Rage!"),
                        false);
            }
        }
    }

    public void completePhaseTransition() {
        this.phaseTransitionTimer = 0;
        this.actionStateTicks = 0;
        setInvulnerablePhase(false);
        setAttackState(STATE_IDLE);

        int phase = getPhase();
        float maxHp = getPhaseMaxHealth(phase);

        // Adjust Max Health and fully heal (equalized across all phases)
        if (this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
        }
        this.setHealth(maxHp);

        // Phase 3 10% stat boost
        if (phase == 3) {
            ElementalConfig config = ElementalConfig.get();
            double boostedDamage = config.seismicColossusBaseDamage * 1.10;
            double boostedSpeed = config.seismicColossusSpeed * 1.10;
            if (this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null) {
                this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(boostedDamage);
            }
            if (this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED) != null) {
                this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(boostedSpeed);
            }

            // Spawn Phase 3 minions!
            spawnColossusMinions();
        }

        this.updateBossBarTitle();
    }

    private void spawnColossusMinions() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld))
            return;

        int count = ElementalConfig.get().seismicColossusMinionCount;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double spawnX = this.getX() + Math.cos(angle) * 4.0;
            double spawnZ = this.getZ() + Math.sin(angle) * 4.0;

            ColossusMiniEntity minion = new ColossusMiniEntity(ModEntities.COLOSSUS_MINI, this.getWorld());
            minion.refreshPositionAndAngles(spawnX, this.getY(), spawnZ, (float) Math.toDegrees(angle), 0.0f);
            serverWorld.spawnEntityAndPassengers(minion);

            serverWorld.spawnParticles(ParticleTypes.POOF, spawnX, this.getY() + 0.5, spawnZ, 15, 0.4, 0.4, 0.4, 0.05);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    spawnX, this.getY() + 0.5, spawnZ, 20, 0.4, 0.4, 0.4, 0.1);
        }
    }

    // ==================== ATTACKS ====================
    public void executeStonePunch(LivingEntity target) {
        setAttackState(STATE_PUNCH);
        this.actionStateTicks = 20;

        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        DamageSource source = this.getDamageSources().mobAttack(this);
        target.damage(source, damage);

        // Heavy stone impact knockback
        Vec3d knockback = target.getPos().subtract(this.getPos()).normalize().multiply(1.2);
        target.addVelocity(knockback.x, 0.3, knockback.z);
        target.velocityModified = true;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_HURT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 2,
                    0, 0, 0, 0);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
        }
    }

    public void executeGroundSlam() {
        setAttackState(STATE_SLAM);
        this.actionStateTicks = 25;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // Sound and particles
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_SLAM, SoundCategory.HOSTILE, 2.5f, 0.6f);

            // Expanding shockwave particles
            double radius = 7.0;
            for (int i = 0; i < 36; i++) {
                double rad = Math.toRadians(i * 10);
                double px = this.getX() + Math.cos(rad) * 4.0;
                double pz = this.getZ() + Math.sin(rad) * 4.0;
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                        px, this.getY() + 0.1, pz, 4, 0.2, 0.1, 0.2, 0.05);
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        px, this.getY() + 0.1, pz, 1, 0.1, 0.05, 0.1, 0.02);
            }

            // Screen shake
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 28.0, 3.5f, 18);

            // Damage and Slowness to nearby players
            Box box = this.getBoundingBox().expand(radius, 2.0, radius);
            List<PlayerEntity> players = serverWorld.getEntitiesByClass(PlayerEntity.class, box, LivingEntity::isAlive);
            for (PlayerEntity player : players) {
                DamageSource source = this.getDamageSources().mobAttack(this);
                player.damage(source, SLAM_DAMAGE);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
            }
        }
    }

    public void executeStomp() {
        setAttackState(STATE_STOMP);
        this.actionStateTicks = 20;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_STEP, SoundCategory.HOSTILE, 2.5f, 0.4f);

            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 0.1, this.getZ(), 40, 1.5, 0.1, 1.5, 0.2);

            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 20.0, 2.4f, 14);

            Box box = this.getBoundingBox().expand(5.0, 1.5, 5.0);
            List<PlayerEntity> players = serverWorld.getEntitiesByClass(PlayerEntity.class, box, LivingEntity::isAlive);
            for (PlayerEntity player : players) {
                DamageSource source = this.getDamageSources().mobAttack(this);
                player.damage(source, STOMP_DAMAGE);
                // Launch upward!
                player.addVelocity(0.0, 0.85, 0.0);
                player.velocityModified = true;
            }
        }
    }

    public void executeRockThrow(LivingEntity target) {
        setAttackState(STATE_ROCK_THROW);
        this.actionStateTicks = 20;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY() + 2.5, this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_THROW, SoundCategory.HOSTILE, 1.5f, 0.8f);

            SeismicRockEntity rock = new SeismicRockEntity(this.getWorld(), this, 4.0f);
            rock.setPos(this.getX(), this.getY() + 3.0, this.getZ());

            Vec3d targetVec = target.getEyePos().subtract(rock.getPos());
            double dist = targetVec.horizontalLength();
            rock.setVelocity(targetVec.x, targetVec.y + dist * 0.15, targetVec.z, 0.85f, 3.0f);
            serverWorld.spawnEntity(rock);
        }
    }

    public void prepareBlockThrow(LivingEntity target) {
        // 1. Find nearby stone/dirt block
        BlockPos ripPos = findNearbyRipBlock();
        BlockState ripState = Blocks.COBBLESTONE.getDefaultState();

        if (ripPos != null) {
            ripState = this.getWorld().getBlockState(ripPos);
            if (ElementalConfig.get().seismicColossusRestoreTerrain) {
                this.pulledBlocks.put(ripPos, ripState);
            }
            this.getWorld().setBlockState(ripPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

            if (this.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.playSound(null, ripPos.getX(), ripPos.getY(), ripPos.getZ(),
                        SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.8f);
                serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ripState),
                        ripPos.getX() + 0.5, ripPos.getY() + 0.5, ripPos.getZ() + 0.5,
                        25, 0.3, 0.3, 0.3, 0.1);
            }
        }

        this.heldRipBlockState = ripState;
        setAttackState(STATE_BLOCK_THROW);
        this.actionStateTicks = 25; // 1.25s windup & launch
    }

    public void releaseBlockThrow(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld))
            return;

        BlockState stateToThrow = this.heldRipBlockState != null ? this.heldRipBlockState
                : Blocks.COBBLESTONE.getDefaultState();
        this.heldRipBlockState = null;

        float damage = 9.0f;
        if (getPhase() >= 3) {
            damage *= 1.10f; // 10% Phase 3 buff
        }

        SeismicThrownBlockEntity blockEntity = new SeismicThrownBlockEntity(this.getWorld(), this, stateToThrow,
                damage);
        Vec3d targetVec = target.getEyePos().subtract(blockEntity.getPos());
        double dist = targetVec.horizontalLength();
        blockEntity.setVelocity(targetVec.x, targetVec.y + dist * 0.18, targetVec.z, 1.25f, 2.0f);
        serverWorld.spawnEntity(blockEntity);

        serverWorld.playSound(null, this.getX(), this.getY() + 2.5, this.getZ(),
                ModSounds.SEISMIC_COLOSSUS_THROW, SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    public void executeFissure(LivingEntity target) {
        setAttackState(STATE_FISSURE);
        this.actionStateTicks = 30;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_SLAM, SoundCategory.HOSTILE, 3.0f, 0.5f);
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 30.0, 3.5f, 20);

            Vec3d start = this.getPos().add(0, 0.2, 0);
            Vec3d diff = target.getPos().subtract(start);
            Vec3d dir = new Vec3d(diff.x, 0, diff.z);
            if (dir.lengthSquared() < 0.001) {
                dir = new Vec3d(0, 0, 1);
            } else {
                dir = dir.normalize();
            }
            double distance = Math.min(22.0, this.distanceTo(target) + 2.0);

            for (double d = 1.5; d <= distance; d += 1.5) {
                Vec3d point = start.add(dir.multiply(d));
                BlockPos floor = new BlockPos((int) Math.floor(point.x), (int) Math.floor(point.y),
                        (int) Math.floor(point.z));
                while (floor.getY() > serverWorld.getBottomY() && serverWorld.getBlockState(floor).isAir()) {
                    floor = floor.down();
                }
                while (floor.getY() < serverWorld.getTopY() && !serverWorld.getBlockState(floor.up()).isAir()) {
                    floor = floor.up();
                }
                double fy = floor.getY() + 1.0;

                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                        point.x, fy, point.z, 20, 0.3, 0.5, 0.3, 0.15);
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        point.x, fy, point.z, 3, 0.2, 0.3, 0.2, 0.02);
                serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                        point.x, fy, point.z, 1, 0, 0, 0, 0);

                Box hitBox = new Box(point.x - 1.2, fy - 0.5, point.z - 1.2, point.x + 1.2, fy + 2.0, point.z + 1.2);
                List<PlayerEntity> hitPlayers = serverWorld.getEntitiesByClass(PlayerEntity.class, hitBox,
                        LivingEntity::isAlive);
                for (PlayerEntity player : hitPlayers) {
                    DamageSource source = this.getDamageSources().mobAttack(this);
                    player.damage(source, FISSURE_DAMAGE);
                    player.addVelocity(0.0, 0.65, 0.0);
                    player.velocityModified = true;
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                }
            }
        }
    }

    public void executeWarRoar() {
        setAttackState(STATE_WAR_ROAR);
        this.actionStateTicks = 35; // 1.75 seconds channel

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_ROAR, SoundCategory.HOSTILE, 3.5f, 0.6f);
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.7f);

            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 32.0, 5.0f, 30);

            // Expanding sonic dust ring
            for (int i = 0; i < 48; i++) {
                double rad = Math.toRadians(i * 7.5);
                double px = this.getX() + Math.cos(rad) * 6.0;
                double pz = this.getZ() + Math.sin(rad) * 6.0;
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                        px, this.getY() + 0.2, pz, 5, 0.2, 0.2, 0.2, 0.1);
                serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM,
                        this.getX() + Math.cos(rad) * 1.5, this.getY() + 2.0, this.getZ() + Math.sin(rad) * 1.5, 1, 0,
                        0, 0, 0);
            }

            Box box = this.getBoundingBox().expand(10.0, 3.0, 10.0);
            List<PlayerEntity> players = serverWorld.getEntitiesByClass(PlayerEntity.class, box, LivingEntity::isAlive);
            for (PlayerEntity player : players) {
                DamageSource source = this.getDamageSources().mobAttack(this);
                player.damage(source, WAR_ROAR_DAMAGE);
                Vec3d push = player.getPos().subtract(this.getPos()).normalize().multiply(1.6);
                player.addVelocity(push.x, 0.35, push.z);
                player.velocityModified = true;
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            }
        }
    }

    public void executeAvalanche(LivingEntity target) {
        setAttackState(STATE_AVALANCHE);
        this.actionStateTicks = 30;

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_SLAM, SoundCategory.HOSTILE, 2.5f, 0.5f);
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 26.0, 3.0f, 20);

            Vec3d targetPos = target.getPos();
            Random rng = new Random();
            int boulderCount = getPhase() >= 3 ? 6 : 4;

            for (int i = 0; i < boulderCount; i++) {
                double offsetX = (rng.nextDouble() - 0.5) * 6.0;
                double offsetZ = (rng.nextDouble() - 0.5) * 6.0;
                double spawnX = targetPos.x + offsetX;
                double spawnY = targetPos.y + 12.0 + rng.nextDouble() * 3.0;
                double spawnZ = targetPos.z + offsetZ;

                // Dust marker on ground
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        spawnX, targetPos.y + 0.1, spawnZ, 5, 0.3, 0.05, 0.3, 0.01);
                serverWorld.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()),
                        spawnX, targetPos.y + 0.1, spawnZ, 12, 0.3, 0.05, 0.3, 0.05);

                BlockState boulderState = rng.nextBoolean() ? Blocks.COBBLESTONE.getDefaultState()
                        : Blocks.STONE.getDefaultState();
                SeismicThrownBlockEntity boulder = new SeismicThrownBlockEntity(this.getWorld(), this, boulderState,
                        11.0f);
                boulder.setPos(spawnX, spawnY, spawnZ);
                boulder.setVelocity(0, -1.0 - rng.nextDouble() * 0.4, 0);
                serverWorld.spawnEntity(boulder);
            }
        }
    }

    public void startCharge(LivingEntity target) {
        setAttackState(STATE_CHARGE);
        this.chargeTicks = 25; // 1.25s sprint
        this.actionStateTicks = 35;
        this.getNavigation().stop();

        Vec3d diff = target.getPos().subtract(this.getPos());
        Vec3d dir = new Vec3d(diff.x, 0, diff.z);
        if (dir.lengthSquared() < 0.001) {
            dir = new Vec3d(0, 0, 1);
        } else {
            dir = dir.normalize();
        }
        this.chargeVelocity = dir.multiply(0.65); // high forward velocity

        if (this.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_ROAR, SoundCategory.HOSTILE, 2.5f, 0.8f);
            SeismicColossusNetworking.sendScreenShakeNearby(sw, this.getPos(), 24.0, 3.0f, 15);
        }
    }

    private void tickChargeSequence() {
        if (!(this.getWorld() instanceof ServerWorld sw))
            return;
        this.chargeTicks--;

        if (this.chargeTicks <= 0) {
            setAttackState(STATE_IDLE);
            this.executeGroundSlam(); // finish with slam!
            return;
        }

        // Apply charge velocity
        this.setVelocity(this.chargeVelocity.x, this.getVelocity().y, this.chargeVelocity.z);
        this.velocityModified = true;

        // Dust trail
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                this.getX(), this.getY() + 0.1, this.getZ(), 10, 1.0, 0.1, 1.0, 0.1);
        if (this.chargeTicks % 5 == 0) {
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_STEP, SoundCategory.HOSTILE, 1.8f, 0.4f);
        }

        // Check collision with players
        Box hitBox = this.getBoundingBox().expand(0.8, 0.2, 0.8);
        List<PlayerEntity> players = sw.getEntitiesByClass(PlayerEntity.class, hitBox, LivingEntity::isAlive);
        if (!players.isEmpty()) {
            for (PlayerEntity player : players) {
                DamageSource source = this.getDamageSources().mobAttack(this);
                player.damage(source, CHARGE_DAMAGE);
                Vec3d knock = this.chargeVelocity.normalize().multiply(1.5).add(0, 0.5, 0);
                player.addVelocity(knock.x, knock.y, knock.z);
                player.velocityModified = true;
            }
            // End charge on impact with slam!
            this.chargeTicks = 0;
            this.executeGroundSlam();
        }
    }

    private BlockPos findNearbyRipBlock() {
        BlockPos center = this.getBlockPos();
        World world = this.getWorld();
        int radius = 6;
        for (int y = -1; y <= 2; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (isValidRipBlock(state)
                            && (world.isAir(pos.up()) || world.getBlockState(pos.up()).isReplaceable())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidRipBlock(BlockState state) {
        if (state.isAir() || state.getHardness(this.getWorld(), BlockPos.ORIGIN) < 0)
            return false;
        Block block = state.getBlock();
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || state.isIn(BlockTags.DIRT)
                || block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.GRANITE
                || block == Blocks.DIORITE
                || block == Blocks.ANDESITE
                || block == Blocks.TUFF
                || block == Blocks.SANDSTONE
                || block == Blocks.TERRACOTTA;
    }

    public void restorePulledBlocks() {
        if (!ElementalConfig.get().seismicColossusRestoreTerrain)
            return;
        for (Map.Entry<BlockPos, BlockState> entry : this.pulledBlocks.entrySet()) {
            if (this.getWorld().isAir(entry.getKey())) {
                this.getWorld().setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
            }
        }
        this.pulledBlocks.clear();
    }

    // ==================== EARTHSHATTER BREAKOUT (OUT OF RIFT HOLES / PITS)
    // ====================
    private boolean isSolidObstructing(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && state.isSolidBlock(world, pos);
    }

    private boolean isTrappedInHole(ServerWorld world) {
        BlockPos feet = this.getBlockPos();
        // Check surrounding horizontal directions at distances 1, 2, and 3
        int blockedDirections = 0;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            boolean blockedInDir = false;
            for (int dist = 1; dist <= 3; dist++) {
                BlockPos wall1 = feet.offset(dir, dist).up(1);
                BlockPos wall2 = feet.offset(dir, dist).up(2);
                if (isSolidObstructing(world, wall1) || isSolidObstructing(world, wall2)) {
                    blockedInDir = true;
                    break;
                }
            }
            if (blockedInDir) {
                blockedDirections++;
            }
        }
        return blockedDirections >= 3;
    }

    private void checkIfTrappedAndTriggerBreakout() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld))
            return;

        BlockPos feet = this.getBlockPos();
        BlockPos body = feet.up();
        BlockPos head = feet.up(2);

        boolean suffocating = isSolidObstructing(serverWorld, body) || isSolidObstructing(serverWorld, head);
        boolean inDeepHole = isTrappedInHole(serverWorld);

        if (suffocating) {
            this.trappedTicks += 4;
        } else if (inDeepHole) {
            this.trappedTicks++;
        } else if (this.trappedTicks > 0) {
            this.trappedTicks--;
        }

        // When suffocating inside collapsed hole: 8 ticks (0.4s) triggers blast
        // breakout!
        // When in a deep open pit/hole: 70 ticks (3.5s) triggers blast breakout!
        int threshold = suffocating ? 8 : 70;
        if (this.trappedTicks >= threshold) {
            this.trappedTicks = 0;
            triggerBreakout();
        }
    }

    public void triggerBreakout() {
        if (getAttackState() == STATE_BREAKOUT)
            return;
        setAttackState(STATE_BREAKOUT);
        this.breakoutTimer = 0;
        this.isBreakoutAirborne = false;
        this.getNavigation().stop();

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_ROAR, SoundCategory.HOSTILE, 3.0f, 0.6f);
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 30.0, 4.0f, 25);
        }
    }

    private void tickBreakoutSequence() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld))
            return;

        this.breakoutTimer++;

        // Phase 1: Tremor windup & gather kinetic power (Ticks 1 - 15)
        if (this.breakoutTimer < 16) {
            this.getNavigation().stop();
            serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(),
                    8, 1.5, 0.2, 1.5, 0.05);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 0.2, this.getZ(), 15, 1.5, 0.2, 1.5, 0.1);
            if (this.breakoutTimer % 4 == 0) {
                serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            return;
        }

        // Phase 2: Pulverize all blocks in a 5x5 column and blast upward! (Tick 16)
        if (this.breakoutTimer == 16) {
            BlockPos center = this.getBlockPos();
            int radius = 2; // 5x5 blast area (matches the blade hole)
            int minY = Math.max(serverWorld.getBottomY() + 1, center.getY() - 1);
            int maxY = Math.min(serverWorld.getTopY(), center.getY() + 8);

            for (int y = minY; y <= maxY; y++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos targetPos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        BlockState state = serverWorld.getBlockState(targetPos);
                        if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && !state.isOf(Blocks.BARRIER)
                                && state.getHardness(serverWorld, targetPos) >= 0) {
                            serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 6, 0.3, 0.3,
                                    0.3, 0.15);
                            serverWorld.breakBlock(targetPos, false);
                            if (!serverWorld.getBlockState(targetPos).isAir()) {
                                serverWorld.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            }
                        }
                    }
                }
            }

            // Explosive blast sounds, emitter particles, and heavy screen shake
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.5f, 0.7f);
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.HOSTILE, 3.0f, 0.5f);
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0, this.getZ(), 2,
                    0.5, 0.5, 0.5, 0.0);
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 36.0, 5.0f, 30);

            // Explosive leap upwards out of the hole!
            this.setVelocity(0, 1.45, 0);
            this.velocityModified = true;
            this.isBreakoutAirborne = true;

            serverWorld.getServer().getPlayerManager().broadcast(
                    Text.literal(
                            "§6§l[Seismic Colossus] §cThe stone titan shatters the earth and erupts from the depths!"),
                    false);
            return;
        }

        // Phase 3: Airborne to Seismic Impact Landing
        if (this.isBreakoutAirborne) {
            if (this.breakoutTimer > 25 && this.isOnGround()) {
                this.isBreakoutAirborne = false;
                this.setAttackState(STATE_IDLE);
                this.breakoutTimer = 0;
                this.attackCooldownTicks = 35;

                // Seismic impact shockwave
                serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.SEISMIC_COLOSSUS_SLAM, SoundCategory.HOSTILE, 3.0f, 0.5f);
                serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5f, 0.8f);

                SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 32.0, 5.0f, 25);

                for (int i = 0; i < 40; i++) {
                    double rad = Math.toRadians(i * 9);
                    double px = this.getX() + Math.cos(rad) * 4.5;
                    double pz = this.getZ() + Math.sin(rad) * 4.5;
                    serverWorld.spawnParticles(
                            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                            px, this.getY() + 0.1, pz, 6, 0.3, 0.1, 0.3, 0.08);
                    serverWorld.spawnParticles(ParticleTypes.EXPLOSION, px, this.getY() + 0.2, pz, 1, 0, 0, 0, 0);
                }

                Box box = this.getBoundingBox().expand(7.0, 2.5, 7.0);
                List<PlayerEntity> players = serverWorld.getEntitiesByClass(PlayerEntity.class, box,
                        LivingEntity::isAlive);
                for (PlayerEntity player : players) {
                    DamageSource source = this.getDamageSources().mobAttack(this);
                    player.damage(source, BREAKOUT_DAMAGE);
                    player.addVelocity(0, 0.75, 0);
                    player.velocityModified = true;
                }
            }
        }

        if (this.breakoutTimer >= 70) {
            this.setAttackState(STATE_IDLE);
            this.isBreakoutAirborne = false;
            this.breakoutTimer = 0;
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);

        // Restore terrain blocks
        this.restorePulledBlocks();

        // Drop all stuck weapons safely
        for (StuckWeapon weapon : this.stuckWeapons) {
            if (!weapon.stack.isEmpty()) {
                this.dropStack(weapon.stack);
            }
        }
        this.stuckWeapons.clear();
        this.syncStuckWeapons();

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SEISMIC_COLOSSUS_DEATH, SoundCategory.HOSTILE, 3.0f, 0.5f);
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 2.0, this.getZ(), 1,
                    0, 0, 0, 0);
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    this.getX(), this.getY() + 2.0, this.getZ(), 120, 2.0, 2.0, 2.0, 0.2);
            SeismicColossusNetworking.sendScreenShakeNearby(serverWorld, this.getPos(), 36.0, 5.5f, 40);
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    // ==================== GECKOLIB CONTROLLERS ====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 3, state -> {
            if (this.isDead()) {
                return state.setAndContinue(DEATH_ANIM);
            }
            int attack = getAttackState();
            if (attack == STATE_BREAKOUT) {
                return state.setAndContinue(BREAKOUT_ANIM);
            }
            if (attack == STATE_PHASE_TRANSITION) {
                return state.setAndContinue(RAGE_ANIM);
            }
            if (attack == STATE_WAR_ROAR) {
                return state.setAndContinue(WAR_ROAR_ANIM);
            }
            if (attack == STATE_FISSURE) {
                return state.setAndContinue(FISSURE_ANIM);
            }
            if (attack == STATE_AVALANCHE) {
                return state.setAndContinue(AVALANCHE_ANIM);
            }
            if (attack == STATE_CHARGE) {
                return state.setAndContinue(CHARGE_ANIM);
            }
            if (attack == STATE_SLAM) {
                return state.setAndContinue(SLAM_ANIM);
            }
            if (attack == STATE_STOMP) {
                return state.setAndContinue(STOMP_ANIM);
            }
            if (attack == STATE_ROCK_THROW || attack == STATE_BLOCK_THROW) {
                return state.setAndContinue(THROW_ANIM);
            }
            if (attack == STATE_PUNCH) {
                return state.setAndContinue(PUNCH_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SEISMIC_COLOSSUS_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SEISMIC_COLOSSUS_DEATH;
    }

    // ==================== AI GOAL CLASSES ====================
    public static class ColossusPhaseTransitionGoal extends Goal {
        private final SeismicColossusEntity boss;

        public ColossusPhaseTransitionGoal(SeismicColossusEntity boss) {
            this.boss = boss;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            return this.boss.getAttackState() == STATE_PHASE_TRANSITION;
        }

        @Override
        public void start() {
            this.boss.getNavigation().stop();
        }

        @Override
        public boolean shouldContinue() {
            return this.boss.getAttackState() == STATE_PHASE_TRANSITION;
        }

        @Override
        public void stop() {
            if (this.boss.getAttackState() == STATE_PHASE_TRANSITION) {
                this.boss.completePhaseTransition();
            }
        }
    }

    public static class ColossusCombatGoal extends Goal {
        private final SeismicColossusEntity boss;
        private int rockThrowCooldown = 140;
        private int blockThrowCooldown = 60;
        private int blockThrowWarmup = -1;
        private int fissureCooldown = 80;
        private int warRoarCooldown = 120;
        private int avalancheCooldown = 140;
        private int chargeCooldown = 100;

        public ColossusCombatGoal(SeismicColossusEntity boss) {
            this.boss = boss;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity target = this.boss.getTarget();
            return target != null && target.isAlive()
                    && this.boss.getAttackState() != STATE_PHASE_TRANSITION
                    && this.boss.getAttackState() != STATE_BREAKOUT
                    && this.boss.getAttackState() != STATE_CHARGE;
        }

        @Override
        public void tick() {
            LivingEntity target = this.boss.getTarget();
            if (target == null)
                return;

            this.boss.getLookControl().lookAt(target, 30.0f, 30.0f);
            double distSq = this.boss.squaredDistanceTo(target);
            int phase = this.boss.getPhase();

            // Tick internal ability cooldowns
            if (this.fissureCooldown > 0)
                this.fissureCooldown--;
            if (this.warRoarCooldown > 0)
                this.warRoarCooldown--;
            if (this.avalancheCooldown > 0)
                this.avalancheCooldown--;
            if (this.chargeCooldown > 0)
                this.chargeCooldown--;

            // 1. Check active block throw windup
            if (this.blockThrowWarmup > 0) {
                this.blockThrowWarmup--;
                this.boss.getNavigation().stop();
                if (this.blockThrowWarmup == 0) {
                    this.boss.releaseBlockThrow(target);
                    this.blockThrowWarmup = -1;
                }
                return;
            }

            // Movement pursuit
            double speed = 1.0;
            if (distSq > 16.0) {
                this.boss.getNavigation().startMovingTo(target, speed);
            }

            if (this.boss.attackCooldownTicks > 0) {
                return;
            }

            int cooldownBase = ElementalConfig.get().seismicColossusAttackCooldown;
            if (phase == 3) {
                cooldownBase = (int) (cooldownBase * 0.90); // 10% faster attack rate
            }
            Random rng = new Random();

            // 1. Close-range Melee Attacks (Within 4.5 blocks)
            if (distSq <= 20.25) {
                // Occasional War Roar pushback if ready
                if (this.warRoarCooldown <= 0 && rng.nextInt(4) == 0) {
                    this.boss.executeWarRoar();
                    this.warRoarCooldown = phase == 3 ? 140 : 180;
                    this.boss.attackCooldownTicks = cooldownBase;
                    return;
                }

                int roll = rng.nextInt(3);
                if (roll == 0) {
                    this.boss.executeStonePunch(target);
                } else if (roll == 1) {
                    this.boss.executeGroundSlam();
                } else {
                    this.boss.executeStomp();
                }
                this.boss.attackCooldownTicks = cooldownBase;
                return;
            }

            // 2. Medium Range Attacks (4.5 to 14 blocks)
            if (distSq > 20.25 && distSq <= 196.0) {
                // Bull Rush Charge
                if (this.chargeCooldown <= 0 && rng.nextInt(3) == 0) {
                    this.boss.startCharge(target);
                    this.chargeCooldown = phase == 3 ? 110 : 150;
                    this.boss.attackCooldownTicks = cooldownBase;
                    return;
                }

                // Fissure Eruption
                if (this.fissureCooldown <= 0) {
                    this.boss.executeFissure(target);
                    this.fissureCooldown = phase == 3 ? 85 : 120;
                    this.boss.attackCooldownTicks = cooldownBase;
                    return;
                }
            }

            // 3. Long Range Attacks (Beyond 14 blocks)
            if (distSq > 196.0) {
                // Phase 2 & 3: Boulder Avalanche or Block Throw
                if (phase >= 2) {
                    if (this.avalancheCooldown <= 0 && rng.nextBoolean()) {
                        this.boss.executeAvalanche(target);
                        this.avalancheCooldown = phase == 3 ? 140 : 180;
                        this.boss.attackCooldownTicks = cooldownBase;
                        return;
                    }
                    if (this.blockThrowCooldown <= 0 && distSq <= ElementalConfig.get().seismicColossusBlockThrowRange
                            * ElementalConfig.get().seismicColossusBlockThrowRange) {
                        this.boss.prepareBlockThrow(target);
                        this.blockThrowWarmup = 15;
                        int cd = phase == 3 ? 65 : 75;
                        this.blockThrowCooldown = cd;
                        this.boss.attackCooldownTicks = cooldownBase;
                        return;
                    }
                }

                // Phase 1 long range: Fissure Eruption or Rock Throw
                if (phase == 1) {
                    if (this.fissureCooldown <= 0 && distSq <= 400.0) {
                        this.boss.executeFissure(target);
                        this.fissureCooldown = 130;
                        this.boss.attackCooldownTicks = cooldownBase;
                        return;
                    }
                    if (this.rockThrowCooldown <= 0 && distSq <= 400.0) {
                        this.boss.executeRockThrow(target);
                        this.rockThrowCooldown = 200;
                        this.boss.attackCooldownTicks = cooldownBase;
                        return;
                    }
                }
            }

            // Decrement remaining timers when not used
            if (this.blockThrowCooldown > 0)
                this.blockThrowCooldown--;
            if (this.rockThrowCooldown > 0)
                this.rockThrowCooldown--;
        }
    }

    // ==================== NBT SAVING & LOADING ====================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Phase", getPhase());
        nbt.putBoolean("Invulnerable", isInvulnerablePhase());

        NbtList weaponsList = new NbtList();
        for (StuckWeapon sw : this.stuckWeapons) {
            weaponsList.add(sw.toNbt());
        }
        nbt.put("StuckWeapons", weaponsList);
        nbt.putBoolean("TridentStuck", isTridentStuck());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Phase")) {
            setPhase(nbt.getInt("Phase"));
        }
        if (nbt.contains("Invulnerable")) {
            setInvulnerablePhase(nbt.getBoolean("Invulnerable") && getAttackState() == STATE_PHASE_TRANSITION);
        }
        if (nbt.contains("StuckWeapons")) {
            this.stuckWeapons.clear();
            NbtList list = nbt.getList("StuckWeapons", 10);
            for (int i = 0; i < list.size(); i++) {
                this.stuckWeapons.add(StuckWeapon.fromNbt(list.getCompound(i)));
            }
            this.syncStuckWeapons();
        }
        if (nbt.contains("TridentStuck")) {
            setTridentStuck(nbt.getBoolean("TridentStuck"));
        }
    }
}
