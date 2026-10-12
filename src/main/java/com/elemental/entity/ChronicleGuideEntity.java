package com.elemental.entity;

import com.elemental.Elemental;
import com.elemental.item.BlueprintMaps;
import com.elemental.item.ModItems;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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

public class ChronicleGuideEntity extends PathAwareEntity {
    private BlockPos homePos;

    public ChronicleGuideEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.setCustomName(Text.translatable("entity.elemental.chronicle_guide.name"));
        this.setCustomNameVisible(true);
    }

    public static DefaultAttributeContainer.Builder createGuideAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
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
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        if (damageSource.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return true; // Immune to accidental attacks, hostile mobs, fire, suffocation, fall damage
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            this.getNavigation().stop();
            this.getLookControl().lookAt(player, 30.0f, 30.0f);

            // If player is already in dialogue, right-clicking skips to completion
            if (DialogueManager.isInConversation(serverPlayer)) {
                DialogueManager.skipConversation(serverPlayer);
                return ActionResult.SUCCESS;
            }

            PlayerQuestData questData = QuestManager.getPlayerData(serverPlayer);
            QuestState state = questData.getState();

            // Phase 1: First meeting
            if (!questData.hasCompletedPhase1Dialogue()) {
                startPhase1Conversation(serverPlayer, false);
            }
            // Phase 2: After the ship (completed discovery & gathered blueprints)
            else if ((state == QuestState.RETURN_TO_AERON || state == QuestState.CRAFT_JETPACK || BlueprintMaps.hasAllBlueprints(serverPlayer))
                    && !questData.hasClaimedJetpack()) {
                startPhase2Conversation(serverPlayer);
            }
            // After Phase 2 or Jetpack already claimed: Desert expedition reminders & questions
            else if (questData.hasClaimedJetpack() || state == QuestState.DESERT_EXPEDITION || state == QuestState.QUEST_COMPLETE) {
                startDesertReminderConversation(serverPlayer);
            }
            // Between Phase 1 and Phase 2: Remind about searching the ship
            else {
                startShipReminderConversation(serverPlayer);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.CONSUME;
    }

    public static void startPhase1Conversation(ServerPlayerEntity player, boolean isReplay) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        // Add skip prompt hint
        MutableText skipHint = Text.literal(" §8[")
                .append(Text.translatable("dialogue.elemental.choice.skip_btn"))
                .append("]")
                .styled(style -> style
                        .withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/elementalskip"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("dialogue.elemental.choice.skip")))
                );

        // 1. Aeron: Easy… you’re awake. Can you stand?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.phase1_1")).append(skipHint),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 2. Player: I think so. What happened here? This world looks corrupted.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_2")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 3. Aeron: It is. The land is still here, but the power that held it in balance has been shattered.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.phase1_3")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 4. Player: What power?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_4")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 5. Aeron: The Elemental Core. It once carried the strength of all six elements: Dry Heat, Snow, Tree, Ocean, Mountain, and Nether Fire.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §b").append(Text.translatable("dialogue.elemental.aeron.phase1_5")),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        // 6. Player: What happened to it?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_6")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 7. Aeron: The Core broke into six fragments. Three were absorbed by Sir Solvane—the Everliving Knight. The other three were taken by Terrakon, the Seismic Colossus.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §c").append(Text.translatable("dialogue.elemental.aeron.phase1_7")),
                ModSounds.NPC_DIALOGUE, 0.95f
        ));

        // 8. Player: Can they be saved?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_8")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 9. Aeron: Perhaps. But not by force alone. The fragments must be recovered, and the Core’s power must be restored before either guardian can be defeated.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.phase1_9")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 10. Player: Why are you telling me this?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_10")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 11. Aeron: Because you’re still standing. And the broken forge at your side… it may be the beginning of a way forward. You’ll need to become an Element-Smith.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §6").append(Text.translatable("dialogue.elemental.aeron.phase1_11")),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        // 12. Player: Where do I start?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_12")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 13. Aeron: First, you need a way to reach places the old roads cannot. There’s a crashed Skyforge ship on a high cliff nearby. Its artificers were building something that could carry them through the sky. Search the wreckage. You may find what you need.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.phase1_13")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 14. Player: And then?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase1_14")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 15. Aeron: Bring back what you find. I’ll help you understand it.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §a§l").append(Text.translatable("dialogue.elemental.aeron.phase1_15")),
                ModSounds.NPC_DIALOGUE, 1.15f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            PlayerQuestData qData = QuestManager.getPlayerData(player);
            if (!isReplay) {
                qData.setCompletedPhase1Dialogue(true);
                QuestManager.setQuestState(player, QuestState.LOCATE_SKYFORGE);
                QuestSaveData.getServerState(player.server).markDirty();
                player.getWorld().playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        ModSounds.QUEST_ACCEPTED,
                        SoundCategory.PLAYERS,
                        1.0f,
                        1.0f
                );
            }
            showDialogueChoices(player);
        });
    }

    public static void startPhase2Conversation(ServerPlayerEntity player) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        PlayerQuestData qData = QuestManager.getPlayerData(player);
        boolean alreadyHas = qData.hasClaimedJetpack() || hasItemOrWorn(player, ModItems.ELEMENTAL_JETPACK);

        // 1. Player: I found some blueprints in the wreckage.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §a").append(Text.translatable("dialogue.elemental.player.phase2_1")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 2. Aeron: Let me see… These are flight schematics. A jetpack—an artificer’s device for rising, hovering, and crossing the highlands.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §a").append(Text.translatable("dialogue.elemental.aeron.phase2_2")),
                ModSounds.NPC_DIALOGUE, 1.1f
        ));

        // 3. Player: Can you make it work?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase2_3")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 4. Aeron: I think I can. Wait here. (2.0 second pause with crafting animation & sound cue)
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.phase2_4")),
                ModSounds.NPC_DIALOGUE, 1.0f,
                40, // 40 ticks = 2.0 seconds pause
                p -> {
                    ChronicleGuideEntity guide = findNearestGuide(p, 16.0);
                    if (guide != null) {
                        guide.swingHand(Hand.MAIN_HAND);
                        if (p.getWorld() instanceof ServerWorld serverWorld) {
                            serverWorld.playSound(null, guide.getX(), guide.getY(), guide.getZ(),
                                    SoundEvents.BLOCK_ANVIL_USE, SoundCategory.NEUTRAL, 0.7f, 1.3f);
                            serverWorld.spawnParticles(ParticleTypes.CRIT, guide.getX(), guide.getY() + 1.0, guide.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
                            serverWorld.spawnParticles(ParticleTypes.SMOKE, guide.getX(), guide.getY() + 1.0, guide.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }
        ));

        // 5. Aeron: Ready…? (Short beat, then grant Jetpack)
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §b").append(Text.translatable("dialogue.elemental.aeron.phase2_5")),
                ModSounds.NPC_DIALOGUE, 1.1f,
                25,
                p -> {
                    PlayerQuestData data = QuestManager.getPlayerData(p);
                    if (!data.hasClaimedJetpack()) {
                        data.setClaimedJetpack(true);
                        if (!hasItemOrWorn(p, ModItems.ELEMENTAL_JETPACK)) {
                            ItemStack jetpack = new ItemStack(ModItems.ELEMENTAL_JETPACK, 1);
                            if (!p.getInventory().insertStack(jetpack)) {
                                p.dropItem(jetpack, false);
                            }
                            p.sendMessage(Text.translatable("message.elemental.jetpack_received").formatted(Formatting.GREEN, Formatting.BOLD), false);
                            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                                    SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        }
                        unlockJetpackRecipes(p);
                        QuestSaveData.getServerState(p.server).markDirty();
                    }
                }
        ));

        // 6. Aeron: There! A little rough around the edges...
        Text line6Text = alreadyHas
                ? Text.translatable("dialogue.elemental.aeron.phase2_6_already_owned")
                : Text.translatable("dialogue.elemental.aeron.phase2_6");
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(line6Text),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        // 7. Player: Where should I go now?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase2_7")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 8. Aeron: The desert. The first element is hidden among its old settlements and ruins. Let the jetpack guide you, but stay alert. The sands remember what happened.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §6").append(Text.translatable("dialogue.elemental.aeron.phase2_8")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 9. Player: Then I’m going.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.phase2_9")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 10. Aeron: Go, Element-Smith. The world is waiting to be mended.
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §d§l").append(Text.translatable("dialogue.elemental.aeron.phase2_10")),
                ModSounds.NPC_DIALOGUE, 1.2f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            PlayerQuestData data = QuestManager.getPlayerData(player);
            // Ensure jetpack claimed and recipes unlocked even if skipped
            if (!data.hasClaimedJetpack()) {
                data.setClaimedJetpack(true);
                if (!hasItemOrWorn(player, ModItems.ELEMENTAL_JETPACK)) {
                    ItemStack jetpack = new ItemStack(ModItems.ELEMENTAL_JETPACK, 1);
                    if (!player.getInventory().insertStack(jetpack)) {
                        player.dropItem(jetpack, false);
                    }
                    player.sendMessage(Text.translatable("message.elemental.jetpack_received").formatted(Formatting.GREEN, Formatting.BOLD), false);
                }
                unlockJetpackRecipes(player);
            }

            QuestManager.setQuestState(player, QuestState.DESERT_EXPEDITION);
            player.getWorld().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ModSounds.QUEST_COMPLETED,
                    SoundCategory.PLAYERS,
                    1.0f,
                    1.0f
            );
            showDialogueChoices(player);
        });
    }

    public static void showDialogueChoices(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.choice.header")), false);

        player.sendMessage(buildChoiceLink("dialogue.elemental.choice.elements", "/elementalask elements"), false);
        player.sendMessage(buildChoiceLink("dialogue.elemental.choice.solvane", "/elementalask solvane"), false);
        player.sendMessage(buildChoiceLink("dialogue.elemental.choice.terrakon", "/elementalask terrakon"), false);
        player.sendMessage(buildChoiceLink("dialogue.elemental.choice.help", "/elementalask help"), false);

        PlayerQuestData qData = QuestManager.getPlayerData(player);
        if (qData.hasCompletedPhase1Dialogue()) {
            MutableText replayText = Text.literal("  §7► [")
                    .append(Text.translatable("dialogue.elemental.choice.story"))
                    .append("]")
                    .styled(style -> style
                            .withColor(Formatting.GRAY)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/elementalask story"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("dialogue.elemental.choice.click_to_ask")))
                    );
            player.sendMessage(replayText, false);
        }
    }

    private static MutableText buildChoiceLink(String key, String command) {
        return Text.literal("  §b► [")
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
        ChronicleGuideEntity guide = findNearestGuide(player, 16.0);
        if (guide == null) {
            player.sendMessage(Text.translatable("dialogue.elemental.aeron.too_far").formatted(Formatting.GRAY), false);
            return 0;
        }

        String name = player.getName().getString();

        switch (topic.toLowerCase()) {
            case "elements" -> {
                player.sendMessage(Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.choice.elements")), false);
                player.sendMessage(Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.elements")), false);
                playNpcSound(player);
                showDialogueChoices(player);
                return 1;
            }
            case "solvane" -> {
                player.sendMessage(Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.choice.solvane")), false);
                player.sendMessage(Text.literal("§6[Aeron] §c").append(Text.translatable("dialogue.elemental.aeron.solvane")), false);
                playNpcSound(player);
                showDialogueChoices(player);
                return 1;
            }
            case "terrakon" -> {
                player.sendMessage(Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.choice.terrakon")), false);
                player.sendMessage(Text.literal("§6[Aeron] §6").append(Text.translatable("dialogue.elemental.aeron.terrakon")), false);
                playNpcSound(player);
                showDialogueChoices(player);
                return 1;
            }
            case "help" -> {
                player.sendMessage(Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.choice.help")), false);
                player.sendMessage(Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.help")), false);
                playNpcSound(player);
                showDialogueChoices(player);
                return 1;
            }
            case "story" -> {
                startPhase1Conversation(player, true);
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }

    private static void playNpcSound(ServerPlayerEntity player) {
        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.NPC_DIALOGUE,
                SoundCategory.PLAYERS,
                0.8f,
                1.0f
        );
    }

    public static ChronicleGuideEntity findNearestGuide(ServerPlayerEntity player, double radius) {
        List<ChronicleGuideEntity> guides = player.getServerWorld().getEntitiesByClass(
                ChronicleGuideEntity.class,
                new net.minecraft.util.math.Box(player.getBlockPos()).expand(radius),
                e -> true
        );
        return guides.isEmpty() ? null : guides.get(0);
    }

    private void startShipReminderConversation(ServerPlayerEntity player) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NPC_DIALOGUE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        player.sendMessage(Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.reminder_ship")), false);
        showDialogueChoices(player);
    }

    private void startDesertReminderConversation(ServerPlayerEntity player) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NPC_DIALOGUE, SoundCategory.PLAYERS, 0.8f, 1.0f);
        player.sendMessage(Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.reminder_desert")), false);
        showDialogueChoices(player);
    }

    public static void unlockJetpackRecipes(ServerPlayerEntity player) {
        var recipeManager = player.server.getRecipeManager();
        List<Recipe<?>> recipesToUnlock = new ArrayList<>();

        recipeManager.get(new Identifier(Elemental.MOD_ID, "pneumatic_piston")).ifPresent(recipesToUnlock::add);
        recipeManager.get(new Identifier(Elemental.MOD_ID, "aerogel_core")).ifPresent(recipesToUnlock::add);
        recipeManager.get(new Identifier(Elemental.MOD_ID, "element_core")).ifPresent(recipesToUnlock::add);
        recipeManager.get(new Identifier(Elemental.MOD_ID, "elemental_jetpack")).ifPresent(recipesToUnlock::add);

        if (!recipesToUnlock.isEmpty()) {
            player.unlockRecipes(recipesToUnlock);
        }
    }

    public static boolean hasItemOrWorn(ServerPlayerEntity player, net.minecraft.item.Item item) {
        if (player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(item)) return true;
        if (com.elemental.util.JetpackSlotHolder.getJetpackStack(player).isOf(item)) return true;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.homePos != null) {
            nbt.putInt("HomeX", this.homePos.getX());
            nbt.putInt("HomeY", this.homePos.getY());
            nbt.putInt("HomeZ", this.homePos.getZ());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("HomeX") && nbt.contains("HomeY") && nbt.contains("HomeZ")) {
            this.homePos = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }
}
