package com.elemental.entity;

import com.elemental.Elemental;
import com.elemental.item.BlueprintMaps;
import com.elemental.item.ModItems;
import com.elemental.quest.DialogueManager;
import com.elemental.quest.PlayerQuestData;
import com.elemental.quest.QuestManager;
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
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

            if (DialogueManager.isInConversation(serverPlayer)) {
                serverPlayer.sendMessage(Text.translatable("dialogue.elemental.aeron.busy").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }

            PlayerQuestData questData = QuestManager.getPlayerData(serverPlayer);
            QuestState state = questData.getState();

            if (state == QuestState.CRAFT_STONE_TOOLS || state == QuestState.FIND_AERON) {
                startPrologueConversation(serverPlayer);
            } else if (state == QuestState.RETURN_TO_AERON || state == QuestState.CRAFT_JETPACK || BlueprintMaps.hasAllBlueprints(serverPlayer)) {
                startBlueprintDeliveryConversation(serverPlayer);
            } else if (state == QuestState.DESERT_EXPEDITION || state == QuestState.QUEST_COMPLETE || questData.hasClaimedJetpack()) {
                startDesertReminderConversation(serverPlayer);
            } else {
                startReminderConversation(serverPlayer, state);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.CONSUME;
    }

    private void startPrologueConversation(ServerPlayerEntity player) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        // 1. Player: Why is this world corrupted?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.prologue_intro")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 2. Aeron: I am Aeron, cosmic tremor shattered the Crucible
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.prologue_intro")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 3. Player: What happened to the guardians?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.prologue_guardians")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 4. Aeron: Sir Solvane became Everliving Knight, Terrakon became Seismic Colossus
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §c").append(Text.translatable("dialogue.elemental.aeron.prologue_guardians")),
                ModSounds.NPC_DIALOGUE, 0.95f
        ));

        // 5. Player: Is there any way to restore balance?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.prologue_balance")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 6. Aeron: You bear spark of Element-Smith, caravel crashed nearby
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §b").append(Text.translatable("dialogue.elemental.aeron.prologue_balance")),
                ModSounds.NPC_DIALOGUE, 1.05f
        ));

        // 7. Player: Where is it buried?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.prologue_ship")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 8. Aeron: Buried between stone and dirt layer! Salvage pistons, aerogel, and all 4 blueprint maps!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.prologue_ship")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 9. Player: Understood!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.prologue_ready")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 10. Aeron: Calibrated compass, go unearth the wreckage!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §d§l").append(Text.translatable("dialogue.elemental.aeron.prologue_go")),
                ModSounds.NPC_DIALOGUE, 1.2f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            QuestManager.setQuestState(player, QuestState.LOCATE_SKYFORGE);
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
        });
    }

    private void startBlueprintDeliveryConversation(ServerPlayerEntity player) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        // 1. Player: Aeron! I excavated the buried spaceship and recovered all 4 Blueprint Maps!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §a").append(Text.translatable("dialogue.elemental.player.deliver_blueprints")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 2. Aeron: Splendid! The schematics are intact!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §a").append(Text.translatable("dialogue.elemental.aeron.blueprints_received")),
                ModSounds.NPC_DIALOGUE, 1.1f
        ));

        // 3. Player: Can you decipher them?
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.ask_recipes")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        // 4. Aeron: I have transcribed every recipe to your crafting knowledge!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.recipes_unlocked")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 5. Aeron: Take this prototype Elemental Jetpack!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §b§l").append(Text.translatable("dialogue.elemental.aeron.give_jetpack")),
                ModSounds.NPC_DIALOGUE, 1.15f
        ));

        // 6. Aeron: Sunforged Scimitar lore
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §6").append(Text.translatable("dialogue.elemental.aeron.scimitar_lore_1")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        // 7. Aeron: Desert waypoint calibrated!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §d§l").append(Text.translatable("dialogue.elemental.aeron.desert_waypoint")),
                ModSounds.NPC_DIALOGUE, 1.2f
        ));

        // 8. Player: Heading to the desert!
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.desert_ready")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        DialogueManager.startConversation(player, lines, () -> {
            PlayerQuestData qData = QuestManager.getPlayerData(player);

            // 1. Give ONLY 1 jetpack to this individual player if they haven't claimed it yet!
            if (!qData.hasClaimedJetpack()) {
                qData.setClaimedJetpack(true);
                ItemStack jetpackStack = new ItemStack(ModItems.ELEMENTAL_JETPACK, 1);
                if (!player.getInventory().insertStack(jetpackStack)) {
                    player.dropItem(jetpackStack, false);
                }
                player.sendMessage(Text.translatable("message.elemental.jetpack_received").formatted(Formatting.GREEN, Formatting.BOLD), false);
            } else {
                player.sendMessage(Text.translatable("message.elemental.already_claimed").formatted(Formatting.GRAY), false);
            }

            // 2. Unlock every recipe required to make a jetpack to the player's crafting menu
            unlockJetpackRecipes(player);

            // 3. Advance state to DESERT_EXPEDITION
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
        });
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

    private void startReminderConversation(ServerPlayerEntity player, QuestState state) {
        String name = player.getName().getString();
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§b<" + name + "> §f").append(Text.translatable("dialogue.elemental.player.reminder")),
                SoundEvents.UI_BUTTON_CLICK.value(), 1.3f
        ));

        int blueprintsCount = BlueprintMaps.countUniqueBlueprints(player);
        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.reminder_recipe", blueprintsCount)),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §a► ").append(Text.translatable(state.getTranslationKey())),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        DialogueManager.startConversation(player, lines, null);
    }

    private void startDesertReminderConversation(ServerPlayerEntity player) {
        List<DialogueManager.DialogueEntry> lines = new ArrayList<>();

        lines.add(new DialogueManager.DialogueEntry(
                Text.literal("§6[Aeron] §e").append(Text.translatable("dialogue.elemental.aeron.desert_waypoint")),
                ModSounds.NPC_DIALOGUE, 1.0f
        ));

        DialogueManager.startConversation(player, lines, null);
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
