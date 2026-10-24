package com.elemental.client.renderer;

import com.elemental.Elemental;
import com.elemental.entity.ElementalWizardEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

public class ElementalWizardRenderer extends BipedEntityRenderer<ElementalWizardEntity, PlayerEntityModel<ElementalWizardEntity>> {
    public static final Identifier TEXTURE_DEFAULT = Elemental.id("textures/entity/elemental_wizard.png");
    public static final Identifier TEXTURE_DESERT = Elemental.id("textures/entity/elemental_wizard_desert.png");
    public static final Identifier TEXTURE_SNOW = Elemental.id("textures/entity/elemental_wizard_snow.png");
    public static final Identifier TEXTURE_OCEAN = Elemental.id("textures/entity/elemental_wizard_ocean.png");
    public static final Identifier TEXTURE_MOUNTAIN = Elemental.id("textures/entity/elemental_wizard_mountain.png");
    public static final Identifier TEXTURE_NETHER = Elemental.id("textures/entity/elemental_wizard_nether.png");

    public ElementalWizardRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(ElementalWizardEntity entity) {
        if (entity == null || entity.getWorld() == null) {
            return TEXTURE_DEFAULT;
        }

        var pos = entity.getBlockPos();
        var biomeEntry = entity.getWorld().getBiome(pos);

        if (biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_NETHER)) {
            return TEXTURE_NETHER;
        }
        if (biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN)
                || biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_DEEP_OCEAN)
                || biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_RIVER)
                || biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_BEACH)) {
            return TEXTURE_OCEAN;
        }
        if (biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_BADLANDS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.DESERT)) {
            return TEXTURE_DESERT;
        }
        if (biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_MOUNTAIN)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.WINDSWEPT_HILLS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.WINDSWEPT_FOREST)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.STONY_PEAKS)) {
            return TEXTURE_MOUNTAIN;
        }
        if (biomeEntry.isIn(net.minecraft.registry.tag.BiomeTags.IS_TAIGA)
                || biomeEntry.value().isCold(pos)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.SNOWY_PLAINS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.ICE_SPIKES)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.FROZEN_PEAKS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.JAGGED_PEAKS)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.SNOWY_SLOPES)
                || biomeEntry.matchesKey(net.minecraft.world.biome.BiomeKeys.GROVE)) {
            return TEXTURE_SNOW;
        }

        return TEXTURE_DEFAULT;
    }

    @Override
    public void render(ElementalWizardEntity entity, float f, float g, net.minecraft.client.util.math.MatrixStack matrixStack, net.minecraft.client.render.VertexConsumerProvider vertexConsumerProvider, int i) {
        PlayerEntityModel<ElementalWizardEntity> model = this.getModel();
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
        super.render(entity, f, g, matrixStack, vertexConsumerProvider, i);
    }
}
