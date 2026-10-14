package com.elemental.client.model;

import com.elemental.Elemental;
import com.elemental.item.StormcallerHammerItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class StormcallerHammerModel extends GeoModel<StormcallerHammerItem> {
    private static final Identifier MODEL = Elemental.id("geo/stormcaller_hammer.geo.json");
    private static final Identifier TEXTURE = Elemental.id("textures/item/stormcaller_hammer.png");
    private static final Identifier ANIMATION = Elemental.id("animations/stormcaller_hammer.animation.json");

    @Override
    public Identifier getModelResource(StormcallerHammerItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(StormcallerHammerItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(StormcallerHammerItem animatable) {
        return ANIMATION;
    }
}
