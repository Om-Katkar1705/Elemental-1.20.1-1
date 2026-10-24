package com.elemental.client.model;

import com.elemental.item.RootboundAxeItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class RootboundAxeModel extends GeoModel<RootboundAxeItem> {
    private static final Identifier MODEL = new Identifier("elemental", "geo/rootbound_axe.geo.json");
    private static final Identifier TEXTURE = new Identifier("elemental", "textures/item/rootbound_axe.png");
    private static final Identifier ANIMATION = new Identifier("elemental", "animations/rootbound_axe.animation.json");

    @Override
    public Identifier getModelResource(RootboundAxeItem animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(RootboundAxeItem animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RootboundAxeItem animatable) {
        return ANIMATION;
    }
}
