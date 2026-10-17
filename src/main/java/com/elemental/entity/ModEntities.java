package com.elemental.entity;

import com.elemental.Elemental;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModEntities {
    public static final EntityType<SolarArcEntity> SOLAR_ARC = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("solar_arc"),
            FabricEntityTypeBuilder.<SolarArcEntity>create(SpawnGroup.MISC, SolarArcEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<EverlivingKnightEntity> EVERLIVING_KNIGHT = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("everliving_knight"),
            FabricEntityTypeBuilder.<EverlivingKnightEntity>create(SpawnGroup.MONSTER, EverlivingKnightEntity::new)
                    .dimensions(EntityDimensions.fixed(1.2f, 2.6f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<TidebreakerTridentEntity> TIDEBREAKER_TRIDENT_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("tidebreaker_trident_projectile"),
            FabricEntityTypeBuilder.<TidebreakerTridentEntity>create(SpawnGroup.MISC, TidebreakerTridentEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<TidebreakerBubbleEntity> TIDEBREAKER_BUBBLE = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("tidebreaker_bubble"),
            FabricEntityTypeBuilder.<TidebreakerBubbleEntity>create(SpawnGroup.MISC, TidebreakerBubbleEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<ChronicleGuideEntity> CHRONICLE_GUIDE = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("chronicle_guide"),
            FabricEntityTypeBuilder.<ChronicleGuideEntity>create(SpawnGroup.CREATURE, ChronicleGuideEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<RockyEntity> ROCKY = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("rocky"),
            FabricEntityTypeBuilder.<RockyEntity>create(SpawnGroup.CREATURE, RockyEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 0.9f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static void registerModEntities() {
        Elemental.LOGGER.info("Registering mod entities for " + Elemental.MOD_ID);
        FabricDefaultAttributeRegistry.register(EVERLIVING_KNIGHT, EverlivingKnightEntity.createKnightAttributes());
        FabricDefaultAttributeRegistry.register(CHRONICLE_GUIDE, ChronicleGuideEntity.createGuideAttributes());
        FabricDefaultAttributeRegistry.register(ROCKY, RockyEntity.createRockyAttributes());
    }
}
