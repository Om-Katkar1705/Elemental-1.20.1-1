package com.elemental.world;

import com.elemental.Elemental;
import com.elemental.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.*;

/**
 * Manages Jungle Dungeons, including Collapsed Shrine active mechanics:
 * - Dynamic ambient audio ticking (creepy echoes, rustling flora, ancient hums)
 * - Decoy / Fake trap triggering
 * - 1-HP Botanical Spore trap triggering
 * - Rootbound Axe interaction with sealed root barriers
 * - Locating and generating shrines in Jungle biomes
 */
public class JungleDungeonManager {

    public record ShrineInstance(
            BlockPos center,
            Box bounds,
            Set<BlockPos> fakeTraps,
            Set<BlockPos> oneHpTraps,
            Set<BlockPos> rootBarriers,
            BlockPos sealedChestPos,
            boolean[] clearedSeal
    ) {
    }

    private static final List<ShrineInstance> ACTIVE_SHRINES = new ArrayList<>();
    private static final Map<UUID, Long> LAST_AMBIENT_TICK = new HashMap<>();
    private static final Map<UUID, Long> TRAP_COOLDOWNS = new HashMap<>();
    private static int serverTickCounter = 0;

    /**
     * Registers a newly generated shrine into the manager.
     */
    public static void registerShrine(BlockPos center, Box bounds,
                                      List<BlockPos> fakeTraps, List<BlockPos> oneHpTraps,
                                      List<BlockPos> rootBarriers, BlockPos sealedChestPos) {
        ShrineInstance instance = new ShrineInstance(
                center,
                bounds,
                new HashSet<>(fakeTraps),
                new HashSet<>(oneHpTraps),
                new HashSet<>(rootBarriers),
                sealedChestPos,
                new boolean[]{false}
        );
        ACTIVE_SHRINES.add(instance);
        Elemental.LOGGER.info("Registered active Collapsed Shrine at {} with {} fake traps, {} 1-HP traps, and {} root barriers",
                center, fakeTraps.size(), oneHpTraps.size(), rootBarriers.size());
    }

    /**
     * Registers server tick listeners for shrine ambient sounds and trap triggering.
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTickCounter++;
            if (serverTickCounter % 10 != 0) return; // check every half second

            long currentTime = System.currentTimeMillis();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                ServerWorld world = player.getServerWorld();
                BlockPos playerPos = player.getBlockPos();

                for (ShrineInstance shrine : ACTIVE_SHRINES) {
                    if (shrine.bounds().contains(player.getX(), player.getY(), player.getZ())) {
                        // 1. Play weird ambient dungeon noises periodically (every 12-25 seconds)
                        long lastAmbient = LAST_AMBIENT_TICK.getOrDefault(player.getUuid(), 0L);
                        if (currentTime - lastAmbient > 15000L) {
                            playWeirdShrineAmbience(world, player, shrine);
                            LAST_AMBIENT_TICK.put(player.getUuid(), currentTime + (world.random.nextInt(10000) - 5000));
                        }

                        // 2. Check Traps Triggering
                        checkTrapTriggers(world, player, playerPos, shrine, currentTime);
                        break;
                    }
                }
            }
        });
    }

    /**
     * Plays weird, eerie anime-dungeon ambient sounds around the player.
     */
    private static void playWeirdShrineAmbience(ServerWorld world, ServerPlayerEntity player, ShrineInstance shrine) {
        Random rand = new Random();
        float soundChoice = rand.nextFloat();

        double ox = player.getX() + (rand.nextDouble() - 0.5) * 16.0;
        double oy = player.getY() + (rand.nextDouble() - 0.5) * 6.0;
        double oz = player.getZ() + (rand.nextDouble() - 0.5) * 16.0;

        if (soundChoice < 0.40f) {
            // Eerie deep subterranean hum / sculk rumble
            world.playSound(null, ox, oy, oz, ModSounds.SHRINE_AMBIENT, SoundCategory.AMBIENT, 1.2f, 0.7f + rand.nextFloat() * 0.3f);
        } else if (soundChoice < 0.75f) {
            // Unsettling rustling flora / distant sniffing
            world.playSound(null, ox, oy, oz, ModSounds.SHRINE_RUSTLE, SoundCategory.AMBIENT, 1.0f, 0.8f + rand.nextFloat() * 0.4f);
        } else {
            // Distant cave groan / metallic wind draft
            world.playSound(null, ox, oy, oz, SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 0.9f, 0.5f);
        }
    }

    /**
     * Checks if player stepped onto a fake trap or a brutal 1-HP spore trap.
     */
    private static void checkTrapTriggers(ServerWorld world, ServerPlayerEntity player, BlockPos playerPos,
                                          ShrineInstance shrine, long currentTime) {
        long lastTrap = TRAP_COOLDOWNS.getOrDefault(player.getUuid(), 0L);
        if (currentTime - lastTrap < 3000L) return; // 3 sec cooldown between traps

        // Check 1-HP Spore Trap
        for (BlockPos oneHpPos : shrine.oneHpTraps()) {
            if (playerPos.isWithinDistance(oneHpPos, 1.8)) {
                triggerOneHpSporeTrap(world, player, oneHpPos);
                TRAP_COOLDOWNS.put(player.getUuid(), currentTime);
                return;
            }
        }

        // Check Fake / Decoy Trap
        for (BlockPos fakePos : shrine.fakeTraps()) {
            if (playerPos.isWithinDistance(fakePos, 1.5)) {
                triggerFakeTrap(world, player, fakePos);
                TRAP_COOLDOWNS.put(player.getUuid(), currentTime);
                return;
            }
        }
    }

    /**
     * Triggers the Fake / Decoy Trap: Loud mechanical click, smoke puff, hiss — but 0 damage!
     */
    private static void triggerFakeTrap(ServerWorld world, ServerPlayerEntity player, BlockPos trapPos) {
        world.playSound(null, trapPos.getX(), trapPos.getY(), trapPos.getZ(),
                ModSounds.TRAP_DECOY_CLICK, SoundCategory.BLOCKS, 1.5f, 1.0f);
        world.playSound(null, trapPos.getX(), trapPos.getY(), trapPos.getZ(),
                SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.BLOCKS, 1.8f, 0.8f);

        // Visual smoke puff
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                trapPos.getX() + 0.5, trapPos.getY() + 0.5, trapPos.getZ() + 0.5,
                15, 0.3, 0.3, 0.3, 0.05);

        player.sendMessage(Text.literal("§8[!] *Click*... An ominous mechanism fires, but only ancient dust rushes out!").formatted(Formatting.DARK_GRAY), true);
    }

    /**
     * Triggers the Brutal 1-HP Spore Trap:
     * Explodes with poisonous spores, reduces health to 1.0 HP (half a heart), and gives Nausea & Slowness.
     */
    private static void triggerOneHpSporeTrap(ServerWorld world, ServerPlayerEntity player, BlockPos trapPos) {
        world.playSound(null, trapPos.getX(), trapPos.getY(), trapPos.getZ(),
                ModSounds.TRAP_SPORE_BURST, SoundCategory.BLOCKS, 1.6f, 0.8f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 1.2f, 0.6f);

        // Toxic botanical spore explosion
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.6, 0.8, 0.6, 0.1);
        world.spawnParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 0.5, player.getZ(),
                25, 0.5, 0.5, 0.5, 0.05);

        // Reduce player to 1 HP (half a heart) if currently above 1 HP
        if (player.getHealth() > 1.0f) {
            player.setHealth(1.0f);
        }

        // Apply temporary panic effects: Nausea (5s), Slowness II (4s), Blindness (2s)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, true));

        player.sendMessage(Text.literal("§2☣ ANCIENT SPORE BURST! §cThe suffocating jungle toxin strips your vitality to 1 HP!").formatted(Formatting.RED), true);
    }

    /**
     * Attempts to clear the Rootbound Vault barrier when player right-clicks with the Rootbound Axe.
     * Returns true if a barrier was severed.
     */
    public static boolean tryClearRootBarrier(ServerWorld world, PlayerEntity player, BlockPos targetPos, ItemStack axeStack) {
        for (ShrineInstance shrine : ACTIVE_SHRINES) {
            if (shrine.rootBarriers().contains(targetPos) ||
                    (shrine.bounds().contains(targetPos.getX(), targetPos.getY(), targetPos.getZ())
                            && isAncientRootBlock(world.getBlockState(targetPos).getBlock()))) {

                // Clear all barrier blocks for this vault
                for (BlockPos bPos : shrine.rootBarriers()) {
                    world.setBlockState(bPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            bPos.getX() + 0.5, bPos.getY() + 0.5, bPos.getZ() + 0.5,
                            8, 0.3, 0.3, 0.3, 0.05);
                }

                // Sound & particle feedback
                world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                        ModSounds.SHRINE_ROOT_BREAK, SoundCategory.PLAYERS, 1.5f, 0.8f);
                world.playSound(null, targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                        SoundEvents.BLOCK_VINE_BREAK, SoundCategory.PLAYERS, 1.2f, 1.0f);

                world.spawnParticles(ParticleTypes.COMPOSTER,
                        targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5,
                        30, 0.8, 0.8, 0.8, 0.1);

                player.sendMessage(Text.literal("§a✦ The Rootbound Axe shatters the ancient root seal! The Offering Vault is unlocked!").formatted(Formatting.GREEN), true);

                shrine.clearedSeal()[0] = true;
                return true;
            }
        }
        return false;
    }

    private static boolean isAncientRootBlock(Block block) {
        return block == Blocks.MANGROVE_ROOTS || block == Blocks.MUDDY_MANGROVE_ROOTS || block == Blocks.WEEPING_VINES;
    }

    public static List<ShrineInstance> getActiveShrines() {
        return Collections.unmodifiableList(ACTIVE_SHRINES);
    }

    /**
     * Locates the nearest Jungle biome or safe spot and generates the Collapsed Shrine.
     */
    public static BlockPos findOrGenerateCollapsedShrine(ServerWorld world, BlockPos originPos) {
        com.elemental.quest.QuestSaveData saveData = com.elemental.quest.QuestSaveData.getServerState(world.getServer());
        BlockPos existing = saveData.getCollapsedShrineLocation();
        if (existing != null) {
            return existing;
        }

        // Search for nearest jungle biome
        BlockPos junglePos = findNearestJungleBiome(world, originPos);
        if (junglePos == null) {
            junglePos = originPos; // Fallback to current position if no jungle nearby
        }

        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, junglePos.getX(), junglePos.getZ());
        BlockPos surfacePos = new BlockPos(junglePos.getX(), surfaceY, junglePos.getZ());

        BlockPos generatedPos = com.elemental.world.shrine.CollaspedShrineStructureGenerator.generate(world, surfacePos);
        saveData.setCollapsedShrineLocation(generatedPos);
        saveData.markDirty();
        return generatedPos;
    }

    /**
     * Scans outward to locate a Jungle biome.
     */
    public static BlockPos findNearestJungleBiome(ServerWorld world, BlockPos startPos) {
        int maxRadius = 1500;
        int step = 64;

        for (int r = 64; r <= maxRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos testPos = startPos.add(dx, 0, dz);
                    var biomeEntry = world.getBiome(testPos);
                    if (biomeEntry.isIn(BiomeTags.IS_JUNGLE)) {
                        return testPos;
                    }
                }
            }
        }
        return null;
    }
}
