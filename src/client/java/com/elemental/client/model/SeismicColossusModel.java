package com.elemental.client.model;

import com.elemental.Elemental;
import com.elemental.entity.SeismicColossusEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class SeismicColossusModel extends GeoModel<SeismicColossusEntity> {

    @Override
    public Identifier getModelResource(SeismicColossusEntity animatable) {
        return Elemental.id("geo/seismic_colossus.geo.json");
    }

    @Override
    public Identifier getTextureResource(SeismicColossusEntity animatable) {
        return Elemental.id("textures/entity/seismic_colossus.png");
    }

    @Override
    public Identifier getAnimationResource(SeismicColossusEntity animatable) {
        return Elemental.id("animations/seismic_colossus.animation.json");
    }
}
