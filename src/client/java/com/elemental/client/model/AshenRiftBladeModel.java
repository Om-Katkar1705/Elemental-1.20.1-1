package com.elemental.client.model;

import com.elemental.Elemental;
import com.elemental.item.AshenRiftBladeItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class AshenRiftBladeModel extends GeoModel<AshenRiftBladeItem> {
    private static final Identifier MODEL = Elemental.id("geo/ashen_rift_blade.geo.json");
    private static final Identifier TEXTURE = Elemental.id("textures/item/ashen_rift_blade.png");
    private static final Identifier ANIMATION = Elemental.id("animations/ashen_rift_blade.animation.json");

    @Override
    public Identifier getModelResource(AshenRiftBladeItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(AshenRiftBladeItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AshenRiftBladeItem animatable) {
        return ANIMATION;
    }
}
