package com.elemental.client.model;

import com.elemental.Elemental;
import com.elemental.entity.RockyEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class RockyModel extends GeoModel<RockyEntity> {

    @Override
    public Identifier getModelResource(RockyEntity animatable) {
        return Elemental.id("geo/rocky.geo.json");
    }

    @Override
    public Identifier getTextureResource(RockyEntity animatable) {
        return Elemental.id("textures/entity/rocky.png");
    }

    @Override
    public Identifier getAnimationResource(RockyEntity animatable) {
        return Elemental.id("animations/rocky.animation.json");
    }
}
