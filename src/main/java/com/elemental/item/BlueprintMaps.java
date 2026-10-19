package com.elemental.item;

import com.elemental.Elemental;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

public class BlueprintMaps {
    public static final String BLUEPRINT_TAG = "ElementalBlueprint";
    private static final Map<Type, byte[]> CACHED_COLORS = new EnumMap<>(Type.class);

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

    public static byte[] getBlueprintColors(Type type) {
        if (CACHED_COLORS.containsKey(type)) {
            return CACHED_COLORS.get(type);
        }
        String path = "/assets/elemental/textures/map/" + type.getId() + "_bytes.bin";
        try (InputStream is = BlueprintMaps.class.getResourceAsStream(path)) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                if (bytes.length == 16384) {
                    CACHED_COLORS.put(type, bytes);
                    return bytes;
                } else {
                    Elemental.LOGGER.error("Invalid map bytes length for {}: {}", type.getId(), bytes.length);
                }
            } else {
                Elemental.LOGGER.error("Could not find blueprint map file at {}", path);
            }
        } catch (Exception e) {
            Elemental.LOGGER.error("Failed to load blueprint map colors for {}", type.getId(), e);
        }
        return null;
    }

    public static void applyBlueprintToMap(MapState state, Type type) {
        if (state == null || type == null) return;
        byte[] colors = getBlueprintColors(type);
        if (colors != null && colors.length == 16384) {
            System.arraycopy(colors, 0, state.colors, 0, 16384);
            state.replaceIcons(java.util.Collections.emptyList());
            // Mark bounds dirty so all players receive full 128x128 map update
            state.setColor(0, 0, state.colors[0]);
            state.setColor(127, 127, state.colors[127 + 127 * 128]);
            if (state instanceof BlueprintMapStateAccessor accessor) {
                accessor.elemental$setBlueprintType(type.getId());
            }
            state.markDirty();
        }
    }

    public static ItemStack createBlueprintMap(ServerWorld world, BlockPos pos, Type type) {
        ItemStack mapStack = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, false, false);
        mapStack.setCustomName(Text.literal(type.getTitle()));

        NbtCompound nbt = mapStack.getOrCreateNbt();
        nbt.putString(BLUEPRINT_TAG, type.getId());

        NbtCompound display = mapStack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
        NbtList loreList = new NbtList();
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Ancient Skyforge Technical Schematic").formatted(Formatting.GRAY))));
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eCrafting: §f" + type.getRecipeDesc()))));
        loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§dDeliver to Aeron at camp to decipher recipe!").formatted(Formatting.LIGHT_PURPLE))));
        display.put(ItemStack.LORE_KEY, loreList);

        // Apply recipe colors to MapState immediately upon creation
        MapState mapState = FilledMapItem.getMapState(mapStack, world);
        if (mapState != null) {
            applyBlueprintToMap(mapState, type);
        }

        return mapStack;
    }

    public static Type getBlueprintType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!stack.isOf(Items.FILLED_MAP) && !stack.isOf(Items.MAP)) return null;

        if (stack.hasNbt() && stack.getNbt().contains(BLUEPRINT_TAG)) {
            String id = stack.getNbt().getString(BLUEPRINT_TAG).toLowerCase();
            for (Type t : Type.values()) {
                if (t.getId().equals(id)) return t;
            }
            if (id.contains("piston") || id.contains("thruster")) return Type.PNEUMATIC_PISTON;
            if (id.contains("aerogel") || id.contains("wings") || id.contains("harness")) return Type.AEROGEL_CORE;
            if (id.contains("element_core") || id.equals("core")) return Type.ELEMENT_CORE;
            if (id.contains("jetpack")) return Type.ELEMENTAL_JETPACK;
        }

        String name = stack.getName().getString().toLowerCase();
        for (Type t : Type.values()) {
            if (name.contains(t.getId().replace("_", " "))) {
                return t;
            }
        }
        if (name.contains("piston") || name.contains("thruster")) return Type.PNEUMATIC_PISTON;
        if (name.contains("aerogel") || name.contains("wings") || name.contains("harness")) return Type.AEROGEL_CORE;
        if (name.contains("element core") || (name.contains("core") && !name.contains("aerogel"))) return Type.ELEMENT_CORE;
        if (name.contains("jetpack")) return Type.ELEMENTAL_JETPACK;

        return null;
    }

    public static boolean isBlueprint(ItemStack stack) {
        return getBlueprintType(stack) != null;
    }

    public static boolean isBlueprint(ItemStack stack, Type type) {
        return getBlueprintType(stack) == type;
    }

    public static void ensureBlueprintMapInitialized(ItemStack stack, World world) {
        Type type = getBlueprintType(stack);
        if (type == null) return;
        if (world.isClient()) return;

        if (world instanceof ServerWorld serverWorld) {
            Integer mapId = FilledMapItem.getMapId(stack);
            MapState state = (mapId != null) ? FilledMapItem.getMapState(mapId, serverWorld) : null;

            if (state == null) {
                // Allocate dynamic map ID in this world
                int newMapId = serverWorld.getNextMapId();
                stack.getOrCreateNbt().putInt("map", newMapId);
                state = MapState.of(0.0, 0.0, (byte) 2, false, false, serverWorld.getRegistryKey());
                serverWorld.putMapState(FilledMapItem.getMapName(newMapId), state);
            }

            // Ensure name and blueprint tag are standardized
            stack.setCustomName(Text.literal(type.getTitle()));
            stack.getOrCreateNbt().putString(BLUEPRINT_TAG, type.getId());

            // Ensure display lore
            NbtCompound display = stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
            if (!display.contains(ItemStack.LORE_KEY)) {
                NbtList loreList = new NbtList();
                loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Ancient Skyforge Technical Schematic").formatted(Formatting.GRAY))));
                loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eCrafting: §f" + type.getRecipeDesc()))));
                loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal("§dDeliver to Aeron at camp to decipher recipe!").formatted(Formatting.LIGHT_PURPLE))));
                display.put(ItemStack.LORE_KEY, loreList);
            }

            applyBlueprintToMap(state, type);
        }
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
        if (player == null) return 0;
        java.util.EnumSet<Type> found = java.util.EnumSet.noneOf(Type.class);
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            Type type = getBlueprintType(stack);
            if (type != null) {
                found.add(type);
                if (found.size() == Type.values().length) {
                    break;
                }
            }
        }
        return found.size();
    }

    public static int countUniqueBlueprintsCombined(Iterable<? extends PlayerEntity> players) {
        if (players == null) return 0;
        java.util.EnumSet<Type> found = java.util.EnumSet.noneOf(Type.class);
        for (PlayerEntity player : players) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                Type type = getBlueprintType(stack);
                if (type != null) {
                    found.add(type);
                }
            }
            if (player.currentScreenHandler != null) {
                ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
                Type type = getBlueprintType(cursorStack);
                if (type != null) {
                    found.add(type);
                }
            }
        }
        return found.size();
    }

    public static boolean hasAllBlueprintsCombined(Iterable<? extends PlayerEntity> players) {
        return countUniqueBlueprintsCombined(players) >= Type.values().length;
    }

    public static int removeAllBlueprintMaps(PlayerEntity player) {
        if (player == null) return 0;
        int removed = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isBlueprint(stack)) {
                removed += stack.getCount();
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
        if (player.currentScreenHandler != null) {
            ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
            if (isBlueprint(cursorStack)) {
                removed += cursorStack.getCount();
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            }
        }
        player.getInventory().markDirty();
        if (player.playerScreenHandler != null) {
            player.playerScreenHandler.syncState();
        }
        return removed;
    }

    public static int removeAllBlueprintMapsFromAll(Iterable<? extends PlayerEntity> players) {
        if (players == null) return 0;
        int removed = 0;
        for (PlayerEntity p : players) {
            removed += removeAllBlueprintMaps(p);
        }
        return removed;
    }
}
