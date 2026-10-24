package com.elemental.command;

import com.elemental.entity.ChronicleGuideEntity;
import com.elemental.entity.ModEntities;
import com.elemental.quest.PlayerQuestData;
import com.elemental.quest.QuestManager;
import com.elemental.quest.QuestSaveData;
import com.elemental.quest.QuestState;
import com.elemental.world.NbtStructureManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class ElementalQuestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Admin quest management commands
        dispatcher.register(CommandManager.literal("elementalquest")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                    PlayerQuestData data = QuestManager.getPlayerData(player);
                                    data.setState(QuestState.CRAFT_STONE_TOOLS);
                                    data.setPistonsSalvaged(0);
                                    data.setAerogelExtracted(0);
                                    data.setBlueprintsFound(0);
                                    data.setCompletedPhase1Dialogue(false);
                                    data.setClaimedJetpack(false);
                                    data.setTargetWaypoint(null);
                                    com.elemental.quest.DialogueManager.cancelConversation(player);
                                    QuestSaveData saveData = QuestSaveData.getServerState(player.server);
                                    saveData.setGuideSpawned(false);
                                    saveData.setTentGenerated(false);
                                    saveData.setEverlivingArenaLocation(null);
                                    saveData.markDirty();
                                    QuestManager.syncToAll(player.server);

                                    ctx.getSource().sendFeedback(() -> Text.literal("§aReset quest state, dialogue phase, Aeron camp, and arena for " + player.getName().getString()), true);
                                    return 1;
                                })))
                .then(CommandManager.literal("advance")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                    PlayerQuestData data = QuestManager.getPlayerData(player);
                                    int nextId = Math.min(data.getState().getId() + 1, QuestState.QUEST_COMPLETE.getId());
                                    QuestManager.setQuestState(player, QuestState.fromId(nextId));

                                    ctx.getSource().sendFeedback(() -> Text.literal("§aAdvanced quest state to " + data.getState().name() + " for " + player.getName().getString()), true);
                                    return 1;
                                })))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("state", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (QuestState s : QuestState.values()) {
                                                builder.suggest(s.name());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                            String stateName = StringArgumentType.getString(ctx, "state");
                                            try {
                                                QuestState s = QuestState.valueOf(stateName.toUpperCase());
                                                QuestManager.setQuestState(player, s);
                                                ctx.getSource().sendFeedback(() -> Text.literal("§aSet quest state to " + s.name() + " for " + player.getName().getString()), true);
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendError(Text.literal("§cInvalid quest state: " + stateName));
                                                return 0;
                                            }
                                        }))))
                .then(CommandManager.literal("waypoint")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            PlayerQuestData data = QuestManager.getPlayerData(player);
                            BlockPos target = data.getTargetWaypoint();
                            if (target != null) {
                                double dist = Math.sqrt(player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()));
                                ctx.getSource().sendFeedback(() -> Text.literal("§eWaypoint: " + target.toShortString() + " §7(Distance: " + (int) dist + "m)"), false);
                            } else {
                                ctx.getSource().sendFeedback(() -> Text.literal("§7No active waypoint for current state: " + data.getState().name()), false);
                            }
                            return 1;
                        }))
                .then(CommandManager.literal("camp")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            NbtStructureManager.initializeWorld(player);
                            QuestSaveData saveData = QuestSaveData.getServerState(player.server);
                            BlockPos camp = saveData.getAeronsTentLocation();
                            ctx.getSource().sendFeedback(() -> Text.literal("§aAeron's Tent Camp located at: " + (camp != null ? camp.toShortString() : "unknown")), true);
                            return 1;
                        }))
                .then(CommandManager.literal("spawn_guide")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            BlockPos safePos = NbtStructureManager.findSafeSurface(player.getServerWorld(), player.getBlockPos().add(2, 0, 1));
                            ChronicleGuideEntity guide = ModEntities.CHRONICLE_GUIDE.create(player.getServerWorld());
                            if (guide != null) {
                                guide.refreshPositionAndAngles(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, player.getYaw() + 180.0f, 0.0f);
                                guide.setHomePos(safePos);
                                guide.setPersistent();
                                player.getServerWorld().spawnEntity(guide);
                                ctx.getSource().sendFeedback(() -> Text.literal("§aSpawned Aeron, the Last Skywright at safe position!"), true);
                                return 1;
                            }
                            ctx.getSource().sendError(Text.literal("§cFailed to create guide entity."));
                            return 0;
                        }))
                .then(CommandManager.literal("spawn_wizard")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            BlockPos safePos = NbtStructureManager.findSafeSurface(player.getServerWorld(), player.getBlockPos().add(2, 0, 1));
                            com.elemental.entity.ElementalWizardEntity wizard = ModEntities.ELEMENTAL_WIZARD.create(player.getServerWorld());
                            if (wizard != null) {
                                wizard.refreshPositionAndAngles(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, player.getYaw() + 180.0f, 0.0f);
                                wizard.setHomePos(safePos);
                                wizard.setPersistent();
                                player.getServerWorld().spawnEntity(wizard);
                                ctx.getSource().sendFeedback(() -> Text.literal("§aSpawned Eldrin, the Elemental Wizard at safe position!"), true);
                                return 1;
                            }
                            ctx.getSource().sendError(Text.literal("§cFailed to create wizard entity."));
                            return 0;
                        }))
                .then(CommandManager.literal("arena")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            QuestSaveData saveData = QuestSaveData.getServerState(player.server);
                            BlockPos arena = saveData.getEverlivingArenaLocation();
                            if (arena == null) {
                                arena = NbtStructureManager.findOrGenerateEverlivingArena(player.getServerWorld(), player.getBlockPos());
                            }
                            final BlockPos target = arena;
                            ctx.getSource().sendFeedback(() -> Text.literal("§6Everliving Knight Arena located at: §e" + (target != null ? target.toShortString() : "unknown")), true);
                            return 1;
                        })
                        .then(CommandManager.literal("tp")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    QuestSaveData saveData = QuestSaveData.getServerState(player.server);
                                    BlockPos arena = saveData.getEverlivingArenaLocation();
                                    if (arena == null) {
                                        arena = NbtStructureManager.findOrGenerateEverlivingArena(player.getServerWorld(), player.getBlockPos());
                                    }
                                    if (arena != null) {
                                        player.teleport(player.getServerWorld(), arena.getX() + 0.5, arena.getY() + 1.0, arena.getZ() + 0.5, player.getYaw(), player.getPitch());
                                        ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to Everliving Knight Arena!"), true);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendError(Text.literal("§cCould not locate or generate arena."));
                                        return 0;
                                    }
                                }))
                        .then(CommandManager.literal("regenerate")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    QuestSaveData saveData = QuestSaveData.getServerState(player.server);
                                    saveData.setEverlivingArenaLocation(null);
                                    saveData.markDirty();
                                    BlockPos arena = NbtStructureManager.findOrGenerateEverlivingArena(player.getServerWorld(), player.getBlockPos());
                                    if (arena != null) {
                                        PlayerQuestData data = QuestManager.getPlayerData(player);
                                        if (data.getState() == QuestState.LOCATE_EVERLIVING_ARENA) {
                                            data.setTargetWaypoint(arena);
                                            QuestManager.syncToAll(player.server);
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("§aRegenerated Everliving Knight Arena on flat plain with stepped boundaries at §e" + arena.toShortString()), true);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendError(Text.literal("§cFailed to generate arena."));
                                        return 0;
                                    }
                                })))
        );

        // Player dialogue choice click handler for Aeron (no OP permission required)
        dispatcher.register(CommandManager.literal("elementalask")
                .then(CommandManager.argument("topic", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String topic = StringArgumentType.getString(ctx, "topic");
                            return ChronicleGuideEntity.handleDialogueChoice(player, topic);
                        }))
        );

        // Player dialogue choice click handler for Elemental Wizard (no OP permission required)
        dispatcher.register(CommandManager.literal("elementalwizard")
                .then(CommandManager.literal("ask")
                        .then(CommandManager.argument("topic", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    String topic = StringArgumentType.getString(ctx, "topic");
                                    return com.elemental.entity.ElementalWizardEntity.handleDialogueChoice(player, topic);
                                })))
        );

        // Player dialogue skip button click handler (no OP permission required)
        dispatcher.register(CommandManager.literal("elementalskip")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    com.elemental.quest.DialogueManager.skipConversation(player);
                    return 1;
                })
        );
    }
}
