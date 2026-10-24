package com.elemental.entity;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import com.elemental.item.ModItems;
import com.elemental.network.QuestNetworking;
import com.elemental.quest.DialogueManager;
import com.elemental.quest.PlayerQuestData;
import com.elemental.quest.QuestManager;
import com.elemental.quest.QuestSaveData;
import com.elemental.quest.QuestState;
import com.elemental.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Elemental Wizard NPC (Eldrin, Scholar of the Primal Spheres).
 * Holds encyclopedic knowledge of every element, weapon, and boss guardian.
 * Assigns tasks to craft the first elemental weapon:
 * Task 1: Gather raw materials and create the Weaponer workstation.
 * Task 2: Gather raw materials and create the Dry Heat Element.
 * Task 3: Combine Stencil and Dry Heat Element in the Weaponer to forge the Sunforged Scimitar.
 */
public class ElementalWizardEntity extends PathAwareEntity {

    public static double MAX_HEALTH = 100.0;
    public static double MOVEMENT_SPEED = 0.22;
    public static double FOLLOW_RANGE = 16.0;
    public static double KNOCKBACK_RESISTANCE = 1.0;

    private BlockPos homePos;

    public ElementalWizardEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.setCustomName(Text.translatable("entity.elemental.elemental_wizard.name"));
        this.setCustomNameVisible(true);
    }

    public static DefaultAttributeContainer.Builder createWizardAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW_RANGE)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return true;
    }

    public BlockPos getHomePos() {
        return homePos;
    }

    public void setHomePos(BlockPos homePos) {
        this.homePos = homePos;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            if (this.random.nextFloat() < 0.25f) {
                double px = this.getX() + (this.random.nextDouble() - 0.5) * 0.8;
                double py = this.getY() + 0.8 + this.random.nextDouble() * 1.2;
                double pz = this.getZ() + (this.random.nextDouble() - 0.5) * 0.8;
                this.getWorld().addParticle(ParticleTypes.ENCHANT, px, py, pz,
                        (this.random.nextDouble() - 0.5) * 0.2,
                        this.random.nextDouble() * 0.3,
                        (this.random.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            this.getNavigation().stop();
            this.getLookControl().lookAt(player, 30.0f, 30.0f);

            if (DialogueManager.isInConversation(serverPlayer)) {
                DialogueManager.skipConversation(serverPlayer);
                return ActionResult.SUCCESS;
            } else if (DialogueManager.isAnyConversationActive()) {
                serverPlayer.sendMessage(Text.literal("§5[Eldrin] §7The Wizard is conversing with another adventurer... Please listen."), false);
                return ActionResult.SUCCESS;
            }

            PlayerQuestData questData = QuestManager.getPlayerData(serverPlayer);
            QuestState state = questData.getState();

            // Unlock Element Core, Weaponer, and Stencil recipes so the player has them immediately
            unlockElementCoreRecipes(serverPlayer);

            // Phase 1: First meeting at Desert Village
            if (state == QuestState.DESERT_EXPEDITION || state == QuestState.TALK_TO_WIZARD || state.getId() < QuestState.CRAFT_WEAPONER.getId()) {
                startWizardIntroduction(serverPlayer);
            }
            // Phase 2: In Task 1 (Craft Weaponer)
            else if (state == QuestState.CRAFT_WEAPONER) {
                if (QuestManager.hasItemOrWorn(serverPlayer, ModBlocks.WEAPONER.asItem())) {
                    startTask1CompletedDialogue(serverPlayer);
                } else {
                    startTask1Reminder(serverPlayer);
                }
            }
            // Phase 3: In Task 2 (Craft Dry Heat Element)
            else if (state == QuestState.CRAFT_DRY_HEAT_ELEMENT) {
                if (QuestManager.hasItemOrWorn(serverPlayer, ModItems.DRY_HEAT_ELEMENT)) {
                    startTask2CompletedDialogue(serverPlayer);
                } else {
                    startTask2Reminder(serverPlayer);
                }
            }
            // Phase 4: In Task 3 (Forge Sunforged Scimitar)
            else if (state == QuestState.FORGE_SUNFORGED_SCIMITAR) {
                if (QuestManager.hasItemOrWorn(serverPlayer, ModItems.SUNFORGED_SCIMITAR)) {
                    startTask3CompletedDialogue(serverPlayer);
                } else {
                    startTask3Reminder(serverPlayer);
                }
            }
            // Phase 5: Craft Snow Element & Forge Frostwake Pick
            else if (state == QuestState.CRAFT_SNOW_ELEMENT || state == QuestState.FORGE_FROSTWAKE_PICK) {
                serverPlayer.sendMessage(Text.literal("§5[Eldrin] §b").append(Text.translatable("dialogue.elemental.wizard.snow_reminder")), false);
                playWizardSoundAll(serverPlayer.server);
            }
            // Phase 6: Craft Tree Element & Forge Rootbound Axe
            else if (state == QuestState.CRAFT_TREE_ELEMENT || state == QuestState.FORGE_ROOTBOUND_AXE) {
                serverPlayer.sendMessage(Text.literal("§5[Eldrin] §a").append(Text.translatable("dialogue.elemental.wizard.tree_reminder")), false);
                playWizardSoundAll(serverPlayer.server);
            }
            // Phase 7: Everliving Knight Boss Fight
            else if (state == QuestState.LOCATE_EVERLIVING_ARENA || state == QuestState.DEFEAT_EVERLIVING_KNIGHT) {
                serverPlayer.sendMessage(Text.literal("§5[Eldrin] §c").append(Text.translatable("dialogue.elemental.wizard.arena_reminder")), false);
                playWizardSoundAll(serverPlayer.server);
            }
            // Completed or Free roaming
            else {
                startCompletedGreeting(serverPlayer);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.CONSUME;
    }

    public static void unlockElementCoreRecipes(ServerPlayerEntity player) {
        List<Identifier> recipeIds = List.of(
                Elemental.id("element_core"),
                Elemental.id("weaponer"),
                Elemental.id("stencil")
        );
        List<Recipe<?>> recipesToUnlock = new ArrayList<>();
        for (Identifier id : recipeIds) {
            player.server.getRecipeManager().get(id).ifPresent(recipesToUnlock::add);
        }
        if (!recipesToUnlock.isEmpty()) {
            player.unlockRecipes(recipesToUnlock);
        }
    }

    public static void startWizardIntroduction(ServerPlayerEntity player) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        MutableText skipHint = Text.literal(" §8[")
                .append(Text.translatable("dialogue.elemental.choice.skip_btn"))
                .append("]")
                .styled(style -> style
                        .withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/elementalskip"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("dialogue.elemental.choice.skip")))
                );

        // 1. Eldrin greetings
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §d").append(Text.translatable("dialogue.elemental.wizard.intro_1")).append(skipHint),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        // 2. Player: Aeron sent me...
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.wizard.intro_2")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 3. Eldrin: Indeed. I hold knowledge of all elements...
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §e").append(Text.translatable("dialogue.elemental.wizard.intro_3")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 4. Eldrin: You need an elemental weapon...
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §6").append(Text.translatable("dialogue.elemental.wizard.intro_4")),
                ModSounds.NPC_DIALOGUE, 0.95f
        ));

        // 5. Eldrin: I have unlocked the Element Core recipe for you.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §b").append(Text.translatable("dialogue.elemental.wizard.intro_5")),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f,
                75,
                p -> {
                    unlockElementCoreRecipes(p);
                    p.sendMessage(Text.translatable("message.elemental.element_core_unlocked").formatted(Formatting.AQUA, Formatting.BOLD), false);
                }
        ));

        // 6. Eldrin: Task 1 - Craft Weaponer
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §a§l").append(Text.translatable("dialogue.elemental.wizard.task1_assign")),
                ModSounds.NPC_DIALOGUE, 1.1f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                unlockElementCoreRecipes(sp);
            }
            QuestManager.setQuestState(player, QuestState.CRAFT_WEAPONER);
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                sp.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    public static void startTask1CompletedDialogue(ServerPlayerEntity player) {
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §a").append(Text.translatable("dialogue.elemental.wizard.task1_done")),
                ModSounds.NPC_DIALOGUE, 1.1f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §6§l").append(Text.translatable("dialogue.elemental.wizard.task2_assign")),
                SoundEvents.BLOCK_ANVIL_USE, 1.2f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            QuestManager.setQuestState(player, QuestState.CRAFT_DRY_HEAT_ELEMENT);
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                sp.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    public static void startTask1Reminder(ServerPlayerEntity player) {
        player.server.getPlayerManager().broadcast(
                Text.literal("§5[Eldrin] §e").append(Text.translatable("dialogue.elemental.wizard.task1_reminder")), false
        );
        playWizardSoundAll(player.server);
    }

    public static void startTask2CompletedDialogue(ServerPlayerEntity player) {
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §6").append(Text.translatable("dialogue.elemental.wizard.task2_done")),
                ModSounds.NPC_DIALOGUE, 1.15f
        ));

        // Tip to craft multiple elements
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §e§l").append(Text.translatable("dialogue.elemental.wizard.multiple_elements_tip")),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.1f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §a§l").append(Text.translatable("dialogue.elemental.wizard.task3_assign")),
                SoundEvents.ITEM_FIRECHARGE_USE, 1.0f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            QuestManager.setQuestState(player, QuestState.FORGE_SUNFORGED_SCIMITAR);
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                sp.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    public static void startTask2Reminder(ServerPlayerEntity player) {
        player.server.getPlayerManager().broadcast(
                Text.literal("§5[Eldrin] §e").append(Text.translatable("dialogue.elemental.wizard.task2_reminder")), false
        );
        playWizardSoundAll(player.server);
    }

    public static void startTask3CompletedDialogue(ServerPlayerEntity player) {
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §e§l").append(Text.translatable("dialogue.elemental.wizard.task3_done_1")),
                SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §6").append(Text.translatable("dialogue.elemental.wizard.task3_done_2")),
                ModSounds.NPC_DIALOGUE, 1.1f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §d§l").append(Text.translatable("dialogue.elemental.wizard.task3_done_3")),
                ModSounds.QUEST_COMPLETED, 1.0f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            QuestNetworking.sendOpenWeaponDetail(player, "sunforged_scimitar");
            QuestManager.setQuestState(player, QuestState.CRAFT_SNOW_ELEMENT);
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                sp.playSound(ModSounds.OBJECTIVE_UPDATED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    public static void startEverlivingBossDialogue(ServerPlayerEntity player) {
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        MutableText skipHint = Text.literal(" §8[")
                .append(Text.translatable("dialogue.elemental.choice.skip_btn"))
                .append("]")
                .styled(style -> style
                        .withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/elementalskip"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("dialogue.elemental.choice.skip")))
                );

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §e§l").append(Text.translatable("dialogue.elemental.wizard.boss_intro_1")).append(skipHint),
                ModSounds.NPC_DIALOGUE, 1.15f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §6").append(Text.translatable("dialogue.elemental.wizard.boss_intro_2")),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §c§l").append(Text.translatable("dialogue.elemental.wizard.boss_intro_3")),
                SoundEvents.ENTITY_WITHER_SPAWN, 0.9f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§5[Eldrin] §a").append(Text.translatable("dialogue.elemental.wizard.boss_intro_4")),
                ModSounds.QUEST_ACCEPTED, 1.0f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            QuestManager.setQuestState(player, QuestState.LOCATE_EVERLIVING_ARENA);
            for (ServerPlayerEntity sp : player.server.getPlayerManager().getPlayerList()) {
                sp.playSound(ModSounds.QUEST_ACCEPTED, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }

    public static void startTask3Reminder(ServerPlayerEntity player) {
        player.server.getPlayerManager().broadcast(
                Text.literal("§5[Eldrin] §e").append(Text.translatable("dialogue.elemental.wizard.task3_reminder")), false
        );
        playWizardSoundAll(player.server);
    }

    public static void startCompletedGreeting(ServerPlayerEntity player) {
        player.server.getPlayerManager().broadcast(
                Text.literal("§5[Eldrin] §d").append(Text.translatable("dialogue.elemental.wizard.greeting_complete")), false
        );
        playWizardSoundAll(player.server);
    }

    public static void showWizardKnowledgeChoices(ServerPlayerEntity player) {
        // Optional clickable choices removed as requested
    }

    private static MutableText buildChoiceLink(String key, String command) {
        return Text.literal("[")
                .append(Text.translatable(key))
                .append("]")
                .styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("dialogue.elemental.choice.click_to_ask")))
                );
    }

    public static int handleDialogueChoice(ServerPlayerEntity player, String topic) {
        ElementalWizardEntity wizard = findNearestWizard(player, 16.0);
        if (wizard == null) {
            player.sendMessage(Text.translatable("dialogue.elemental.wizard.too_far").formatted(Formatting.GRAY), false);
            return 0;
        }

        String name = player.getName().getString();

        switch (topic.toLowerCase()) {
            case "dry_heat" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_dry_heat", "dialogue.elemental.wizard.info_dry_heat", Formatting.GOLD);
                return 1;
            }
            case "snow" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_snow", "dialogue.elemental.wizard.info_snow", Formatting.AQUA);
                return 1;
            }
            case "tree" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_tree", "dialogue.elemental.wizard.info_tree", Formatting.GREEN);
                return 1;
            }
            case "ocean" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_ocean", "dialogue.elemental.wizard.info_ocean", Formatting.BLUE);
                return 1;
            }
            case "mountain" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_mountain", "dialogue.elemental.wizard.info_mountain", Formatting.GRAY);
                return 1;
            }
            case "fire" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_fire", "dialogue.elemental.wizard.info_fire", Formatting.RED);
                return 1;
            }
            case "scimitar" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_scimitar", "dialogue.elemental.wizard.info_scimitar", Formatting.GOLD);
                return 1;
            }
            case "pick" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_pick", "dialogue.elemental.wizard.info_pick", Formatting.AQUA);
                return 1;
            }
            case "axe" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_axe", "dialogue.elemental.wizard.info_axe", Formatting.GREEN);
                return 1;
            }
            case "trident" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_trident", "dialogue.elemental.wizard.info_trident", Formatting.BLUE);
                return 1;
            }
            case "hammer" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_hammer", "dialogue.elemental.wizard.info_hammer", Formatting.LIGHT_PURPLE);
                return 1;
            }
            case "rift_blade" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_rift_blade", "dialogue.elemental.wizard.info_rift_blade", Formatting.DARK_RED);
                return 1;
            }
            case "solvane" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_solvane", "dialogue.elemental.wizard.info_solvane", Formatting.GOLD);
                return 1;
            }
            case "terrakon" -> {
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_terrakon", "dialogue.elemental.wizard.info_terrakon", Formatting.DARK_GREEN);
                return 1;
            }
            case "current_task" -> {
                PlayerQuestData qData = QuestManager.getPlayerData(player);
                QuestState s = qData.getState();
                String reminderKey = switch (s) {
                    case CRAFT_WEAPONER -> "dialogue.elemental.wizard.task1_reminder";
                    case CRAFT_DRY_HEAT_ELEMENT -> "dialogue.elemental.wizard.task2_reminder";
                    case FORGE_SUNFORGED_SCIMITAR -> "dialogue.elemental.wizard.task3_reminder";
                    default -> "dialogue.elemental.wizard.task_general_help";
                };
                broadcastTopic(player, name, "dialogue.elemental.wizard.opt_tasks", reminderKey, Formatting.YELLOW);
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }

    private static void broadcastTopic(ServerPlayerEntity player, String playerName, String choiceKey, String replyKey, Formatting color) {
        player.server.getPlayerManager().broadcast(Text.literal("§b<" + playerName + "> §f").append(Text.translatable(choiceKey)), false);
        player.server.getPlayerManager().broadcast(Text.literal("§5[Eldrin] ").formatted(color).append(Text.translatable(replyKey)), false);
        playWizardSoundAll(player.server);
        showWizardKnowledgeChoices(player);
    }

    private static void playWizardSoundAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSound(ModSounds.NPC_DIALOGUE, SoundCategory.PLAYERS, 0.8f, 1.05f);
        }
    }

    public static ElementalWizardEntity findNearestWizard(ServerPlayerEntity player, double radius) {
        List<ElementalWizardEntity> wizards = player.getServerWorld().getEntitiesByClass(
                ElementalWizardEntity.class,
                new net.minecraft.util.math.Box(player.getBlockPos()).expand(radius),
                e -> true
        );
        return wizards.isEmpty() ? null : wizards.get(0);
    }
}
