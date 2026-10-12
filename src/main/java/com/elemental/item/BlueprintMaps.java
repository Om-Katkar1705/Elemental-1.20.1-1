package com.elemental.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class BlueprintMaps {
    public static final String BLUEPRINT_TAG = "ElementalBlueprint";

    public enum Type {
        PNEUMATIC_PISTON("pneumatic_piston", "§6§lBlueprint: Pneumatic Piston", "Pneumatic Piston (Iron, Copper, Piston, Redstone)", Formatting.GOLD),
        AEROGEL_CORE("aerogel_core", "§b§lBlueprint: Aerogel Core", "Aerogel Core (Glass, Feathers, Snowball/Slime/Quartz)", Formatting.AQUA),
        ELEMENT_CORE("element_core", "§c§lBlueprint: Element Core", "Element Core (Copper, Cobblestone, Coal)", Formatting.RED),
        ELEMENTAL_JETPACK("elemental_jetpack", "§a§lBlueprint: Elemental Jetpack", "Elemental Jetpack (Pistons, Aerogel Cores, Element Core)", Formatting.GREEN);

        private final String id;
        private final String title;
        private final String recipeDesc;
        private final Formatting color;

        Type(String id, String title, String recipeDesc, Formatting color) {
            this.id = id;
            this.title = title;
            this.recipeDesc = recipeDesc;
            this.color = color;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getRecipeDesc() {
            return recipeDesc;
        }

        public Formatting getColor() {
            return color;
        }
    }

    public static ItemStack createBlueprintMap(ServerWorld world, BlockPos pos, Type type) {
        ItemStack mapStack = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, true, true);
        mapStack.setCustomName(Text.literal(type.getTitle()));

        NbtCompound nbt = mapStack.getOrCreateNbt();
        nbt.putString(BLUEPRINT_TAG, type.getId());

        NbtCompound display = mapStack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
        NbtList loreList = new NbtList();
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Ancient Skyforge Technical Schematic").formatted(Formatting.GRAY))));
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eCrafting: §f" + type.getRecipeDesc()))));
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§dDeliver to Aeron at camp to decipher recipe!").formatted(Formatting.LIGHT_PURPLE))));
        display.put(ItemStack.LORE_KEY, loreList);

        return mapStack;
    }

    public static boolean isBlueprint(ItemStack stack, Type type) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.FILLED_MAP) && !stack.isOf(Items.MAP)) return false;
        if (stack.hasNbt() && type.getId().equals(stack.getNbt().getString(BLUEPRINT_TAG))) {
            return true;
        }
        String name = stack.getName().getString().toLowerCase();
        return name.contains("blueprint") && name.contains(type.getId().replace("_", " "));
    }

    public static boolean hasBlueprint(PlayerEntity player, Type type) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isBlueprint(stack, type)) {
                return true;
            }
        }
        return false;
    }

    public static int countUniqueBlueprints(PlayerEntity player) {
        int count = 0;
        for (Type t : Type.values()) {
            if (hasBlueprint(player, t)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasAllBlueprints(PlayerEntity player) {
        return countUniqueBlueprints(player) >= Type.values().length;
    }
}
