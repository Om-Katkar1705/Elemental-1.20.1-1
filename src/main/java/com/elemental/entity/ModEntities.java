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

    public static final EntityType<ElementalWizardEntity> ELEMENTAL_WIZARD = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("elemental_wizard"),
            FabricEntityTypeBuilder.<ElementalWizardEntity>create(SpawnGroup.CREATURE, ElementalWizardEntity::new)
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

    public static final EntityType<SeismicColossusEntity> SEISMIC_COLOSSUS = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("seismic_colossus"),
            FabricEntityTypeBuilder.<SeismicColossusEntity>create(SpawnGroup.MONSTER, SeismicColossusEntity::new)
                    .dimensions(EntityDimensions.fixed(2.5f, 3.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<ColossusMiniEntity> COLOSSUS_MINI = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("colossus_mini"),
            FabricEntityTypeBuilder.<ColossusMiniEntity>create(SpawnGroup.MONSTER, ColossusMiniEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 1.1f))
                    .trackRangeBlocks(32)
                    .trackedUpdateRate(3)
                    .build()
    );

    public static final EntityType<SeismicRockEntity> SEISMIC_ROCK = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("seismic_rock"),
            FabricEntityTypeBuilder.<SeismicRockEntity>create(SpawnGroup.MISC, SeismicRockEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<SeismicThrownBlockEntity> SEISMIC_THROWN_BLOCK = Registry.register(
            Registries.ENTITY_TYPE,
            Elemental.id("seismic_thrown_block"),
            FabricEntityTypeBuilder.<SeismicThrownBlockEntity>create(SpawnGroup.MISC, SeismicThrownBlockEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.9f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static void registerModEntities() {
        Elemental.LOGGER.info("Registering mod entities for " + Elemental.MOD_ID);
        FabricDefaultAttributeRegistry.register(EVERLIVING_KNIGHT, EverlivingKnightEntity.createKnightAttributes());
        FabricDefaultAttributeRegistry.register(CHRONICLE_GUIDE, ChronicleGuideEntity.createGuideAttributes());
        FabricDefaultAttributeRegistry.register(ELEMENTAL_WIZARD, ElementalWizardEntity.createWizardAttributes());
        FabricDefaultAttributeRegistry.register(ROCKY, RockyEntity.createRockyAttributes());
        FabricDefaultAttributeRegistry.register(SEISMIC_COLOSSUS, SeismicColossusEntity.createColossusAttributes());
        FabricDefaultAttributeRegistry.register(COLOSSUS_MINI, ColossusMiniEntity.createMiniAttributes());
    }
}
