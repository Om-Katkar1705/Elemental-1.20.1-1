package com.elemental.client.model;

import com.elemental.Elemental;
import com.elemental.entity.ColossusMiniEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ColossusMiniModel extends GeoModel<ColossusMiniEntity> {

    @Override
    public Identifier getModelResource(ColossusMiniEntity animatable) {
        return Elemental.id("geo/colossus_mini.geo.json");
    }

    @Override
    public Identifier getTextureResource(ColossusMiniEntity animatable) {
        return Elemental.id("textures/entity/colossus_mini.png");
    }

    @Override
    public Identifier getAnimationResource(ColossusMiniEntity animatable) {
        return Elemental.id("animations/colossus_mini.animation.json");
    }
}
