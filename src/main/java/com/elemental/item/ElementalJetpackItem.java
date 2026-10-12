package com.elemental.item;

import com.elemental.network.QuestNetworking;
import com.elemental.quest.PlayerQuestData;
import com.elemental.quest.QuestManager;
import com.elemental.sound.ModSounds;
import com.elemental.util.JetpackSlotHolder;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ElementalJetpackItem extends Item {

    public ElementalJetpackItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack heldStack = user.getStackInHand(hand);

        // Sneak-right-click toggles guidance HUD
        if (user.isSneaking()) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                toggleGuidance(serverPlayer);
            }
            return TypedActionResult.success(heldStack, world.isClient());
        }

        // Equips ONLY into the dedicated Jetpack slot (never standard chestplate)
        if (user instanceof JetpackSlotHolder holder) {
            ItemStack currentEquipped = holder.elemental$getJetpackSlot();
            if (currentEquipped.isEmpty()) {
                if (!world.isClient()) {
                    holder.elemental$setJetpackSlot(heldStack.copy());
                    heldStack.setCount(0);
                    if (user instanceof ServerPlayerEntity serverPlayer) {
                        QuestNetworking.sendExtraSlotSync(serverPlayer);
                    }
                }
                user.playSound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
                return TypedActionResult.success(heldStack, world.isClient());
            } else {
                // Swap equipped jetpack with held jetpack
                if (!world.isClient()) {
                    ItemStack temp = currentEquipped.copy();
                    holder.elemental$setJetpackSlot(heldStack.copy());
                    user.setStackInHand(hand, temp);
                    if (user instanceof ServerPlayerEntity serverPlayer) {
                        QuestNetworking.sendExtraSlotSync(serverPlayer);
                    }
                }
                user.playSound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
                return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
            }
        }

        return TypedActionResult.pass(heldStack);
    }

    public static void toggleGuidance(ServerPlayerEntity player) {
        PlayerQuestData questData = QuestManager.getPlayerData(player);
        boolean newState = !questData.isGuidanceActive();
        questData.setGuidanceActive(newState);
        QuestManager.syncToClient(player);

        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.JETPACK_GUIDANCE,
                SoundCategory.PLAYERS,
                0.8f,
                newState ? 1.2f : 0.8f
        );

        if (newState) {
            player.sendMessage(Text.translatable("message.elemental.guidance_enabled").formatted(Formatting.AQUA), true);
        } else {
            player.sendMessage(Text.translatable("message.elemental.guidance_disabled").formatted(Formatting.GRAY), true);
        }
    }

    public static void performThrust(ServerPlayerEntity player, boolean hasForwardInput) {
        ItemStack jetpackStack = JetpackSlotHolder.getJetpackStack(player);
        if (!(jetpackStack.getItem() instanceof ElementalJetpackItem)) return;

        Vec3d vel = player.getVelocity();
        Vec3d look = player.getRotationVec(1.0f);

        double targetX = vel.x;
        double targetY = vel.y;
        double targetZ = vel.z;

        if (hasForwardInput) {
            // Elytra-speed flight along look direction
            double targetSpeed = 1.45;
            Vec3d targetVel = look.multiply(targetSpeed);
            targetX = vel.x * 0.75 + targetVel.x * 0.25;
            targetY = vel.y * 0.75 + targetVel.y * 0.25;
            targetZ = vel.z * 0.75 + targetVel.z * 0.25;
        } else {
            // Vertical climb
            targetY = Math.min(vel.y + 0.15, 0.75);
            targetX = vel.x * 0.95;
            targetZ = vel.z * 0.95;
        }

        player.setVelocity(targetX, targetY, targetZ);
        player.velocityModified = true;
        player.fallDistance = 0.0f;

        if (player.age % 3 == 0 && player.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.1, 0.05, 0.1, 0.02);
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.08, 0.02, 0.08, 0.03);
        }
    }

    public static void performGlide(ServerPlayerEntity player) {
        ItemStack jetpackStack = JetpackSlotHolder.getJetpackStack(player);
        if (!(jetpackStack.getItem() instanceof ElementalJetpackItem)) return;

        Vec3d vel = player.getVelocity();
        if (vel.y < -0.15) {
            player.setVelocity(vel.x, -0.15, vel.z);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.elemental.jetpack.desc").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("tooltip.elemental.jetpack.thrust").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.elemental.jetpack.glide").formatted(Formatting.BLUE));
        tooltip.add(Text.translatable("tooltip.elemental.jetpack.guidance").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
