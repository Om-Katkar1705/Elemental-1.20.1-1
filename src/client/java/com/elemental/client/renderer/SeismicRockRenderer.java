package com.elemental.client.renderer;

import com.elemental.entity.SeismicRockEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class SeismicRockRenderer extends FlyingItemEntityRenderer<SeismicRockEntity> {
    public SeismicRockRenderer(EntityRendererFactory.Context context) {
        super(context, 1.2f, true);
    }
}
