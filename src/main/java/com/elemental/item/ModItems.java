package com.elemental.item;

import com.elemental.Elemental;
import com.elemental.block.ModBlocks;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import com.elemental.entity.ModEntities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item SUNFORGED_SCIMITAR = Registry.register(
            Registries.ITEM,
            new Identifier("elemental", "sunforged_scimitar"),
            new SunforgedScimitarItem(
                    ScimitarToolMaterial.INSTANCE,
                    SunforgedScimitarItem.BASE_ATTACK_DAMAGE,
                    SunforgedScimitarItem.BASE_ATTACK_SPEED,
                    new FabricItemSettings().maxDamage(SunforgedScimitarItem.DURABILITY).rarity(Rarity.COMMON)
            )
    );
    public static final Item FROSTWAKE_PICK = Registry.register(
            Registries.ITEM,
            new Identifier(Elemental.MOD_ID, "frostwake_pick"),
            new FrostwakePickItem(
                    FrostwakePickToolMaterial.INSTANCE,
                    FrostwakePickItem.BASE_ATTACK_DAMAGE,
                    FrostwakePickItem.BASE_ATTACK_SPEED,
                    new FabricItemSettings().maxDamage(FrostwakePickItem.DURABILITY).rarity(Rarity.COMMON)
            )
    );
    public static final Item ROOTBOUND_AXE = Registry.register(
            Registries.ITEM,
            new Identifier(Elemental.MOD_ID, "rootbound_axe"),
            new RootboundAxeItem(
                    RootboundAxeToolMaterial.INSTANCE,
                    RootboundAxeItem.BASE_ATTACK_DAMAGE,
                    RootboundAxeItem.BASE_ATTACK_SPEED,
                    new FabricItemSettings().maxDamage(RootboundAxeItem.DURABILITY).rarity(Rarity.COMMON)
            )
    );
    public static final Item TIDEBREAKER_TRIDENT = Registry.register(
            Registries.ITEM,
            new Identifier(Elemental.MOD_ID, "tidebreaker_trident"),
            new TidebreakerTridentItem(
                    new FabricItemSettings().maxDamage(TidebreakerTridentItem.MAX_DURABILITY).rarity(Rarity.RARE)
            )
    );
    public static final Item STORMCALLER_HAMMER = Registry.register(
            Registries.ITEM,
            new Identifier(Elemental.MOD_ID, "stormcaller_hammer"),
            new StormcallerHammerItem(
                    new FabricItemSettings().maxDamage(StormcallerHammerItem.DURABILITY).rarity(Rarity.RARE)
            )
    );
    public static final Item ASHEN_RIFT_BLADE = Registry.register(
            Registries.ITEM,
            new Identifier(Elemental.MOD_ID, "ashen_rift_blade"),
            new AshenRiftBladeItem(
                    new FabricItemSettings().maxDamage(AshenRiftBladeItem.DURABILITY).rarity(Rarity.EPIC)
            )
    );


    public static final Item STENCIL = registerItem("stencil",
            new Item(new FabricItemSettings()));
    public static final Item ELEMENT_CORE = registerItem("element_core",
            new Item(new FabricItemSettings()));
    public static final Item DRY_HEAT_ELEMENT = registerItem("dry_heat_element",
            new Item(new FabricItemSettings()));
    public static final Item SNOW_ELEMENT = registerItem("snow_element",
            new Item(new FabricItemSettings()));
    public static final Item TREE_ELEMENT = registerItem("tree_element",
            new Item(new FabricItemSettings()));
    public static final Item OCEAN_ELEMENT = registerItem("ocean_element",
            new Item(new FabricItemSettings()));
    public static final Item MOUNTAIN_ELEMENT = registerItem("mountain_element",
            new Item(new FabricItemSettings()));
    public static final Item FIRE_ELEMENT = registerItem("fire_element",
            new Item(new FabricItemSettings()));

    public static final Item PNEUMATIC_PISTON = registerItem("pneumatic_piston",
            new Item(new FabricItemSettings()));
    public static final Item AEROGEL_CORE = registerItem("aerogel_core",
            new Item(new FabricItemSettings()));
    public static final Item ELEMENTAL_JETPACK = registerItem("elemental_jetpack",
            new ElementalJetpackItem(new FabricItemSettings().maxDamage(480).rarity(Rarity.RARE)));

    public static final Item ROCKY_SPAWN_EGG = registerItem("rocky_spawn_egg",
            new SpawnEggItem(ModEntities.ROCKY, 0x3E3832, 0x247A70, new FabricItemSettings()));

    public static void registerModItems() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(ROCKY_SPAWN_EGG);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(SUNFORGED_SCIMITAR);
            entries.add(ROOTBOUND_AXE);
            entries.add(TIDEBREAKER_TRIDENT);
            entries.add(STORMCALLER_HAMMER);
            entries.add(ASHEN_RIFT_BLADE);
            entries.add(ELEMENTAL_JETPACK);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(SUNFORGED_SCIMITAR);
            entries.add(FROSTWAKE_PICK);
            entries.add(ROOTBOUND_AXE);
            entries.add(TIDEBREAKER_TRIDENT);
            entries.add(STORMCALLER_HAMMER);
            entries.add(ASHEN_RIFT_BLADE);
            entries.add(ELEMENTAL_JETPACK);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(STENCIL);
            entries.add(ELEMENT_CORE);
            entries.add(DRY_HEAT_ELEMENT);
            entries.add(SNOW_ELEMENT);
            entries.add(TREE_ELEMENT);
            entries.add(OCEAN_ELEMENT);
            entries.add(MOUNTAIN_ELEMENT);
            entries.add(FIRE_ELEMENT);
            entries.add(PNEUMATIC_PISTON);
            entries.add(AEROGEL_CORE);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ModBlocks.WEAPONER);
            entries.add(ModBlocks.EVERLIVING_ALTAR);
        });
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Elemental.MOD_ID, name), item);
    }
}
