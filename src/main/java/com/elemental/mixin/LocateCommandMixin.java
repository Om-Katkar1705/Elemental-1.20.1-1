package com.elemental.mixin;

import com.elemental.Elemental;
import com.elemental.quest.QuestSaveData;
import com.elemental.world.JungleDungeonManager;
import com.elemental.world.NbtStructureManager;
import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {

    @Inject(method = "executeLocateStructure", at = @At("HEAD"), cancellable = true)
    private static void onExecuteLocateStructure(
            ServerCommandSource source,
            RegistryPredicateArgumentType.RegistryPredicate<Structure> predicate,
            CallbackInfoReturnable<Integer> cir) {

        Either<RegistryKey<Structure>, TagKey<Structure>> keyOrTag = predicate.getKey();
        String matchedPath = null;
        boolean isAllStructuresTag = false;

        if (keyOrTag.left().isPresent()) {
            RegistryKey<Structure> key = keyOrTag.left().get();
            Identifier id = key.getValue();
            String namespace = id.getNamespace();
            String path = id.getPath();

            if (isElementalStructurePath(path) && (namespace.equals(Elemental.MOD_ID) || namespace.equals("minecraft"))) {
                matchedPath = path;
            }
        } else if (keyOrTag.right().isPresent()) {
            TagKey<Structure> tag = keyOrTag.right().get();
            Identifier tagId = tag.id();
            if (tagId.getPath().equals("structures") && (tagId.getNamespace().equals(Elemental.MOD_ID) || tagId.getNamespace().equals("minecraft"))) {
                isAllStructuresTag = true;
            }
        }

        if (matchedPath == null && !isAllStructuresTag) {
            return;
        }

        ServerWorld world = source.getWorld();
        BlockPos originPos = BlockPos.ofFloored(source.getPosition());
        QuestSaveData saveData = QuestSaveData.getServerState(world.getServer());
        Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);

        BlockPos targetPos;
        String resolvedPath;

        if (isAllStructuresTag) {
            Map<String, BlockPos> candidates = new LinkedHashMap<>();
            candidates.put("aerons_camp", getOrLocateCamp(world, source, saveData, originPos));
            candidates.put("crashed_skyforge_ship", getOrLocateAirship(world, saveData, originPos));
            candidates.put("everliving_knight_arena", getOrLocateEverlivingArena(world, saveData, originPos));
            candidates.put("collapsed_shrine", getOrLocateCollapsedShrine(world, saveData, originPos));
            candidates.put("lush_stronghold_temple", getOrLocateLushStrongholdTemple(world, saveData, originPos));
            candidates.put("seismic_golem_arena", getOrLocateSeismicGolemArena(world, saveData, originPos));

            String closestKey = "aerons_camp";
            double closestDistSq = Double.MAX_VALUE;

            for (Map.Entry<String, BlockPos> entry : candidates.entrySet()) {
                BlockPos pos = entry.getValue();
                if (pos != null) {
                    double distSq = originPos.getSquaredDistance(pos);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closestKey = entry.getKey();
                    }
                }
            }

            resolvedPath = closestKey;
            targetPos = candidates.get(closestKey);
        } else {
            resolvedPath = matchedPath;
            targetPos = switch (matchedPath) {
                case "aerons_camp" -> getOrLocateCamp(world, source, saveData, originPos);
                case "crashed_skyforge_ship" -> getOrLocateAirship(world, saveData, originPos);
                case "everliving_knight_arena" -> getOrLocateEverlivingArena(world, saveData, originPos);
                case "collapsed_shrine" -> getOrLocateCollapsedShrine(world, saveData, originPos);
                case "lush_stronghold_temple" -> getOrLocateLushStrongholdTemple(world, saveData, originPos);
                case "seismic_golem_arena" -> getOrLocateSeismicGolemArena(world, saveData, originPos);
                default -> null;
            };
        }

        if (targetPos == null) {
            return;
        }

        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        RegistryKey<Structure> targetKey = RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(Elemental.MOD_ID, resolvedPath));
        Optional<RegistryEntry.Reference<Structure>> entryOpt = registry.getEntry(targetKey);

        RegistryEntry<Structure> entry = entryOpt.isPresent()
                ? entryOpt.get()
                : registry.getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", resolvedPath))).orElse(null);

        stopwatch.stop();

        int result = LocateCommand.sendCoordinates(
                source,
                predicate,
                originPos,
                Pair.of(targetPos, entry),
                "commands.locate.structure.success",
                false,
                stopwatch.elapsed()
        );

        cir.setReturnValue(result);
    }

    private static boolean isElementalStructurePath(String path) {
        return path.equals("aerons_camp")
                || path.equals("crashed_skyforge_ship")
                || path.equals("everliving_knight_arena")
                || path.equals("collapsed_shrine")
                || path.equals("lush_stronghold_temple")
                || path.equals("seismic_golem_arena");
    }

    private static BlockPos getOrLocateCamp(ServerWorld world, ServerCommandSource source, QuestSaveData saveData, BlockPos originPos) {
        BlockPos camp = saveData.getAeronsTentLocation();
        if (camp != null) {
            return camp;
        }
        if (source.getPlayer() != null) {
            NbtStructureManager.initializeWorld(source.getPlayer());
            camp = saveData.getAeronsTentLocation();
            if (camp != null) {
                return camp;
            }
        }
        BlockPos safe = NbtStructureManager.findSafeSurface(world, world.getSpawnPos());
        saveData.setAeronsTentLocation(safe);
        saveData.setTentGenerated(true);
        saveData.markDirty();
        NbtStructureManager.placeNbtStructure(world, NbtStructureManager.AERONS_CAMP_ID, safe.add(-4, -1, -4));
        return safe;
    }

    private static BlockPos getOrLocateAirship(ServerWorld world, QuestSaveData saveData, BlockPos originPos) {
        BlockPos existing = saveData.getSkyforgeAirshipLocation();
        if (existing != null) {
            return existing;
        }
        return NbtStructureManager.findOrGenerateAirship(world, originPos);
    }

    private static BlockPos getOrLocateEverlivingArena(ServerWorld world, QuestSaveData saveData, BlockPos originPos) {
        BlockPos existing = saveData.getEverlivingArenaLocation();
        if (existing != null) {
            return existing;
        }
        return NbtStructureManager.findOrGenerateEverlivingArena(world, originPos);
    }

    private static BlockPos getOrLocateCollapsedShrine(ServerWorld world, QuestSaveData saveData, BlockPos originPos) {
        BlockPos existing = saveData.getCollapsedShrineLocation();
        if (existing != null) {
            return existing;
        }
        return JungleDungeonManager.findOrGenerateCollapsedShrine(world, originPos);
    }

    private static BlockPos getOrLocateSeismicGolemArena(ServerWorld world, QuestSaveData saveData, BlockPos originPos) {
        BlockPos existing = saveData.getSeismicGolemArenaLocation();
        if (existing != null) {
            return existing;
        }
        return NbtStructureManager.findOrGenerateSeismicGolemArena(world, originPos);
    }

    private static BlockPos getOrLocateLushStrongholdTemple(ServerWorld world, QuestSaveData saveData, BlockPos originPos) {
        BlockPos existing = saveData.getLushStrongholdTempleLocation();
        if (existing != null) {
            return existing;
        }
        return NbtStructureManager.findOrGenerateLushStrongholdTemple(world, originPos);
    }
}
